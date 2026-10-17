package com.unscroll.app.domain.overlay

import com.unscroll.app.domain.session.SessionSwipes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        assertEquals(PillLevel.CALM, PillRules.levelFor(30 * minute - 1, defaults))
        assertEquals(PillLevel.WARNING, PillRules.levelFor(30 * minute, defaults))
        assertEquals(PillLevel.WARNING, PillRules.levelFor(60 * minute - 1, defaults))
        assertEquals(PillLevel.DANGER, PillRules.levelFor(60 * minute, defaults))
        assertEquals(PillLevel.DANGER, PillRules.levelFor(3 * hour, defaults))
    }

    @Test
    fun levelFor_customThresholds() {
        val custom = ColorThresholds(warningAfterMinutes = 10, dangerAfterMinutes = 15)
        assertEquals(PillLevel.CALM, PillRules.levelFor(10 * minute - second, custom))
        assertEquals(PillLevel.WARNING, PillRules.levelFor(10 * minute, custom))
        assertEquals(PillLevel.DANGER, PillRules.levelFor(15 * minute, custom))
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
        assertTrue(PillRules.shouldShow(IG, enabled, canDrawOverlays = true))
        assertFalse(PillRules.shouldShow(null, enabled, canDrawOverlays = true))
        assertFalse(PillRules.shouldShow(IG, enabled, canDrawOverlays = false))
        assertFalse(PillRules.shouldShow(IG, OverlaySettings(enabled = false), canDrawOverlays = true))
    }

    @Test
    fun shouldShow_respectsThePerAppSwitch() {
        val hiddenForInstagram = OverlaySettings(pillHiddenFor = setOf(IG))
        assertFalse(PillRules.shouldShow(IG, hiddenForInstagram, canDrawOverlays = true))
        assertTrue(PillRules.shouldShow(TIKTOK, hiddenForInstagram, canDrawOverlays = true))
    }

    @Test
    fun swipeCount_needsTheGlobalAndThePerAppSwitch() {
        assertTrue(OverlaySettings().showsSwipesFor(IG))
        assertFalse(OverlaySettings(swipesHiddenFor = setOf(IG)).showsSwipesFor(IG))
        assertTrue(OverlaySettings(swipesHiddenFor = setOf(IG)).showsSwipesFor(TIKTOK))
        assertFalse(OverlaySettings(showSwipes = false).showsSwipesFor(TIKTOK))
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

    private companion object {
        const val IG = "com.instagram.android"
        const val TIKTOK = "com.zhiliaoapp.musically"
    }

    // --- Swipes on the pill: only while the accessibility service counts -------------------------

    private val session = SessionSwipes(sessionId = 1, packageName = "com.instagram.android", count = 12)

    @Test
    fun serviceOff_hidesSwipes_noZeroIsShown() {
        // Not counting (service off, not connected, or no consent): the pill is the fox and the time only.
        assertNull(PillRules.swipes(counting = false, session = session, showCount = true, remaining = null))
        assertNull(PillRules.swipes(counting = false, session = SessionSwipes(1, "x", 0), showCount = true, remaining = 5))
        assertNull(PillRules.shownSwipes(swipes = null, todaySwipes = 0))
    }

    @Test
    fun serviceOn_showsTodaysSwipes_includingZero() {
        val swipes = PillRules.swipes(counting = true, session = session, showCount = true, remaining = null)
        assertEquals(PillSwipes(sessionCount = 12, showCount = true), swipes)
        assertEquals(86, PillRules.shownSwipes(swipes, todaySwipes = 86))
        // Counting and nothing swiped yet: a real 0.
        val fresh = PillRules.swipes(counting = true, session = SessionSwipes(2, "x", 0), showCount = true, remaining = null)
        assertEquals(0, PillRules.shownSwipes(fresh, todaySwipes = 0))
    }

    @Test
    fun serviceOn_withoutAnOpenSession_hasNoSwipes() {
        assertNull(PillRules.swipes(counting = true, session = null, showCount = true, remaining = null))
    }

    @Test
    fun showSwipesOff_hidesTheNumber_butKeepsSwipesForColorsAndTheLimit() {
        val swipes = PillRules.swipes(counting = true, session = session, showCount = false, remaining = 7)
        assertEquals(PillSwipes(sessionCount = 12, showCount = false, remaining = 7), swipes)
        assertNull(PillRules.shownSwipes(swipes, todaySwipes = 86))
    }
}
