package com.unscroll.app.data.db

/** Result row: time spent in one app inside a range. */
data class AppTotalRow(
    val packageName: String,
    val totalMillis: Long,
)

/** Result row: session statistics for one app, for sessions that started in a range. */
data class AppSessionStatsRow(
    val packageName: String,
    val opens: Int,
    val totalDurationMillis: Long,
    val longestMillis: Long,
)

/** Result row: swipe statistics for one app, for sessions that started in a range. */
data class AppScrollStatsRow(
    val packageName: String,
    val swipes: Int,
    val sessions: Int,
    val durationMillis: Long,
)

/** Result row: when one session started and ended. */
data class SessionSpanRow(
    val startTime: Long,
    val endTime: Long?,
)

/** Changes whenever a session is inserted, closed or gets new swipes. */
data class SessionsChangeToken(
    val count: Int,
    val endTimeSum: Long,
    val scrollSum: Long = 0,
)
