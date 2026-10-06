package com.unscroll.app.domain.blocking

import com.unscroll.app.domain.time.Clock
import java.time.DayOfWeek
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.TUESDAY
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class BlockEvaluatorTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private var now = at("2026-10-05T12:00") // A Monday.
    private val evaluator = BlockEvaluator(Clock { now })

    private fun at(text: String): Long =
        LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()

    private fun evaluate(settings: LimitSettings, usedMinutes: Long = 0, extensionUntil: Long? = null) =
        evaluator.evaluate(settings, usedMinutes * MINUTE, extensionUntil, zone = zone)

    private fun schedule(start: String, end: String, vararg days: DayOfWeek) = BlockSchedule(
        enabled = true,
        days = days.toSet(),
        startMinute = start.toMinutes(),
        endMinute = end.toMinutes(),
    )

    private fun String.toMinutes() = split(":").let { it[0].toInt() * 60 + it[1].toInt() }

    @Test
    fun noRules_isAllowedWithoutRemaining() {
        assertEquals(BlockDecision.Allowed(null), evaluate(LimitSettings.NONE, usedMinutes = 500))
    }

    @Test
    fun blockedAlways_wins_evenWithExtension() {
        val settings = LimitSettings(dailyLimitMinutes = 60, blockedAlways = true)
        assertEquals(
            BlockDecision.Blocked(BlockReason.BLOCKED_ALWAYS, until = null),
            evaluate(settings, extensionUntil = now + 5 * MINUTE),
        )
    }

    @Test
    fun dailyLimit_underLimit_reportsRemaining() {
        assertEquals(
            BlockDecision.Allowed(remainingMillis = 12 * MINUTE),
            evaluate(LimitSettings(dailyLimitMinutes = 30), usedMinutes = 18),
        )
    }

    @Test
    fun dailyLimit_reached_blocksUntilMidnight() {
        val expected = BlockDecision.Blocked(BlockReason.DAILY_LIMIT_REACHED, until = at("2026-10-06T00:00"))
        assertEquals(expected, evaluate(LimitSettings(dailyLimitMinutes = 30), usedMinutes = 30))
        assertEquals(expected, evaluate(LimitSettings(dailyLimitMinutes = 30), usedMinutes = 95))
    }

    @Test
    fun dailyLimit_reached_butExtensionRunning_isAllowedUntilExtensionEnds() {
        val settings = LimitSettings(dailyLimitMinutes = 30)
        assertEquals(
            BlockDecision.Allowed(remainingMillis = 4 * MINUTE),
            evaluate(settings, usedMinutes = 31, extensionUntil = now + 4 * MINUTE),
        )
        // Expired extension: blocked again.
        assertEquals(
            BlockReason.DAILY_LIMIT_REACHED,
            (evaluate(settings, usedMinutes = 31, extensionUntil = now - 1) as BlockDecision.Blocked).reason,
        )
    }

    @Test
    fun schedule_sameDay_insideAndOutside() {
        val settings = LimitSettings(schedule = schedule("09:00", "17:00", MONDAY))

        now = at("2026-10-05T08:59")
        assertEquals(BlockDecision.Allowed(null), evaluate(settings))
        now = at("2026-10-05T09:00")
        assertEquals(BlockDecision.Blocked(BlockReason.INSIDE_SCHEDULE, at("2026-10-05T17:00")), evaluate(settings))
        now = at("2026-10-05T16:59")
        assertEquals(BlockReason.INSIDE_SCHEDULE, (evaluate(settings) as BlockDecision.Blocked).reason)
        now = at("2026-10-05T17:00")
        assertEquals(BlockDecision.Allowed(null), evaluate(settings))
    }

    @Test
    fun schedule_onlyOnSelectedDays() {
        val settings = LimitSettings(schedule = schedule("09:00", "17:00", SATURDAY, SUNDAY))
        now = at("2026-10-05T12:00") // Monday
        assertEquals(BlockDecision.Allowed(null), evaluate(settings))
        now = at("2026-10-10T12:00") // Saturday
        assertEquals(BlockReason.INSIDE_SCHEDULE, (evaluate(settings) as BlockDecision.Blocked).reason)
    }

    @Test
    fun schedule_crossingMidnight_eveningAndNextMorning() {
        // Monday 22:00 to Tuesday 07:00.
        val settings = LimitSettings(schedule = schedule("22:00", "07:00", MONDAY))

        now = at("2026-10-05T21:59")
        assertEquals(BlockDecision.Allowed(null), evaluate(settings))
        now = at("2026-10-05T23:30")
        assertEquals(BlockDecision.Blocked(BlockReason.INSIDE_SCHEDULE, at("2026-10-06T07:00")), evaluate(settings))
        now = at("2026-10-06T06:59") // Tuesday morning, belongs to Monday's window.
        assertEquals(BlockDecision.Blocked(BlockReason.INSIDE_SCHEDULE, at("2026-10-06T07:00")), evaluate(settings))
        now = at("2026-10-06T07:00")
        assertEquals(BlockDecision.Allowed(null), evaluate(settings))
        now = at("2026-10-06T23:00") // Tuesday evening: Tuesday not selected.
        assertEquals(BlockDecision.Allowed(null), evaluate(settings))
    }

    @Test
    fun schedule_crossingMidnight_morningPartNeedsPreviousDaySelected() {
        // Only Tuesday selected: Monday night/Tuesday early morning is free, Tuesday night isn't.
        val settings = LimitSettings(schedule = schedule("22:00", "07:00", TUESDAY))
        now = at("2026-10-06T03:00")
        assertEquals(BlockDecision.Allowed(null), evaluate(settings))
        now = at("2026-10-07T03:00") // Wednesday early morning, from Tuesday's window.
        assertEquals(BlockReason.INSIDE_SCHEDULE, (evaluate(settings) as BlockDecision.Blocked).reason)
    }

    @Test
    fun schedule_disabled_orNoDays_neverBlocks() {
        now = at("2026-10-05T23:00")
        val disabled = schedule("22:00", "07:00", MONDAY).copy(enabled = false)
        assertEquals(BlockDecision.Allowed(null), evaluate(LimitSettings(schedule = disabled)))
        assertEquals(BlockDecision.Allowed(null), evaluate(LimitSettings(schedule = schedule("22:00", "07:00"))))
    }

    @Test
    fun schedule_startEqualsEnd_blocksWholeDay() {
        val settings = LimitSettings(schedule = schedule("00:00", "00:00", MONDAY))
        now = at("2026-10-05T00:00")
        assertEquals(BlockDecision.Blocked(BlockReason.INSIDE_SCHEDULE, at("2026-10-06T00:00")), evaluate(settings))
        now = at("2026-10-06T00:00")
        assertEquals(BlockDecision.Allowed(null), evaluate(settings))
    }

    @Test
    fun schedule_winsOverRemainingLimit() {
        val settings = LimitSettings(dailyLimitMinutes = 60, schedule = schedule("09:00", "17:00", MONDAY))
        assertEquals(BlockReason.INSIDE_SCHEDULE, (evaluate(settings, usedMinutes = 0) as BlockDecision.Blocked).reason)
    }

    @Test
    fun usesInjectedClockByDefault() {
        now = at("2026-10-05T23:30")
        val settings = LimitSettings(schedule = schedule("22:00", "07:00", MONDAY))
        val decision = evaluator.evaluate(settings, usedTodayMillis = 0, zone = zone)
        assertEquals(BlockReason.INSIDE_SCHEDULE, (decision as BlockDecision.Blocked).reason)
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
