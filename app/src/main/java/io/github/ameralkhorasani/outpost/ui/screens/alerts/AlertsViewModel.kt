package io.github.ameralkhorasani.outpost.ui.screens.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ameralkhorasani.outpost.data.db.ServerDao
import io.github.ameralkhorasani.outpost.domain.AlertThresholds
import io.github.ameralkhorasani.outpost.domain.HealthScore
import io.github.ameralkhorasani.outpost.data.model.ServerEntity
import io.github.ameralkhorasani.outpost.data.model.thresholds
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlertsUiState(
    val server: ServerEntity? = null,
    val thresholds: AlertThresholds = AlertThresholds(),
    val isSaved: Boolean = false
)

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val serverDao: ServerDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlertsUiState())
    val uiState: StateFlow<AlertsUiState> = _uiState.asStateFlow()

    fun initialize(serverId: String) {
        viewModelScope.launch {
            val server = serverDao.getServerById(serverId)
            _uiState.value = _uiState.value.copy(
                server = server,
                // Load the thresholds already stored for this server rather than
                // showing defaults over the top of saved settings.
                thresholds = server?.thresholds() ?: AlertThresholds()
            )
        }
    }

    fun setAlertsEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(
            thresholds = _uiState.value.thresholds.copy(alertsEnabled = enabled)
        )
    }

    fun setCpuAbove(value: Int) {
        _uiState.value = _uiState.value.copy(
            thresholds = _uiState.value.thresholds.copy(cpuAbove = value)
        )
    }

    fun setRamAbove(value: Int) {
        _uiState.value = _uiState.value.copy(
            thresholds = _uiState.value.thresholds.copy(ramAbove = value)
        )
    }

    fun setDiskAbove(value: Int) {
        _uiState.value = _uiState.value.copy(
            thresholds = _uiState.value.thresholds.copy(diskAbove = value)
        )
    }

    fun setSslExpiryDays(days: Int) {
        _uiState.value = _uiState.value.copy(
            thresholds = _uiState.value.thresholds.copy(sslExpiryDays = days)
        )
    }

    fun saveSettings() {
        val server = _uiState.value.server ?: return
        val thresholds = _uiState.value.thresholds

        viewModelScope.launch {
            serverDao.updateAlertSettings(
                id = server.id,
                enabled = thresholds.alertsEnabled,
                cpuAbove = thresholds.cpuAbove,
                ramAbove = thresholds.ramAbove,
                diskAbove = thresholds.diskAbove,
                sslExpiryDays = thresholds.sslExpiryDays
            )
            // Re-score with the new thresholds so the Overview reflects them immediately.
            serverDao.getServerById(server.id)?.let { updated ->
                serverDao.updateLiveStats(
                    id = updated.id,
                    isOnline = updated.isOnline,
                    healthScore = HealthScore.compute(
                        cpuPercent = updated.lastCpuPercent,
                        ramPercent = updated.lastRamPercent,
                        diskPercent = updated.lastDiskPercent,
                        thresholds = updated.thresholds()
                    ),
                    cpuPercent = updated.lastCpuPercent,
                    ramPercent = updated.lastRamPercent,
                    diskPercent = updated.lastDiskPercent,
                    timestamp = updated.lastSeenTimestamp
                )
                _uiState.value = _uiState.value.copy(server = updated)
            }
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }
}
