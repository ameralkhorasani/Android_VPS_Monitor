package io.github.ameralkhorasani.outpost.data.model

data class ServerStats(
    val cpuPercent: Float = 0f,
    val ramPercent: Float = 0f,
    val ramUsedMb: Long = 0L,
    val ramTotalMb: Long = 0L,
    val swapPercent: Float = 0f,
    val loadAvg1: Float = 0f,
    val loadAvg5: Float = 0f,
    val loadAvg15: Float = 0f,
    val netRxKbSec: Float = 0f,
    val netTxKbSec: Float = 0f,
    val diskPercent: Float = 0f,
    val uptimeSecs: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
)
