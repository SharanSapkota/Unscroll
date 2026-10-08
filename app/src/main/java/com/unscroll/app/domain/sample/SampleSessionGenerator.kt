package com.unscroll.app.domain.sample

import com.unscroll.app.domain.insights.localDate
import com.unscroll.app.domain.insights.startOfDay
import com.unscroll.app.domain.tracking.DefaultTrackedApps
import java.time.ZoneId
import kotlin.random.Random

/** A closed session to insert as sample data. */
data class SampleSession(val packageName: String, val startTime: Long, val endTime: Long)

/**
 * Generates realistic-looking fake sessions for testing the dashboard without real usage (debug
 * builds only). Sessions never overlap, never end after `now`, lean towards evenings, and
 * sometimes cross midnight.
 */
object SampleSessionGenerator {

    private val apps = listOf(
        DefaultTrackedApps.INSTAGRAM,
        DefaultTrackedApps.TIKTOK,
        DefaultTrackedApps.FACEBOOK,
    )

    // Relative weight of each hour of the day: quiet at night, busy at lunch and in the evening.
    private val hourWeights = intArrayOf(
        3, 2, 1, 0, 0, 0, 1, 2, 3, 2, 2, 2,
        4, 3, 2, 2, 3, 4, 5, 6, 7, 8, 8, 6,
    )

    fun generate(
        now: Long,
        zone: ZoneId,
        days: Int = 30,
        random: Random = Random(SEED),
    ): List<SampleSession> {
        val today = localDate(now, zone)
        val sessions = mutableListOf<SampleSession>()
        var earliestNextStart = startOfDay(today.minusDays(days - 1L), zone)

        for (offset in days - 1 downTo 0) {
            val dayStart = startOfDay(today.minusDays(offset.toLong()), zone)
            val starts = List(random.nextInt(4, 14)) {
                dayStart + randomHour(random) * HOUR + random.nextLong(HOUR)
            }.sorted()
            for (start in starts) {
                val begin = maxOf(start, earliestNextStart)
                val duration = MINUTE * random.nextLong(1, 26) + random.nextLong(MINUTE)
                val end = begin + duration
                if (end > now) break
                sessions += SampleSession(apps[random.nextInt(apps.size)], begin, end)
                earliestNextStart = end + MINUTE * random.nextLong(1, 30)
            }
        }
        return sessions
    }

    private fun randomHour(random: Random): Int {
        var pick = random.nextInt(hourWeights.sum())
        for ((hour, weight) in hourWeights.withIndex()) {
            if (pick < weight) return hour
            pick -= weight
        }
        return hourWeights.lastIndex
    }

    private const val SEED = 42
    private const val MINUTE = 60_000L
    private const val HOUR = 60 * MINUTE
}
