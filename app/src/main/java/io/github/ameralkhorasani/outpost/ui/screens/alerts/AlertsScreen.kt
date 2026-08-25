package io.github.ameralkhorasani.outpost.ui.screens.alerts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import androidx.hilt.navigation.compose.hiltViewModel
import io.github.ameralkhorasani.outpost.ui.theme.CpuBlue
import io.github.ameralkhorasani.outpost.ui.theme.AppBackground
import io.github.ameralkhorasani.outpost.ui.theme.AppSurface
import io.github.ameralkhorasani.outpost.ui.theme.DiskOrange
import io.github.ameralkhorasani.outpost.ui.theme.PrimaryAccent
import io.github.ameralkhorasani.outpost.ui.theme.RamPurple
import io.github.ameralkhorasani.outpost.ui.theme.TextPrimary
import io.github.ameralkhorasani.outpost.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(
    serverId: String,
    viewModel: AlertsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(serverId) {
        viewModel.initialize(serverId)
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) {
            onNavigateBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Alert Settings", style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary))
                        Text(uiState.server?.name ?: "", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
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

            // Master Enable Toggle Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AppSurface)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Enable System Alerts",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary)
                        )
                        Text(
                            "Receive notifications when thresholds are crossed",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }
                    Switch(
                        checked = uiState.thresholds.alertsEnabled,
                        onCheckedChange = { viewModel.setAlertsEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AppBackground,
                            checkedTrackColor = PrimaryAccent,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = AppBackground
                        )
                    )
                }
            }

            // CPU Threshold Slider
            ThresholdSliderCard(
                title = "CPU Usage Alert Threshold",
                subtitle = "Alert when CPU exceeds target %",
                value = uiState.thresholds.cpuAbove.toFloat(),
                valueRange = 50f..99f,
                suffix = "%",
                activeColor = CpuBlue,
                enabled = uiState.thresholds.alertsEnabled,
                onValueChange = { viewModel.setCpuAbove(it.toInt()) }
            )

            // RAM Threshold Slider
            ThresholdSliderCard(
                title = "RAM Usage Alert Threshold",
                subtitle = "Alert when memory usage exceeds target %",
                value = uiState.thresholds.ramAbove.toFloat(),
                valueRange = 50f..99f,
                suffix = "%",
                activeColor = RamPurple,
                enabled = uiState.thresholds.alertsEnabled,
                onValueChange = { viewModel.setRamAbove(it.toInt()) }
            )

            // Disk Threshold Slider
            ThresholdSliderCard(
                title = "Disk Usage Alert Threshold",
                subtitle = "Alert when disk partition exceeds target %",
                value = uiState.thresholds.diskAbove.toFloat(),
                valueRange = 50f..99f,
                suffix = "%",
                activeColor = DiskOrange,
                enabled = uiState.thresholds.alertsEnabled,
                onValueChange = { viewModel.setDiskAbove(it.toInt()) }
            )

            // SSL Expiry Slider
            ThresholdSliderCard(
                title = "SSL Expiry Warning",
                subtitle = "Alert when SSL certificate expires within N days",
                value = uiState.thresholds.sslExpiryDays.toFloat(),
                valueRange = 1f..30f,
                suffix = " days",
                activeColor = PrimaryAccent,
                enabled = uiState.thresholds.alertsEnabled,
                onValueChange = { viewModel.setSslExpiryDays(it.toInt()) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.saveSettings() },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent, contentColor = AppBackground)
            ) {
                Text("Save Alert Configuration", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun ThresholdSliderCard(
    title: String,
    subtitle: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    suffix: String,
    activeColor: Color,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
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
                    Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = TextPrimary))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                }
                Text(
                    "${value.toInt()}$suffix",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = activeColor)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                enabled = enabled,
                colors = SliderDefaults.colors(
                    thumbColor = activeColor,
                    activeTrackColor = activeColor,
                    inactiveTrackColor = AppBackground
                )
            )
        }
    }
}
