package com.unscroll.app.data.blocking

import com.unscroll.app.data.db.SessionDao
import com.unscroll.app.domain.blocking.LimitSettings
import com.unscroll.app.domain.blocking.SessionSpan
import com.unscroll.app.domain.blocking.SwipeLimitRules
import com.unscroll.app.domain.blocking.SwipeLimitScope
import com.unscroll.app.domain.blocking.SwipeLimitStatus
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Swipes and "I need access" extensions in the current window of an app's swipe limit. */
@Singleton
class SwipeLimitRepository @Inject constructor(
    private val sessions: SessionDao,
    private val limits: LimitRepository,
) {
    /** Null when the app has no swipe limit. */
    suspend fun status(packageName: String, settings: LimitSettings, now: Long, zone: ZoneId): SwipeLimitStatus? {
        val limit = settings.swipeLimit ?: return null
        val since = windowStart(packageName, settings, now, zone)
        return SwipeLimitRules.status(
            limit = limit,
            used = sessions.swipesSince(packageName, since),
            extensions = limits.swipeExtensionsSince(packageName, since),
        )
    }

    private suspend fun windowStart(packageName: String, settings: LimitSettings, now: Long, zone: ZoneId): Long {
        val recent = if (settings.swipeLimitScope == SwipeLimitScope.SESSION) {
            sessions.sessionSpansSince(packageName, now - LOOKBACK_MILLIS).map { SessionSpan(it.startTime, it.endTime) }
        } else {
            emptyList()
        }
        return SwipeLimitRules.windowStart(settings.swipeLimitScope, recent, settings.swipeSessionGapMinutes, now, zone)
    }

    private companion object {
        /** A per-session window looks back at most this far. */
        const val LOOKBACK_MILLIS = 2 * 24 * 60 * 60_000L
    }
}
