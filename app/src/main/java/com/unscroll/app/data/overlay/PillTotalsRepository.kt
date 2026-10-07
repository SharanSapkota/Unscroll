package com.unscroll.app.data.overlay

import com.unscroll.app.data.db.SessionDao
import com.unscroll.app.domain.overlay.PillToday
import com.unscroll.app.domain.overlay.PillTodayBase
import com.unscroll.app.domain.session.ActiveSession
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Measures the pill's base for the app on screen, once per visit: today's time and swipes in the
 * app's sessions before the open one. The open session is left out on purpose (its time and
 * swipes are added live), so it is never counted twice.
 */
@Singleton
class PillTotalsRepository @Inject constructor(
    private val sessions: SessionDao,
) {
    suspend fun base(session: ActiveSession, now: Long, zone: ZoneId, floorMillis: Long = 0): PillTodayBase {
        val dayStart = PillToday.dayStart(now, zone)
        // Everything before the open session started, from midnight. Earlier sessions are closed
        // by then, and a session that crossed midnight only counts its part after it.
        val until = session.startTime
        val earlier = until > dayStart
        val completedMillis = if (earlier) {
            sessions.appTotals(rangeStart = dayStart, rangeEnd = until, now = until)
                .firstOrNull { it.packageName == session.packageName }?.totalMillis ?: 0L
        } else {
            0L
        }
        val completedSwipes = if (earlier) sessions.swipesBetween(session.packageName, dayStart, until) else 0
        return PillTodayBase(
            packageName = session.packageName,
            sessionId = session.id,
            sessionStart = session.startTime,
            dayStart = dayStart,
            completedMillis = completedMillis,
            completedSwipes = completedSwipes,
            floorMillis = floorMillis,
        )
    }
}
