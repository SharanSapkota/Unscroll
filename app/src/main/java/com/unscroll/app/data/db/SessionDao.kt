package com.unscroll.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Insert
    suspend fun insertAll(sessions: List<SessionEntity>)

    @Query("UPDATE sessions SET endTime = :endTime WHERE id = :id")
    suspend fun close(id: Long, endTime: Long)

    /** Closes all open sessions at [endTime], but never before a session's own start. */
    @Query("UPDATE sessions SET endTime = MAX(startTime, :endTime) WHERE endTime IS NULL")
    suspend fun closeAllOpen(endTime: Long): Int

    @Query("SELECT * FROM sessions WHERE endTime IS NULL ORDER BY startTime")
    suspend fun getOpen(): List<SessionEntity>

    @Query("SELECT * FROM sessions ORDER BY startTime DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SessionEntity>>

    /**
     * Time per app inside [rangeStart, rangeEnd). Sessions are clipped to the range, so one that
     * crosses midnight only counts the part on each side. Open sessions run until [now].
     */
    @Query(
        """
        SELECT packageName,
            SUM(
                MAX(0, MIN(COALESCE(endTime, :now), :rangeEnd) - MAX(startTime, :rangeStart))
            ) AS totalMillis
        FROM sessions
        WHERE startTime < :rangeEnd AND COALESCE(endTime, :now) > :rangeStart
        GROUP BY packageName
        """,
    )
    suspend fun appTotals(rangeStart: Long, rangeEnd: Long, now: Long): List<AppTotalRow>

    /**
     * Opens, total and longest full-session duration per app, for sessions that started in
     * [rangeStart, rangeEnd). Open sessions run until [now].
     */
    @Query(
        """
        SELECT packageName,
            COUNT(*) AS opens,
            SUM(MAX(0, COALESCE(endTime, :now) - startTime)) AS totalDurationMillis,
            MAX(MAX(0, COALESCE(endTime, :now) - startTime)) AS longestMillis
        FROM sessions
        WHERE startTime >= :rangeStart AND startTime < :rangeEnd
        GROUP BY packageName
        """,
    )
    suspend fun appSessionStats(
        rangeStart: Long,
        rangeEnd: Long,
        now: Long,
    ): List<AppSessionStatsRow>

    /** Sessions overlapping [rangeStart, rangeEnd), including open ones, oldest first. */
    @Query(
        """
        SELECT * FROM sessions
        WHERE startTime < :rangeEnd AND (endTime IS NULL OR endTime > :rangeStart)
        ORDER BY startTime
        """,
    )
    suspend fun sessionsOverlapping(rangeStart: Long, rangeEnd: Long): List<SessionEntity>

    /**
     * Swipes, sessions and full session length per app, for sessions that started in
     * [rangeStart, rangeEnd). Open sessions run until [now].
     */
    @Query(
        """
        SELECT packageName,
            SUM(scrollCount) AS swipes,
            COUNT(*) AS sessions,
            SUM(MAX(0, COALESCE(endTime, :now) - startTime)) AS durationMillis
        FROM sessions
        WHERE startTime >= :rangeStart AND startTime < :rangeEnd
        GROUP BY packageName
        """,
    )
    suspend fun appScrollStats(rangeStart: Long, rangeEnd: Long, now: Long): List<AppScrollStatsRow>

    /** Every session, oldest first, for the CSV export. */
    @Query("SELECT * FROM sessions ORDER BY startTime, id")
    suspend fun getAll(): List<SessionEntity>

    @Query("SELECT MIN(startTime) FROM sessions")
    suspend fun firstSessionStart(): Long?

    /** "Delete usage history". Returns how many sessions were deleted. */
    @Query("DELETE FROM sessions")
    suspend fun deleteAll(): Int

    /** Swipes in the app's sessions that started at or after [since] (the swipe limit's window). */
    @Query("SELECT COALESCE(SUM(scrollCount), 0) FROM sessions WHERE packageName = :packageName AND startTime >= :since")
    suspend fun swipesSince(packageName: String, since: Long): Int

    /** Swipes in the app's sessions that started in [from, until) (the pill's earlier sessions today). */
    @Query(
        "SELECT COALESCE(SUM(scrollCount), 0) FROM sessions " +
            "WHERE packageName = :packageName AND startTime >= :from AND startTime < :until",
    )
    suspend fun swipesBetween(packageName: String, from: Long, until: Long): Int

    /** The app's sessions that started at or after [since], newest first (for per-session swipe windows). */
    @Query(
        "SELECT startTime, endTime FROM sessions WHERE packageName = :packageName AND startTime >= :since " +
            "ORDER BY startTime DESC",
    )
    suspend fun sessionSpansSince(packageName: String, since: Long): List<SessionSpanRow>

    /** Sets the swipe count of a session. Only SessionManager writes it. */
    @Query("UPDATE sessions SET scrollCount = :scrollCount WHERE id = :id")
    suspend fun updateScrollCount(id: Long, scrollCount: Int)

    @Query(
        "SELECT COUNT(*) AS count, COALESCE(SUM(endTime), 0) AS endTimeSum, " +
            "COALESCE(SUM(scrollCount), 0) AS scrollSum FROM sessions",
    )
    fun observeChangeToken(): Flow<SessionsChangeToken>
}
