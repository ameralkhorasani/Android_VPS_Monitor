package io.github.ameralkhorasani.outpost.domain

data class AlertThresholds(
    val cpuAbove: Int = 90,
    val ramAbove: Int = 90,
    val diskAbove: Int = 85,
    val sslExpiryDays: Int = 7,
    val alertsEnabled: Boolean = true
)
