package com.unscroll.app.domain.blocking

import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LimitChangePolicyTest {

    private val delay = 10 * 60_000L
    private val now = 1_000_000L

    private fun schedule(start: Int, end: Int, vararg days: DayOfWeek, enabled: Boolean = true) =
        BlockSchedule(enabled, days.toSet(), start * 60, end * 60)

    private fun weaker(old: LimitSettings, new: LimitSettings) = LimitChangePolicy.isWeaker(old, new)

    @Test
    fun blockCompletely_onIsStronger_offIsWeaker() {
        assertFalse(weaker(LimitSettings(), LimitSettings(blockedAlways = true)))
        assertTrue(weaker(LimitSettings(blockedAlways = true), LimitSettings()))
    }

    @Test
    fun dailyLimit_addingOrLoweringIsStronger_raisingOrRemovingIsWeaker() {
        assertFalse(weaker(LimitSettings(), LimitSettings(dailyLimitMinutes = 30)))
        assertFalse(weaker(LimitSettings(dailyLimitMinutes = 60), LimitSettings(dailyLimitMinutes = 30)))
        assertFalse(weaker(LimitSettings(dailyLimitMinutes = 30), LimitSettings(dailyLimitMinutes = 30)))
        assertTrue(weaker(LimitSettings(dailyLimitMinutes = 30), LimitSettings(dailyLimitMinutes = 45)))
        assertTrue(weaker(LimitSettings(dailyLimitMinutes = 30), LimitSettings(dailyLimitMinutes = null)))
    }

    @Test
    fun schedule_enablingOrWideningIsStronger() {
        val base = LimitSettings(schedule = schedule(22, 7, MONDAY))
        assertFalse(weaker(LimitSettings(), base))
        assertFalse(weaker(base, LimitSettings(schedule = schedule(21, 8, MONDAY))))
        assertFalse(weaker(base, LimitSettings(schedule = schedule(22, 7, MONDAY, FRIDAY))))
    }

    @Test
    fun schedule_disablingNarrowingOrDroppingDaysIsWeaker() {
        val base = LimitSettings(schedule = schedule(22, 7, MONDAY, FRIDAY))
        assertTrue(weaker(base, LimitSettings(schedule = schedule(22, 7, MONDAY, FRIDAY, enabled = false))))
        assertTrue(weaker(base, LimitSettings(schedule = schedule(23, 7, MONDAY, FRIDAY))))
        assertTrue(weaker(base, LimitSettings(schedule = schedule(22, 6, MONDAY, FRIDAY))))
        assertTrue(weaker(base, LimitSettings(schedule = schedule(22, 7, MONDAY))))
    }

    @Test
    fun schedule_movingTheWindow_isWeaker() {
        // Same length, different hours: some previously blocked minutes become free.
        val base = LimitSettings(schedule = schedule(9, 17, MONDAY))
        assertTrue(weaker(base, LimitSettings(schedule = schedule(10, 18, MONDAY))))
    }

    @Test
    fun mixedChange_strongerAndWeaker_countsAsWeaker() {
        val old = LimitSettings(dailyLimitMinutes = 30)
        val new = LimitSettings(dailyLimitMinutes = 60, blockedAlways = true)
        assertTrue(weaker(old, new))
    }

    @Test
    fun request_strongerChange_appliesImmediately_andDropsPending() {
        val current = AppLimit(
            "pkg",
            LimitSettings(dailyLimitMinutes = 60),
            pending = PendingChange(LimitSettings(), appliesAt = now + 1),
        )
        val result = LimitChangePolicy.request(current, LimitSettings(dailyLimitMinutes = 30), now, delay)
        assertEquals(AppLimit("pkg", LimitSettings(dailyLimitMinutes = 30), pending = null), result)
    }

    @Test
    fun request_weakerChange_isPending_untilDelayPasses() {
        val current = AppLimit("pkg", LimitSettings(dailyLimitMinutes = 30))
        val requested = LimitSettings(dailyLimitMinutes = 90)

        val pending = LimitChangePolicy.request(current, requested, now, delay)
        assertEquals(LimitSettings(dailyLimitMinutes = 30), pending.settings)
        assertEquals(PendingChange(requested, appliesAt = now + delay), pending.pending)

        // Not yet.
        assertEquals(pending, LimitChangePolicy.resolve(pending, now + delay - 1))
        // Cooldown over.
        assertEquals(AppLimit("pkg", requested, null), LimitChangePolicy.resolve(pending, now + delay))
    }

    @Test
    fun applyNow_andCancel() {
        val pending = AppLimit("pkg", LimitSettings(blockedAlways = true), PendingChange(LimitSettings(), now + delay))
        assertEquals(AppLimit("pkg", LimitSettings(), null), LimitChangePolicy.applyNow(pending))
        assertEquals(AppLimit("pkg", LimitSettings(blockedAlways = true), null), LimitChangePolicy.cancel(pending))
        assertNull(LimitChangePolicy.resolve(AppLimit("pkg"), now).pending)
    }

    @Test
    fun blockedMinutes_crossingMidnightFromSunday_wrapsToMonday() {
        val bits = LimitChangePolicy.blockedMinutesOfWeek(schedule(22, 7, DayOfWeek.SUNDAY))
        assertEquals(9 * 60, bits.cardinality())
        assertTrue(bits[0]) // Monday 00:00
        assertTrue(bits[6 * 1440 + 22 * 60]) // Sunday 22:00
        assertFalse(bits[7 * 60]) // Monday 07:00
    }
}
