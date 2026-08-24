package io.github.ameralkhorasani.outpost.ui.screens.overview

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ameralkhorasani.outpost.data.db.ServerDao
import io.github.ameralkhorasani.outpost.data.preferences.SettingsRepository
import io.github.ameralkhorasani.outpost.domain.HealthScore
import io.github.ameralkhorasani.outpost.data.model.ServerEntity
import io.github.ameralkhorasani.outpost.data.model.thresholds
import io.github.ameralkhorasani.outpost.ssh.SshRepository
import io.github.ameralkhorasani.outpost.core.result.runSuspendCatching
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class OverviewUiState(
    val servers: List<ServerEntity> = emptyList(),
    val isRefreshing: Boolean = false,
    val overallHealth: Int = 100,
    val worstHealth: Int = 100,
    val onlineCount: Int = 0,
    val totalCount: Int = 0,
    val avgCpuPercent: Float = 0f,
    val avgRamPercent: Float = 0f
)

@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val serverDao: ServerDao,
    private val sshRepository: SshRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private companion object {
        /** Budget for probing one server end to end: connect, two samples, disconnect. */
        const val PROBE_TIMEOUT_MS = 20_000L

        /** How many servers to contact at once. */
        const val MAX_PARALLEL_PROBES = 2
    }

    private val _uiState = MutableStateFlow(OverviewUiState())
    val uiState: StateFlow<OverviewUiState> = _uiState.asStateFlow()

    /**
     * Last-resort net. An exception escaping a viewModelScope coroutine reaches the
     * thread's default handler, which kills the process - a server that cannot be
     * reached must never do that.
     *
     * Declared above the init block on purpose: property initialisers and init blocks
     * run in source order, and the startup probe below reaches for this handler. Moving
     * it back below that block leaves it null at that point, and `Dispatchers.IO + null`
     * throws before the first frame is ever drawn.
     */
    private val refreshErrorHandler = CoroutineExceptionHandler { _, error ->
        Log.w("OutpostOverview", "Server refresh failed", error)
        _uiState.value = _uiState.value.copy(isRefreshing = false)
    }

    init {
        observeServers()
        // Opening the app otherwise dials every configured server; the setting exists
        // for people on metered connections. The manual refresh button always works.
        if (settingsRepository.probeOnStartup.value) {
            refreshServers()
        }
    }

    /**
     * Mirrors the database into UI state. Deliberately does not kick off probes: the
     * probes write back to this same table, which would loop.
     */
    private fun observeServers() {
        viewModelScope.launch {
            serverDao.getAllServers().collect { servers ->
                val online = servers.filter { it.isOnline }

                _uiState.value = _uiState.value.copy(
                    servers = servers,
                    onlineCount = online.size,
                    totalCount = servers.size,
                    overallHealth = if (servers.isEmpty()) 100
                    else servers.map { it.healthScore }.average().toInt(),
                    worstHealth = servers.minOfOrNull { it.healthScore } ?: 100,
                    // Averaging over online servers only; offline ones report 0 and would
                    // otherwise drag the figure down and read as "low load".
                    avgCpuPercent = if (online.isEmpty()) 0f
                    else online.map { it.lastCpuPercent }.average().toFloat(),
                    avgRamPercent = if (online.isEmpty()) 0f
                    else online.map { it.lastRamPercent }.average().toFloat()
                )
            }
        }
    }

    /**
     * Connects to every configured server in parallel, takes one real metrics sample,
     * and writes it back so the dashboard reflects reality rather than placeholders.
     */
    fun refreshServers() {
        viewModelScope.launch(Dispatchers.IO + refreshErrorHandler) {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            try {
                val servers = serverDao.getAllServers().first()
                // Bounded, not unbounded: each probe builds an SSHClient, and doing many
                // of those at once is what makes sshj contend for entropy and stall.
                val gate = Semaphore(MAX_PARALLEL_PROBES)
                coroutineScope {
                    servers.map { server ->
                        // Isolated per server: one unreachable host must not abort the
                        // others, nor propagate out of this scope.
                        async {
                            gate.withPermit { runSuspendCatching { probeServer(server) } }
                        }
                    }.awaitAll()
                }
            } finally {
                _uiState.value = _uiState.value.copy(isRefreshing = false)
            }
        }
    }

    private suspend fun probeServer(server: ServerEntity) {
        val sampled = withTimeoutOrNull(PROBE_TIMEOUT_MS) {
            val client = sshRepository.connect(server).getOrNull() ?: return@withTimeoutOrNull null
            try {
                sshRepository.sampleStats(client)
            } catch (e: CancellationException) {
                // Swallowing this would leave the coroutine cancelled but running on,
                // so every suspend call after it would throw instead.
                throw e
            } catch (_: Exception) {
                null
            } finally {
                // On timeout this coroutine is already cancelled, and a plain suspend
                // call here would abort before closing the socket - leaking the
                // connection. NonCancellable guarantees the disconnect actually runs.
                withContext(NonCancellable) { sshRepository.disconnect(client) }
            }
        }

        // The writes are deliberately outside the timeout scope: recording the result
        // should not be abandoned halfway just because the probe ran long.
        withContext(NonCancellable) {
            if (sampled == null) {
                serverDao.markOffline(server.id)
                return@withContext
            }

            serverDao.updateLiveStats(
                id = server.id,
                isOnline = true,
                healthScore = HealthScore.compute(
                    cpuPercent = sampled.cpuPercent,
                    ramPercent = sampled.ramPercent,
                    diskPercent = sampled.diskPercent,
                    thresholds = server.thresholds()
                ),
                cpuPercent = sampled.cpuPercent,
                ramPercent = sampled.ramPercent,
                diskPercent = sampled.diskPercent
            )
        }
    }

    fun deleteServer(server: ServerEntity) {
        viewModelScope.launch {
            serverDao.deleteServer(server)
        }
    }
}
