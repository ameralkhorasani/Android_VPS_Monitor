package io.github.ameralkhorasani.outpost.ui.screens.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.ameralkhorasani.outpost.data.model.ServerEntity
import io.github.ameralkhorasani.outpost.ui.theme.AlertRed
import io.github.ameralkhorasani.outpost.ui.theme.CpuBlue
import io.github.ameralkhorasani.outpost.ui.theme.AppBackground
import io.github.ameralkhorasani.outpost.ui.theme.AppSurface
import io.github.ameralkhorasani.outpost.ui.theme.DiskOrange
import io.github.ameralkhorasani.outpost.ui.theme.PrimaryAccent
import io.github.ameralkhorasani.outpost.ui.theme.RamPurple
import io.github.ameralkhorasani.outpost.ui.theme.TextPrimary
import io.github.ameralkhorasani.outpost.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    viewModel: OverviewViewModel = hiltViewModel(),
    onAddServerClick: () -> Unit,
    onServerClick: (String) -> Unit,
    onTerminalClick: (String) -> Unit,
    onAlertsClick: (String) -> Unit,
    onDockerClick: (String) -> Unit = {},
    onPortsClick: (String) -> Unit = {},
    onSettingsClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    // Returning from a server screen used to show whatever the cards held at launch.
    // The dashboard's own lifecycle owner is the nav back stack entry, so this fires
    // exactly when the dashboard becomes visible again - and the ViewModel decides
    // whether that is worth a round of probes.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshIfStale()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // "Updated 2m ago" has to keep counting on its own, or it freezes at the moment of
    // the last probe and becomes a lie the longer the screen stays open.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Outpost",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            freshnessLabel(uiState.isRefreshing, uiState.lastUpdatedAt, now),
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }
                },
                actions = {
                    if (uiState.isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = PrimaryAccent,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    } else {
                        IconButton(onClick = { viewModel.refreshServers() }) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh all servers",
                                tint = TextSecondary
                            )
                        }
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddServerClick,
                containerColor = PrimaryAccent,
                contentColor = AppBackground,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Server")
            }
        },
        containerColor = AppBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 2x2 Top Summary Grid
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MetricSummaryCard(
                                modifier = Modifier.weight(1f),
                                title = "Health",
                                value = "${uiState.overallHealth}",
                                subtitle = "Worst: ${uiState.worstHealth}",
                                icon = Icons.Default.Favorite,
                                iconColor = PrimaryAccent
                            )
                            MetricSummaryCard(
                                modifier = Modifier.weight(1f),
                                title = "Online",
                                value = "${uiState.onlineCount}/${uiState.totalCount}",
                                subtitle = if (uiState.totalCount > 0 && uiState.onlineCount == uiState.totalCount) "All Systems Normal" else "Degraded",
                                icon = Icons.Default.CheckCircle,
                                iconColor = if (uiState.onlineCount == uiState.totalCount && uiState.totalCount > 0) PrimaryAccent else AlertRed
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            MetricSummaryCard(
                                modifier = Modifier.weight(1f),
                                title = "Avg CPU",
                                value = "${uiState.avgCpuPercent.toInt()}%",
                                subtitle = "Across servers",
                                icon = Icons.Default.Speed,
                                iconColor = CpuBlue
                            )
                            MetricSummaryCard(
                                modifier = Modifier.weight(1f),
                                title = "Avg RAM",
                                value = "${uiState.avgRamPercent.toInt()}%",
                                subtitle = "Across servers",
                                icon = Icons.Default.Memory,
                                iconColor = RamPurple
                            )
                        }
                    }
                }

                // Header for Server List
                item {
                    Text(
                        "SERVERS",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary,
                            letterSpacing = 1.2.sp
                        ),
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }

                // Server Cards
                if (uiState.servers.isEmpty()) {
                    item {
                        EmptyServersPlaceholder(onAddServerClick = onAddServerClick)
                    }
                } else {
                    items(uiState.servers, key = { it.id }) { server ->
                        ServerCard(
                            server = server,
                            onClick = { onServerClick(server.id) },
                            onTerminalClick = { onTerminalClick(server.id) },
                            onAlertsClick = { onAlertsClick(server.id) },
                            onDockerClick = { onDockerClick(server.id) },
                            onPortsClick = { onPortsClick(server.id) },
                            onDeleteClick = { viewModel.deleteServer(server) }
                        )
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun MetricSummaryCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppSurface)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary)
                )
                Icon(
                    icon,
                    contentDescription = title,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium.copy(
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
fun ServerCard(
    server: ServerEntity,
    onClick: () -> Unit,
    onTerminalClick: () -> Unit,
    onAlertsClick: () -> Unit,
    onDockerClick: () -> Unit,
    onPortsClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppSurface)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left Status Accent Border
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(140.dp)
                    .background(if (server.isOnline) PrimaryAccent else AlertRed)
            )

            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (server.isOnline) PrimaryAccent else AlertRed)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                server.name,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                            )
                        }
                        Text(
                            "${server.username}@${server.host}:${server.port}",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }

                    // Health Score Badge
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(PrimaryAccent.copy(alpha = 0.15f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "${server.healthScore}%",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = PrimaryAccent
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Progress Bars for CPU, RAM, Disk
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MiniProgressMetric(
                        modifier = Modifier.weight(1f),
                        label = "CPU",
                        percent = (server.lastCpuPercent / 100f).coerceIn(0f, 1f),
                        color = CpuBlue
                    )
                    MiniProgressMetric(
                        modifier = Modifier.weight(1f),
                        label = "RAM",
                        percent = (server.lastRamPercent / 100f).coerceIn(0f, 1f),
                        color = RamPurple
                    )
                    MiniProgressMetric(
                        modifier = Modifier.weight(1f),
                        label = "Disk",
                        percent = (server.lastDiskPercent / 100f).coerceIn(0f, 1f),
                        color = DiskOrange
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Toolbar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onTerminalClick, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Terminal,
                            contentDescription = "Terminal",
                            tint = PrimaryAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "More Actions",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            containerColor = AppSurface
                        ) {
                            DropdownMenuItem(
                                text = { Text("Docker", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Widgets, contentDescription = null, tint = CpuBlue) },
                                onClick = {
                                    menuExpanded = false
                                    onDockerClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Port Forwards", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.SettingsEthernet, contentDescription = null, tint = CpuBlue) },
                                onClick = {
                                    menuExpanded = false
                                    onPortsClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Alert Settings", color = TextPrimary) },
                                leadingIcon = { Icon(Icons.Default.Notifications, contentDescription = null, tint = TextSecondary) },
                                onClick = {
                                    menuExpanded = false
                                    onAlertsClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = AlertRed) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = AlertRed) },
                                onClick = {
                                    menuExpanded = false
                                    onDeleteClick()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MiniProgressMetric(
    modifier: Modifier = Modifier,
    label: String,
    percent: Float,
    color: Color
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
            Text("${(percent * 100).toInt()}%", style = MaterialTheme.typography.labelSmall.copy(color = TextPrimary))
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { percent },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = color,
            trackColor = AppBackground
        )
    }
}

@Composable
fun EmptyServersPlaceholder(onAddServerClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppSurface)
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Storage,
                contentDescription = null,
                tint = PrimaryAccent,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No Servers Configured",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Add your Linux server to start real-time monitoring and SSH terminal sessions.",
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))
            FloatingActionButton(
                onClick = onAddServerClick,
                containerColor = PrimaryAccent,
                contentColor = AppBackground
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add First Server", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Describes how old the figures on the cards are.
 *
 * The dashboard reads cached metrics out of the database, so without this line there is
 * nothing on screen to distinguish a reading taken seconds ago from one taken when the
 * app was opened this morning.
 */
private fun freshnessLabel(isRefreshing: Boolean, lastUpdatedAt: Long?, now: Long): String {
    if (isRefreshing) return "Refreshing servers..."
    if (lastUpdatedAt == null) return "Not refreshed yet - tap refresh"

    val ageSeconds = ((now - lastUpdatedAt) / 1000L).coerceAtLeast(0L)
    return when {
        ageSeconds < 15 -> "Updated just now"
        ageSeconds < 60 -> "Updated ${ageSeconds}s ago"
        ageSeconds < 3600 -> "Updated ${ageSeconds / 60}m ago"
        else -> "Updated ${ageSeconds / 3600}h ago"
    }
}
