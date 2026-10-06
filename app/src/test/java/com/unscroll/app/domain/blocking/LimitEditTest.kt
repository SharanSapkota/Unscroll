package com.unscroll.app.domain.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Edits from the Apps screen, where the controls show the pending target. Regression tests for the
 * "Block completely switch looks stuck" bug: tapping the switch again restarted the countdown, and
 * changing another setting while a block-off was pending silently dropped it.
 */
class LimitEditTest {

    private val delay = 10 * 60_000L
    private val start = 1_000_000L
    private val blocked = AppLimit(PKG, LimitSettings(blockedAlways = true))

    private fun AppLimit.edit(at: Long, change: (LimitSettings) -> LimitSettings) =
        LimitChangePolicy.edit(this, change, at, delay)

    private fun turnBlockOff(at: Long = start) = blocked.edit(at) { it.copy(blockedAlways = false) }

    @Test
    fun turningBlockOff_isPending_withACountdown_andTheTargetIsOff() {
        val limit = turnBlockOff()

        assertEquals(LimitSettings(blockedAlways = true), limit.settings)
        assertEquals(PendingChange(LimitSettings(blockedAlways = false), appliesAt = start + delay), limit.pending)
        assertEquals(false, LimitChangePolicy.target(limit).blockedAlways)
        assertEquals(listOf(PendingPart.BlockOff), LimitChangePolicy.describe(limit.settings, limit.pending!!.settings))
    }

    @Test
    fun pendingBlockOff_appliesWhenTheDelayPasses() {
        val limit = turnBlockOff()

        assertEquals(limit, LimitChangePolicy.resolve(limit, start + delay - 1))
        assertEquals(AppLimit(PKG, LimitSettings(blockedAlways = false)), LimitChangePolicy.resolve(limit, start + delay))
    }

    @Test
    fun tappingTheSwitchAgain_undoesThePendingChange_insteadOfRestartingIt() {
        // The switch shows "off" (pending), so tapping it means "keep it on".
        val limit = turnBlockOff().edit(start + 60_000) { it.copy(blockedAlways = true) }

        assertEquals(AppLimit(PKG, LimitSettings(blockedAlways = true)), limit)
    }

    @Test
    fun changingAnotherSetting_whileBlockOffIsPending_keepsIt_andItsCountdown() {
        // Before the fix this dropped the pending block-off and left the switch "stuck" on.
        val limit = turnBlockOff().edit(start + 60_000) { it.copy(dailyLimitMinutes = 30) }

        // The stronger part (a new limit) applies at once; the block-off still waits, same timer.
        assertEquals(LimitSettings(blockedAlways = true, dailyLimitMinutes = 30), limit.settings)
        assertEquals(
            PendingChange(LimitSettings(blockedAlways = false, dailyLimitMinutes = 30), appliesAt = start + delay),
            limit.pending,
        )
    }

    @Test
    fun turningOnTheSchedule_whileBlockOffIsPending_appliesTheScheduleNow() {
        val limit = turnBlockOff().edit(start + 1) { it.copy(schedule = it.schedule.copy(enabled = true)) }

        assertEquals(true, limit.settings.schedule.enabled)
        assertEquals(true, limit.settings.blockedAlways)
        assertEquals(start + delay, limit.pending?.appliesAt)
        assertEquals(true, limit.pending?.settings?.schedule?.enabled)
    }

    @Test
    fun loosening_thePendingChangeFurther_restartsTheCountdown() {
        val limited = AppLimit(PKG, LimitSettings(dailyLimitMinutes = 30))
        val raised = limited.edit(start) { it.copy(dailyLimitMinutes = 60) }
        val raisedMore = raised.edit(start + 60_000) { it.copy(dailyLimitMinutes = 90) }

        assertEquals(PendingChange(LimitSettings(dailyLimitMinutes = 90), start + 60_000 + delay), raisedMore.pending)
        assertEquals(listOf(PendingPart.LimitRaised(90)), LimitChangePolicy.describe(raisedMore.settings, raisedMore.pending!!.settings))
    }

    @Test
    fun tighteningThePendingChange_keepsTheCountdown() {
        val limited = AppLimit(PKG, LimitSettings(dailyLimitMinutes = 30))
        val raised = limited.edit(start) { it.copy(dailyLimitMinutes = 90) }
        val lessRaised = raised.edit(start + 60_000) { it.copy(dailyLimitMinutes = 60) }

        assertEquals(PendingChange(LimitSettings(dailyLimitMinutes = 60), start + delay), lessRaised.pending)
    }

    @Test
    fun strongerEdits_withoutAPendingChange_applyAtOnce() {
        val limit = AppLimit(PKG).edit(start) { it.copy(blockedAlways = true) }
        assertEquals(blocked, limit)
        assertNull(limit.pending)
    }

    @Test
    fun describe_listsEveryLoosenedPart() {
        val current = LimitSettings(
            dailyLimitMinutes = 30,
            blockedAlways = true,
            schedule = BlockSchedule(enabled = true),
        )
        assertEquals(
            listOf(PendingPart.BlockOff, PendingPart.LimitRemoved, PendingPart.ScheduleOff),
            LimitChangePolicy.describe(current, LimitSettings()),
        )
        val narrower = current.copy(schedule = current.schedule.copy(startMinute = 23 * 60))
        assertEquals(listOf(PendingPart.ScheduleLoosened), LimitChangePolicy.describe(current, narrower))
    }

    private companion object {
        const val PKG = "com.instagram.android"
    }
}
