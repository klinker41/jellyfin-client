package com.makd.afinity.ui.components

import android.content.Context
import com.makd.afinity.R
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test

class RuntimeAndClockFormatTest {

    private val ticksPerSecond = 10_000_000L
    private val ticksPerMinute = 600_000_000L

    @Test
    fun ticksAndSecondsToTotalMinutesClampNonPositiveAndCeilPositive() {
        assertEquals(0, ticksToTotalMinutes(-100L))
        assertEquals(0, ticksToTotalMinutes(0L))
        assertEquals(1, ticksToTotalMinutes(1L))
        assertEquals(2, ticksToTotalMinutes(ticksPerMinute + 1L))
        assertEquals(90, ticksToTotalMinutes(90L * ticksPerMinute))

        assertEquals(0, secondsToTotalMinutes(-5.0))
        assertEquals(0, secondsToTotalMinutes(0.0))
        assertEquals(1, secondsToTotalMinutes(0.1))
        assertEquals(2, secondsToTotalMinutes(61.0))
        assertEquals(45, secondsToTotalMinutes(2700.0))
    }

    @Test
    fun formatRuntimeFormatsMinutesAndHoursMinutes() {
        val context = mockk<Context>()
        every { context.getString(R.string.meta_runtime_hours_minutes) } returns "%1\$dh %2\$dm"
        every { context.getString(R.string.meta_runtime_minutes) } returns "%1\$dm"

        assertEquals("0m", formatRuntime(context, -10))
        assertEquals("45m", formatRuntime(context, 45))
        assertEquals("1h 0m", formatRuntime(context, 60))
        assertEquals("2h 35m", formatRuntime(context, 155))
    }

    @Test
    fun clockFormattingHandlesClampingMinutesAndHoursAcrossAllUnits() {
        assertEquals("0:00", formatClock(-5_000L))
        assertEquals("0:45", formatClock(45_999L))
        assertEquals("5:09", formatClock(309_000L))
        assertEquals("1:02:03", formatClock(3_723_000L))

        assertEquals("0:00", formatClockSeconds(-1.0))
        assertEquals("1:05", formatClockSeconds(65.9))
        assertEquals("2:00:01", formatClockSeconds(7201.0))

        assertEquals("0:00", formatClockTicks(-1L))
        assertEquals("0:09", formatClockTicks(9L * ticksPerSecond))
        assertEquals("1:01:05", formatClockTicks(3_665L * ticksPerSecond))
    }
}
