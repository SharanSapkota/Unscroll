package com.unscroll.app.data.reels

import com.unscroll.app.data.db.SectionSegmentDao
import com.unscroll.app.domain.insights.localDate
import com.unscroll.app.domain.insights.startOfDay
import com.unscroll.app.domain.session.ScreenSection
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Today's time and swipes in Reels / For You for one app (the Reels-only timer's segments). */
data class ReelsToday(val millis: Long, val swipes: Int)

/** Reads the Reels figures shown next to the app totals (Apps tab, App detail). */
@Singleton
class ReelsUsageRepository @Inject constructor(
    private val segments: SectionSegmentDao,
) {
    /** Per app, since local midnight (segments crossing midnight count only their part after it). */
    suspend fun today(now: Long, zone: ZoneId): Map<String, ReelsToday> {
        val dayStart = startOfDay(localDate(now, zone), zone)
        return segments.totals(ScreenSection.REELS.name, rangeStart = dayStart, rangeEnd = now, now = now)
            .associate { it.packageName to ReelsToday(it.totalMillis, it.swipes) }
    }
}
