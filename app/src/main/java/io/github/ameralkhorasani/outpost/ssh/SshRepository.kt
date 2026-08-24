package io.github.ameralkhorasani.outpost.ssh

import io.github.ameralkhorasani.outpost.data.model.ServerEntity
import io.github.ameralkhorasani.outpost.data.model.ServerStats
import io.github.ameralkhorasani.outpost.data.security.SecureKeyManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.schmizz.sshj.Config
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.IOUtils
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.password.PasswordUtils
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SshRepository @Inject constructor(
    private val secureKeyManager: SecureKeyManager
) {
    private companion object {
        const val KEEP_ALIVE_SECONDS = 20
    }

    /**
     * One Config shared by every client.
     *
     * DefaultConfig wraps its PRNG in a SingletonRandomFactory - but that singleton is
     * per Config, so constructing a Config per connection re-seeds SecureRandom every
     * time. Doing that from several threads at once makes the constructors contend for
     * system entropy and stall, which is the documented cause of `new SSHClient()`
     * hanging (sshj issue #611). Sharing the Config seeds the PRNG once for the process.
     */
    private val sharedConfig: Config by lazy { DefaultConfig() }

    /**
     * Client construction is cheap once the Config is shared, but serialising it removes
     * the last of the start-up contention and costs nothing in practice.
     */
    private val constructionMutex = Mutex()

    /**
     * Connects to a remote SSH server using decrypted private key authentication.
     */
    suspend fun connect(
        server: ServerEntity,
        keepAlive: Boolean = false
    ): Result<SSHClient> = withContext(Dispatchers.IO) {
        try {
            val client = constructionMutex.withLock { SSHClient(sharedConfig) }
            client.addHostKeyVerifier(PromiscuousVerifier())
            client.connectTimeout = 10000
            client.timeout = 10000
            client.connect(server.host, server.port)

            val decryptedPem = normalizePem(secureKeyManager.decryptPrivateKey(server.encryptedPrivateKey))
            validatePrivateKey(decryptedPem)

            val passphraseStr = server.keyPassphrase?.let { secureKeyManager.decryptPrivateKey(it) }
            val passwordFinder = passphraseStr
                ?.takeIf { it.isNotEmpty() }
                ?.let { PasswordUtils.createOneOff(it.toCharArray()) }

            // The three-argument overload takes the key *content*. The single-argument
            // one treats its String as a filesystem path, so passing PEM text to it
            // fails with ENAMETOOLONG. Always use this form.
            val keyProvider = client.loadKeys(decryptedPem, null, passwordFinder)

            client.authPublickey(server.username, keyProvider)

            if (client.isAuthenticated) {
                if (keepAlive) startKeepAlive(client)
                Result.success(client)
            } else {
                client.disconnect()
                Result.failure(Exception("Authentication failed for ${server.username}@${server.host}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(describeFailure(e, server), e))
        }
    }

    /**
     * Normalises a pasted key: strips surrounding whitespace, converts Windows line
     * endings, and guarantees the trailing newline that PEM parsers expect.
     */
    private fun normalizePem(raw: String): String =
        raw.replace("\r\n", "\n").replace("\r", "\n").trim() + "\n"

    private fun validatePrivateKey(pem: String) {
        val firstLine = pem.lineSequence().firstOrNull()?.trim().orEmpty()

        // A public key is a single line like "ssh-ed25519 AAAA... comment". It is a
        // common mix-up and produces a baffling error further down if left to sshj.
        if (firstLine.startsWith("ssh-") || firstLine.startsWith("ecdsa-")) {
            throw IllegalArgumentException(
                "That looks like a public key (.pub). Paste the private key instead - " +
                    "the file without the .pub extension, starting with " +
                    "-----BEGIN OPENSSH PRIVATE KEY-----"
            )
        }

        if (!firstLine.startsWith("-----BEGIN") || !pem.contains("PRIVATE KEY")) {
            throw IllegalArgumentException(
                "The private key does not look like a PEM key. It should begin with " +
                    "-----BEGIN OPENSSH PRIVATE KEY----- and end with the matching END line."
            )
        }
    }

    /**
     * Produces a short, actionable message. Deliberately never echoes the exception's
     * own text when it may contain key material - sshj embeds the whole key in some
     * errors, which would otherwise be splashed across the screen.
     */
    private fun describeFailure(e: Exception, server: ServerEntity): String {
        if (e is IllegalArgumentException) return e.message.orEmpty()

        val raw = e.message.orEmpty()
        val mentionsKeyMaterial = raw.contains("PRIVATE KEY") || raw.contains("ENAMETOOLONG")

        return when {
            mentionsKeyMaterial ->
                "Could not read the private key. Check that the whole key was pasted, " +
                    "including the BEGIN and END lines."

            e is UserAuthException ->
                "Server refused the key for '${server.username}'. Confirm the matching " +
                    "public key is in ~/.ssh/authorized_keys for that user."

            raw.contains("passphrase", ignoreCase = true) ||
                raw.contains("decrypt", ignoreCase = true) ->
                "The key is passphrase-protected. Enter its passphrase in the server settings."

            e is UnknownHostException -> "Host '${server.host}' could not be resolved."

            e is SocketTimeoutException ->
                "Timed out reaching ${server.host}:${server.port}. Check the host, port and firewall."

            e is ConnectException ->
                "Connection refused by ${server.host}:${server.port}. Is SSH listening on that port?"

            raw.isNotBlank() -> raw.lines().first().take(200)

            else -> "Connection failed (${e.javaClass.simpleName})"
        }
    }

    /**
     * Starts sshj's heartbeat, which keeps idle sessions (notably the code-server
     * tunnel) from being dropped by NAT and firewall timeouts.
     *
     * Only worth doing for connections that stay open. Short-lived ones - a connection
     * test, a metrics probe - disconnect straight after authenticating, and the
     * heartbeat's first packet (sent immediately, with no initial sleep) then races
     * the teardown. sshj reacts to that write failing by calling Transport.die(),
     * which can throw out of its own thread and take the process down with it.
     *
     * This must happen only once the connection is fully established. sshj starts its
     * keep-alive thread inside onConnect(), *before* the key exchange runs, and the
     * thread sends its first SSH_MSG_IGNORE immediately rather than sleeping first.
     * Enabling the interval before connect() therefore injects a stray packet into the
     * initial key exchange, and any server with Terrapin (strict KEX) protection - every
     * OpenSSH 9.x - drops the connection with "strict KEX violation: unexpected packet
     * type 2 (seqnr 1)". Starting the thread here instead keeps the handshake clean.
     */
    private fun startKeepAlive(client: SSHClient) {
        runCatching {
            val keepAlive = client.connection.keepAlive
            if (keepAlive.state == Thread.State.NEW) {
                keepAlive.keepAliveInterval = KEEP_ALIVE_SECONDS
                keepAlive.start()
            }
        }
    }

    /**
     * Executes a single command via SSH exec session and returns stdout.
     */
    suspend fun executeCommand(
        client: SSHClient,
        command: String,
        timeoutSeconds: Long = 5
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            client.startSession().use { session ->
                val cmd = session.exec(command)
                // Decode via the byte array, not ByteArrayOutputStream.toString(Charset):
                // that overload only exists from API 33, so on anything older this - the
                // call every screen in the app funnels through - died with NoSuchMethodError
                // while still compiling cleanly against compileSdk 35.
                val output = String(
                    IOUtils.readFully(cmd.inputStream).toByteArray(),
                    Charsets.UTF_8
                )
                cmd.join(timeoutSeconds, TimeUnit.SECONDS)
                Result.success(output)
            }
        } catch (e: CancellationException) {
            // Cancellation is not a command failure. Turning it into Result.failure
            // leaves the caller running inside a cancelled coroutine, where the next
            // suspend call throws somewhere far less obvious.
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Executes a long-running command and emits stdout line by line as it arrives,
     * so the UI can show progress instead of freezing until completion.
     * Callers should redirect stderr into stdout (`2>&1`) if they want it included.
     */
    fun executeStreaming(
        client: SSHClient,
        command: String,
        timeoutSeconds: Long = 900
    ): Flow<String> = flow {
        val session = client.startSession()
        try {
            val cmd = session.exec(command)
            val reader = cmd.inputStream.bufferedReader(Charsets.UTF_8)
            while (true) {
                val line = reader.readLine() ?: break
                emit(line)
            }
            cmd.join(timeoutSeconds, TimeUnit.SECONDS)
        } finally {
            try {
                session.close()
            } catch (_: Exception) {
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Streams real-time ServerStats every 1.5 seconds by executing batch proc commands.
     */
    fun getRealtimeStats(client: SSHClient): Flow<ServerStats> = flow {
        val parser = StatsParser()

        while (client.isConnected && client.isAuthenticated) {
            readStats(client, parser)?.let { emit(it) }
            delay(1500)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Takes a single metrics reading, for callers that want one snapshot rather than a
     * stream.
     *
     * Two samples are needed: CPU and network figures are deltas between readings, so
     * the first pass only primes the parser and always reports 0%.
     *
     * Collecting the endless [getRealtimeStats] flow and cancelling it after two values
     * would do the same job, but cancelling mid-command leaves the loop calling into a
     * cancelled coroutine - which is how this used to crash the app.
     */
    suspend fun sampleStats(client: SSHClient): ServerStats? = withContext(Dispatchers.IO) {
        val parser = StatsParser()
        readStats(client, parser) ?: return@withContext null
        delay(1200)
        readStats(client, parser)
    }

    private suspend fun readStats(client: SSHClient, parser: StatsParser): ServerStats? {
        val combinedCommand = "cat /proc/stat; echo '---SPLIT---'; " +
                "cat /proc/meminfo; echo '---SPLIT---'; " +
                "cat /proc/loadavg; echo '---SPLIT---'; " +
                "cat /proc/net/dev; echo '---SPLIT---'; " +
                "df /; echo '---SPLIT---'; " +
                "cat /proc/uptime"

        val rawOutput = executeCommand(client, combinedCommand).getOrNull() ?: return null
        val sections = rawOutput.split("---SPLIT---")
        if (sections.size < 6) return null

        return parser.buildServerStats(
            procStat = sections[0],
            procMem = sections[1],
            procLoad = sections[2],
            procNet = sections[3],
            df = sections[4],
            uptime = sections[5]
        )
    }

    /**
     * Starts an interactive SSH Shell session for terminal stream I/O.
     *
     * [cols] and [rows] come from the measured terminal view. allocateDefaultPTY() asks
     * for a 80x24 vt100, which on a phone means the shell wraps lines at a width the
     * display does not have: long commands and anything full-screen (top, htop, nano,
     * vim) redraw over themselves. xterm-256color also gets the colours and key
     * sequences the front end actually speaks.
     */
    suspend fun openShellSession(
        client: SSHClient,
        cols: Int = 80,
        rows: Int = 24
    ): Result<Session.Shell> = withContext(Dispatchers.IO) {
        try {
            val session = client.startSession()
            session.allocatePTY(
                "xterm-256color",
                cols.coerceIn(20, 500),
                rows.coerceIn(5, 200),
                0,
                0,
                emptyMap()
            )
            val shell = session.startShell()
            Result.success(shell)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Closes an SSH connection safely.
     */
    suspend fun disconnect(client: SSHClient?) = withContext(Dispatchers.IO) {
        if (client == null) return@withContext
        // Stop the heartbeat before tearing the transport down. Once its interrupt flag
        // is set, sshj's keep-alive thread exits quietly instead of treating the closing
        // socket as a fatal transport error.
        runCatching { client.connection.keepAlive.interrupt() }
        try {
            if (client.isConnected) {
                client.disconnect()
            }
        } catch (_: Exception) {
        }
    }
}
