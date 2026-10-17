package com.unscroll.app.data.overlay

import com.unscroll.app.data.db.SectionSegmentDao
import com.unscroll.app.data.db.SessionDao
import com.unscroll.app.domain.overlay.PillToday
import com.unscroll.app.domain.overlay.PillTodayBase
import com.unscroll.app.domain.session.ActiveSegment
import com.unscroll.app.domain.session.ActiveSession
import com.unscroll.app.domain.session.ScreenSection
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
    private val segments: SectionSegmentDao,
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

    /**
     * The Reels-only timer's base for [segment] (an open REELS segment): today's time and swipes
     * in the app's earlier Reels segments, before the open one, clipped to midnight like sessions.
     * The pill adds the open segment live, exactly as it does with a session ([PillToday]).
     */
    suspend fun reelsBase(segment: ActiveSegment, now: Long, zone: ZoneId, floorMillis: Long = 0): PillTodayBase {
        val dayStart = PillToday.dayStart(now, zone)
        val until = segment.startTime
        val earlier = if (until > dayStart) {
            segments.totals(ScreenSection.REELS.name, rangeStart = dayStart, rangeEnd = until, now = until)
                .firstOrNull { it.packageName == segment.packageName }
        } else {
            null
        }
        return PillTodayBase(
            packageName = segment.packageName,
            sessionId = segment.id,
            sessionStart = segment.startTime,
            dayStart = dayStart,
            completedMillis = earlier?.totalMillis ?: 0L,
            completedSwipes = earlier?.swipes ?: 0,
            floorMillis = floorMillis,
        )
    }

    /**
     * Instagram/TikTok off Reels: today's Reels time and swipes so far, to show frozen on the
     * dimmed pill. [PillTodayBase.sessionStart] is [now], the moment the numbers froze, so
     * [PillToday.totalMillis] at that moment is exactly this total.
     */
    suspend fun reelsPaused(packageName: String, now: Long, zone: ZoneId, floorMillis: Long = 0): PillTodayBase {
        val dayStart = PillToday.dayStart(now, zone)
        val today = segments.totals(ScreenSection.REELS.name, rangeStart = dayStart, rangeEnd = now, now = now)
            .firstOrNull { it.packageName == packageName }
        return PillTodayBase(
            packageName = packageName,
            sessionId = PAUSED_ID,
            sessionStart = now,
            dayStart = dayStart,
            completedMillis = today?.totalMillis ?: 0L,
            completedSwipes = today?.swipes ?: 0,
            floorMillis = floorMillis,
        )
    }

    companion object {
        /** The paused base belongs to no session or segment. */
        const val PAUSED_ID = -1L
    }
}
