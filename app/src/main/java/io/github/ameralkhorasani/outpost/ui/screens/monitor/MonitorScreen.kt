package io.github.ameralkhorasani.outpost.ui.screens.monitor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import io.github.ameralkhorasani.outpost.ui.theme.AlertRed
import io.github.ameralkhorasani.outpost.ui.theme.CpuBlue
import io.github.ameralkhorasani.outpost.ui.theme.AppBackground
import io.github.ameralkhorasani.outpost.ui.theme.AppSurface
import io.github.ameralkhorasani.outpost.ui.theme.DiskOrange
import io.github.ameralkhorasani.outpost.ui.theme.PrimaryAccent
import io.github.ameralkhorasani.outpost.ui.theme.RamPurple
import io.github.ameralkhorasani.outpost.ui.theme.TextPrimary
import io.github.ameralkhorasani.outpost.ui.theme.TextSecondary
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Tabs that hand off to another screen instead of swapping the body below. */
private val NAVIGATING_TABS = setOf("Logs", "Docker", "Terminal")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(
    serverId: String,
    viewModel: MonitorViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onTabSelected: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Live Monitor", "Processes", "Uptime", "Logs", "Docker")

    LaunchedEffect(serverId) {
        viewModel.initialize(serverId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            uiState.server?.name ?: "Server Monitor",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                        )
                        Text(
                            uiState.server?.let { "${it.username}@${it.host}:${it.port}" } ?: "",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    // Pause / Resume Toggle Pill
                    Button(
                        onClick = { viewModel.togglePause() },
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (uiState.isPaused) AlertRed else PrimaryAccent,
                            contentColor = AppBackground
                        ),
                        modifier = Modifier
                            .height(36.dp)
                            .padding(end = 8.dp)
                    ) {
                        Icon(
                            if (uiState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            if (uiState.isPaused) "Resume" else "Pause",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
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
        ) {
            // Scrollable Tab Row
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = AppBackground,
                contentColor = PrimaryAccent,
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = PrimaryAccent
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = {
                            // Logs and Docker are screens of their own, not panes of this
                            // one. Marking them selected as well left the indicator on a
                            // tab with no body, so coming back from them showed an empty
                            // page until another tab was tapped.
                            if (title in NAVIGATING_TABS) {
                                onTabSelected(title)
                            } else {
                                selectedTabIndex = index
                                if (title == "Processes") viewModel.fetchProcesses()
                            }
                        },
                        text = {
                            Text(
                                title,
                                color = if (selectedTabIndex == index) PrimaryAccent else TextSecondary,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (selectedTabIndex) {
                0 -> LiveMonitorTab(
                    uiState = uiState,
                    onReconnect = { viewModel.reconnect() }
                )
                1 -> ProcessesTab(uiState = uiState, onRefresh = { viewModel.fetchProcesses() })
                2 -> UptimeTab(uiState = uiState)
            }
        }
    }
}

@Composable
fun LiveMonitorTab(uiState: MonitorUiState, onReconnect: () -> Unit = {}) {
    if (!uiState.isConnected && uiState.errorMessage == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = PrimaryAccent)
        }
        return
    }

    // Only a full-screen error when there is nothing to show. Once samples exist, the
    // charts stay up and the failure becomes a banner - losing the connection should not
    // wipe the history that was already collected.
    if (uiState.errorMessage != null && uiState.cpuHistory.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    uiState.errorMessage,
                    color = AlertRed,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onReconnect,
                    enabled = !uiState.isReconnecting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryAccent,
                        contentColor = AppBackground
                    )
                ) {
                    Text(if (uiState.isReconnecting) "Reconnecting..." else "Reconnect")
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (uiState.errorMessage != null) {
            item { ConnectionLostBanner(uiState.errorMessage, uiState.isReconnecting, onReconnect) }
        }

        item {
            ChartCard(
                title = "CPU",
                currentValue = "${uiState.currentStats.cpuPercent.toInt()}%",
                dataPoints = uiState.cpuHistory,
                lineColor = CpuBlue,
                windowSeconds = uiState.windowSeconds
            )
        }
        item {
            ChartCard(
                title = "RAM",
                currentValue = "${uiState.currentStats.ramPercent.toInt()}%",
                subtitle = "${uiState.currentStats.ramUsedMb} / ${uiState.currentStats.ramTotalMb} MB",
                dataPoints = uiState.ramHistory,
                lineColor = RamPurple,
                windowSeconds = uiState.windowSeconds
            )
        }
        item {
            ChartCard(
                title = "Swap",
                currentValue = "${uiState.currentStats.swapPercent.toInt()}%",
                dataPoints = uiState.swapHistory,
                lineColor = AlertRed,
                windowSeconds = uiState.windowSeconds
            )
        }
        item {
            ChartCard(
                title = "Disk (/)",
                currentValue = "${uiState.currentStats.diskPercent.toInt()}%",
                dataPoints = uiState.diskHistory,
                lineColor = DiskOrange,
                windowSeconds = uiState.windowSeconds
            )
        }
        item {
            ChartCard(
                title = "Load average",
                currentValue = "%.2f".format(uiState.currentStats.loadAvg1),
                subtitle = "5m %.2f · 15m %.2f".format(
                    uiState.currentStats.loadAvg5,
                    uiState.currentStats.loadAvg15
                ),
                dataPoints = uiState.loadHistory,
                lineColor = PrimaryAccent,
                windowSeconds = uiState.windowSeconds,
                // Load is not a percentage. Pinning it to a 0-100 axis drew every normal
                // machine as a flat line along the bottom; this scales to the data.
                axisMax = null,
                axisFloor = 1f,
                formatValue = { "%.2f".format(it) }
            )
        }
        item {
            DualChartCard(
                title = "Network",
                rxValue = formatRate(uiState.currentStats.netRxKbSec),
                txValue = formatRate(uiState.currentStats.netTxKbSec),
                rxPoints = uiState.netRxHistory,
                txPoints = uiState.netTxHistory,
                rxColor = CpuBlue,
                txColor = DiskOrange,
                windowSeconds = uiState.windowSeconds
            )
        }
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}

@Composable
private fun ConnectionLostBanner(
    message: String,
    isReconnecting: Boolean,
    onReconnect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AlertRed.copy(alpha = 0.15f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodySmall.copy(color = AlertRed),
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onReconnect,
            enabled = !isReconnecting,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AlertRed,
                contentColor = AppBackground
            )
        ) {
            Text(
                if (isReconnecting) "..." else "Reconnect",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private val CHART_HEIGHT = 132.dp
private val AXIS_GUTTER = 52.dp

/**
 * Rounds a value up to a readable axis top: 1, 2 or 5 times a power of ten.
 *
 * Auto-scaling to the raw maximum gives axis labels like "0.83" and a line that always
 * touches the ceiling; snapping to these steps keeps the top label round and leaves the
 * trace some headroom.
 */
private fun niceCeiling(value: Float, floor: Float): Float {
    val target = maxOf(value, floor)
    if (target <= 0f) return floor
    val magnitude = 10f.pow(floor(log10(target.toDouble())).toFloat())
    val normalised = target / magnitude
    val step = when {
        normalised <= 1f -> 1f
        normalised <= 2f -> 2f
        normalised <= 5f -> 5f
        else -> 10f
    }
    return step * magnitude
}

private fun formatRate(kbSec: Float): String = when {
    kbSec >= 1024f -> "%.1f MB/s".format(kbSec / 1024f)
    kbSec >= 10f -> "%.0f KB/s".format(kbSec)
    else -> "%.1f KB/s".format(kbSec)
}

private fun formatWindow(seconds: Int): String = when {
    seconds <= 0 -> "live"
    seconds < 60 -> "last ${seconds}s"
    else -> "last ${seconds / 60}m ${seconds % 60}s"
}

@Composable
fun ChartCard(
    title: String,
    currentValue: String,
    dataPoints: List<Float>,
    lineColor: Color,
    windowSeconds: Int,
    subtitle: String? = null,
    /** Fixed axis top, or null to scale to the data. */
    axisMax: Float? = 100f,
    axisFloor: Float = 1f,
    formatValue: (Float) -> String = { "${it.toInt()}%" }
) {
    val top = axisMax ?: niceCeiling(dataPoints.maxOrNull() ?: 0f, axisFloor)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }
                }
                Text(
                    currentValue,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = lineColor
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            ChartPlot(
                series = listOf(ChartSeries(dataPoints, lineColor)),
                axisTop = top,
                formatValue = formatValue
            )

            Spacer(modifier = Modifier.height(8.dp))

            ChartFooter(
                points = dataPoints,
                windowSeconds = windowSeconds,
                formatValue = formatValue
            )
        }
    }
}

@Composable
fun DualChartCard(
    title: String,
    rxValue: String,
    txValue: String,
    rxPoints: List<Float>,
    txPoints: List<Float>,
    rxColor: Color,
    txColor: Color,
    windowSeconds: Int
) {
    val peak = maxOf(rxPoints.maxOrNull() ?: 0f, txPoints.maxOrNull() ?: 0f)
    val top = niceCeiling(peak, 10f)

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
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                )
                Column(horizontalAlignment = Alignment.End) {
                    LegendEntry("↓ $rxValue", rxColor)
                    LegendEntry("↑ $txValue", txColor)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            ChartPlot(
                series = listOf(
                    ChartSeries(rxPoints, rxColor),
                    ChartSeries(txPoints, txColor)
                ),
                axisTop = top,
                formatValue = ::formatRate
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "peak ${formatRate(peak)} · ${formatWindow(windowSeconds)}",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            )
        }
    }
}

@Composable
private fun LegendEntry(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

/** One line on a plot. */
data class ChartSeries(val points: List<Float>, val color: Color)

/**
 * The plot itself: gridlines, y-axis labels, smoothed traces and a marker on the latest
 * reading.
 *
 * Axis labels are Compose text in a gutter beside the canvas rather than text drawn into
 * the canvas, so they pick up the theme's colours and scale with the user's font size.
 */
@Composable
private fun ChartPlot(
    series: List<ChartSeries>,
    axisTop: Float,
    formatValue: (Float) -> String
) {
    val gridColor = TextSecondary.copy(alpha = 0.18f)
    val hasData = series.any { it.points.size >= 2 }

    Row(modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT)) {
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Gridlines at 0 / 25 / 50 / 75 / 100 % of the axis.
                repeat(5) { i ->
                    val y = h * i / 4f
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f
                    )
                }

                if (!hasData) return@Canvas

                series.forEach { s ->
                    if (s.points.size < 2) return@forEach
                    val stepX = w / (s.points.size - 1)
                    fun yOf(v: Float) = h - (v / axisTop).coerceIn(0f, 1f) * h

                    // Smoothed with a cubic through the midpoint of each pair. Straight
                    // segments made a 1.5s sample interval look far spikier than the
                    // machine actually was.
                    val line = Path()
                    val fill = Path()
                    line.moveTo(0f, yOf(s.points[0]))
                    fill.moveTo(0f, h)
                    fill.lineTo(0f, yOf(s.points[0]))

                    for (i in 1 until s.points.size) {
                        val prevX = (i - 1) * stepX
                        val curX = i * stepX
                        val prevY = yOf(s.points[i - 1])
                        val curY = yOf(s.points[i])
                        val midX = (prevX + curX) / 2f
                        line.cubicTo(midX, prevY, midX, curY, curX, curY)
                        fill.cubicTo(midX, prevY, midX, curY, curX, curY)
                    }

                    fill.lineTo(w, h)
                    fill.close()

                    drawPath(
                        path = fill,
                        brush = Brush.verticalGradient(
                            colors = listOf(s.color.copy(alpha = 0.28f), Color.Transparent)
                        )
                    )
                    drawPath(
                        path = line,
                        color = s.color,
                        style = Stroke(width = 2.5.dp.toPx())
                    )

                    // Marker on the newest sample, so "now" is obvious at a glance.
                    val lastY = yOf(s.points.last())
                    drawCircle(
                        color = s.color.copy(alpha = 0.25f),
                        radius = 6.dp.toPx(),
                        center = Offset(w, lastY)
                    )
                    drawCircle(
                        color = s.color,
                        radius = 3.dp.toPx(),
                        center = Offset(w, lastY)
                    )
                }
            }

            if (!hasData) {
                Text(
                    "Collecting samples...",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary),
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // Y axis, labelling the top, middle and bottom gridlines.
        //
        // Each label is nudged by half a line so its *centre* lands on the rule rather
        // than its top edge - stacking them with SpaceBetween instead leaves every label
        // sitting visibly below the line it describes.
        Box(
            modifier = Modifier
                .width(AXIS_GUTTER)
                .fillMaxHeight()
                .padding(start = 6.dp)
        ) {
            val halfLine = 7.dp
            AxisLabel(
                formatValue(axisTop),
                Modifier.align(Alignment.TopStart).offset(y = -halfLine)
            )
            AxisLabel(
                formatValue(axisTop / 2f),
                Modifier.align(Alignment.CenterStart)
            )
            AxisLabel(
                formatValue(0f),
                Modifier.align(Alignment.BottomStart).offset(y = halfLine)
            )
        }
    }
}

@Composable
private fun AxisLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall.copy(
            color = TextSecondary,
            fontSize = 10.sp
        ),
        maxLines = 1
    )
}

/** min / average / peak for the visible window. */
@Composable
private fun ChartFooter(
    points: List<Float>,
    windowSeconds: Int,
    formatValue: (Float) -> String
) {
    if (points.isEmpty()) {
        Text(
            "waiting for the first sample",
            style = MaterialTheme.typography.bodySmall.copy(
                color = TextSecondary,
                fontSize = 11.sp
            )
        )
        return
    }

    val summary = buildString {
        append("min ").append(formatValue(points.min()))
        append(" · avg ").append(formatValue(points.average().toFloat()))
        append(" · peak ").append(formatValue(points.max()))
        append(" · ").append(formatWindow(windowSeconds))
    }

    Text(
        summary,
        style = MaterialTheme.typography.bodySmall.copy(
            color = TextSecondary,
            fontSize = 11.sp
        )
    )
}

@Composable
fun ProcessesTab(uiState: MonitorUiState, onRefresh: () -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "TOP PROCESSES (BY CPU)",
                    style = MaterialTheme.typography.labelMedium.copy(color = TextSecondary, fontWeight = FontWeight.Bold)
                )
                Button(onClick = onRefresh, colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent, contentColor = AppBackground)) {
                    Text("Refresh", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        items(uiState.processes) { proc ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = AppSurface)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(proc.command, fontWeight = FontWeight.Bold, color = TextPrimary, maxLines = 1)
                        Text("PID: ${proc.pid} | PPID: ${proc.ppid}", color = TextSecondary, fontSize = 12.sp)
                    }
                    Row {
                        Text("${proc.cpuPercent}% CPU", color = CpuBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${proc.memPercent}% RAM", color = RamPurple, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun UptimeTab(uiState: MonitorUiState) {
    val totalSec = uiState.currentStats.uptimeSecs
    val days = totalSec / 86400
    val hours = (totalSec % 86400) / 3600
    val mins = (totalSec % 3600) / 60

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("SYSTEM UPTIME", color = TextSecondary, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "${days}d ${hours}h ${mins}m",
            fontSize = 42.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryAccent
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("System is online and running stably", color = TextSecondary)
    }
}
