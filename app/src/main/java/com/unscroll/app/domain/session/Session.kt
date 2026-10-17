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

/** Swipes counted so far in the session in progress. */
data class SessionSwipes(
    val sessionId: Long,
    val packageName: String,
    val count: Int,
)

/** The open section segment of the session on screen, with its swipes so far. */
data class ActiveSegment(
    val id: Long,
    val sessionId: Long,
    val packageName: String,
    val section: ScreenSection,
    val startTime: Long,
    val swipes: Int = 0,
)
