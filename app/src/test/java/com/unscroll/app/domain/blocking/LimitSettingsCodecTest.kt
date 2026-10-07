package com.unscroll.app.domain.blocking

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LimitSettingsCodecTest {

    @Test
    fun roundTrip() {
        val settings = LimitSettings(
            dailyLimitMinutes = 45,
            blockedAlways = false,
            schedule = BlockSchedule(true, setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY), 22 * 60, 7 * 60),
        )
        assertEquals(settings, LimitSettingsCodec.decode(LimitSettingsCodec.encode(settings)))
        assertEquals(LimitSettings.NONE, LimitSettingsCodec.decode(LimitSettingsCodec.encode(LimitSettings.NONE)))
    }

    @Test
    fun encodesAsFlatJson() {
        assertEquals(
            "{\"dailyLimitMinutes\":null,\"blockedAlways\":true,\"scheduleEnabled\":false," +
                "\"scheduleDays\":127,\"scheduleStartMinute\":1320,\"scheduleEndMinute\":420," +
                "\"swipeLimit\":null,\"swipeLimitPerSession\":false,\"swipeSessionGapMinutes\":30," +
                "\"swipeAccessAllowed\":false}",
            LimitSettingsCodec.encode(LimitSettings(blockedAlways = true)),
        )
    }

    @Test
    fun corruptOrMissing_isNull() {
        assertNull(LimitSettingsCodec.decode(null))
        assertNull(LimitSettingsCodec.decode(""))
        assertNull(LimitSettingsCodec.decode("{\"dailyLimitMinutes\":30}"))
        assertNull(LimitSettingsCodec.decode("not json"))
    }

    @Test
    fun daysBitmask_mondayIsBitZero() {
        assertEquals(1, BlockSchedule(days = setOf(DayOfWeek.MONDAY)).daysBitmask)
        assertEquals(64, BlockSchedule(days = setOf(DayOfWeek.SUNDAY)).daysBitmask)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY), BlockSchedule.daysFromBitmask(65))
    }

    @Test
    fun swipeLimitFields_roundTrip() {
        val settings = LimitSettings(
            dailyLimitMinutes = 30,
            swipeLimit = 150,
            swipeLimitScope = SwipeLimitScope.SESSION,
            swipeSessionGapMinutes = 15,
            swipeAccessAllowed = true,
        )
        assertEquals(settings, LimitSettingsCodec.decode(LimitSettingsCodec.encode(settings)))
    }

    @Test
    fun jsonFromBeforeSwipeLimits_decodesWithDefaults() {
        val old = "{\"dailyLimitMinutes\":30,\"blockedAlways\":false,\"scheduleEnabled\":false," +
            "\"scheduleDays\":127,\"scheduleStartMinute\":1320,\"scheduleEndMinute\":420}"
        val decoded = LimitSettingsCodec.decode(old)!!
        assertEquals(30, decoded.dailyLimitMinutes)
        assertEquals(null, decoded.swipeLimit)
        assertEquals(SwipeLimitScope.DAY, decoded.swipeLimitScope)
        assertEquals(SwipeLimitRules.DEFAULT_SESSION_GAP_MINUTES, decoded.swipeSessionGapMinutes)
        assertEquals(false, decoded.swipeAccessAllowed)
    }
}
