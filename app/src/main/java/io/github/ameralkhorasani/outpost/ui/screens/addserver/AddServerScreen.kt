package io.github.ameralkhorasani.outpost.ui.screens.addserver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.ameralkhorasani.outpost.ui.theme.AlertRed
import io.github.ameralkhorasani.outpost.ui.theme.AppBackground
import io.github.ameralkhorasani.outpost.ui.theme.AppSurface
import io.github.ameralkhorasani.outpost.ui.theme.PrimaryAccent
import io.github.ameralkhorasani.outpost.ui.theme.TextPrimary
import io.github.ameralkhorasani.outpost.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServerScreen(
    viewModel: AddServerViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onServerSaved: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isSaveSuccess) {
        if (uiState.isSaveSuccess) {
            onServerSaved()
        }
    }

    val textFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = PrimaryAccent,
        unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
        focusedContainerColor = AppSurface,
        unfocusedContainerColor = AppSurface,
        focusedLabelColor = PrimaryAccent,
        unfocusedLabelColor = TextSecondary,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Add Linux Server",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )
        },
        containerColor = AppBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Display Name
            OutlinedTextField(
                value = uiState.name,
                onValueChange = { viewModel.onNameChange(it) },
                label = { Text("Server Display Name") },
                placeholder = { Text("e.g. Production Web-01") },
                leadingIcon = { Icon(Icons.Default.Storage, contentDescription = null, tint = PrimaryAccent) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = textFieldColors,
                singleLine = true
            )

            // Host & Port Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = uiState.host,
                    onValueChange = { viewModel.onHostChange(it) },
                    label = { Text("Host (IP / Domain)") },
                    placeholder = { Text("192.168.1.100") },
                    leadingIcon = { Icon(Icons.Default.Router, contentDescription = null, tint = PrimaryAccent) },
                    modifier = Modifier.weight(2f),
                    shape = RoundedCornerShape(12.dp),
                    colors = textFieldColors,
                    singleLine = true
                )

                OutlinedTextField(
                    value = uiState.port,
                    onValueChange = { viewModel.onPortChange(it) },
                    label = { Text("Port") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = textFieldColors,
                    singleLine = true
                )
            }

            // Username
            OutlinedTextField(
                value = uiState.username,
                onValueChange = { viewModel.onUsernameChange(it) },
                label = { Text("SSH Username") },
                placeholder = { Text("root / ubuntu") },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = PrimaryAccent) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = textFieldColors,
                singleLine = true
            )

            // Private Key PEM Text Area
            OutlinedTextField(
                value = uiState.privateKeyPem,
                onValueChange = { viewModel.onPrivateKeyPemChange(it) },
                label = { Text("OpenSSH Private Key (PEM format)") },
                placeholder = { Text("-----BEGIN OPENSSH PRIVATE KEY-----\n...\n-----END OPENSSH PRIVATE KEY-----") },
                leadingIcon = { Icon(Icons.Default.Key, contentDescription = null, tint = PrimaryAccent) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                shape = RoundedCornerShape(12.dp),
                colors = textFieldColors,
                maxLines = 10
            )

            // Passphrase (Optional)
            OutlinedTextField(
                value = uiState.passphrase,
                onValueChange = { viewModel.onPassphraseChange(it) },
                label = { Text("Key Passphrase (Optional)") },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = PrimaryAccent) },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = textFieldColors,
                singleLine = true
            )

            // code-server port (VS Code in the browser), bound to the VPS loopback
            OutlinedTextField(
                value = uiState.codeServerPort,
                onValueChange = { viewModel.onCodeServerPortChange(it) },
                label = { Text("VS Code (code-server) Port") },
                placeholder = { Text("8080") },
                supportingText = {
                    Text(
                        "Port code-server listens on at 127.0.0.1 of the VPS. Reached through the SSH tunnel only.",
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                leadingIcon = { Icon(Icons.Default.Code, contentDescription = null, tint = PrimaryAccent) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = textFieldColors,
                singleLine = true
            )

            // Test Connection Feedback Message
            uiState.testConnectionMessage?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = if (uiState.isTestSuccess) PrimaryAccent else AlertRed,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.testConnection() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryAccent),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryAccent),
                    enabled = !uiState.isTestingConnection
                ) {
                    if (uiState.isTestingConnection) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(18.dp),
                            color = PrimaryAccent,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Test Connection")
                    }
                }

                Button(
                    onClick = { viewModel.saveServer() },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryAccent,
                        contentColor = AppBackground
                    ),
                    enabled = !uiState.isSaving
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(18.dp),
                            color = AppBackground,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Save Server", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
