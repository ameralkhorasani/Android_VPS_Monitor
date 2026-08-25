package io.github.ameralkhorasani.outpost.data.model

import io.github.ameralkhorasani.outpost.domain.AlertThresholds

/**
 * Bridges the stored [ServerEntity] to the pure scoring types in `domain`.
 *
 * These live here rather than next to [io.github.ameralkhorasani.outpost.domain.HealthScore]
 * so that `domain` keeps depending on nothing at all - the dependency runs data -> domain,
 * never the other way.
 */

/** The alert thresholds configured for this server. */
fun ServerEntity.thresholds(): AlertThresholds = AlertThresholds(
    cpuAbove = alertCpuAbove,
    ramAbove = alertRamAbove,
    diskAbove = alertDiskAbove,
    sslExpiryDays = alertSslExpiryDays,
    alertsEnabled = alertsEnabled
)

/** True when a live metric has crossed one of this server's alert thresholds. */
fun ServerEntity.isBreachingThresholds(): Boolean = alertsEnabled && isOnline && (
    lastCpuPercent >= alertCpuAbove ||
        lastRamPercent >= alertRamAbove ||
        lastDiskPercent >= alertDiskAbove
    )
