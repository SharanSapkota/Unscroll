package com.unscroll.app.domain.insights

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightScheduleTest {

    @Test
    fun weeklyDay_isSunday() {
        assertEquals(DayOfWeek.SUNDAY, InsightSchedule.WEEKLY_DAY)
        // 2026-10-18 is a Sunday.
        assertTrue(InsightSchedule.isWeeklyDay(at("2026-10-18T09:00"), BERLIN))
        assertFalse(InsightSchedule.isWeeklyDay(at("2026-10-17T23:59"), BERLIN))
    }

    @Test
    fun nextNotification_isTheComingSundayAt18() {
        assertEquals(at("2026-10-18T18:00"), InsightSchedule.nextNotification(at("2026-10-14T20:00"), BERLIN))
        assertEquals(at("2026-10-18T18:00"), InsightSchedule.nextNotification(at("2026-10-18T09:00"), BERLIN))
        // Once it is past (or exactly at) 18:00 on Sunday: the next week.
        assertEquals(at("2026-10-25T18:00"), InsightSchedule.nextNotification(at("2026-10-18T18:00"), BERLIN))
        assertEquals(at("2026-10-25T18:00"), InsightSchedule.nextNotification(at("2026-10-18T21:00"), BERLIN))
    }

    @Test
    fun stableRandom_sameAllDayForTheSameMessage() {
        assertEquals(InsightSchedule.stableRandom("T01", 10).nextInt(), InsightSchedule.stableRandom("T01", 10).nextInt())
    }
}
