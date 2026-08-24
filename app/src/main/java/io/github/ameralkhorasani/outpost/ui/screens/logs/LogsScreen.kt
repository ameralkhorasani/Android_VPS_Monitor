package io.github.ameralkhorasani.outpost.ui.screens.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.ameralkhorasani.outpost.ui.theme.AlertRed
import io.github.ameralkhorasani.outpost.ui.theme.CpuBlue
import io.github.ameralkhorasani.outpost.ui.theme.AppBackground
import io.github.ameralkhorasani.outpost.ui.theme.AppSurface
import io.github.ameralkhorasani.outpost.ui.theme.PrimaryAccent
import io.github.ameralkhorasani.outpost.ui.theme.TextPrimary
import io.github.ameralkhorasani.outpost.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(
    serverId: String,
    viewModel: LogsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var expandedDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(serverId) {
        viewModel.initialize(serverId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Log Viewer", style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary))
                        Text(uiState.server?.name ?: "", style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.fetchLogContent() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = PrimaryAccent)
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
        ) {
            // Log File Selector Dropdown
            ExposedDropdownMenuBox(
                expanded = expandedDropdown,
                onExpandedChange = { expandedDropdown = !expandedDropdown }
            ) {
                OutlinedTextField(
                    value = uiState.selectedLogFile,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Select Log File") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDropdown) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryAccent,
                        unfocusedBorderColor = TextSecondary.copy(alpha = 0.3f),
                        focusedContainerColor = AppSurface,
                        unfocusedContainerColor = AppSurface,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    shape = RoundedCornerShape(12.dp)
                )

                ExposedDropdownMenu(
                    expanded = expandedDropdown,
                    onDismissRequest = { expandedDropdown = false },
                    modifier = Modifier.background(AppSurface)
                ) {
                    uiState.availableLogFiles.forEach { file ->
                        DropdownMenuItem(
                            text = { Text(file, color = TextPrimary) },
                            onClick = {
                                viewModel.selectLogFile(file)
                                expandedDropdown = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Control Buttons Row (Tail, Head, Live, Wrap)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Tail", "Head").forEach { mode ->
                    Button(
                        onClick = { viewModel.setMode(mode) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.mode == mode) PrimaryAccent else AppSurface,
                            contentColor = if (uiState.mode == mode) AppBackground else TextPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(mode, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = { viewModel.toggleLineWrap() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.lineWrapEnabled) PrimaryAccent.copy(alpha = 0.2f) else AppSurface,
                        contentColor = PrimaryAccent
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(if (uiState.lineWrapEnabled) "Wrap: ON" else "Wrap: OFF", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Log Console Content Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(AppSurface, shape = RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        color = PrimaryAccent,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else if (uiState.errorMessage != null) {
                    Text(
                        "Error: ${uiState.errorMessage}",
                        color = AlertRed,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    val lines = uiState.logOutput.lines()

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(if (!uiState.lineWrapEnabled) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
                    ) {
                        items(lines) { line ->
                            ColorCodedLogLine(line = line)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun ColorCodedLogLine(line: String) {
    val annotatedString = buildAnnotatedString {
        val lower = line.lowercase()
        when {
            lower.contains("error") || lower.contains("failed") || lower.contains("fatal") -> {
                withStyle(style = SpanStyle(color = AlertRed, fontWeight = FontWeight.Bold)) {
                    append(line)
                }
            }
            lower.contains("listening") || lower.contains("success") || lower.contains("started") -> {
                withStyle(style = SpanStyle(color = PrimaryAccent)) {
                    append(line)
                }
            }
            lower.contains("warn") || lower.contains("warning") -> {
                withStyle(style = SpanStyle(color = io.github.ameralkhorasani.outpost.ui.theme.DiskOrange)) {
                    append(line)
                }
            }
            else -> {
                withStyle(style = SpanStyle(color = TextPrimary)) {
                    append(line)
                }
            }
        }
    }

    Text(
        text = annotatedString,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        modifier = Modifier.padding(vertical = 2.dp)
    )
}
