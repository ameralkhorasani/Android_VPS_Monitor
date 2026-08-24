package io.github.ameralkhorasani.outpost.ui.screens.code

import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ameralkhorasani.outpost.data.db.ServerDao
import io.github.ameralkhorasani.outpost.data.model.ServerEntity
import io.github.ameralkhorasani.outpost.data.security.SecureKeyManager
import io.github.ameralkhorasani.outpost.ssh.editor.CodeServerRepository
import io.github.ameralkhorasani.outpost.ssh.editor.CodeServerStatus
import io.github.ameralkhorasani.outpost.ssh.tunnel.SshPortForward
import io.github.ameralkhorasani.outpost.ssh.editor.SetupEvent
import io.github.ameralkhorasani.outpost.ssh.SshRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.schmizz.sshj.SSHClient
import javax.inject.Inject

/** What the screen is currently doing. */
enum class CodeStage {
    CONNECTING,     // opening the SSH connection
    CHECKING,       // probing for code-server on the VPS
    NEEDS_SETUP,    // code-server missing or not running - offer one-tap install
    INSTALLING,     // running the setup script remotely
    TUNNELING,      // opening the local port forward
    READY,          // WebView can load the editor
    ERROR
}

data class CodeUiState(
    val server: ServerEntity? = null,
    val stage: CodeStage = CodeStage.CONNECTING,
    val statusText: String = "Connecting over SSH...",
    val errorMessage: String? = null,
    val editorUrl: String? = null,
    val remoteStatus: CodeServerStatus? = null,
    val setupLog: List<String> = emptyList(),
    /** Base64 of the code-server password, injected into the login form by the WebView. */
    val autoLoginPayload: String? = null,
    val desktopMode: Boolean = true
)

@HiltViewModel
class CodeViewModel @Inject constructor(
    private val serverDao: ServerDao,
    private val sshRepository: SshRepository,
    private val codeServerRepository: CodeServerRepository,
    private val secureKeyManager: SecureKeyManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CodeUiState())
    val uiState: StateFlow<CodeUiState> = _uiState.asStateFlow()

    private var sshClient: SSHClient? = null
    private val tunnel = SshPortForward()
    private var initialized = false

    /**
     * Anything escaping one of these coroutines would otherwise reach the thread's
     * default handler and kill the process. A server that behaves unexpectedly should
     * surface as an error pane, never as the app disappearing.
     */
    private val errorHandler = CoroutineExceptionHandler { _, error ->
        Log.w("OutpostCode", "Code screen failed", error)
        _uiState.value = _uiState.value.copy(
            stage = CodeStage.ERROR,
            errorMessage = error.message ?: error.javaClass.simpleName
        )
    }

    fun initialize(serverId: String) {
        if (initialized) return
        initialized = true
        connectAndPrepare(serverId)
    }

    /** Full teardown and retry, used by the "Retry" button. */
    fun retry() {
        val serverId = _uiState.value.server?.id ?: return
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            tunnel.stop()
            sshRepository.disconnect(sshClient)
            sshClient = null
            _uiState.value = CodeUiState(server = _uiState.value.server)
            connectAndPrepare(serverId)
        }
    }

    private fun connectAndPrepare(serverId: String) {
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            val server = serverDao.getServerById(serverId)
            if (server == null) {
                _uiState.value = _uiState.value.copy(
                    stage = CodeStage.ERROR,
                    errorMessage = "Server configuration not found"
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(
                server = server,
                stage = CodeStage.CONNECTING,
                statusText = "Connecting to ${server.username}@${server.host}..."
            )

            val connectResult = sshRepository.connect(server, keepAlive = true)
            val client = connectResult.getOrElse { error ->
                _uiState.value = _uiState.value.copy(
                    stage = CodeStage.ERROR,
                    errorMessage = error.message ?: "SSH authentication failed"
                )
                return@launch
            }
            sshClient = client

            probeAndOpen(server, client)
        }
    }

    /**
     * Looks at [port] on the remote host and, if anything is serving it, forwards it.
     *
     * Install is only ever offered when nothing answers - and even then it is a
     * suggestion, not a gate: [connectAnyway] forwards the port regardless.
     */
    private suspend fun probeAndOpen(server: ServerEntity, client: SSHClient) {
        _uiState.value = _uiState.value.copy(
            stage = CodeStage.CHECKING,
            statusText = "Checking 127.0.0.1:${server.codeServerPort} on the server..."
        )

        val status = codeServerRepository.checkStatus(client, server.codeServerPort)
            .getOrElse { error ->
                _uiState.value = _uiState.value.copy(
                    stage = CodeStage.ERROR,
                    errorMessage = "Could not inspect the server: ${error.message}"
                )
                return
            }

        _uiState.value = _uiState.value.copy(remoteStatus = status)

        if (!status.ready) {
            _uiState.value = _uiState.value.copy(
                stage = CodeStage.NEEDS_SETUP,
                statusText = if (status.installed) {
                    "code-server is installed but nothing is listening on " +
                        "127.0.0.1:${server.codeServerPort}."
                } else {
                    "Nothing is listening on 127.0.0.1:${server.codeServerPort}. " +
                        "If your editor runs on a different port, change it below."
                }
            )
            return
        }

        // The remote config is the source of truth for the password - pick it up so
        // auto sign-in keeps working even if it was changed outside the app.
        status.remotePassword?.let { persistPassword(server, it) }
        openTunnel(server)
    }

    /**
     * Forwards the port without waiting for the probe to bless it.
     *
     * The probe can be wrong - a container publishing the port, a host with no `ss`,
     * `netstat` or readable `/proc/net`, a service that only accepts a connection after
     * the first request. `ssh -L` would forward it anyway, so this offers the same out
     * rather than leaving an install prompt as the only way forward.
     */
    fun connectAnyway() {
        val server = _uiState.value.server ?: return
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            val client = sshClient
            if (client == null || !client.isConnected) {
                _uiState.value = _uiState.value.copy(
                    stage = CodeStage.ERROR,
                    errorMessage = "SSH connection dropped. Retry to reconnect."
                )
                return@launch
            }
            openTunnel(server)
        }
    }

    /**
     * The tunnel opened but the editor never loaded through it.
     *
     * That means the forward is fine and nothing is answering on the far end - almost
     * always the wrong port. Chrome's own "connection refused" page is a dead end with no
     * way back, so drop the tunnel and return to the port picker with the reason spelled
     * out.
     */
    fun onEditorLoadFailed(reason: String) {
        if (_uiState.value.stage != CodeStage.READY) return
        val port = _uiState.value.server?.codeServerPort ?: return

        tunnel.stop()
        _uiState.value = _uiState.value.copy(
            stage = CodeStage.NEEDS_SETUP,
            editorUrl = null,
            statusText = "The tunnel opened, but nothing answered on 127.0.0.1:$port " +
                "($reason). Check the port below."
        )
    }

    /**
     * Points the tunnel at a different remote port, remembers it, and probes again.
     */
    fun updatePort(port: Int) {
        val server = _uiState.value.server ?: return
        if (port !in 1..65535 || port == server.codeServerPort) return

        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            serverDao.updateCodeServerConfig(server.id, port, server.encryptedCodeServerPassword)
            val updated = server.copy(codeServerPort = port)
            _uiState.value = _uiState.value.copy(server = updated, remoteStatus = null)

            val client = sshClient
            if (client == null || !client.isConnected) {
                _uiState.value = _uiState.value.copy(
                    stage = CodeStage.ERROR,
                    errorMessage = "SSH connection dropped. Retry to reconnect."
                )
                return@launch
            }
            probeAndOpen(updated, client)
        }
    }

    /** Runs the bundled setup script on the VPS, streaming its output into the log. */
    fun runSetup() {
        val server = _uiState.value.server ?: return
        val client = sshClient ?: return

        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            _uiState.value = _uiState.value.copy(
                stage = CodeStage.INSTALLING,
                statusText = "Installing code-server (this can take a few minutes)...",
                setupLog = emptyList()
            )

            codeServerRepository.runSetup(client, server.codeServerPort).collect { event ->
                when (event) {
                    is SetupEvent.Output -> {
                        val log = (_uiState.value.setupLog + event.line).takeLast(200)
                        _uiState.value = _uiState.value.copy(setupLog = log)
                    }

                    is SetupEvent.Finished -> {
                        if (event.success) {
                            event.password?.let { persistPassword(server, it) }
                            openTunnel(_uiState.value.server ?: server)
                        } else {
                            _uiState.value = _uiState.value.copy(
                                stage = CodeStage.ERROR,
                                errorMessage = "Setup failed. See the log above for details."
                            )
                        }
                    }
                }
            }
        }
    }

    private suspend fun openTunnel(server: ServerEntity) {
        _uiState.value = _uiState.value.copy(
            stage = CodeStage.TUNNELING,
            statusText = "Opening the secure tunnel..."
        )

        val client = sshClient
        if (client == null || !client.isConnected) {
            _uiState.value = _uiState.value.copy(
                stage = CodeStage.ERROR,
                errorMessage = "SSH connection dropped before the tunnel could open"
            )
            return
        }

        // Terminate the forward on whichever loopback address answered the probe. A
        // service bound to ::1 only refuses IPv4, so hard-coding 127.0.0.1 here produced
        // an open tunnel onto a closed port.
        val remoteHost = _uiState.value.remoteStatus?.connectHost ?: "127.0.0.1"

        tunnel.start(client, server.codeServerPort, remoteHost).fold(
            onSuccess = { localPort ->
                _uiState.value = _uiState.value.copy(
                    stage = CodeStage.READY,
                    statusText = "Connected",
                    errorMessage = null,
                    editorUrl = "http://127.0.0.1:$localPort/",
                    autoLoginPayload = decryptedPassword(server)?.let {
                        Base64.encodeToString(it.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
                    }
                )
            },
            onFailure = { error ->
                _uiState.value = _uiState.value.copy(
                    stage = CodeStage.ERROR,
                    errorMessage = "Could not open the tunnel: ${error.message}"
                )
            }
        )
    }

    private suspend fun persistPassword(server: ServerEntity, password: String) {
        val encrypted = runCatching { secureKeyManager.encryptPrivateKey(password) }.getOrNull() ?: return
        serverDao.updateCodeServerConfig(server.id, server.codeServerPort, encrypted)
        _uiState.value = _uiState.value.copy(
            server = server.copy(encryptedCodeServerPassword = encrypted)
        )
    }

    private fun decryptedPassword(server: ServerEntity): String? {
        val stored = _uiState.value.server?.encryptedCodeServerPassword
            ?: server.encryptedCodeServerPassword
            ?: return null
        return runCatching { secureKeyManager.decryptPrivateKey(stored) }.getOrNull()
    }

    /** Plain-text password, shown behind a reveal toggle so the user can copy it. */
    fun revealPassword(): String? = _uiState.value.server?.let { decryptedPassword(it) }

    fun toggleDesktopMode() {
        _uiState.value = _uiState.value.copy(desktopMode = !_uiState.value.desktopMode)
    }

    fun restartCodeServer() {
        val server = _uiState.value.server ?: return
        val client = sshClient ?: return
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            _uiState.value = _uiState.value.copy(statusText = "Restarting code-server...")
            codeServerRepository.restart(client, server.codeServerPort)
            _uiState.value = _uiState.value.copy(statusText = "Connected")
        }
    }

    override fun onCleared() {
        super.onCleared()
        val client = sshClient
        sshClient = null
        tunnel.stop()
        // Detached from viewModelScope on purpose: the scope is already cancelled here.
        Thread {
            runCatching {
                if (client != null && client.isConnected) client.disconnect()
            }
        }.apply { isDaemon = true }.start()
    }
}
