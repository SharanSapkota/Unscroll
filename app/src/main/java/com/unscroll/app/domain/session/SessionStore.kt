package com.unscroll.app.domain.session

/** Persistence used by the SessionManager. Implemented by SessionRepository on top of Room. */
interface SessionStore {
    /** Inserts an open session and returns its id. */
    suspend fun openSession(packageName: String, startTime: Long): Long

    suspend fun closeSession(id: Long, endTime: Long)

    /** Saves the swipe count of a session (M7 scroll counting). */
    suspend fun updateScrollCount(id: Long, scrollCount: Int)

    /**
     * Closes every session that is still open (left behind when the process died) at [endTime],
     * or at its own start time if [endTime] is earlier. Returns how many were closed.
     */
    suspend fun closeOrphanedSessions(endTime: Long): Int
}

/** Last time the tracker was known to be alive with a session open. */
interface HeartbeatStore {
    suspend fun saveHeartbeat(timestamp: Long)

    suspend fun lastHeartbeat(): Long?
}

/** The two sections a segment can be in (Reels-only timer, Instagram and TikTok). */
enum class ScreenSection { REELS, OTHER }

/** Persistence of section segments (parts of a session in one section). SessionManager only. */
interface SegmentStore {
    /** Inserts an open segment of session [sessionId] and returns its id. */
    suspend fun openSegment(sessionId: Long, packageName: String, section: ScreenSection, startTime: Long): Long

    suspend fun closeSegment(id: Long, endTime: Long)

    suspend fun updateSegmentSwipes(id: Long, swipes: Int)

    /** Closes segments left open by a killed process, like [SessionStore.closeOrphanedSessions]. */
    suspend fun closeOrphanedSegments(endTime: Long): Int
}
