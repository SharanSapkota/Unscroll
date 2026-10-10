package com.unscroll.app.domain.blocking

/**
 * How long a quick block lasts: 15 min, 30 min, 1 hour, 2 hours or "Until I turn it off".
 * Stored as minutes, with 0 for [UNTIL_OFF].
 */
enum class BlockDuration(val minutes: Int) {
    MIN_15(15),
    MIN_30(30),
    HOUR_1(60),
    HOUR_2(120),
    UNTIL_OFF(0),
    ;

    /** When a block started at [now] with this duration ends ([TimedBlock.FOREVER] for [UNTIL_OFF]). */
    fun untilFrom(now: Long): Long = if (this == UNTIL_OFF) TimedBlock.FOREVER else now + minutes * MINUTE

    companion object {
        /** The default for every app and toggle. */
        val DEFAULT = UNTIL_OFF

        /** The stored minutes; anything unknown reads as [DEFAULT]. */
        fun fromMinutes(minutes: Int): BlockDuration = entries.firstOrNull { it.minutes == minutes } ?: DEFAULT

        private const val MINUTE = 60_000L
    }
}

/**
 * A block that ends at an epoch-millis timestamp. [FOREVER] means "until I turn it off". Expiry is
 * always decided by comparing with the clock, so a block ends on time even after process death;
 * the stored value is only cleaned up later (BlockExpiryScheduler).
 */
object TimedBlock {
    /** "Until I turn it off". */
    const val FOREVER = Long.MAX_VALUE

    /** Active from when it was set until (excluding) [until]. */
    fun isActive(until: Long?, now: Long): Boolean = until != null && now < until

    /** Time left, or null when the block is off, over, or has no end. */
    fun remainingMillis(until: Long?, now: Long): Long? =
        if (!isActive(until, now) || until == FOREVER) null else until!! - now
}

/** The two quick toggles: "Block reels only" (Reels-capable apps) and "Block entire app". */
enum class QuickBlockTarget { REELS, ENTIRE_APP }

/**
 * Pure rules for the quick toggles. Turning one on starts the block for its remembered duration;
 * choosing another duration while it is on restarts it with the new one; turning it off ends it
 * at once. No cooldown, no confirmation.
 */
object QuickBlockRules {

    fun isOn(settings: LimitSettings, target: QuickBlockTarget, now: Long): Boolean =
        TimedBlock.isActive(settings.untilFor(target), now)

    /** Turns [target] on or off. On starts it for the remembered duration from [now]. */
    fun setOn(settings: LimitSettings, target: QuickBlockTarget, on: Boolean, now: Long): LimitSettings =
        settings.withUntil(target, if (on) settings.durationFor(target).untilFrom(now) else null)

    /** Remembers [duration]; if [target] is on, its block restarts with it from [now]. */
    fun setDuration(settings: LimitSettings, target: QuickBlockTarget, duration: BlockDuration, now: Long): LimitSettings {
        val remembered = when (target) {
            QuickBlockTarget.REELS -> settings.copy(lastReelsDuration = duration)
            QuickBlockTarget.ENTIRE_APP -> settings.copy(lastEntireDuration = duration)
        }
        return if (isOn(settings, target, now)) remembered.withUntil(target, duration.untilFrom(now)) else remembered
    }

    /** Clears blocks that are over (the toggle turns itself off). Returns the same object if nothing changed. */
    fun clearExpired(settings: LimitSettings, now: Long): LimitSettings {
        var result = settings
        QuickBlockTarget.entries.forEach { target ->
            val until = result.untilFor(target)
            if (until != null && !TimedBlock.isActive(until, now)) result = result.withUntil(target, null)
        }
        return result
    }

    /** The next moment a timed block of [settings] ends, or null (none running, or only "until off"). */
    fun nextExpiry(settings: LimitSettings, now: Long): Long? =
        QuickBlockTarget.entries
            .mapNotNull { settings.untilFor(it) }
            .filter { TimedBlock.isActive(it, now) && it != TimedBlock.FOREVER }
            .minOrNull()

    private fun LimitSettings.untilFor(target: QuickBlockTarget): Long? = when (target) {
        QuickBlockTarget.REELS -> reelsBlockedUntil
        QuickBlockTarget.ENTIRE_APP -> entireAppBlockedUntil
    }

    private fun LimitSettings.durationFor(target: QuickBlockTarget): BlockDuration = when (target) {
        QuickBlockTarget.REELS -> lastReelsDuration
        QuickBlockTarget.ENTIRE_APP -> lastEntireDuration
    }

    private fun LimitSettings.withUntil(target: QuickBlockTarget, until: Long?): LimitSettings = when (target) {
        QuickBlockTarget.REELS -> copy(reelsBlockedUntil = until)
        QuickBlockTarget.ENTIRE_APP -> copy(entireAppBlockedUntil = until)
    }
}
