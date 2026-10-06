package com.unscroll.app.domain.insights

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrendTest {

    @Test
    fun moreUsage_isUpWithPercent() {
        val trend = Trend(currentMillis = 90 * MINUTE, previousMillis = 60 * MINUTE)
        assertEquals(TrendDirection.UP, trend.direction)
        assertEquals(30 * MINUTE, trend.deltaMillis)
        assertEquals(50, trend.percentChange)
    }

    @Test
    fun lessUsage_isDownWithNegativePercent() {
        val trend = Trend(currentMillis = 15 * MINUTE, previousMillis = 60 * MINUTE)
        assertEquals(TrendDirection.DOWN, trend.direction)
        assertEquals(-75, trend.percentChange)
    }

    @Test
    fun differenceUnderAMinute_isFlat() {
        assertEquals(TrendDirection.FLAT, Trend(60 * MINUTE + 59_000, 60 * MINUTE).direction)
        assertEquals(TrendDirection.UP, Trend(61 * MINUTE, 60 * MINUTE).direction)
    }

    @Test
    fun noPreviousUsage_hasNoPercent() {
        val trend = Trend(currentMillis = 10 * MINUTE, previousMillis = 0)
        assertNull(trend.percentChange)
        assertEquals(TrendDirection.UP, trend.direction)
    }

    @Test
    fun bothZero_isFlat() {
        assertEquals(TrendDirection.FLAT, Trend(0, 0).direction)
    }
}
