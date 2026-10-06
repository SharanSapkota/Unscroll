package com.unscroll.app.domain.insights

import org.junit.Assert.assertEquals
import org.junit.Test

class UsagePeriodTest {

    private val now = at("2026-10-06T15:30:00")
    private val endOfToday = at("2026-10-07T00:00:00")

    @Test
    fun today_isLocalMidnightToNextMidnight() {
        assertEquals(
            TimeRange(at("2026-10-06T00:00:00"), endOfToday),
            UsagePeriod.TODAY.range(now, BERLIN),
        )
    }

    @Test
    fun week_isTodayAndSixDaysBefore() {
        assertEquals(
            TimeRange(at("2026-09-30T00:00:00"), endOfToday),
            UsagePeriod.WEEK.range(now, BERLIN),
        )
    }

    @Test
    fun month_isTodayAndTwentyNineDaysBefore() {
        assertEquals(
            TimeRange(at("2026-09-07T00:00:00"), endOfToday),
            UsagePeriod.MONTH.range(now, BERLIN),
        )
    }

    @Test
    fun allTime_startsAtEpoch() {
        assertEquals(TimeRange(0L, endOfToday), UsagePeriod.ALL_TIME.range(now, BERLIN))
    }

    @Test
    fun dayAfterDstEnds_is25HoursLong() {
        // Clocks go back on 2026-10-25 in Berlin.
        val range = UsagePeriod.TODAY.range(at("2026-10-25T12:00:00"), BERLIN)
        assertEquals(25 * HOUR, range.to - range.from)
    }
}
