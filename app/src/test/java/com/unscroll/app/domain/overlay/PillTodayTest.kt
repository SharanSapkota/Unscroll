package com.unscroll.app.domain.overlay

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** Today's total on the pill, ticked from a fake clock: no database reads while it runs. */
class PillTodayTest {

    private val zone: ZoneId = ZoneId.of("Europe/Helsinki")
    private val second = 1_000L
    private val minute = 60 * second

    private fun at(hour: Int, minute: Int, second: Int = 0, day: Int = 7): Long =
        LocalDateTime.of(2026, 10, day, hour, minute, second).atZone(zone).toInstant().toEpochMilli()

    private val midnight = at(0, 0)

    /** Earlier sessions today: 20 min + 12 min 30 s; this visit started at 14:00. */
    private val base = PillTodayBase(
        packageName = "com.instagram.android",
        sessionId = 3,
        sessionStart = at(14, 0),
        dayStart = midnight,
        completedMillis = 32 * minute + 30 * second,
        completedSwipes = 140,
    )

    @Test
    fun total_isEarlierSessionsPlusThisVisit_tickingEverySecond() {
        assertEquals(32 * minute + 30 * second, PillToday.totalMillis(base, at(14, 0), zone))
        assertEquals(32 * minute + 31 * second, PillToday.totalMillis(base, at(14, 0, 1), zone))
        assertEquals(47 * minute + 42 * second, PillToday.totalMillis(base, at(14, 15, 12), zone))
        assertEquals("47:42", PillRules.formatTime(PillToday.totalMillis(base, at(14, 15, 12), zone)))
    }

    @Test
    fun total_continuesWhereTheLastVisitLeftOff() {
        // Visit 1: 09:00–09:10, then visit 2 starts at 12:00 with the first one as its base.
        val first = PillTodayBase("app", 1, at(9, 0), midnight, completedMillis = 0, completedSwipes = 0)
        val shown = PillToday.shownAt(first, at(9, 10), zone)
        assertEquals(10 * minute, shown.millis)

        val visit2 = PillTodayBase(
            "app", 2, at(12, 0), midnight,
            completedMillis = 10 * minute, completedSwipes = 0, floorMillis = shown.floorFor(midnight),
        )
        assertEquals(10 * minute, PillToday.totalMillis(visit2, at(12, 0), zone))
        assertEquals(10 * minute + 5 * second, PillToday.totalMillis(visit2, at(12, 0, 5), zone))
    }

    @Test
    fun total_neverStepsBack_whenTheStoredEndIsEarlierThanTheLastTick() {
        // The pill showed 10:00, but the closed session was stored a second shorter.
        val next = PillTodayBase(
            "app", 2, at(12, 0), midnight,
            completedMillis = 10 * minute - second, completedSwipes = 0, floorMillis = 10 * minute,
        )
        assertEquals(10 * minute, PillToday.totalMillis(next, at(12, 0), zone))
        assertEquals(10 * minute, PillToday.totalMillis(next, at(12, 0, 1), zone))
        assertEquals(10 * minute + second, PillToday.totalMillis(next, at(12, 0, 2), zone))
    }

    @Test
    fun total_openSessionThatStartedYesterday_onlyCountsSinceMidnight() {
        val crossing = PillTodayBase("app", 9, at(23, 50, day = 6), midnight, completedMillis = 0, completedSwipes = 0)
        assertEquals(5 * minute, PillToday.totalMillis(crossing, at(0, 5), zone))
    }

    @Test
    fun total_rollsOverAtMidnight_whileThePillIsShowing() {
        val evening = PillTodayBase("app", 4, at(23, 50), midnight, completedMillis = 2 * 60 * minute, completedSwipes = 30)
        assertEquals(2 * 60 * minute + 9 * minute + 59 * second, PillToday.totalMillis(evening, at(23, 59, 59), zone))
        // Midnight: back to zero, then counts the new day only.
        assertEquals(0L, PillToday.totalMillis(evening, at(0, 0, day = 8), zone))
        assertEquals(3 * minute, PillToday.totalMillis(evening, at(0, 3, day = 8), zone))
        // An old floor from yesterday doesn't carry over.
        val yesterday = ShownTotal(midnight, 5 * 60 * minute)
        assertEquals(0L, yesterday.floorFor(at(0, 0, day = 8)))
    }

    @Test
    fun sessionTime_isThisVisitOnly() {
        assertEquals(3 * minute + 5 * second, PillToday.sessionMillis(base, at(14, 3, 5)))
        assertEquals(0L, PillToday.sessionMillis(base, at(13, 59)))
    }

    @Test
    fun swipes_areEarlierSessionsPlusThisVisit_resetAtMidnight() {
        assertEquals(140, PillToday.swipes(base, sessionSwipes = 0, now = at(14, 0), zone = zone))
        assertEquals(226, PillToday.swipes(base, sessionSwipes = 86, now = at(14, 10), zone = zone))
        assertEquals(86, PillToday.swipes(base, sessionSwipes = 86, now = at(0, 1, day = 8), zone = zone))
    }

    @Test
    fun color_withADailyLimit_isByShareOfIt() {
        val limit = 60 * minute
        val thresholds = ColorThresholds()
        assertEquals(PillLevel.CALM, PillRules.levelForToday(36 * minute - 1, limit, thresholds))
        assertEquals(PillLevel.WARNING, PillRules.levelForToday(36 * minute, limit, thresholds))
        assertEquals(PillLevel.WARNING, PillRules.levelForToday(limit - 1, limit, thresholds))
        assertEquals(PillLevel.DANGER, PillRules.levelForToday(limit, limit, thresholds))
        assertEquals(PillLevel.DANGER, PillRules.levelForToday(3 * limit, limit, thresholds))
    }

    @Test
    fun color_withoutADailyLimit_usesTheThresholds() {
        val defaults = ColorThresholds()
        assertEquals(PillLevel.CALM, PillRules.levelForToday(29 * minute, null, defaults))
        assertEquals(PillLevel.WARNING, PillRules.levelForToday(30 * minute, null, defaults))
        assertEquals(PillLevel.DANGER, PillRules.levelForToday(60 * minute, null, defaults))
        // A zero limit counts as no limit.
        assertEquals(PillLevel.CALM, PillRules.levelForToday(29 * minute, 0, defaults))
    }
}
