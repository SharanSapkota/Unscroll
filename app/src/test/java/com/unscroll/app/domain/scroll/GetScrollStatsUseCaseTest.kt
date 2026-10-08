package com.unscroll.app.domain.scroll

import com.unscroll.app.domain.insights.UsagePeriod
import com.unscroll.app.domain.session.Session
import com.unscroll.app.domain.tracking.DefaultTrackedApps.FACEBOOK
import com.unscroll.app.domain.tracking.DefaultTrackedApps.INSTAGRAM
import com.unscroll.app.testing.FakeUsageDataSource
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class GetScrollStatsUseCaseTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, 10, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private val now = at(6, 20)
    private var nextId = 1L
    private fun session(pkg: String, start: Long, minutes: Int, swipes: Int) =
        Session(nextId++, pkg, start, start + minutes * 60_000L, swipes)

    @Test
    fun perAppTotals_averages_andRates() = runTest {
        val source = FakeUsageDataSource(
            mutableListOf(
                session(INSTAGRAM, at(6, 9), minutes = 10, swipes = 100),
                session(INSTAGRAM, at(6, 12), minutes = 10, swipes = 50),
                session(FACEBOOK, at(6, 13), minutes = 5, swipes = 20),
            ),
        )

        val stats = GetScrollStatsUseCase(source)(UsagePeriod.TODAY, countingSince = 0, now = now, zone = zone)

        assertEquals(170, stats.swipesToday)
        assertEquals(listOf(INSTAGRAM, FACEBOOK), stats.apps.map { it.packageName })
        val instagram = stats.apps.first()
        assertEquals(150, instagram.swipes)
        assertEquals(75.0, instagram.averagePerSession, 0.001)
        assertEquals(7.5, instagram.perMinute, 0.001)
        assertEquals(170, stats.combined.swipes)
        assertEquals(3, stats.combined.sessions)
    }

    @Test
    fun sessionsBeforeCountingStarted_areLeftOut() = runTest {
        val source = FakeUsageDataSource(
            mutableListOf(
                // Before scroll counting was switched on: zero swipes that would skew the average.
                session(INSTAGRAM, at(6, 8), minutes = 30, swipes = 0),
                session(INSTAGRAM, at(6, 15), minutes = 10, swipes = 60),
            ),
        )

        val stats = GetScrollStatsUseCase(source)(UsagePeriod.TODAY, countingSince = at(6, 10), now = now, zone = zone)

        assertEquals(1, stats.apps.single().sessions)
        assertEquals(60.0, stats.apps.single().averagePerSession, 0.001)
    }

    @Test
    fun weekPeriod_coversEarlierDays_butSwipesTodayOnlyToday() = runTest {
        val source = FakeUsageDataSource(
            mutableListOf(
                session(INSTAGRAM, at(3, 21), minutes = 10, swipes = 40),
                session(INSTAGRAM, at(6, 9), minutes = 10, swipes = 10),
            ),
        )

        val stats = GetScrollStatsUseCase(source)(UsagePeriod.WEEK, countingSince = 0, now = now, zone = zone)

        assertEquals(50, stats.apps.single().swipes)
        assertEquals(10, stats.swipesToday)
    }

    @Test
    fun countingStartedInTheFuture_givesEmptyStats() = runTest {
        val source = FakeUsageDataSource(mutableListOf(session(INSTAGRAM, at(6, 9), minutes = 10, swipes = 10)))

        val stats = GetScrollStatsUseCase(source)(UsagePeriod.TODAY, countingSince = at(7, 9), now = now, zone = zone)

        assertEquals(0, stats.swipesToday)
        assertEquals(emptyList<AppScrollStats>(), stats.apps)
        assertEquals(0.0, stats.combined.perMinute, 0.0)
    }
}
