package com.unscroll.app.ui.home

import com.unscroll.app.domain.blocking.AppLimit
import com.unscroll.app.domain.blocking.LimitSettings
import com.unscroll.app.domain.insights.AppUsage
import com.unscroll.app.domain.insights.PeriodUsage
import com.unscroll.app.domain.insights.UsagePeriod
import com.unscroll.app.domain.insights.UsageSummary
import com.unscroll.app.domain.scroll.AppScrollStats
import com.unscroll.app.domain.scroll.ScrollStats
import com.unscroll.app.domain.tracking.DefaultTrackedApps.FACEBOOK
import com.unscroll.app.domain.tracking.DefaultTrackedApps.INSTAGRAM
import com.unscroll.app.domain.tracking.DefaultTrackedApps.TIKTOK
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeTilesTest {

    private val packages = listOf(INSTAGRAM, TIKTOK, FACEBOOK)

    private fun usage(period: UsagePeriod, vararg apps: Pair<String, Long>) = PeriodUsage(
        period = period,
        combined = UsageSummary(apps.sumOf { it.second }, 0, 0, 0),
        apps = apps.map { (pkg, millis) -> AppUsage(pkg, UsageSummary(millis, 1, millis, millis)) },
        hourly = List(24) { 0L },
    )

    private fun scroll(vararg apps: Pair<String, Int>) = ScrollStats(
        swipesToday = apps.sumOf { it.second },
        combined = AppScrollStats("", apps.sumOf { it.second }, 0, 0),
        apps = apps.map { (pkg, swipes) -> AppScrollStats(pkg, swipes, 1, 60_000) },
    )

    @Test
    fun everyInstalledApp_mostUsedFirst_tiesInTrackedOrder() {
        val tiles = HomeTiles.build(packages, usage(UsagePeriod.TODAY, TIKTOK to 600_000L), null, emptyMap())

        assertEquals(listOf(TIKTOK, INSTAGRAM, FACEBOOK), tiles.map { it.packageName })
        assertEquals(listOf(600_000L, 0L, 0L), tiles.map { it.millis })
    }

    @Test
    fun swipes_onlyOnceScrollCountingWasOn() {
        assertNull(HomeTiles.build(packages, usage(UsagePeriod.TODAY), null, emptyMap()).first().swipes)

        val tiles = HomeTiles.build(packages, usage(UsagePeriod.TODAY), scroll(INSTAGRAM to 86), emptyMap())
        assertEquals(86, tiles.first { it.packageName == INSTAGRAM }.swipes)
        assertEquals(0, tiles.first { it.packageName == TIKTOK }.swipes)
    }

    @Test
    fun limitBar_onlyInTheDayView_asShareOfTheDailyLimit() {
        val limits = mapOf(INSTAGRAM to AppLimit(INSTAGRAM, LimitSettings(dailyLimitMinutes = 60)))

        val day = HomeTiles.build(packages, usage(UsagePeriod.TODAY, INSTAGRAM to 45 * 60_000L), null, limits)
        assertEquals(0.75f, day.first { it.packageName == INSTAGRAM }.limitProgress!!, 0.0001f)
        assertNull(day.first { it.packageName == TIKTOK }.limitProgress)

        val week = HomeTiles.build(packages, usage(UsagePeriod.WEEK, INSTAGRAM to 45 * 60_000L), null, limits)
        assertNull(week.first { it.packageName == INSTAGRAM }.limitProgress)
    }

    @Test
    fun blockedApps_areMarked() {
        val limits = mapOf(FACEBOOK to AppLimit(FACEBOOK, LimitSettings(blockedAlways = true)))
        val tiles = HomeTiles.build(packages, null, null, limits)
        assertEquals(listOf(false, false, true), tiles.map { it.blocked })
    }
}
