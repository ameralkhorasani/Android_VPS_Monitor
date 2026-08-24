package io.github.ameralkhorasani.outpost.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.ameralkhorasani.outpost.data.model.ThemeMode
import io.github.ameralkhorasani.outpost.ui.theme.AlertRed
import io.github.ameralkhorasani.outpost.ui.theme.AppBackground
import io.github.ameralkhorasani.outpost.ui.theme.AppSurface
import io.github.ameralkhorasani.outpost.ui.theme.PrimaryAccent
import io.github.ameralkhorasani.outpost.ui.theme.TextDisabled
import io.github.ameralkhorasani.outpost.ui.theme.TextPrimary
import io.github.ameralkhorasani.outpost.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val crashLog by viewModel.crashLog.collectAsState()
    val clipboard = LocalClipboardManager.current
    var portText by remember(uiState.defaultCodeServerPort) {
        mutableStateOf(uiState.defaultCodeServerPort.toString())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            SettingsSection(title = "APPEARANCE") {
                SettingsRow(
                    icon = Icons.Default.DarkMode,
                    title = "Theme",
                    subtitle = "System follows your device's light/dark setting"
                )
                Spacer(modifier = Modifier.height(12.dp))
                ThemeSelector(
                    selected = uiState.themeMode,
                    onSelect = { viewModel.setThemeMode(it) }
                )
            }

            SettingsSection(title = "BEHAVIOUR") {
                SettingsToggleRow(
                    icon = Icons.Default.Refresh,
                    title = "Check servers on startup",
                    subtitle = "Connects to every server when the app opens to refresh " +
                        "health and metrics. Turn off to save mobile data and battery.",
                    checked = uiState.probeOnStartup,
                    onCheckedChange = { viewModel.setProbeOnStartup(it) }
                )
                Spacer(modifier = Modifier.height(4.dp))
                SettingsToggleRow(
                    icon = Icons.Default.Visibility,
                    title = "Keep screen on",
                    subtitle = "Stops the display sleeping while the app is open - useful " +
                        "when watching live metrics or a long build.",
                    checked = uiState.keepScreenOn,
                    onCheckedChange = { viewModel.setKeepScreenOn(it) }
                )
            }

            SettingsSection(title = "DEFAULTS") {
                SettingsRow(
                    icon = Icons.Default.Code,
                    title = "Default code-server port",
                    subtitle = "Pre-filled when you add a new server. Existing servers keep their own port."
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = portText,
                    onValueChange = {
                        portText = it
                        viewModel.setDefaultCodeServerPort(it)
                    },
                    label = { Text("Port") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryAccent,
                        unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                        focusedLabelColor = PrimaryAccent,
                        unfocusedLabelColor = TextSecondary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
            }

            if (uiState.publicKeys.isNotEmpty()) {
                SettingsSection(title = "SSH PUBLIC KEYS") {
                    SettingsRow(
                        icon = Icons.Default.Key,
                        title = "Authorise these on your servers",
                        subtitle = "A key only works once its public half is listed in " +
                            "~/.ssh/authorized_keys for the user you connect as. Copy the " +
                            "line below and add it there."
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    uiState.publicKeys.forEach { entry ->
                        PublicKeyCard(
                            entry = entry,
                            onCopy = { line -> clipboard.setText(AnnotatedString(line)) }
                        )
                    }
                }
            }

            SettingsSection(title = "SECURITY") {
                SettingsRow(
                    icon = Icons.Default.Lock,
                    title = "Key storage",
                    subtitle = "SSH private keys are encrypted with AES-256-GCM under an " +
                        "Android Keystore master key and never leave this device."
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Host key verification is currently permissive: the app does not detect " +
                        "if a server's identity changes between connections.",
                    style = MaterialTheme.typography.bodySmall.copy(color = AlertRed)
                )
            }

            crashLog?.let { log ->
                SettingsSection(title = "CRASH LOG") {
                    SettingsRow(
                        icon = Icons.Default.BugReport,
                        title = "The app closed unexpectedly",
                        subtitle = "Copy this and send it over - it identifies the exact cause."
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        log.takeLast(1500),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { clipboard.setText(AnnotatedString(log)) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryAccent,
                                contentColor = AppSurface
                            )
                        ) {
                            Text("Copy", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = { viewModel.clearCrashLog() },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Clear", color = TextSecondary)
                        }
                    }
                }
            }

            SettingsSection(title = "ABOUT") {
                InfoLine("Servers configured", uiState.serverCount.toString())
                InfoLine("Version", "1.0.0 (debug)")
                InfoLine("SSH engine", "sshj 0.38.0")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ThemeSelector(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ThemeMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) PrimaryAccent.copy(alpha = 0.16f) else AppBackground
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) PrimaryAccent else TextSecondary.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelect(mode) },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    mode.label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = if (isSelected) PrimaryAccent else TextSecondary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.2.sp
            ),
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AppSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) { content() }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            icon,
            contentDescription = null,
            tint = PrimaryAccent,
            modifier = Modifier
                .size(20.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
            )
        }
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Top) {
            Icon(
                icon,
                contentDescription = null,
                tint = PrimaryAccent,
                modifier = Modifier
                    .size(20.dp)
                    .padding(top = 2.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AppSurface,
                checkedTrackColor = PrimaryAccent,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = AppBackground
            )
        )
    }
}

@Composable
private fun PublicKeyCard(
    entry: ServerPublicKey,
    onCopy: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(AppBackground)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.serverName,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Text(
                    "${entry.username}@${entry.host}",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
            }
            entry.publicKeyLine?.let { line ->
                IconButton(onClick = { onCopy(line) }) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy public key",
                        tint = PrimaryAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (entry.error != null) {
            Text(
                entry.error,
                style = MaterialTheme.typography.bodySmall.copy(color = AlertRed)
            )
        } else {
            Text(
                entry.publicKeyLine.orEmpty(),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "On the server, run:\nmkdir -p ~/.ssh && echo '<paste>' >> ~/.ssh/authorized_keys" +
                    " && chmod 700 ~/.ssh && chmod 600 ~/.ssh/authorized_keys",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextDisabled,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            )
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        )
    }
}
