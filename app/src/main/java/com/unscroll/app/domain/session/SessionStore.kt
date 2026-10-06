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
