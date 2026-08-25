package io.github.ameralkhorasani.outpost.ui.screens.monitor

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ameralkhorasani.outpost.data.db.ServerDao
import io.github.ameralkhorasani.outpost.data.model.ServerEntity
import io.github.ameralkhorasani.outpost.data.model.ServerStats
import io.github.ameralkhorasani.outpost.ssh.SshRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import net.schmizz.sshj.SSHClient
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProcessInfo(
    val pid: String,
    val ppid: String,
    val command: String,
    val cpuPercent: Float,
    val memPercent: Float
)

data class MonitorUiState(
    val server: ServerEntity? = null,
    val currentStats: ServerStats = ServerStats(),
    val cpuHistory: List<Float> = emptyList(),
    val ramHistory: List<Float> = emptyList(),
    val swapHistory: List<Float> = emptyList(),
    val diskHistory: List<Float> = emptyList(),
    val loadHistory: List<Float> = emptyList(),
    val netRxHistory: List<Float> = emptyList(),
    val netTxHistory: List<Float> = emptyList(),
    val processes: List<ProcessInfo> = emptyList(),
    val isPaused: Boolean = false,
    val isConnected: Boolean = false,
    val isReconnecting: Boolean = false,
    /** Seconds covered by the charts, so the x-axis can be labelled honestly. */
    val windowSeconds: Int = 0,
    val errorMessage: String? = null
)

@HiltViewModel
class MonitorViewModel @Inject constructor(
    private val serverDao: ServerDao,
    private val sshRepository: SshRepository
) : ViewModel() {

    private companion object {
        const val MAX_HISTORY = 60
    }

    private val _uiState = MutableStateFlow(MonitorUiState())
    val uiState: StateFlow<MonitorUiState> = _uiState.asStateFlow()

    private var sshClient: SSHClient? = null
    private var statsJob: Job? = null
    private var initialized = false

    /** Timestamp of the oldest sample still in the charts, for the window label. */
    private var windowStartedAt: Long = 0L

    /**
     * Without this, anything thrown inside one of these coroutines reaches the thread's
     * default handler and takes the whole process down - a monitored server going away
     * must not be able to close the app.
     */
    private val errorHandler = CoroutineExceptionHandler { _, error ->
        Log.w("OutpostMonitor", "Monitor failed", error)
        _uiState.value = _uiState.value.copy(
            isConnected = false,
            isReconnecting = false,
            errorMessage = error.message ?: error.javaClass.simpleName
        )
    }

    /**
     * Guarded: the screen calls this from a LaunchedEffect, and running it twice would
     * open a second SSH session while the first stayed live and unreferenced.
     */
    fun initialize(serverId: String) {
        if (initialized) return
        initialized = true

        viewModelScope.launch(errorHandler) {
            val server = serverDao.getServerById(serverId)
            _uiState.value = _uiState.value.copy(server = server)

            if (server != null) {
                connectAndStartMonitoring(server)
            } else {
                _uiState.value = _uiState.value.copy(errorMessage = "Server not found")
            }
        }
    }

    /** Drops the current session and dials again, used by the error pane's retry. */
    fun reconnect() {
        val server = _uiState.value.server ?: return
        if (_uiState.value.isReconnecting) return

        _uiState.value = _uiState.value.copy(isReconnecting = true, errorMessage = null)
        statsJob?.cancel()
        val stale = sshClient
        sshClient = null
        disconnectDetached(stale)

        viewModelScope.launch(errorHandler) { connectAndStartMonitoring(server) }
    }

    private suspend fun connectAndStartMonitoring(server: ServerEntity) {
        val result = sshRepository.connect(server, keepAlive = true)
        val client = result.getOrElse { error ->
            _uiState.value = _uiState.value.copy(
                isConnected = false,
                isReconnecting = false,
                errorMessage = error.message ?: "Failed to connect to SSH host"
            )
            return
        }

        sshClient = client
        _uiState.value = _uiState.value.copy(
            isConnected = true,
            isReconnecting = false,
            errorMessage = null
        )
        startPollingStats(client)
    }

    private fun startPollingStats(client: SSHClient) {
        statsJob?.cancel()
        statsJob = viewModelScope.launch(errorHandler) {
            sshRepository.getRealtimeStats(client).collect { stats -> record(stats) }

            // The stats flow only ends when the transport is gone. Say so instead of
            // leaving the last sample on screen looking like a live reading.
            if (sshClient === client) {
                _uiState.value = _uiState.value.copy(
                    isConnected = false,
                    errorMessage = "Connection to the server was lost."
                )
            }
        }
    }

    private fun record(stats: ServerStats) {
        val state = _uiState.value
        if (state.isPaused) return

        if (state.cpuHistory.isEmpty()) windowStartedAt = stats.timestamp
        fun push(history: List<Float>, value: Float) = (history + value).takeLast(MAX_HISTORY)

        _uiState.value = state.copy(
            currentStats = stats,
            cpuHistory = push(state.cpuHistory, stats.cpuPercent),
            ramHistory = push(state.ramHistory, stats.ramPercent),
            swapHistory = push(state.swapHistory, stats.swapPercent),
            diskHistory = push(state.diskHistory, stats.diskPercent),
            loadHistory = push(state.loadHistory, stats.loadAvg1),
            netRxHistory = push(state.netRxHistory, stats.netRxKbSec),
            netTxHistory = push(state.netTxHistory, stats.netTxKbSec),
            windowSeconds = ((stats.timestamp - windowStartedAt) / 1000L).toInt().coerceAtLeast(0)
        )
    }

    fun togglePause() {
        _uiState.value = _uiState.value.copy(isPaused = !_uiState.value.isPaused)
    }

    fun fetchProcesses() {
        val client = sshClient ?: return
        viewModelScope.launch(errorHandler) {
            val cmdResult = sshRepository.executeCommand(
                client,
                "ps -eo pid,ppid,cmd,%cpu,%mem --sort=-%cpu | head -n 25"
            )
            val output = cmdResult.getOrNull() ?: return@launch
            val parsed = output.lines().drop(1).mapNotNull { line ->
                val tokens = line.trim().split("\\s+".toRegex())
                if (tokens.size >= 5) {
                    ProcessInfo(
                        pid = tokens[0],
                        ppid = tokens[1],
                        command = tokens.subList(2, tokens.size - 2).joinToString(" "),
                        cpuPercent = tokens[tokens.size - 2].toFloatOrNull() ?: 0f,
                        memPercent = tokens.last().toFloatOrNull() ?: 0f
                    )
                } else null
            }
            _uiState.value = _uiState.value.copy(processes = parsed)
        }
    }

    override fun onCleared() {
        super.onCleared()
        statsJob?.cancel()
        val client = sshClient
        sshClient = null
        disconnectDetached(client)
    }

    /**
     * Closes the session off the ViewModel's own scope.
     *
     * onCleared() runs *after* viewModelScope has been cancelled, so a
     * `viewModelScope.launch { disconnect() }` here never executed - which left an
     * authenticated session open on the server every time this screen was closed. Those
     * accumulate until sshd's MaxSessions/MaxStartups starts refusing new connections,
     * and the app looks like it "randomly stops connecting".
     */
    private fun disconnectDetached(client: SSHClient?) {
        if (client == null) return
        Thread {
            runCatching {
                runCatching { client.connection.keepAlive.interrupt() }
                if (client.isConnected) client.disconnect()
            }
        }.apply { isDaemon = true }.start()
    }
}
