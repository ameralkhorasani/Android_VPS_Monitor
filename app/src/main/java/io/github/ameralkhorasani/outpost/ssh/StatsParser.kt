package io.github.ameralkhorasani.outpost.ssh

import io.github.ameralkhorasani.outpost.data.model.ServerStats
import kotlin.math.max

class StatsParser {

    data class CpuSnapshot(val total: Long, val idle: Long)
    data class NetSnapshot(val rxBytes: Long, val txBytes: Long)

    private var previousCpuSnapshot: CpuSnapshot? = null
    private var previousNetSnapshot: NetSnapshot? = null
    private var previousTimestamp: Long = 0L

    fun parseCpu(procStatOutput: String): Float {
        val firstLine = procStatOutput.lines().firstOrNull { it.startsWith("cpu ") } ?: return 0f
        val tokens = firstLine.trim().split("\\s+".toRegex())
        if (tokens.size < 5) return 0f

        val user = tokens.getOrNull(1)?.toLongOrNull() ?: 0L
        val nice = tokens.getOrNull(2)?.toLongOrNull() ?: 0L
        val system = tokens.getOrNull(3)?.toLongOrNull() ?: 0L
        val idle = tokens.getOrNull(4)?.toLongOrNull() ?: 0L
        val iowait = tokens.getOrNull(5)?.toLongOrNull() ?: 0L
        val irq = tokens.getOrNull(6)?.toLongOrNull() ?: 0L
        val softirq = tokens.getOrNull(7)?.toLongOrNull() ?: 0L
        val steal = tokens.getOrNull(8)?.toLongOrNull() ?: 0L

        val total = user + nice + system + idle + iowait + irq + softirq + steal
        val idleTime = idle + iowait

        val currentSnapshot = CpuSnapshot(total, idleTime)
        val prev = previousCpuSnapshot
        previousCpuSnapshot = currentSnapshot

        if (prev == null) return 0f

        val totalDelta = max(1L, currentSnapshot.total - prev.total)
        val idleDelta = max(0L, currentSnapshot.idle - prev.idle)

        val usage = (1.0f - (idleDelta.toFloat() / totalDelta.toFloat())) * 100f
        return usage.coerceIn(0f, 100f)
    }

    fun parseMem(procMeminfoOutput: String): Triple<Float, Long, Long> {
        var memTotalKb = 0L
        var memAvailableKb = 0L

        for (line in procMeminfoOutput.lines()) {
            val parts = line.split(":")
            if (parts.size >= 2) {
                val key = parts[0].trim()
                val valueStr = parts[1].trim().replace("kB", "").trim()
                val valueKb = valueStr.toLongOrNull() ?: 0L
                when (key) {
                    "MemTotal" -> memTotalKb = valueKb
                    "MemAvailable" -> memAvailableKb = valueKb
                }
            }
        }

        if (memTotalKb == 0L) return Triple(0f, 0L, 0L)

        val memUsedKb = max(0L, memTotalKb - memAvailableKb)
        val percent = (memUsedKb.toFloat() / memTotalKb.toFloat()) * 100f
        val ramUsedMb = memUsedKb / 1024L
        val ramTotalMb = memTotalKb / 1024L

        return Triple(percent.coerceIn(0f, 100f), ramUsedMb, ramTotalMb)
    }

    fun parseSwap(procMeminfoOutput: String): Float {
        var swapTotalKb = 0L
        var swapFreeKb = 0L

        for (line in procMeminfoOutput.lines()) {
            val parts = line.split(":")
            if (parts.size >= 2) {
                val key = parts[0].trim()
                val valueStr = parts[1].trim().replace("kB", "").trim()
                val valueKb = valueStr.toLongOrNull() ?: 0L
                when (key) {
                    "SwapTotal" -> swapTotalKb = valueKb
                    "SwapFree" -> swapFreeKb = valueKb
                }
            }
        }

        if (swapTotalKb == 0L) return 0f

        val swapUsedKb = max(0L, swapTotalKb - swapFreeKb)
        val percent = (swapUsedKb.toFloat() / swapTotalKb.toFloat()) * 100f
        return percent.coerceIn(0f, 100f)
    }

    fun parseLoadAvg(procLoadavgOutput: String): Triple<Float, Float, Float> {
        val tokens = procLoadavgOutput.trim().split("\\s+".toRegex())
        val load1 = tokens.getOrNull(0)?.toFloatOrNull() ?: 0f
        val load5 = tokens.getOrNull(1)?.toFloatOrNull() ?: 0f
        val load15 = tokens.getOrNull(2)?.toFloatOrNull() ?: 0f
        return Triple(load1, load5, load15)
    }

    fun parseNet(procNetDevOutput: String): Pair<Float, Float> {
        var totalRxBytes = 0L
        var totalTxBytes = 0L

        for (line in procNetDevOutput.lines()) {
            val trimmed = line.trim()
            if (trimmed.contains(":") && !trimmed.startsWith("lo:")) {
                val parts = trimmed.split(":")
                val values = parts[1].trim().split("\\s+".toRegex())
                val rx = values.getOrNull(0)?.toLongOrNull() ?: 0L
                val tx = values.getOrNull(8)?.toLongOrNull() ?: 0L
                totalRxBytes += rx
                totalTxBytes += tx
            }
        }

        val now = System.currentTimeMillis()
        val currentSnapshot = NetSnapshot(totalRxBytes, totalTxBytes)
        val prev = previousNetSnapshot
        val prevTime = previousTimestamp
        previousNetSnapshot = currentSnapshot
        previousTimestamp = now

        if (prev == null || prevTime == 0L) return Pair(0f, 0f)

        val timeDeltaSec = max(0.1f, (now - prevTime) / 1000f)
        val rxDeltaKb = max(0L, currentSnapshot.rxBytes - prev.rxBytes) / 1024f
        val txDeltaKb = max(0L, currentSnapshot.txBytes - prev.txBytes) / 1024f

        val rxKbSec = rxDeltaKb / timeDeltaSec
        val txKbSec = txDeltaKb / timeDeltaSec

        return Pair(rxKbSec, txKbSec)
    }

    fun parseDisk(dfOutput: String): Float {
        for (line in dfOutput.lines()) {
            val tokens = line.trim().split("\\s+".toRegex())
            if (tokens.size >= 5 && (line.endsWith("/") || tokens.lastOrNull() == "/")) {
                val percentStr = tokens.firstOrNull { it.endsWith("%") }?.replace("%", "")
                val percent = percentStr?.toFloatOrNull()
                if (percent != null) return percent.coerceIn(0f, 100f)
            }
        }
        return 0f
    }

    fun parseUptime(uptimeOutput: String): Long {
        val firstToken = uptimeOutput.trim().split("\\s+".toRegex()).firstOrNull()
        return firstToken?.toDoubleOrNull()?.toLong() ?: 0L
    }

    fun buildServerStats(
        procStat: String,
        procMem: String,
        procLoad: String,
        procNet: String,
        df: String,
        uptime: String
    ): ServerStats {
        val cpu = parseCpu(procStat)
        val (ramPercent, ramUsed, ramTotal) = parseMem(procMem)
        val swap = parseSwap(procMem)
        val (l1, l5, l15) = parseLoadAvg(procLoad)
        val (rx, tx) = parseNet(procNet)
        val disk = parseDisk(df)
        val uptimeSecs = parseUptime(uptime)

        return ServerStats(
            cpuPercent = cpu,
            ramPercent = ramPercent,
            ramUsedMb = ramUsed,
            ramTotalMb = ramTotal,
            swapPercent = swap,
            loadAvg1 = l1,
            loadAvg5 = l5,
            loadAvg15 = l15,
            netRxKbSec = rx,
            netTxKbSec = tx,
            diskPercent = disk,
            uptimeSecs = uptimeSecs
        )
    }
}
