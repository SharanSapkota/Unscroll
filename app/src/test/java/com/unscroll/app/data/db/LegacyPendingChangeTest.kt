package com.unscroll.app.data.db

import com.unscroll.app.domain.blocking.BlockSchedule
import com.unscroll.app.domain.blocking.LimitSettings
import com.unscroll.app.domain.blocking.SwipeLimitRules
import com.unscroll.app.domain.blocking.SwipeLimitScope
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LegacyPendingChangeTest {

    @Test
    fun decodesTheJsonWrittenBeforeV7() {
        val json = "{\"dailyLimitMinutes\":45,\"blockedAlways\":false,\"scheduleEnabled\":true," +
            "\"scheduleDays\":65,\"scheduleStartMinute\":1320,\"scheduleEndMinute\":420," +
            "\"swipeLimit\":150,\"swipeLimitPerSession\":true,\"swipeSessionGapMinutes\":15," +
            "\"swipeAccessAllowed\":true}"
        assertEquals(
            LimitSettings(
                dailyLimitMinutes = 45,
                schedule = BlockSchedule(true, setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY), 22 * 60, 7 * 60),
                swipeLimit = 150,
                swipeLimitScope = SwipeLimitScope.SESSION,
                swipeSessionGapMinutes = 15,
                swipeAccessAllowed = true,
            ),
            LegacyPendingChange.decode(json),
        )
    }

    @Test
    fun noLimitsAtAll() {
        val json = "{\"dailyLimitMinutes\":null,\"blockedAlways\":false,\"scheduleEnabled\":false," +
            "\"scheduleDays\":127,\"scheduleStartMinute\":1320,\"scheduleEndMinute\":420," +
            "\"swipeLimit\":null,\"swipeLimitPerSession\":false,\"swipeSessionGapMinutes\":30," +
            "\"swipeAccessAllowed\":false}"
        assertEquals(LimitSettings.NONE, LegacyPendingChange.decode(json))
    }

    @Test
    fun jsonFromBeforeSwipeLimits_decodesWithDefaults() {
        val old = "{\"dailyLimitMinutes\":30,\"blockedAlways\":false,\"scheduleEnabled\":false," +
            "\"scheduleDays\":127,\"scheduleStartMinute\":1320,\"scheduleEndMinute\":420}"
        val decoded = LegacyPendingChange.decode(old)!!
        assertEquals(30, decoded.dailyLimitMinutes)
        assertEquals(null, decoded.swipeLimit)
        assertEquals(SwipeLimitScope.DAY, decoded.swipeLimitScope)
        assertEquals(SwipeLimitRules.DEFAULT_SESSION_GAP_MINUTES, decoded.swipeSessionGapMinutes)
        assertEquals(false, decoded.swipeAccessAllowed)
    }

    @Test
    fun corruptOrMissing_isNull() {
        assertNull(LegacyPendingChange.decode(null))
        assertNull(LegacyPendingChange.decode(""))
        assertNull(LegacyPendingChange.decode("{\"dailyLimitMinutes\":30}"))
        assertNull(LegacyPendingChange.decode("not json"))
    }

    @Test
    fun daysBitmask_mondayIsBitZero() {
        assertEquals(1, BlockSchedule(days = setOf(DayOfWeek.MONDAY)).daysBitmask)
        assertEquals(64, BlockSchedule(days = setOf(DayOfWeek.SUNDAY)).daysBitmask)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY), BlockSchedule.daysFromBitmask(65))
    }
}
