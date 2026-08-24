package io.github.ameralkhorasani.outpost.ssh.editor

import io.github.ameralkhorasani.outpost.ssh.SshRepository
import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * State of code-server on the remote host, as reported by [CodeServerRepository.checkStatus].
 */
data class CodeServerStatus(
    val installed: Boolean = false,
    val listening: Boolean = false,
    val version: String? = null,
    val bindAddr: String? = null,
    val authMode: String? = null,
    /** Password read back from the remote config.yaml, when auth is password-based. */
    val remotePassword: String? = null,
    /** Which probe found the listener - useful when explaining what was detected. */
    val detectedBy: String? = null,
    /**
     * Loopback address on the server that actually answered, and therefore the one the
     * forward should terminate on. Defaults to IPv4 loopback, but becomes ::1 for a
     * service listening on IPv6 loopback only.
     */
    val connectHost: String = "127.0.0.1"
) {
    /**
     * Whether the tunnel can be opened.
     *
     * Deliberately keyed off [listening] alone. `ssh -L 8090:127.0.0.1:8090` does not
     * care what is on the far end, and neither should this: anything serving that port
     * - code-server started by hand, run from a container, installed somewhere the
     * probe's PATH never sees, or an entirely different editor - is forwardable. Also
     * requiring [installed] meant a perfectly reachable editor got hidden behind an
     * install prompt because `command -v code-server` came up empty in a non-login shell.
     */
    val ready: Boolean get() = listening
}

/**
 * Progress event emitted while installing/configuring code-server on the VPS.
 */
sealed class SetupEvent {
    data class Output(val line: String) : SetupEvent()
    data class Finished(val success: Boolean, val password: String?, val port: Int?) : SetupEvent()
}

@Singleton
class CodeServerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sshRepository: SshRepository
) {
    private companion object {
        const val SETUP_SCRIPT_ASSET = "scripts/setup_code_server.sh"
    }

    /**
     * Inspects the remote host: is code-server installed, is it listening on [port],
     * and how is it configured.
     */
    suspend fun checkStatus(client: SSHClient, port: Int): Result<CodeServerStatus> =
        withContext(Dispatchers.IO) {
            sshRepository.executeCommand(client, statusProbe(port)).map { output ->
                val fields = output.lineSequence()
                    .mapNotNull { line ->
                        val idx = line.indexOf('=')
                        if (idx <= 0) null else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
                    }
                    .toMap()

                CodeServerStatus(
                    installed = fields["CS_INSTALLED"] == "1",
                    listening = fields["CS_LISTENING"] == "1",
                    version = fields["CS_VERSION"]?.takeIf { it.isNotBlank() },
                    bindAddr = fields["CS_BIND"]?.takeIf { it.isNotBlank() },
                    authMode = fields["CS_AUTH"]?.takeIf { it.isNotBlank() },
                    remotePassword = fields["CS_PASSWORD"]?.takeIf { it.isNotBlank() },
                    detectedBy = fields["CS_METHOD"]?.takeIf { it.isNotBlank() && it != "none" },
                    connectHost = fields["CS_HOST"]?.takeIf { it.isNotBlank() } ?: "127.0.0.1"
                )
            }
        }

    /**
     * Shell probe reporting whether [port] is being served, plus whatever it can learn
     * about a code-server install behind it.
     *
     * The listener check tries three independent methods, because relying on `ss` alone
     * reported "nothing there" on hosts that simply do not ship iproute2 - and that false
     * negative is what pushed people into a reinstall they did not need:
     *
     *  1. `ss` / `netstat` - fast, but absent on minimal and container images.
     *  2. `/proc/net/tcp{,6}` - no tooling at all, just the kernel's own table. Ports are
     *     4-digit uppercase hex there, and state `0A` is LISTEN.
     *  3. A real TCP connect via bash's `/dev/tcp`. This is the same thing the tunnel
     *     will do, so it is the most faithful test - it also catches a listener the first
     *     two miss, e.g. one inside a container with its own network namespace mapped in.
     *
     * Only binds that 127.0.0.1 can actually reach count - the wildcard addresses and
     * the loopback addresses themselves. Accepting *any* bind address looks tempting but
     * is wrong: something listening on a Docker bridge or a LAN address answers on that
     * interface only, so the probe says "found it", the tunnel opens, and the editor
     * loads to a connection-refused page with no explanation.
     */
    private fun statusProbe(port: Int): String {
        val hexPort = "%04X".format(port)
        return buildString {
            append("export PATH=\"\$HOME/.local/bin:/usr/local/bin:\$PATH\"; ")

            append("if command -v code-server >/dev/null 2>&1; then echo \"CS_INSTALLED=1\"; ")
            append("echo \"CS_VERSION=\$(code-server --version 2>/dev/null | head -n1)\"; ")
            append("else echo \"CS_INSTALLED=0\"; fi; ")

            append("M=none; H=127.0.0.1; ")

            // Authoritative, and first for that reason: this is the exact thing the
            // tunnel will do. bash only - a dash login shell just fails to open the path.
            // Both families are tried because a service bound to ::1 alone refuses IPv4
            // loopback, and forwarding to 127.0.0.1 then lands on a closed port.
            append("if (exec 3<>/dev/tcp/127.0.0.1/$port) 2>/dev/null; ")
            append("then exec 3>&- 2>/dev/null; exec 3<&- 2>/dev/null; ")
            append("M=tcp-connect; H=127.0.0.1; fi; ")
            append("if [ \"\$M\" = none ] && (exec 3<>/dev/tcp/::1/$port) 2>/dev/null; ")
            append("then exec 3>&- 2>/dev/null; exec 3<&- 2>/dev/null; ")
            append("M=tcp-connect; H=::1; fi; ")

            // Kernel socket table, no tooling required. Ports are 4-digit uppercase hex
            // and 0A is LISTEN. Only wildcard and loopback binds count - see below.
            // An all-zero IPv6 address is the dual-stack wildcard and is reachable over
            // IPv4 loopback; ::1 is not, so that one switches the forward's target.
            append("if [ \"\$M\" = none ]; then R=\$(")
            append("awk -v p=\"$hexPort\" '\$4==\"0A\" { ")
            append("n=split(\$2,a,\":\"); if (a[2]!=p) next; ")
            append("if (a[1]==\"00000000\" || a[1]==\"0100007F\" || ")
            append("a[1]==\"00000000000000000000000000000000\") { print \"v4\"; exit } ")
            append("if (a[1]==\"00000000000000000000000001000000\") { print \"v6only\"; exit } ")
            append("}' /proc/net/tcp /proc/net/tcp6 2>/dev/null); ")
            append("if [ -n \"\$R\" ]; then M=proc-net; ")
            append("if [ \"\$R\" = v6only ]; then H=::1; fi; fi; fi; ")

            append("if [ \"\$M\" = none ] && (ss -ltn 2>/dev/null; netstat -ltn 2>/dev/null) ")
            append("| grep -qE '(^|[[:space:]])(0\\.0\\.0\\.0|127\\.0\\.0\\.1|\\*|")
            append("\\[::\\]|\\[::1\\]|::|::1):$port([[:space:]]|\$)'; ")
            append("then M=socket-table; fi; ")
            append("echo \"CS_HOST=\$H\"; ")

            append("if [ \"\$M\" = none ]; then echo \"CS_LISTENING=0\"; ")
            append("else echo \"CS_LISTENING=1\"; fi; ")
            append("echo \"CS_METHOD=\$M\"; ")

            append("CFG=\"\$HOME/.config/code-server/config.yaml\"; ")
            append("echo \"CS_BIND=\$(grep '^bind-addr:' \"\$CFG\" 2>/dev/null | head -n1 | sed 's/^bind-addr:[[:space:]]*//')\"; ")
            append("echo \"CS_AUTH=\$(grep '^auth:' \"\$CFG\" 2>/dev/null | head -n1 | sed 's/^auth:[[:space:]]*//')\"; ")
            append("echo \"CS_PASSWORD=\$(grep '^password:' \"\$CFG\" 2>/dev/null | head -n1 | sed 's/^password:[[:space:]]*//')\"")
        }
    }

    /**
     * Ships the bundled setup script to the VPS and runs it, streaming its output back.
     *
     * The script is base64-encoded on the way over so that quoting, newlines and any
     * shell metacharacters survive the trip through the remote shell intact.
     */
    fun runSetup(client: SSHClient, port: Int, password: String? = null): Flow<SetupEvent> = flow {
        val script = context.assets.open(SETUP_SCRIPT_ASSET).use { it.readBytes() }
        val encoded = Base64.encodeToString(script, Base64.NO_WRAP)
        val args = buildString {
            append(port)
            if (!password.isNullOrBlank()) append(" '").append(password.replace("'", "'\\''")).append("'")
        }
        val command = "echo '$encoded' | base64 -d | bash -s -- $args 2>&1"

        var resultPassword: String? = null
        var resultPort: Int? = null
        var success = false

        sshRepository.executeStreaming(client, command).collect { line ->
            when {
                line.startsWith("OUTPOST_RESULT_PASSWORD=") ->
                    resultPassword = line.removePrefix("OUTPOST_RESULT_PASSWORD=").trim()
                line.startsWith("OUTPOST_RESULT_PORT=") ->
                    resultPort = line.removePrefix("OUTPOST_RESULT_PORT=").trim().toIntOrNull()
                line.startsWith("OUTPOST_RESULT_OK=") ->
                    success = line.removePrefix("OUTPOST_RESULT_OK=").trim() == "1"
                else -> emit(SetupEvent.Output(line))
            }
        }

        emit(SetupEvent.Finished(success, resultPassword, resultPort))
    }.flowOn(Dispatchers.IO)

    /**
     * Restarts code-server remotely, e.g. after the user changes its configuration.
     */
    suspend fun restart(client: SSHClient, port: Int): Result<String> = withContext(Dispatchers.IO) {
        val command = buildString {
            append("export PATH=\"\$HOME/.local/bin:/usr/local/bin:\$PATH\"; ")
            append("U=\$(id -un); ")
            append("if command -v systemctl >/dev/null 2>&1 && systemctl is-enabled \"code-server@\$U\" >/dev/null 2>&1; then ")
            append("(systemctl restart \"code-server@\$U\" 2>/dev/null || sudo -n systemctl restart \"code-server@\$U\" 2>&1); ")
            append("else pkill -u \$(id -u) -f 'code-server.*--bind-addr' >/dev/null 2>&1; sleep 1; ")
            append("nohup code-server --bind-addr 127.0.0.1:$port > \"\$HOME/.code-server.log\" 2>&1 & fi; ")
            append("echo restarted")
        }
        sshRepository.executeCommand(client, command)
    }
}
