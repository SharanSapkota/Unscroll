package com.unscroll.app.domain.goals

/** A daily time goal across all tracked apps: a day "counts" if total time stays within it. */
object DailyGoal {
    /** Choices offered in Settings, in minutes. */
    val PRESETS = listOf(30, 60, 90, 120, 180)

    /** How far back streaks look. */
    const val HISTORY_DAYS = 365

    fun millis(goalMinutes: Int): Long = goalMinutes * 60_000L
}

/** Streak facts from complete days only (up to yesterday). */
data class StreakHistory(
    /** Days in a row within the goal, ending yesterday. */
    val endingYesterday: Int,
    /** Longest run within the goal among complete days. */
    val bestCompleted: Int,
)

data class Streak(
    /** Days in a row within the goal, today included while today is still within it. */
    val current: Int,
    val best: Int,
    /** Today is still within the goal. */
    val todayOnTrack: Boolean,
)

/** Pure streak rules; the use case feeds them daily totals. */
object StreakRules {

    /**
     * [dailyTotals] are complete days in order, oldest first, starting with the first day Unscroll
     * tracked anything and ending yesterday. A day within [goalMillis] (equal counts) extends a
     * streak; a day over it ends one.
     */
    fun history(dailyTotals: List<Long>, goalMillis: Long): StreakHistory {
        var run = 0
        var best = 0
        for (total in dailyTotals) {
            run = if (total <= goalMillis) run + 1 else 0
            best = maxOf(best, run)
        }
        return StreakHistory(endingYesterday = run, bestCompleted = best)
    }

    /** Adds today: it counts while it is still within the goal, and breaks the streak once it isn't. */
    fun withToday(history: StreakHistory, todayMillis: Long, goalMillis: Long): Streak {
        val onTrack = todayMillis <= goalMillis
        val current = if (onTrack) history.endingYesterday + 1 else 0
        return Streak(current = current, best = maxOf(history.bestCompleted, current), todayOnTrack = onTrack)
    }
}
