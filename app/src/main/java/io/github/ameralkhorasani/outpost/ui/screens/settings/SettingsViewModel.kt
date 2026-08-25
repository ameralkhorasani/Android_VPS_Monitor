package io.github.ameralkhorasani.outpost.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ameralkhorasani.outpost.core.crash.CrashReporter
import io.github.ameralkhorasani.outpost.data.db.ServerDao
import io.github.ameralkhorasani.outpost.data.preferences.SettingsRepository
import io.github.ameralkhorasani.outpost.data.model.ThemeMode
import io.github.ameralkhorasani.outpost.data.security.SecureKeyManager
import io.github.ameralkhorasani.outpost.data.security.SshKeyUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A server's public key, ready to paste into authorized_keys. */
data class ServerPublicKey(
    val serverName: String,
    val username: String,
    val host: String,
    val publicKeyLine: String?,
    val error: String? = null
)

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val probeOnStartup: Boolean = true,
    val keepScreenOn: Boolean = false,
    val serverCount: Int = 0,
    val publicKeys: List<ServerPublicKey> = emptyList()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val serverDao: ServerDao,
    private val secureKeyManager: SecureKeyManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _crashLog = MutableStateFlow(CrashReporter.readLog(context))
    val crashLog: StateFlow<String?> = _crashLog.asStateFlow()

    fun clearCrashLog() {
        CrashReporter.clearLog(context)
        _crashLog.value = null
    }

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.themeMode,
                settingsRepository.probeOnStartup,
                settingsRepository.keepScreenOn,
                serverDao.getAllServers()
            ) { theme, probe, keepScreen, servers ->
                SettingsUiState(
                    themeMode = theme,
                    probeOnStartup = probe,
                    keepScreenOn = keepScreen,
                    serverCount = servers.size,
                    publicKeys = servers.map { server ->
                        val derived = runCatching {
                            SshKeyUtils.derivePublicKeyLine(
                                privateKeyPem = secureKeyManager.decryptPrivateKey(server.encryptedPrivateKey),
                                passphrase = server.keyPassphrase?.let {
                                    secureKeyManager.decryptPrivateKey(it)
                                },
                                comment = "outpost-${server.name.replace(' ', '-')}"
                            ).getOrThrow()
                        }
                        ServerPublicKey(
                            serverName = server.name,
                            username = server.username,
                            host = server.host,
                            publicKeyLine = derived.getOrNull(),
                            error = derived.exceptionOrNull()?.let { "Could not read this server's key" }
                        )
                    }
                )
            }.collect { _uiState.value = it }
        }
    }

    fun setThemeMode(mode: ThemeMode) = settingsRepository.setThemeMode(mode)
    fun setProbeOnStartup(enabled: Boolean) = settingsRepository.setProbeOnStartup(enabled)
    fun setKeepScreenOn(enabled: Boolean) = settingsRepository.setKeepScreenOn(enabled)
}
