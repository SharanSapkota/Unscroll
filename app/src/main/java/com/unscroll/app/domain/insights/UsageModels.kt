package com.unscroll.app.domain.insights

import java.time.LocalDate

/** Raw per-app session statistics for sessions that started in a period. */
data class AppSessionStats(
    val packageName: String,
    val opens: Int,
    val totalDurationMillis: Long,
    val longestMillis: Long,
)

/** Usage of one app, or of all apps combined, in a period. */
data class UsageSummary(
    /** Time spent inside the period. Sessions crossing its edges only count the part inside. */
    val totalMillis: Long,
    /** Sessions that started in the period. */
    val opens: Int,
    val averageSessionMillis: Long,
    val longestSessionMillis: Long,
) {
    companion object {
        val EMPTY = UsageSummary(0, 0, 0, 0)
    }
}

data class AppUsage(val packageName: String, val usage: UsageSummary)

data class PeriodUsage(
    val period: UsagePeriod,
    val combined: UsageSummary,
    /** Apps with any time or opens in the period, most time first. */
    val apps: List<AppUsage>,
    /** Time per local hour of day, 24 entries starting at midnight. */
    val hourly: List<Long>,
)

data class DayUsage(val date: LocalDate, val totalMillis: Long)

/** The last 7 days (ending today) next to the 7 days before them. */
data class WeekComparison(
    val thisWeek: List<DayUsage>,
    val lastWeek: List<DayUsage>,
    val trend: Trend,
)

/** All-time usage reframed into more tangible units. */
data class HoursInvested(
    val totalMillis: Long,
    val hours: Double,
    val days: Double,
    val books: Double,
    val flightsToTokyo: Double,
)
