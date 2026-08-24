package io.github.ameralkhorasani.outpost.domain

/**
 * Turns a metrics sample into the 0-100 health score shown on the Overview.
 *
 * The score is defined relative to each server's own alert thresholds rather than
 * fixed constants: a box that is expected to sit at 80% disk should not look sick.
 * Nothing is deducted until a metric reaches 75% of its threshold, then the penalty
 * grows linearly and saturates once the threshold is crossed.
 *
 * Deliberately free of Android and persistence imports so it can be exercised on the
 * JVM with plain numbers - see `HealthScoreTest`.
 */
object HealthScore {

    private const val CPU_WEIGHT = 25f
    private const val RAM_WEIGHT = 25f
    private const val DISK_WEIGHT = 30f

    fun compute(
        cpuPercent: Float,
        ramPercent: Float,
        diskPercent: Float,
        thresholds: AlertThresholds
    ): Int {
        val penalty = penalty(cpuPercent, thresholds.cpuAbove, CPU_WEIGHT) +
            penalty(ramPercent, thresholds.ramAbove, RAM_WEIGHT) +
            penalty(diskPercent, thresholds.diskAbove, DISK_WEIGHT)

        return (100f - penalty).toInt().coerceIn(0, 100)
    }

    private fun penalty(value: Float, threshold: Int, weight: Float): Float {
        val start = threshold * 0.75f
        if (value <= start) return 0f
        val span = (threshold - start).coerceAtLeast(1f)
        return (weight * ((value - start) / span)).coerceAtMost(weight)
    }
}
