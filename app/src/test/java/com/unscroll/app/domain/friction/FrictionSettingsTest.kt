package com.unscroll.app.domain.friction

import org.junit.Assert.assertEquals
import org.junit.Test

class FrictionSettingsTest {

    @Test
    fun defaults() {
        val d = FrictionSettings.DEFAULT
        assertEquals(true, d.pauseEnabled)
        assertEquals(10, d.pauseSeconds)
        assertEquals(listOf(5, 10, 20), d.nudgeThresholds)
        assertEquals(15, d.breakIntervalMinutes)
        assertEquals(false, d.tintEnabled)
        assertEquals(null, d.swipeBreakAfter)
    }

    @Test
    fun normalized_clampsPauseAndSortsThresholds() {
        val n = FrictionSettings(pauseSeconds = 99, nudgeThresholds = listOf(20, 5, 5, 0)).normalized()
        assertEquals(30, n.pauseSeconds)
        assertEquals(listOf(5, 20), n.nudgeThresholds)
        assertEquals(5, FrictionSettings(pauseSeconds = 1).normalized().pauseSeconds)
        assertEquals(null, FrictionSettings(swipeBreakAfter = -5).normalized().swipeBreakAfter)
        assertEquals(25, FrictionSettings(swipeBreakAfter = 25).normalized().swipeBreakAfter)
    }

    @Test
    fun thresholds_roundTrip() {
        assertEquals("5,10,20", FrictionSettings.encodeThresholds(listOf(5, 10, 20)))
        assertEquals(listOf(5, 10, 20), FrictionSettings.decodeThresholds("20, 5,10,x,-1"))
        assertEquals(emptyList<Int>(), FrictionSettings.decodeThresholds(""))
    }
}
