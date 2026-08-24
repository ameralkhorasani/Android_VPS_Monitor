package io.github.ameralkhorasani.outpost.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The health score is what the Overview screen shows for every server, so a regression
 * here silently mislabels healthy boxes as sick or vice versa.
 *
 * These tests pin the two properties that matter: the score is measured against each
 * server's *own* thresholds, and the penalty curve starts at 75% of a threshold and
 * saturates once the threshold is crossed.
 */
class HealthScoreTest {

    private val defaults = AlertThresholds(cpuAbove = 90, ramAbove = 90, diskAbove = 85)

    @Test
    fun `idle server scores 100`() {
        assertEquals(100, HealthScore.compute(0f, 0f, 0f, defaults))
    }

    @Test
    fun `nothing is deducted below 75 percent of a threshold`() {
        // 75% of the 90 CPU threshold is 67.5 - exactly the point where penalties begin.
        assertEquals(100, HealthScore.compute(67.5f, 0f, 0f, defaults))
        assertEquals(100, HealthScore.compute(67.4f, 0f, 0f, defaults))
    }

    @Test
    fun `crossing a threshold costs that metric's full weight`() {
        // CPU carries a weight of 25.
        assertEquals(75, HealthScore.compute(90f, 0f, 0f, defaults))
        // RAM also carries 25.
        assertEquals(75, HealthScore.compute(0f, 90f, 0f, defaults))
        // Disk carries 30, and its threshold is 85.
        assertEquals(70, HealthScore.compute(0f, 0f, 85f, defaults))
    }

    @Test
    fun `penalty grows linearly between the knee and the threshold`() {
        // Halfway between 67.5 and 90 is 78.75, so half of CPU's weight of 25.
        assertEquals(87, HealthScore.compute(78.75f, 0f, 0f, defaults))
    }

    @Test
    fun `penalty saturates once past the threshold`() {
        val atThreshold = HealthScore.compute(90f, 0f, 0f, defaults)
        val wayPast = HealthScore.compute(100f, 0f, 0f, defaults)
        assertEquals(atThreshold, wayPast)
    }

    @Test
    fun `everything maxed floors at the combined weight`() {
        // 25 + 25 + 30 = 80 deducted from 100.
        assertEquals(20, HealthScore.compute(100f, 100f, 100f, defaults))
    }

    @Test
    fun `score is relative to the server's own thresholds`() {
        // A backup box expected to run hot: 80% disk is normal for it, so it must not
        // be scored the same as a box whose disk threshold is 50%.
        val tolerant = AlertThresholds(diskAbove = 95)
        val strict = AlertThresholds(diskAbove = 50)

        assertEquals(100, HealthScore.compute(0f, 0f, 70f, tolerant))
        assertEquals(70, HealthScore.compute(0f, 0f, 70f, strict))
    }

    @Test
    fun `score never leaves the 0 to 100 range`() {
        val absurd = HealthScore.compute(1_000f, 1_000f, 1_000f, defaults)
        assert(absurd in 0..100) { "score $absurd out of range" }

        val negative = HealthScore.compute(-50f, -50f, -50f, defaults)
        assert(negative in 0..100) { "score $negative out of range" }
    }
}
