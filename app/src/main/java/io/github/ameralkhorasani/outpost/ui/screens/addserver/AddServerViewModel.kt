package io.github.ameralkhorasani.outpost.ui.screens.addserver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.ameralkhorasani.outpost.data.db.ServerDao
import io.github.ameralkhorasani.outpost.data.preferences.SettingsRepository
import io.github.ameralkhorasani.outpost.data.model.ServerEntity
import io.github.ameralkhorasani.outpost.data.security.SecureKeyManager
import io.github.ameralkhorasani.outpost.ssh.SshRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddServerUiState(
    val name: String = "",
    val host: String = "",
    val port: String = "22",
    val username: String = "root",
    val privateKeyPem: String = "",
    val passphrase: String = "",
    val codeServerPort: String = "8080",
    val isTestingConnection: Boolean = false,
    val testConnectionMessage: String? = null,
    val isTestSuccess: Boolean = false,
    val isSaving: Boolean = false,
    val isSaveSuccess: Boolean = false
)

@HiltViewModel
class AddServerViewModel @Inject constructor(
    private val serverDao: ServerDao,
    private val secureKeyManager: SecureKeyManager,
    private val sshRepository: SshRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AddServerUiState(codeServerPort = settingsRepository.defaultCodeServerPort.value.toString())
    )
    val uiState: StateFlow<AddServerUiState> = _uiState.asStateFlow()

    fun onNameChange(name: String) { _uiState.value = _uiState.value.copy(name = name) }
    fun onHostChange(host: String) { _uiState.value = _uiState.value.copy(host = host) }
    fun onPortChange(port: String) { _uiState.value = _uiState.value.copy(port = port) }
    fun onUsernameChange(username: String) { _uiState.value = _uiState.value.copy(username = username) }
    fun onPrivateKeyPemChange(pem: String) { _uiState.value = _uiState.value.copy(privateKeyPem = pem) }
    fun onPassphraseChange(pass: String) { _uiState.value = _uiState.value.copy(passphrase = pass) }
    fun onCodeServerPortChange(port: String) { _uiState.value = _uiState.value.copy(codeServerPort = port) }

    fun testConnection() {
        val state = _uiState.value
        if (state.host.isBlank() || state.privateKeyPem.isBlank()) {
            _uiState.value = state.copy(
                testConnectionMessage = "Please enter host and private key PEM",
                isTestSuccess = false
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isTestingConnection = true, testConnectionMessage = null)
            val portInt = state.port.toIntOrNull() ?: 22

            val encryptedKey = secureKeyManager.encryptPrivateKey(state.privateKeyPem)
            val encryptedPass = if (state.passphrase.isNotBlank()) secureKeyManager.encryptPrivateKey(state.passphrase) else null

            val tempServer = ServerEntity(
                name = state.name.ifBlank { state.host },
                host = state.host,
                port = portInt,
                username = state.username,
                encryptedPrivateKey = encryptedKey,
                keyPassphrase = encryptedPass
            )

            val connectResult = sshRepository.connect(tempServer)
            if (connectResult.isSuccess) {
                val client = connectResult.getOrThrow()
                sshRepository.disconnect(client)
                _uiState.value = _uiState.value.copy(
                    isTestingConnection = false,
                    isTestSuccess = true,
                    testConnectionMessage = "SSH Connection Successful!"
                )
            } else {
                val err = connectResult.exceptionOrNull()?.message ?: "Connection failed"
                _uiState.value = _uiState.value.copy(
                    isTestingConnection = false,
                    isTestSuccess = false,
                    testConnectionMessage = "SSH Error: $err"
                )
            }
        }
    }

    fun saveServer() {
        val state = _uiState.value
        if (state.host.isBlank() || state.privateKeyPem.isBlank()) {
            _uiState.value = state.copy(testConnectionMessage = "Host and Private Key required")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true)
            val portInt = state.port.toIntOrNull() ?: 22
            val encryptedKey = secureKeyManager.encryptPrivateKey(state.privateKeyPem)
            val encryptedPass = if (state.passphrase.isNotBlank()) secureKeyManager.encryptPrivateKey(state.passphrase) else null

            val server = ServerEntity(
                name = state.name.ifBlank { state.host },
                host = state.host,
                port = portInt,
                username = state.username.ifBlank { "root" },
                encryptedPrivateKey = encryptedKey,
                keyPassphrase = encryptedPass,
                isOnline = true,
                codeServerPort = state.codeServerPort.toIntOrNull() ?: 8080
            )

            serverDao.insertServer(server)
            _uiState.value = _uiState.value.copy(isSaving = false, isSaveSuccess = true)
        }
    }
}
