package com.unscroll.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormatterTest {

    @Test
    fun zero_formatsAsMinutesAndSeconds() {
        assertEquals("0:00", formatElapsed(0L))
    }

    @Test
    fun negative_isClampedToZero() {
        assertEquals("0:00", formatElapsed(-5_000L))
    }

    @Test
    fun partialSeconds_areTruncated() {
        assertEquals("0:05", formatElapsed(5_999L))
    }

    @Test
    fun underAnHour_omitsHours() {
        assertEquals("12:34", formatElapsed((12 * 60 + 34) * 1_000L))
    }

    @Test
    fun exactlyOneHour_includesHours() {
        assertEquals("1:00:00", formatElapsed(3_600_000L))
    }

    @Test
    fun overAnHour_padsMinutesAndSeconds() {
        assertEquals("1:02:03", formatElapsed((3_600 + 2 * 60 + 3) * 1_000L))
    }
}
