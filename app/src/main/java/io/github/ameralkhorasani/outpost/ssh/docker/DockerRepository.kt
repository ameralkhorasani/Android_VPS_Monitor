package io.github.ameralkhorasani.outpost.ssh.docker

import io.github.ameralkhorasani.outpost.ssh.SshRepository
import io.github.ameralkhorasani.outpost.data.model.DockerAvailability
import io.github.ameralkhorasani.outpost.data.model.DockerContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DockerRepository @Inject constructor(
    private val sshRepository: SshRepository
) {
    private companion object {
        /** Container ids and names we are willing to interpolate into a shell command. */
        val SAFE_REF = Regex("^[A-Za-z0-9_.-]+$")
        const val SEP = "\t" // docker --format turns \t in the template into a real tab
    }

    /**
     * Determines whether docker is installed and reachable. Being in the `docker` group is
     * not a given, so this also probes whether passwordless sudo gets us to the daemon.
     */
    suspend fun checkAvailability(client: SSHClient): DockerAvailability =
        withContext(Dispatchers.IO) {
            val probe = buildString {
                append("export PATH=\"/usr/local/bin:/usr/bin:/bin:\$PATH\"; ")
                append("if command -v docker >/dev/null 2>&1; then echo INSTALLED=1; else echo INSTALLED=0; fi; ")
                append("echo VERSION=\$(docker --version 2>/dev/null); ")
                append("if docker ps >/dev/null 2>&1; then echo MODE=direct; ")
                append("elif sudo -n docker ps >/dev/null 2>&1; then echo MODE=sudo; ")
                append("else echo MODE=denied; fi")
            }

            val output = sshRepository.executeCommand(client, probe).getOrElse {
                return@withContext DockerAvailability(message = it.message)
            }

            val fields = output.parseKeyValues()
            val installed = fields["INSTALLED"] == "1"
            val mode = fields["MODE"].orEmpty()

            DockerAvailability(
                installed = installed,
                usable = installed && (mode == "direct" || mode == "sudo"),
                commandPrefix = if (mode == "sudo") "sudo -n docker" else "docker",
                version = fields["VERSION"]?.takeIf { it.isNotBlank() },
                message = when {
                    !installed -> "Docker is not installed on this server."
                    mode == "denied" ->
                        "This SSH user cannot reach the Docker daemon, and passwordless " +
                            "sudo is not available either. Add the user to the docker group " +
                            "on the server, then reconnect."
                    else -> null
                }
            )
        }

    /**
     * Lists every container, running or not, and merges in live CPU/memory for the
     * running ones. `docker stats` only reports running containers, hence the merge.
     */
    suspend fun listContainers(
        client: SSHClient,
        prefix: String
    ): Result<List<DockerContainer>> = withContext(Dispatchers.IO) {
        val psCommand =
            "$prefix ps -a --format '{{.ID}}\\t{{.Names}}\\t{{.Image}}\\t{{.State}}\\t{{.Status}}\\t{{.Ports}}' 2>&1"

        val psOutput = sshRepository.executeCommand(client, psCommand)
            .getOrElse { return@withContext Result.failure(it) }

        val containers = psOutput.lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() && it.contains(SEP) }
            .mapNotNull { line ->
                val cols = line.split(SEP)
                if (cols.size < 5) return@mapNotNull null
                val status = cols[4].trim()
                DockerContainer(
                    id = cols[0].trim(),
                    name = cols[1].trim(),
                    image = cols[2].trim(),
                    // .State is unavailable on older docker builds; fall back to the
                    // status text, which starts with "Up" only while running.
                    state = cols[3].trim().ifBlank { deriveState(status) },
                    status = status,
                    ports = cols.getOrNull(5)?.trim().orEmpty()
                )
            }
            .toList()

        if (containers.none { it.isRunning }) {
            return@withContext Result.success(containers)
        }

        val statsCommand =
            "$prefix stats --no-stream --format '{{.ID}}\\t{{.CPUPerc}}\\t{{.MemUsage}}\\t{{.MemPerc}}' 2>/dev/null"
        val statsOutput = sshRepository.executeCommand(client, statsCommand).getOrNull().orEmpty()

        val statsById = statsOutput.lineSequence()
            .filter { it.contains(SEP) }
            .mapNotNull { line ->
                val cols = line.split(SEP)
                if (cols.size < 4) return@mapNotNull null
                val (used, limit) = parseMemUsage(cols[2])
                cols[0].trim() to LiveStats(
                    cpuPercent = cols[1].trim().removeSuffix("%").toFloatOrNull() ?: 0f,
                    memUsedMb = used,
                    memLimitMb = limit,
                    memPercent = cols[3].trim().removeSuffix("%").toFloatOrNull() ?: 0f
                )
            }
            .toMap()

        Result.success(
            containers.map { container ->
                // docker stats reports short ids; match on the shared prefix.
                val stats = statsById.entries.firstOrNull { (id, _) ->
                    container.id.startsWith(id) || id.startsWith(container.id)
                }?.value ?: return@map container

                container.copy(
                    cpuPercent = stats.cpuPercent,
                    memUsedMb = stats.memUsedMb,
                    memLimitMb = stats.memLimitMb,
                    memPercent = stats.memPercent
                )
            }
        )
    }

    private data class LiveStats(
        val cpuPercent: Float,
        val memUsedMb: Float,
        val memLimitMb: Float,
        val memPercent: Float
    )

    suspend fun start(client: SSHClient, prefix: String, ref: String) =
        runAction(client, prefix, "start", ref)

    suspend fun stop(client: SSHClient, prefix: String, ref: String) =
        runAction(client, prefix, "stop", ref)

    suspend fun restart(client: SSHClient, prefix: String, ref: String) =
        runAction(client, prefix, "restart", ref)

    private suspend fun runAction(
        client: SSHClient,
        prefix: String,
        action: String,
        ref: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!SAFE_REF.matches(ref)) {
            return@withContext Result.failure(IllegalArgumentException("Invalid container reference"))
        }
        // stop/restart wait for the container's grace period; give it room.
        sshRepository.executeCommand(client, "$prefix $action $ref 2>&1", timeoutSeconds = 60)
    }

    /**
     * Follows a container's logs. stderr is folded into stdout because most images log
     * to stderr by default.
     */
    fun streamLogs(
        client: SSHClient,
        prefix: String,
        ref: String,
        tail: Int = 200
    ): Flow<String> {
        require(SAFE_REF.matches(ref)) { "Invalid container reference" }
        return sshRepository.executeStreaming(
            client,
            "$prefix logs -f --tail $tail $ref 2>&1",
            // Only reached once the stream ends; a day is effectively "no limit" here
            // without risking overflow inside sshj's join().
            timeoutSeconds = 86_400
        )
    }

    private fun deriveState(status: String): String = when {
        status.startsWith("Up", ignoreCase = true) && status.contains("Paused", true) -> "paused"
        status.startsWith("Up", ignoreCase = true) -> "running"
        status.startsWith("Created", ignoreCase = true) -> "created"
        status.startsWith("Restarting", ignoreCase = true) -> "restarting"
        else -> "exited"
    }

    /**
     * Parses docker's "123.4MiB / 1.944GiB" memory column into megabytes.
     */
    private fun parseMemUsage(raw: String): Pair<Float, Float> {
        val parts = raw.split("/")
        if (parts.size < 2) return 0f to 0f
        return toMegabytes(parts[0].trim()) to toMegabytes(parts[1].trim())
    }

    private fun toMegabytes(value: String): Float {
        val match = Regex("([0-9.]+)\\s*([A-Za-z]+)").find(value) ?: return 0f
        val number = match.groupValues[1].toFloatOrNull() ?: return 0f
        return when (match.groupValues[2].lowercase()) {
            "b" -> number / (1024f * 1024f)
            "kb", "kib" -> number / 1024f
            "mb", "mib" -> number
            "gb", "gib" -> number * 1024f
            "tb", "tib" -> number * 1024f * 1024f
            else -> 0f
        }
    }

    private fun String.parseKeyValues(): Map<String, String> =
        lineSequence().mapNotNull { line ->
            val idx = line.indexOf('=')
            if (idx <= 0) null else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
        }.toMap()
}
