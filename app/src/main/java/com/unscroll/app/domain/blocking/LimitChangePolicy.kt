package com.unscroll.app.domain.blocking

import java.util.BitSet

/**
 * Cooldown friction. A change that makes blocking stronger applies at once; a change that makes it
 * weaker in any way (removing a block, raising or removing a limit, shrinking or disabling a
 * schedule) waits as a [PendingChange]. A change that is stronger in one way and weaker in another
 * counts as weaker.
 */
object LimitChangePolicy {

    private const val MINUTES_PER_WEEK = 7 * BlockSchedule.MINUTES_PER_DAY

    fun isWeaker(old: LimitSettings, new: LimitSettings): Boolean {
        if (old.blockedAlways && !new.blockedAlways) return true
        val oldLimit = old.dailyLimitMinutes
        val newLimit = new.dailyLimitMinutes
        if (oldLimit != null && (newLimit == null || newLimit > oldLimit)) return true
        // Weaker if any minute of the week blocked before is no longer blocked.
        val oldBlocked = blockedMinutesOfWeek(old.schedule)
        val newBlocked = blockedMinutesOfWeek(new.schedule)
        oldBlocked.andNot(newBlocked)
        return !oldBlocked.isEmpty
    }

    /** Applies [requested] now if it is not weaker, otherwise queues it for [now] + [delayMillis]. */
    fun request(current: AppLimit, requested: LimitSettings, now: Long, delayMillis: Long): AppLimit =
        if (isWeaker(current.settings, requested)) {
            current.copy(pending = PendingChange(requested, appliesAt = now + delayMillis))
        } else {
            // A stronger change wins over anything still pending.
            current.copy(settings = requested, pending = null)
        }

    /** Applies the pending change if its cooldown is over. */
    fun resolve(limit: AppLimit, now: Long): AppLimit {
        val pending = limit.pending ?: return limit
        return if (now >= pending.appliesAt) limit.copy(settings = pending.settings, pending = null) else limit
    }

    /** Applies the pending change right away, after the user typed the unlock phrase. */
    fun applyNow(limit: AppLimit): AppLimit {
        val pending = limit.pending ?: return limit
        return limit.copy(settings = pending.settings, pending = null)
    }

    fun cancel(limit: AppLimit): AppLimit = limit.copy(pending = null)

    /** Bit i is set if minute i of the week (Monday 00:00 = 0) is inside the schedule. */
    internal fun blockedMinutesOfWeek(schedule: BlockSchedule): BitSet {
        val bits = BitSet(MINUTES_PER_WEEK)
        if (!schedule.enabled) return bits
        val length = when {
            schedule.startMinute == schedule.endMinute -> BlockSchedule.MINUTES_PER_DAY
            schedule.crossesMidnight -> BlockSchedule.MINUTES_PER_DAY - schedule.startMinute + schedule.endMinute
            else -> schedule.endMinute - schedule.startMinute
        }
        val start = if (schedule.startMinute == schedule.endMinute) 0 else schedule.startMinute
        for (day in schedule.days) {
            val dayStart = (day.value - 1) * BlockSchedule.MINUTES_PER_DAY + start
            for (offset in 0 until length) bits.set((dayStart + offset) % MINUTES_PER_WEEK)
        }
        return bits
    }
}
