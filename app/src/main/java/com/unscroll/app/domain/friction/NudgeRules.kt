package com.unscroll.app.domain.friction

/** Which once-a-day nudges are due. Pure; the caller records what it sent. */
object NudgeRules {

    /**
     * Open-count thresholds reached today that haven't been notified yet. The caller notifies once
     * (for the highest) and records all of them, so each threshold fires at most once per day.
     */
    fun thresholdsToNotify(opensToday: Int, thresholds: List<Int>, alreadySent: Set<Int>): List<Int> =
        thresholds.filter { it in 1..opensToday && it !in alreadySent }.sorted()

    const val LIMIT_WARNING_PERCENT = 80
    const val LIMIT_REACHED_PERCENT = 100

    /** 80 % and 100 % of the daily limit, each once per day. */
    fun limitLevelsToWarn(usedMillis: Long, limitMinutes: Int, alreadySent: Set<Int>): List<Int> {
        if (limitMinutes <= 0) return emptyList()
        val usedPercent = usedMillis * 100 / (limitMinutes * 60_000L)
        return listOf(LIMIT_WARNING_PERCENT, LIMIT_REACHED_PERCENT)
            .filter { usedPercent >= it && it !in alreadySent }
    }

    /** When usage will reach the next unsent warning level, or null. */
    fun nextLimitWarningAt(usedMillis: Long, limitMinutes: Int, alreadySent: Set<Int>, now: Long): Long? {
        if (limitMinutes <= 0) return null
        val limitMillis = limitMinutes * 60_000L
        return listOf(LIMIT_WARNING_PERCENT, LIMIT_REACHED_PERCENT)
            .filter { it !in alreadySent }
            .map { limitMillis * it / 100 - usedMillis }
            .filter { it > 0 }
            .minOrNull()
            ?.let { now + it }
    }
}

/** Break reminders every [intervalMillis] of a session: at start + interval, start + 2 × interval, … */
object BreakReminders {

    /**
     * The reminder number due at [now] (1 for the first), or null if none is due or the latest due
     * one was already shown. "Keep going" just records it; the next one comes an interval later.
     */
    fun dueReminder(sessionStart: Long, intervalMillis: Long, now: Long, lastShown: Int): Int? {
        if (intervalMillis <= 0) return null
        val index = ((now - sessionStart) / intervalMillis).toInt()
        return index.takeIf { it >= 1 && it > lastShown }
    }

    fun nextReminderAt(sessionStart: Long, intervalMillis: Long, lastShown: Int): Long =
        sessionStart + intervalMillis * (lastShown + 1)
}
