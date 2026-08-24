package io.github.ameralkhorasani.outpost.ui.screens.logs

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ameralkhorasani.outpost.data.db.ServerDao
import io.github.ameralkhorasani.outpost.data.model.ServerEntity
import io.github.ameralkhorasani.outpost.ssh.SshRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import net.schmizz.sshj.SSHClient
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LogsUiState(
    val server: ServerEntity? = null,
    val selectedLogFile: String = "/var/log/syslog",
    val availableLogFiles: List<String> = listOf(
        "/var/log/syslog",
        "/var/log/auth.log",
        "/var/log/nginx/error.log",
        "/var/log/messages"
    ),
    val mode: String = "Tail", // Tail, Head, Live
    val lineWrapEnabled: Boolean = true,
    val logOutput: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val serverDao: ServerDao,
    private val sshRepository: SshRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogsUiState())
    val uiState: StateFlow<LogsUiState> = _uiState.asStateFlow()

    private var sshClient: SSHClient? = null
    private var initialized = false

    private val errorHandler = CoroutineExceptionHandler { _, error ->
        Log.w("OutpostLogs", "Log fetch failed", error)
        _uiState.value = _uiState.value.copy(
            isLoading = false,
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
                fetchLogContent()
            }
        }
    }

    fun selectLogFile(filePath: String) {
        _uiState.value = _uiState.value.copy(selectedLogFile = filePath)
        fetchLogContent()
    }

    fun setMode(mode: String) {
        _uiState.value = _uiState.value.copy(mode = mode)
        fetchLogContent()
    }

    fun toggleLineWrap() {
        _uiState.value = _uiState.value.copy(lineWrapEnabled = !_uiState.value.lineWrapEnabled)
    }

    fun fetchLogContent() {
        val server = _uiState.value.server ?: return
        viewModelScope.launch(errorHandler) {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

            val client = sshRepository.connect(server, keepAlive = false).getOrElse { error ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = error.message ?: "SSH Connection error",
                    isLoading = false
                )
                return@launch
            }
            sshClient = client

            // finally, not a trailing call: a read that throws - or the screen closing
            // mid-fetch - would otherwise leave this session open on the server.
            try {
                val cmd = when (_uiState.value.mode) {
                    "Head" -> "sudo head -n 200 ${_uiState.value.selectedLogFile}"
                    else -> "sudo tail -n 200 ${_uiState.value.selectedLogFile}"
                }
                sshRepository.executeCommand(client, cmd).fold(
                    onSuccess = { output ->
                        _uiState.value = _uiState.value.copy(
                            logOutput = output,
                            isLoading = false
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            errorMessage = error.message ?: "Failed to read log file",
                            isLoading = false
                        )
                    }
                )
            } finally {
                withContext(NonCancellable) { sshRepository.disconnect(client) }
                if (sshClient === client) sshClient = null
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        val client = sshClient
        sshClient = null
        if (client != null) {
            // viewModelScope is already cancelled by the time this runs, so the close has
            // to happen off it or it silently never happens.
            Thread {
                runCatching { if (client.isConnected) client.disconnect() }
            }.apply { isDaemon = true }.start()
        }
    }
}
