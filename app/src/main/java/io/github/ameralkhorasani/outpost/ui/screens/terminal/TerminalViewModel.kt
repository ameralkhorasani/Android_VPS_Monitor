package io.github.ameralkhorasani.outpost.ui.screens.terminal

import android.util.Base64
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ameralkhorasani.outpost.data.db.ServerDao
import io.github.ameralkhorasani.outpost.data.model.ServerEntity
import io.github.ameralkhorasani.outpost.ssh.SshRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

data class TerminalUiState(
    val server: ServerEntity? = null,
    val isConnected: Boolean = false,
    val isConnecting: Boolean = true,
    val errorMessage: String? = null,
    /** Live PTY geometry, shown in the title bar so a wrong size is visible rather than baffling. */
    val cols: Int = 0,
    val rows: Int = 0
)

@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val serverDao: ServerDao,
    private val sshRepository: SshRepository
) : ViewModel() {

    private companion object {
        const val READ_BUFFER_BYTES = 8192

        /** Ceiling on one emission, so a firehose cannot build an unbounded array. */
        const val MAX_BATCH_BYTES = 64 * 1024
    }

    private val _uiState = MutableStateFlow(TerminalUiState())
    val uiState: StateFlow<TerminalUiState> = _uiState.asStateFlow()

    /**
     * Replays the backlog to a late subscriber.
     *
     * The shell starts producing output - the login banner, the MOTD, the first prompt -
     * as soon as it opens, which is usually before the WebView has finished loading and
     * subscribed. With no replay those bytes went nowhere, so the terminal opened blank
     * and looked dead even when the connection was fine.
     */
    private val _terminalOutput = MutableSharedFlow<String>(
        replay = 128,
        extraBufferCapacity = 256
    )
    val terminalOutput: SharedFlow<String> = _terminalOutput.asSharedFlow()

    private var sshClient: SSHClient? = null
    private var shellSession: Session.Shell? = null
    private var shellOutputStream: OutputStream? = null

    /**
     * Last geometry reported by the front end. The PTY is allocated with whatever is
     * known at connect time and corrected afterwards, since the WebView may not have
     * measured itself yet when the connection opens.
     */
    @Volatile
    private var cols: Int = 80

    @Volatile
    private var rows: Int = 24

    // Guarded: the screen calls this from a LaunchedEffect, and a second pass would open
    // another shell while the first stayed live and unreachable.
    private var initialized = false

    /**
     * Bumped on every connect attempt. A read loop belonging to a superseded session
     * finishes some time after Reconnect has already opened the next one, and without
     * this it would post "session closed" over a connection that is perfectly healthy.
     */
    @Volatile
    private var generation = 0

    private val errorHandler = CoroutineExceptionHandler { _, error ->
        Log.w("OutpostTerminal", "Terminal failed", error)
        _uiState.value = _uiState.value.copy(
            isConnecting = false,
            isConnected = false,
            errorMessage = error.message ?: error.javaClass.simpleName
        )
    }

    fun initialize(serverId: String) {
        if (initialized) return
        initialized = true

        viewModelScope.launch(errorHandler) {
            val server = serverDao.getServerById(serverId)
            _uiState.value = _uiState.value.copy(server = server)

            if (server != null) {
                connectAndStartShell(server)
            } else {
                _uiState.value = _uiState.value.copy(
                    isConnecting = false,
                    errorMessage = "Server configuration not found"
                )
            }
        }
    }

    /** Tears the session down and dials again, for the "Reconnect" action. */
    fun reconnect() {
        val server = _uiState.value.server ?: return
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            closeSession()
            _uiState.value = _uiState.value.copy(
                isConnecting = true,
                isConnected = false,
                errorMessage = null
            )
            connectAndStartShell(server)
        }
    }

    private fun connectAndStartShell(server: ServerEntity) {
        val attempt = ++generation
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            val connResult = sshRepository.connect(server, keepAlive = true)
            if (connResult.isSuccess) {
                val client = connResult.getOrThrow()
                sshClient = client

                val shellResult = sshRepository.openShellSession(client, cols, rows)
                if (shellResult.isSuccess) {
                    val shell = shellResult.getOrThrow()
                    shellSession = shell
                    shellOutputStream = shell.outputStream

                    _uiState.value = _uiState.value.copy(
                        isConnected = true,
                        isConnecting = false,
                        errorMessage = null,
                        cols = cols,
                        rows = rows
                    )

                    startShellReadLoop(shell.inputStream)

                    // read() returning -1 means the remote closed the channel: the user
                    // typed `exit`, the network dropped, or sshd killed the session.
                    // Saying so beats leaving a frozen terminal on screen.
                    if (attempt == generation && _uiState.value.isConnected) {
                        _uiState.value = _uiState.value.copy(
                            isConnected = false,
                            errorMessage = "Session closed by the server."
                        )
                    }
                } else {
                    val err = shellResult.exceptionOrNull()?.message ?: "Failed to open shell"
                    _uiState.value = _uiState.value.copy(isConnecting = false, errorMessage = err)
                }
            } else {
                val err = connResult.exceptionOrNull()?.message ?: "Failed SSH authentication"
                _uiState.value = _uiState.value.copy(isConnecting = false, errorMessage = err)
            }
        }
    }

    /**
     * Reads the shell and forwards it to the front end.
     *
     * Bytes already waiting are coalesced into one emission rather than one per 4 KB
     * read. Each emission costs a hop to the UI thread and an evaluateJavascript call, so
     * a command producing a lot of output - a build log, `cat` on something large - used
     * to arrive as thousands of separate round trips and the terminal crawled behind it.
     */
    private suspend fun startShellReadLoop(inputStream: InputStream) = withContext(Dispatchers.IO) {
        val buffer = ByteArray(READ_BUFFER_BYTES)
        val batch = ByteArrayOutputStream(READ_BUFFER_BYTES)
        try {
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                if (bytesRead <= 0) continue

                batch.reset()
                batch.write(buffer, 0, bytesRead)

                // Drain whatever else has already arrived, but never block waiting for
                // more: an interactive prompt must not sit in the buffer.
                while (batch.size() < MAX_BATCH_BYTES && inputStream.available() > 0) {
                    val extra = inputStream.read(buffer)
                    if (extra <= 0) break
                    batch.write(buffer, 0, extra)
                }

                _terminalOutput.emit(Base64.encodeToString(batch.toByteArray(), Base64.NO_WRAP))
            }
        } catch (_: Exception) {}
    }

    fun sendInputBase64(base64Input: String) {
        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            try {
                val bytes = Base64.decode(base64Input, Base64.NO_WRAP)
                shellOutputStream?.write(bytes)
                shellOutputStream?.flush()
            } catch (_: Exception) {}
        }
    }

    fun sendKeySequence(seq: String) {
        val base64Seq = Base64.encodeToString(seq.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        sendInputBase64(base64Seq)
    }

    /**
     * Sends a control character: Ctrl+C is 0x03, Ctrl+D 0x04, and so on - letter minus 64.
     */
    fun sendControlChar(letter: Char) {
        val upper = letter.uppercaseChar()
        if (upper !in 'A'..'Z') return
        val code = upper.code - 64
        sendInputBase64(Base64.encodeToString(byteArrayOf(code.toByte()), Base64.NO_WRAP))
    }

    /**
     * Tells the remote PTY how large the terminal actually is.
     *
     * Without this the shell formats for the 80x24 it was given at allocation time,
     * which is why output wrapped in the wrong place and full-screen programs drew
     * outside the visible area.
     */
    fun onTerminalResize(newCols: Int, newRows: Int) {
        if (newCols <= 0 || newRows <= 0) return
        if (newCols == cols && newRows == rows) return
        cols = newCols
        rows = newRows
        _uiState.value = _uiState.value.copy(cols = newCols, rows = newRows)

        viewModelScope.launch(Dispatchers.IO + errorHandler) {
            runCatching { shellSession?.changeWindowDimensions(newCols, newRows, 0, 0) }
        }
    }

    private fun closeSession() {
        val stream = shellOutputStream
        val session = shellSession
        val client = sshClient
        shellOutputStream = null
        shellSession = null
        sshClient = null

        Thread {
            runCatching { stream?.close() }
            runCatching { session?.close() }
            runCatching { client?.connection?.keepAlive?.interrupt() }
            runCatching { if (client?.isConnected == true) client.disconnect() }
        }.apply { isDaemon = true }.start()
    }

    /**
     * Closes the shell and the transport off this ViewModel's own scope.
     *
     * onCleared() runs after viewModelScope has already been cancelled, so a
     * `viewModelScope.launch { ... }` here would never run: every visit to the terminal
     * would leave an authenticated session and a live PTY behind on the server. Enough of
     * those and sshd stops accepting new connections.
     */
    override fun onCleared() {
        super.onCleared()
        closeSession()
    }
}
