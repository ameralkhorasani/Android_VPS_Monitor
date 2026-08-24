package io.github.ameralkhorasani.outpost.ui.screens.docker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ameralkhorasani.outpost.data.db.ServerDao
import io.github.ameralkhorasani.outpost.data.model.DockerAvailability
import io.github.ameralkhorasani.outpost.data.model.DockerContainer
import io.github.ameralkhorasani.outpost.data.model.ServerEntity
import io.github.ameralkhorasani.outpost.ssh.docker.DockerRepository
import io.github.ameralkhorasani.outpost.ssh.SshRepository
import io.github.ameralkhorasani.outpost.core.result.runSuspendCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.schmizz.sshj.SSHClient
import javax.inject.Inject

data class DockerUiState(
    val server: ServerEntity? = null,
    val containers: List<DockerContainer> = emptyList(),
    val availability: DockerAvailability? = null,
    val isConnecting: Boolean = true,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    /** Container id currently running a start/stop/restart, for per-row spinners. */
    val busyContainerId: String? = null,
    val actionMessage: String? = null,
    /** Container whose logs are open, if any. */
    val logsContainer: DockerContainer? = null,
    val logLines: List<String> = emptyList()
) {
    val runningCount: Int get() = containers.count { it.isRunning }
}

@HiltViewModel
class DockerViewModel @Inject constructor(
    private val serverDao: ServerDao,
    private val sshRepository: SshRepository,
    private val dockerRepository: DockerRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DockerUiState())
    val uiState: StateFlow<DockerUiState> = _uiState.asStateFlow()

    private var sshClient: SSHClient? = null
    private var pollJob: Job? = null
    private var logsJob: Job? = null
    private var initialized = false

    private val prefix: String
        get() = _uiState.value.availability?.commandPrefix ?: "docker"

    fun initialize(serverId: String) {
        if (initialized) return
        initialized = true

        viewModelScope.launch(Dispatchers.IO) {
            val server = serverDao.getServerById(serverId)
            if (server == null) {
                _uiState.value = _uiState.value.copy(
                    isConnecting = false,
                    errorMessage = "Server configuration not found"
                )
                return@launch
            }
            _uiState.value = _uiState.value.copy(server = server)

            val client = sshRepository.connect(server, keepAlive = true).getOrElse { error ->
                _uiState.value = _uiState.value.copy(
                    isConnecting = false,
                    errorMessage = error.message ?: "SSH authentication failed"
                )
                return@launch
            }
            sshClient = client

            val availability = dockerRepository.checkAvailability(client)
            _uiState.value = _uiState.value.copy(
                availability = availability,
                isConnecting = false,
                errorMessage = if (availability.usable) null else availability.message
            )

            if (availability.usable) startPolling()
        }
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                refreshOnce()
                // docker stats --no-stream already costs a second or two; polling
                // faster than this just keeps the daemon busy for no benefit.
                delay(4000)
            }
        }
    }

    private suspend fun refreshOnce() {
        val client = sshClient ?: return
        dockerRepository.listContainers(client, prefix).fold(
            onSuccess = { containers ->
                _uiState.value = _uiState.value.copy(
                    containers = containers,
                    isRefreshing = false,
                    errorMessage = null
                )
            },
            onFailure = { error ->
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    errorMessage = error.message
                )
            }
        )
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        viewModelScope.launch(Dispatchers.IO) { refreshOnce() }
    }

    fun startContainer(container: DockerContainer) = runAction(container, "start")
    fun stopContainer(container: DockerContainer) = runAction(container, "stop")
    fun restartContainer(container: DockerContainer) = runAction(container, "restart")

    private fun runAction(container: DockerContainer, action: String) {
        val client = sshClient ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = _uiState.value.copy(
                busyContainerId = container.id,
                actionMessage = null
            )

            val result = when (action) {
                "start" -> dockerRepository.start(client, prefix, container.shortId)
                "stop" -> dockerRepository.stop(client, prefix, container.shortId)
                else -> dockerRepository.restart(client, prefix, container.shortId)
            }

            val message = result.fold(
                onSuccess = { output ->
                    // docker echoes the container ref on success; anything else is a message
                    // worth surfacing (e.g. "port is already allocated").
                    val trimmed = output.trim()
                    if (trimmed.isEmpty() || trimmed.equals(container.shortId, true) ||
                        trimmed.equals(container.name, true)
                    ) {
                        "${container.name}: $action ok"
                    } else {
                        trimmed.lines().last()
                    }
                },
                onFailure = { "${container.name}: ${it.message}" }
            )

            _uiState.value = _uiState.value.copy(
                busyContainerId = null,
                actionMessage = message
            )
            refreshOnce()
        }
    }

    fun openLogs(container: DockerContainer) {
        val client = sshClient ?: return
        logsJob?.cancel()
        _uiState.value = _uiState.value.copy(logsContainer = container, logLines = emptyList())

        logsJob = viewModelScope.launch(Dispatchers.IO) {
            // Not runCatching: closing the viewer cancels this job, and swallowing that
            // cancellation would leave the log stream running against a dead session.
            runSuspendCatching {
                dockerRepository.streamLogs(client, prefix, container.shortId).collect { line ->
                    val lines = (_uiState.value.logLines + line).takeLast(500)
                    _uiState.value = _uiState.value.copy(logLines = lines)
                }
            }.onFailure { error ->
                val lines = _uiState.value.logLines + "[log stream ended: ${error.message}]"
                _uiState.value = _uiState.value.copy(logLines = lines)
            }
        }
    }

    fun closeLogs() {
        logsJob?.cancel()
        logsJob = null
        _uiState.value = _uiState.value.copy(logsContainer = null, logLines = emptyList())
    }

    fun consumeActionMessage() {
        _uiState.value = _uiState.value.copy(actionMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        pollJob?.cancel()
        logsJob?.cancel()
        val client = sshClient
        sshClient = null
        // viewModelScope is already cancelled at this point, so close off-scope.
        Thread {
            runCatching { if (client != null && client.isConnected) client.disconnect() }
        }.apply { isDaemon = true }.start()
    }
}
