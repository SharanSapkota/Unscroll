package com.unscroll.app.domain.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PillRulesTest {

    private val second = 1_000L
    private val minute = 60 * second
    private val hour = 60 * minute

    @Test
    fun formatTime_underAnHour_isZeroPaddedMinutesAndSeconds() {
        assertEquals("00:00", PillRules.formatTime(0))
        assertEquals("00:09", PillRules.formatTime(9 * second))
        assertEquals("07:05", PillRules.formatTime(7 * minute + 5 * second))
        assertEquals("12:41", PillRules.formatTime(12 * minute + 41 * second))
        assertEquals("59:59", PillRules.formatTime(hour - second))
    }

    @Test
    fun formatTime_fromOneHour_addsHours() {
        assertEquals("1:00:00", PillRules.formatTime(hour))
        assertEquals("1:02:03", PillRules.formatTime(hour + 2 * minute + 3 * second))
        assertEquals("12:00:00", PillRules.formatTime(12 * hour))
    }

    @Test
    fun formatTime_truncatesMillisAndClampsNegative() {
        assertEquals("00:01", PillRules.formatTime(1_999))
        assertEquals("00:00", PillRules.formatTime(-5_000))
    }

    @Test
    fun levelFor_defaultThresholds_greenYellowRed() {
        val defaults = ColorThresholds()
        assertEquals(PillLevel.CALM, PillRules.levelFor(0, defaults))
        assertEquals(PillLevel.CALM, PillRules.levelFor(10 * minute - 1, defaults))
        assertEquals(PillLevel.WARNING, PillRules.levelFor(10 * minute, defaults))
        assertEquals(PillLevel.WARNING, PillRules.levelFor(20 * minute - 1, defaults))
        assertEquals(PillLevel.DANGER, PillRules.levelFor(20 * minute, defaults))
        assertEquals(PillLevel.DANGER, PillRules.levelFor(3 * hour, defaults))
    }

    @Test
    fun levelFor_customThresholds() {
        val custom = ColorThresholds(warningAfterMinutes = 2, dangerAfterMinutes = 5)
        assertEquals(PillLevel.CALM, PillRules.levelFor(119 * second, custom))
        assertEquals(PillLevel.WARNING, PillRules.levelFor(2 * minute, custom))
        assertEquals(PillLevel.DANGER, PillRules.levelFor(5 * minute, custom))
    }

    @Test
    fun levelFor_invalidThresholds_areNormalizedFirst() {
        // Danger before warning: danger is pushed to warning + 1.
        val swapped = ColorThresholds(warningAfterMinutes = 15, dangerAfterMinutes = 5)
        assertEquals(PillLevel.WARNING, PillRules.levelFor(15 * minute, swapped))
        assertEquals(PillLevel.DANGER, PillRules.levelFor(16 * minute, swapped))
    }

    @Test
    fun shouldShow_onlyWithTrackedAppEnabledAndPermission() {
        val enabled = OverlaySettings(enabled = true)
        assertTrue(PillRules.shouldShow(true, enabled, canDrawOverlays = true))
        assertFalse(PillRules.shouldShow(false, enabled, canDrawOverlays = true))
        assertFalse(PillRules.shouldShow(true, enabled, canDrawOverlays = false))
        assertFalse(PillRules.shouldShow(true, OverlaySettings(enabled = false), canDrawOverlays = true))
    }

    @Test
    fun levelForSwipes_greenYellowRed() {
        val thresholds = SwipeColorThresholds(warningAfterSwipes = 50, dangerAfterSwipes = 100)
        assertEquals(PillLevel.CALM, PillRules.levelForSwipes(49, thresholds))
        assertEquals(PillLevel.WARNING, PillRules.levelForSwipes(50, thresholds))
        assertEquals(PillLevel.DANGER, PillRules.levelForSwipes(100, thresholds))
    }

    @Test
    fun combinedLevel_isTheMoreUrgentOne() {
        assertEquals(PillLevel.DANGER, PillRules.combinedLevel(PillLevel.CALM, PillLevel.DANGER))
        assertEquals(PillLevel.WARNING, PillRules.combinedLevel(PillLevel.WARNING, PillLevel.CALM))
        assertEquals(PillLevel.WARNING, PillRules.combinedLevel(PillLevel.WARNING, null))
    }
}
