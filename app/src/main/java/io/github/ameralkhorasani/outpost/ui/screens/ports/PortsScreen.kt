package io.github.ameralkhorasani.outpost.ui.screens.ports

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.ameralkhorasani.outpost.data.model.PortForwardEntity
import io.github.ameralkhorasani.outpost.ui.theme.AlertRed
import io.github.ameralkhorasani.outpost.ui.theme.AppBackground
import io.github.ameralkhorasani.outpost.ui.theme.AppSurface
import io.github.ameralkhorasani.outpost.ui.theme.PrimaryAccent
import io.github.ameralkhorasani.outpost.ui.theme.TextPrimary
import io.github.ameralkhorasani.outpost.ui.theme.TextSecondary

/**
 * Port forwards for one server.
 *
 * The job this screen does: a service running on the VPS - a dev server, an admin panel,
 * anything bound to the VPS loopback - becomes reachable from the phone's own browser at
 * http://localhost:<port>, without exposing it to the internet or opening a firewall port.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortsScreen(
    serverId: String,
    viewModel: PortsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }

    // The tunnel runs in a foreground service, and on Android 13+ its notification - the
    // only way to stop the tunnel from outside the app - needs permission to appear.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(serverId) {
        viewModel.initialize(serverId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Port Forwards",
                            style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary)
                        )
                        Text(
                            uiState.server?.let { "${it.username}@${it.host}" } ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBackground)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = PrimaryAccent
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add forward", tint = AppBackground)
            }
        },
        containerColor = AppBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            uiState.errorMessage?.let { message ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = AppSurface)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            message,
                            color = AlertRed,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.dismissError() }) {
                            Text("Dismiss", color = TextSecondary)
                        }
                    }
                }
            }

            if (uiState.forwards.isEmpty() && !uiState.isLoading) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        top = 12.dp,
                        bottom = 96.dp
                    )
                ) {
                    items(uiState.forwards, key = { it.id }) { forward ->
                        val boundPort = uiState.activeFor(forward.id)?.localPort ?: forward.localPort
                        val url = "http://localhost:$boundPort/"
                        ForwardCard(
                            forward = forward,
                            isRunning = uiState.isRunning(forward.id),
                            isBusy = uiState.busyIds.contains(forward.id),
                            boundPort = boundPort,
                            onToggle = { viewModel.toggle(forward) },
                            onOpen = { openInBrowser(context, url) },
                            onCopy = { copyToClipboard(context, url) },
                            onDelete = { viewModel.delete(forward) },
                            onAutoStartChange = { viewModel.setAutoStart(forward, it) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddForwardDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { label, host, remotePort, localPort ->
                viewModel.addForward(label, host, remotePort, localPort)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                "No port forwards yet",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Add one to reach a service running on your VPS from this phone's " +
                    "browser. A web app on the server's port 8090 becomes " +
                    "http://localhost:8090 here - carried over SSH, with nothing " +
                    "exposed to the internet.",
                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ForwardCard(
    forward: PortForwardEntity,
    isRunning: Boolean,
    isBusy: Boolean,
    boundPort: Int,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    onAutoStartChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        forward.label,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        "localhost:$boundPort  →  ${forward.remoteHost}:${forward.remotePort}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = TextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }

                if (isBusy) {
                    CircularProgressIndicator(
                        color = PrimaryAccent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Button(
                        onClick = onToggle,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning) AlertRed else PrimaryAccent,
                            contentColor = AppBackground
                        )
                    ) {
                        Text(if (isRunning) "Stop" else "Start", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onOpen,
                    enabled = isRunning,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.OpenInBrowser,
                        contentDescription = null,
                        tint = if (isRunning) PrimaryAccent else TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Open in browser",
                        color = if (isRunning) TextPrimary else TextSecondary
                    )
                }
                IconButton(onClick = onCopy) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy URL",
                        tint = TextSecondary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextSecondary)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Open automatically",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
                Switch(
                    checked = forward.autoStart,
                    onCheckedChange = onAutoStartChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AppBackground,
                        checkedTrackColor = PrimaryAccent
                    )
                )
            }
        }
    }
}

@Composable
private fun AddForwardDialog(
    onDismiss: () -> Unit,
    onConfirm: (label: String, host: String, remotePort: Int, localPort: Int) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("127.0.0.1") }
    var remotePort by remember { mutableStateOf("") }
    var localPort by remember { mutableStateOf("") }

    // Mirroring the remote port is what the user almost always wants, so the local field
    // follows along until they type in it themselves.
    var localEdited by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppSurface,
        title = { Text("New port forward", color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Reach a service on the VPS from this phone's browser.",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
                PortField(label = "Name", value = label, onValueChange = { label = it })
                PortField(
                    label = "Remote port (on the VPS)",
                    value = remotePort,
                    numeric = true,
                    onValueChange = {
                        remotePort = it.filter(Char::isDigit).take(5)
                        if (!localEdited) localPort = remotePort
                    }
                )
                PortField(
                    label = "Local port (on this phone)",
                    value = localPort,
                    numeric = true,
                    onValueChange = {
                        localEdited = true
                        localPort = it.filter(Char::isDigit).take(5)
                    }
                )
                PortField(
                    label = "Bind address on the VPS",
                    value = host,
                    onValueChange = { host = it }
                )
                if (localPort.isNotBlank()) {
                    Text(
                        "Will be served at http://localhost:$localPort",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = PrimaryAccent,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        label,
                        host,
                        remotePort.toIntOrNull() ?: 0,
                        localPort.toIntOrNull() ?: remotePort.toIntOrNull() ?: 0
                    )
                },
                enabled = remotePort.isNotBlank()
            ) {
                Text("Add", color = PrimaryAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )
}

@Composable
private fun PortField(
    label: String,
    value: String,
    numeric: Boolean = false,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = TextSecondary) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedBorderColor = PrimaryAccent,
            unfocusedBorderColor = TextSecondary,
            cursorColor = PrimaryAccent
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

private fun openInBrowser(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val opened = runCatching { context.startActivity(intent); true }.getOrDefault(false)
    if (!opened) {
        Toast.makeText(context, "No browser found to open $url", Toast.LENGTH_LONG).show()
    }
}

private fun copyToClipboard(context: Context, url: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("Tunnel URL", url))
    Toast.makeText(context, "Copied $url", Toast.LENGTH_SHORT).show()
}
