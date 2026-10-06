package com.unscroll.app.domain.insights

import com.unscroll.app.domain.scroll.AppScrollStats
import com.unscroll.app.domain.session.Session
import kotlinx.coroutines.flow.Flow

/**
 * Aggregated session queries. Open sessions (no end time) count as running until `now`.
 * Implemented by UsageRepository on top of Room.
 */
interface UsageDataSource {

    /** Time per app inside [range], with sessions clipped to the range. */
    suspend fun appTotals(range: TimeRange, now: Long): Map<String, Long>

    /** Opens, total and longest duration per app, for sessions that started in [range]. */
    suspend fun appSessionStats(range: TimeRange, now: Long): List<AppSessionStats>

    /** Swipes, sessions and their full length per app, for sessions that started in [range]. */
    suspend fun appScrollStats(range: TimeRange, now: Long): List<AppScrollStats>

    /** Sessions that overlap [range], oldest first. */
    suspend fun sessionsOverlapping(range: TimeRange): List<Session>

    /** Start of the first session ever logged, or null if there is none. */
    suspend fun firstSessionStart(): Long?

    /** Emits whenever sessions are added, closed or get new swipes. */
    fun observeChanges(): Flow<Unit>
}
