package com.unscroll.app.domain.blocking

import com.unscroll.app.domain.apps.ExcludedApps
import com.unscroll.app.domain.section.SectionVerdict
import com.unscroll.app.domain.time.Clock
import java.time.DayOfWeek
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.TUESDAY
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockEvaluatorTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private var now = at("2026-10-05T12:00") // A Monday.
    private val evaluator = BlockEvaluator(Clock { now }, ExcludedApps.STATIC)

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
    fun entireApp_wins_evenWithExtension() {
        val settings = LimitSettings(dailyLimitMinutes = 60, entireAppBlockedUntil = TimedBlock.FOREVER)
        assertEquals(
            BlockDecision.Blocked(BlockReason.BLOCKED_ENTIRE_APP, until = null),
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

    @Test
    fun excludedApps_areNeverBlocked_whateverTheirSettingsSay() {
        val everything = LimitSettings(entireAppBlockedUntil = TimedBlock.FOREVER, dailyLimitMinutes = 1)
        listOf(
            "com.sharansapkota.unscroll",
            "com.android.settings",
            "com.google.android.dialer",
            "com.android.vending",
            "com.android.systemui",
            "com.google.android.apps.messaging",
            "com.android.emergency",
        ).forEach { packageName ->
            assertEquals(
                packageName,
                BlockDecision.Allowed(remainingMillis = null),
                evaluator.evaluate(everything, usedTodayMillis = 60 * 60_000L, packageName = packageName),
            )
        }
    }

    @Test
    fun anyAddedApp_isBlockedLikeTheDefaults() {
        val decision = evaluator.evaluate(LimitSettings(entireAppBlockedUntil = TimedBlock.FOREVER), usedTodayMillis = 0, packageName = "com.example.anyapp")
        assertEquals(BlockDecision.Blocked(BlockReason.BLOCKED_ENTIRE_APP, until = null), decision)
    }

    // Quick toggles: timed and "until I turn it off" blocks.

    @Test
    fun entireApp_untilTurnedOff_blocksWithoutAnEnd() {
        assertEquals(
            BlockDecision.Blocked(BlockReason.BLOCKED_ENTIRE_APP, until = null),
            evaluate(LimitSettings(entireAppBlockedUntil = TimedBlock.FOREVER)),
        )
    }

    @Test
    fun entireApp_timed_blocksUntilItsEnd() {
        val until = now + 15 * MINUTE
        assertEquals(
            BlockDecision.Blocked(BlockReason.BLOCKED_ENTIRE_APP, until = until),
            evaluate(LimitSettings(entireAppBlockedUntil = until)),
        )
    }

    @Test
    fun entireApp_expiresAtTheExactBoundary() {
        val until = now + 30 * MINUTE
        val settings = LimitSettings(entireAppBlockedUntil = until, dailyLimitMinutes = 60)
        now = until - 1
        assertEquals(BlockDecision.Blocked(BlockReason.BLOCKED_ENTIRE_APP, until = until), evaluate(settings))
        now = until
        assertEquals(BlockDecision.Allowed(remainingMillis = 60 * MINUTE), evaluate(settings))
        now = until + 1
        assertEquals(BlockDecision.Allowed(remainingMillis = 60 * MINUTE), evaluate(settings))
    }

    @Test
    fun entireApp_winsOverTheScheduleAndTheDailyLimit() {
        val until = now + 15 * MINUTE
        val settings = LimitSettings(
            entireAppBlockedUntil = until,
            dailyLimitMinutes = 10,
            schedule = schedule("11:00", "13:00", MONDAY),
        )
        assertEquals(
            BlockDecision.Blocked(BlockReason.BLOCKED_ENTIRE_APP, until = until),
            evaluate(settings, usedMinutes = 30, extensionUntil = now + 5 * MINUTE),
        )
    }

    @Test
    fun reelsOnly_neverBlocksTheWholeApp() {
        assertEquals(
            BlockDecision.Allowed(remainingMillis = null),
            evaluate(LimitSettings(reelsBlockedUntil = TimedBlock.FOREVER)),
        )
    }

    @Test
    fun reelsOnly_coversOnlyAPositivelyIdentifiedSection() {
        val reels = LimitSettings(reelsBlockedUntil = now + 15 * MINUTE)
        assertTrue(evaluator.blocksSection(reels, SectionVerdict.IN_BLOCKED_SECTION, now))
        // Fail open: unknown screens and chat are never covered.
        assertFalse(evaluator.blocksSection(reels, SectionVerdict.UNKNOWN, now))
        assertFalse(evaluator.blocksSection(reels, SectionVerdict.IN_ALLOWED_SECTION, now))
        // Off: nothing is covered.
        assertFalse(evaluator.blocksSection(LimitSettings.NONE, SectionVerdict.IN_BLOCKED_SECTION, now))
    }

    @Test
    fun reelsOnly_timed_expiresAtTheExactBoundary_andUntilOffNeverDoes() {
        val until = now + 15 * MINUTE
        val timed = LimitSettings(reelsBlockedUntil = until)
        assertTrue(evaluator.blocksSection(timed, SectionVerdict.IN_BLOCKED_SECTION, until - 1))
        assertFalse(evaluator.blocksSection(timed, SectionVerdict.IN_BLOCKED_SECTION, until))
        val forever = LimitSettings(reelsBlockedUntil = TimedBlock.FOREVER)
        assertTrue(evaluator.blocksSection(forever, SectionVerdict.IN_BLOCKED_SECTION, now + 3_650L * 24 * 60 * MINUTE))
    }

    @Test
    fun entireApp_includesReels() {
        val entire = LimitSettings(entireAppBlockedUntil = now + 15 * MINUTE, reelsBlockedUntil = null)
        assertTrue(evaluator.blocksSection(entire, SectionVerdict.IN_BLOCKED_SECTION, now))
    }

    @Test
    fun section_neverBlockedForExcludedApps() {
        val reels = LimitSettings(reelsBlockedUntil = TimedBlock.FOREVER)
        assertFalse(evaluator.blocksSection(reels, SectionVerdict.IN_BLOCKED_SECTION, now, "com.android.settings"))
        assertTrue(evaluator.blocksSection(reels, SectionVerdict.IN_BLOCKED_SECTION, now, "com.instagram.android"))
    }

    @Test
    fun changingTheDurationWhileActive_restartsTheBlock() {
        val started = QuickBlockRules.setOn(LimitSettings(lastEntireDuration = BlockDuration.MIN_15), QuickBlockTarget.ENTIRE_APP, true, now)
        assertEquals(now + 15 * MINUTE, started.entireAppBlockedUntil)

        now += 10 * MINUTE
        val changed = QuickBlockRules.setDuration(started, QuickBlockTarget.ENTIRE_APP, BlockDuration.HOUR_1, now)
        assertEquals(now + 60 * MINUTE, changed.entireAppBlockedUntil)
        assertEquals(BlockDuration.HOUR_1, changed.lastEntireDuration)
        assertEquals(BlockDecision.Blocked(BlockReason.BLOCKED_ENTIRE_APP, until = now + 60 * MINUTE), evaluate(changed))

        // 20 minutes after the first start: the old 15 min block would be over, the new one isn't.
        now += 10 * MINUTE
        assertTrue(evaluate(changed) is BlockDecision.Blocked)
    }
}
