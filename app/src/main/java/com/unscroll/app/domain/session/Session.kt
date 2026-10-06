package com.unscroll.app.domain.session

/** A logged session in a tracked app. [endTime] is null while it is still open. */
data class Session(
    val id: Long,
    val packageName: String,
    val startTime: Long,
    val endTime: Long?,
    val scrollCount: Int,
)

/** The session currently in progress. */
data class ActiveSession(
    val id: Long,
    val packageName: String,
    val startTime: Long,
)
