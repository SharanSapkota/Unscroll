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

/** Changes whenever a session is inserted or closed. */
data class SessionsChangeToken(
    val count: Int,
    val endTimeSum: Long,
)
