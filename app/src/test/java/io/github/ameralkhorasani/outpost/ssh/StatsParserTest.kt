package io.github.ameralkhorasani.outpost.ssh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Server output varies between distributions, and a parser regression does not crash -
 * it silently shows the user wrong numbers. So every branch is pinned against fixture
 * text captured from real `/proc` files rather than exercised against a live host.
 */
class StatsParserTest {

    private lateinit var parser: StatsParser

    @Before
    fun setUp() {
        parser = StatsParser()
    }

    // ---------------------------------------------------------------- CPU

    @Test
    fun `first cpu sample reads zero because there is no previous snapshot`() {
        // CPU is a rate, not a level. This is documented behaviour, not a bug.
        val stat = "cpu  100 0 100 800 0 0 0 0 0 0"
        assertEquals(0f, parser.parseCpu(stat), 0.001f)
    }

    @Test
    fun `cpu usage is computed from the delta between two samples`() {
        // total 1000, idle 800
        parser.parseCpu("cpu  100 0 100 800 0 0 0 0 0 0")
        // total 2000, idle 1600 -> deltas: total 1000, idle 800 -> 20% busy
        val usage = parser.parseCpu("cpu  200 0 200 1600 0 0 0 0 0 0")
        assertEquals(20f, usage, 0.001f)
    }

    @Test
    fun `cpu accounts for iowait and steal`() {
        // A VPS with a noisy neighbour burns time in steal. Ignoring it would make a
        // contended box look idle.
        parser.parseCpu("cpu  0 0 0 100 0 0 0 0 0 0")
        // Second sample adds 100 steal and 100 idle: half the time was stolen.
        val usage = parser.parseCpu("cpu  0 0 0 200 0 0 0 100 0 0")
        assertEquals(50f, usage, 0.001f)
    }

    @Test
    fun `malformed cpu line yields zero rather than throwing`() {
        assertEquals(0f, parser.parseCpu(""), 0.001f)
        assertEquals(0f, parser.parseCpu("garbage"), 0.001f)
        assertEquals(0f, parser.parseCpu("cpu  1 2"), 0.001f)
    }

    // ------------------------------------------------------------- Memory

    @Test
    fun `memory is derived from MemAvailable, not MemFree`() {
        val meminfo = """
            MemTotal:        2048000 kB
            MemFree:          100000 kB
            MemAvailable:    1024000 kB
            Buffers:           50000 kB
        """.trimIndent()

        val (percent, usedMb, totalMb) = parser.parseMem(meminfo)

        // Used is total minus *available*, so cache does not count as used.
        assertEquals(50f, percent, 0.001f)
        assertEquals(1000L, usedMb)
        assertEquals(2000L, totalMb)
    }

    @Test
    fun `missing MemTotal yields zeroes rather than dividing by zero`() {
        val (percent, usedMb, totalMb) = parser.parseMem("Nonsense: 5 kB")
        assertEquals(0f, percent, 0.001f)
        assertEquals(0L, usedMb)
        assertEquals(0L, totalMb)
    }

    // --------------------------------------------------------------- Swap

    @Test
    fun `swap percentage is used over total`() {
        val meminfo = """
            SwapTotal:       1000 kB
            SwapFree:         250 kB
        """.trimIndent()
        assertEquals(75f, parser.parseSwap(meminfo), 0.001f)
    }

    @Test
    fun `a server with swap disabled reports zero`() {
        val meminfo = """
            SwapTotal:          0 kB
            SwapFree:           0 kB
        """.trimIndent()
        assertEquals(0f, parser.parseSwap(meminfo), 0.001f)
    }

    // ----------------------------------------------------------- Load avg

    @Test
    fun `load average takes the first three fields`() {
        val (one, five, fifteen) = parser.parseLoadAvg("0.52 0.31 0.15 1/234 5678")
        assertEquals(0.52f, one, 0.001f)
        assertEquals(0.31f, five, 0.001f)
        assertEquals(0.15f, fifteen, 0.001f)
    }

    @Test
    fun `empty loadavg yields zeroes`() {
        val (one, five, fifteen) = parser.parseLoadAvg("")
        assertEquals(0f, one, 0.001f)
        assertEquals(0f, five, 0.001f)
        assertEquals(0f, fifteen, 0.001f)
    }

    // --------------------------------------------------------------- Disk

    @Test
    fun `disk usage is read from the root mount`() {
        val df = """
            Filesystem     1K-blocks     Used Available Use% Mounted on
            udev              999999        0    999999   0% /dev
            /dev/vda1       20000000 12000000   7000000  64% /
            /dev/vda15        126976     6144    120832   5% /boot/efi
        """.trimIndent()

        assertEquals(64f, parser.parseDisk(df), 0.001f)
    }

    @Test
    fun `df output without a root mount yields zero`() {
        val df = """
            Filesystem     1K-blocks     Used Available Use% Mounted on
            tmpfs             999999        0    999999   0% /run
        """.trimIndent()

        assertEquals(0f, parser.parseDisk(df), 0.001f)
    }

    // ------------------------------------------------------------- Uptime

    @Test
    fun `uptime takes the whole-second part of the first field`() {
        assertEquals(123456L, parser.parseUptime("123456.78 98765.43"))
    }

    @Test
    fun `malformed uptime yields zero`() {
        assertEquals(0L, parser.parseUptime(""))
        assertEquals(0L, parser.parseUptime("not-a-number"))
    }

    // ---------------------------------------------------------------- Net

    @Test
    fun `first network sample reads zero because throughput needs two samples`() {
        val netDev = """
            Inter-|   Receive                                                |  Transmit
             face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets
                lo: 1000      10    0    0    0     0          0         0     1000      10
              eth0: 5000      50    0    0    0     0          0         0     2500      25
        """.trimIndent()

        val (rx, tx) = parser.parseNet(netDev)
        assertEquals(0f, rx, 0.001f)
        assertEquals(0f, tx, 0.001f)
    }

    @Test
    fun `counter deltas are never negative when an interface resets`() {
        val first = "  eth0: 5000 50 0 0 0 0 0 0 2500 25"
        // A reboot or counter wrap makes the new reading smaller than the old one.
        val second = "  eth0: 10 1 0 0 0 0 0 0 5 1"

        parser.parseNet(first)
        val (rx, tx) = parser.parseNet(second)

        assertTrue("rx should not go negative, was $rx", rx >= 0f)
        assertTrue("tx should not go negative, was $tx", tx >= 0f)
    }

    // ------------------------------------------------------------ Assembly

    @Test
    fun `buildServerStats assembles every field`() {
        val stats = parser.buildServerStats(
            procStat = "cpu  100 0 100 800 0 0 0 0 0 0",
            procMem = "MemTotal: 2048000 kB\nMemAvailable: 1024000 kB\nSwapTotal: 1000 kB\nSwapFree: 250 kB",
            procLoad = "0.52 0.31 0.15 1/234 5678",
            procNet = "  eth0: 5000 50 0 0 0 0 0 0 2500 25",
            df = "/dev/vda1 20000000 12000000 7000000 64% /",
            uptime = "123456.78 98765.43"
        )

        assertEquals(50f, stats.ramPercent, 0.001f)
        assertEquals(1000L, stats.ramUsedMb)
        assertEquals(2000L, stats.ramTotalMb)
        assertEquals(75f, stats.swapPercent, 0.001f)
        assertEquals(0.52f, stats.loadAvg1, 0.001f)
        assertEquals(64f, stats.diskPercent, 0.001f)
        assertEquals(123456L, stats.uptimeSecs)
    }
}
