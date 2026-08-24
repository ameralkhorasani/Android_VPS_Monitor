package io.github.ameralkhorasani.outpost.ui.screens.ports

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ameralkhorasani.outpost.data.db.PortForwardDao
import io.github.ameralkhorasani.outpost.data.db.ServerDao
import io.github.ameralkhorasani.outpost.data.model.PortForwardEntity
import io.github.ameralkhorasani.outpost.data.model.ServerEntity
import io.github.ameralkhorasani.outpost.ssh.tunnel.ActiveTunnel
import io.github.ameralkhorasani.outpost.ssh.tunnel.TunnelManager
import io.github.ameralkhorasani.outpost.ssh.tunnel.TunnelService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PortsUiState(
    val server: ServerEntity? = null,
    val forwards: List<PortForwardEntity> = emptyList(),
    val active: List<ActiveTunnel> = emptyList(),
    /** Forwards mid-connect, so their row can show a spinner instead of the wrong state. */
    val busyIds: Set<String> = emptySet(),
    val errorMessage: String? = null,
    val isLoading: Boolean = true
) {
    fun isRunning(id: String): Boolean = active.any { it.forwardId == id }

    fun activeFor(id: String): ActiveTunnel? = active.firstOrNull { it.forwardId == id }
}

@HiltViewModel
class PortsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serverDao: ServerDao,
    private val portForwardDao: PortForwardDao,
    private val tunnelManager: TunnelManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PortsUiState())
    val uiState: StateFlow<PortsUiState> = _uiState.asStateFlow()

    private var initialized = false

    /**
     * Held separately from uiState.server: the "add" action must work the moment the
     * screen is up, not only once the server row has come back from the database.
     */
    private var serverId: String? = null

    private val errorHandler = CoroutineExceptionHandler { _, error ->
        Log.w("OutpostPorts", "Port forward action failed", error)
        _uiState.value = _uiState.value.copy(
            errorMessage = error.message ?: error.javaClass.simpleName,
            busyIds = emptySet()
        )
    }

    fun initialize(serverId: String) {
        if (initialized) return
        initialized = true
        this.serverId = serverId

        viewModelScope.launch(errorHandler) {
            _uiState.value = _uiState.value.copy(server = serverDao.getServerById(serverId))
        }

        viewModelScope.launch(errorHandler) {
            portForwardDao.getForServer(serverId).collect { forwards ->
                _uiState.value = _uiState.value.copy(forwards = forwards, isLoading = false)
                startPendingAutoForwards(forwards)
            }
        }

        viewModelScope.launch(errorHandler) {
            tunnelManager.active.collect { active ->
                _uiState.value = _uiState.value.copy(active = active)
            }
        }
    }

    private var autoStartDone = false

    private fun startPendingAutoForwards(forwards: List<PortForwardEntity>) {
        if (autoStartDone) return
        autoStartDone = true
        forwards.filter { it.autoStart && !tunnelManager.isRunning(it.id) }
            .forEach { start(it) }
    }

    fun start(forward: PortForwardEntity) {
        if (_uiState.value.busyIds.contains(forward.id)) return
        _uiState.value = _uiState.value.copy(
            busyIds = _uiState.value.busyIds + forward.id,
            errorMessage = null
        )

        viewModelScope.launch(errorHandler) {
            val result = tunnelManager.start(forward)
            _uiState.value = _uiState.value.copy(
                busyIds = _uiState.value.busyIds - forward.id,
                errorMessage = result.exceptionOrNull()?.message
            )
            // Only once a forward is actually open is there anything for the service to
            // keep alive.
            if (result.isSuccess) TunnelService.start(context)
        }
    }

    fun stop(forward: PortForwardEntity) {
        _uiState.value = _uiState.value.copy(busyIds = _uiState.value.busyIds + forward.id)
        viewModelScope.launch(errorHandler) {
            tunnelManager.stop(forward.id)
            _uiState.value = _uiState.value.copy(
                busyIds = _uiState.value.busyIds - forward.id
            )
        }
    }

    fun toggle(forward: PortForwardEntity) {
        if (_uiState.value.isRunning(forward.id)) stop(forward) else start(forward)
    }

    /**
     * Saves a forward. [localPort] defaulting to the remote port is what makes the
     * address predictable: a service on the VPS at 8090 is reachable at localhost:8090.
     */
    fun addForward(label: String, remoteHost: String, remotePort: Int, localPort: Int) {
        val serverId = serverId ?: return

        val problem = validate(remotePort, localPort)
        if (problem != null) {
            _uiState.value = _uiState.value.copy(errorMessage = problem)
            return
        }

        viewModelScope.launch(errorHandler) {
            portForwardDao.upsert(
                PortForwardEntity(
                    serverId = serverId,
                    label = label.ifBlank { "Port $remotePort" },
                    remoteHost = remoteHost.ifBlank { "127.0.0.1" },
                    remotePort = remotePort,
                    localPort = localPort
                )
            )
            _uiState.value = _uiState.value.copy(errorMessage = null)
        }
    }

    private fun validate(remotePort: Int, localPort: Int): String? = when {
        remotePort !in 1..65535 -> "Remote port must be between 1 and 65535."
        localPort !in 1..65535 -> "Local port must be between 1 and 65535."

        // Android refuses to bind these to an unprivileged process, and the failure
        // surfaces later as a bind error that reads like a bug.
        localPort < 1024 -> "Local ports below 1024 are reserved on Android. " +
            "Pick something above 1024 - it can still point at remote port $remotePort."

        _uiState.value.forwards.any { it.localPort == localPort } ->
            "Local port $localPort is already used by another forward on this server."

        else -> null
    }

    fun delete(forward: PortForwardEntity) {
        viewModelScope.launch(errorHandler) {
            tunnelManager.stop(forward.id)
            portForwardDao.delete(forward)
        }
    }

    fun setAutoStart(forward: PortForwardEntity, enabled: Boolean) {
        viewModelScope.launch(errorHandler) {
            portForwardDao.setAutoStart(forward.id, enabled)
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * Note there is no teardown in onCleared: leaving the screen must *not* close the
     * tunnels, since walking over to the browser is exactly when they are needed. They
     * are closed from the row's Stop button or the notification's "Stop all".
     */
}
