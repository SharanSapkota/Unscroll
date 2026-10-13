package com.unscroll.app.domain.insights

import com.unscroll.app.domain.session.Session
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightFactsBuilderTest {

    private val instagram = "com.instagram.android"
    private val tiktok = "com.zhiliaoapp.musically"

    /** Wednesday 2026-10-14, 20:00 in Berlin. */
    private val now = at("2026-10-14T20:00")
    private val today = LocalDate.of(2026, 10, 14)

    private fun day(offset: Long, from: String, to: String, packageName: String = instagram, swipes: Int = 0, id: Long = 0) =
        today.minusDays(offset).let { date ->
            Session(id, packageName, at("${date}T$from"), at("${date}T$to"), swipes)
        }

    private fun inputs(
        sessions: List<Session>,
        period: UsagePeriod = UsagePeriod.TODAY,
        goalMinutes: Int? = null,
        limits: Map<String, Int> = emptyMap(),
        countingSince: Long? = null,
        periodSwipes: Map<String, Int>? = null,
        age: Int? = null,
    ): InsightInputs {
        val range = period.range(now, BERLIN)
        val totals = sessions.groupBy { it.packageName }
            .mapValues { (_, list) -> list.sumOf { UsageMath.overlap(it, range, now) } }
            .filterValues { it > 0 }
        val apps = totals.map { (pkg, millis) -> AppUsage(pkg, UsageSummary(millis, 1, millis, millis)) }
            .sortedByDescending { it.usage.totalMillis }
        val firstWeekEnd = sessions.minOfOrNull { it.startTime }?.let { startOfDay(localDate(it, BERLIN).plusDays(7), BERLIN) }
        val first = sessions.minOfOrNull { it.startTime }
        return InsightInputs(
            now = now,
            zone = BERLIN,
            period = period,
            periodUsage = PeriodUsage(
                period,
                UsageSummary(totals.values.sum(), apps.size, 0, 0),
                apps,
                UsageMath.hourlyTotals(sessions, range, BERLIN, now),
            ),
            periodSwipes = periodSwipes,
            countedApps = setOf(instagram, tiktok),
            countingSince = countingSince,
            sessions = sessions,
            firstSessionStart = first,
            allTimeMillis = sessions.sumOf { (it.endTime ?: now) - it.startTime },
            firstWeekMillis = if (first != null && firstWeekEnd != null) {
                sessions.sumOf { UsageMath.overlap(it, TimeRange(startOfDay(localDate(first, BERLIN), BERLIN), firstWeekEnd), now) }
            } else {
                null
            },
            labels = mapOf(instagram to "Instagram", tiktok to "TikTok"),
            goalMinutes = goalMinutes,
            dailyLimitMinutes = limits,
            activeApps = setOf(instagram, tiktok),
            swipeLengthMeters = 0.08,
            age = age,
        )
    }

    /** One hour of Instagram every evening for [days] days before today, and 30 minutes today. */
    private fun evenings(days: Int, todayMinutes: Int = 30): List<Session> =
        (1..days).map { day(it.toLong(), "19:00", "20:00") } +
            listOf(day(0, "19:00", "19:${todayMinutes.toString().padStart(2, '0')}"))

    @Test
    fun today_comparesWithYesterdayUpToTheSameTime() {
        // Yesterday: 18:00–19:00 and 21:00–23:00. By 20:00 only the first hour counts.
        val sessions = listOf(
            day(2, "10:00", "10:10"),
            day(1, "18:00", "19:00"),
            day(1, "21:00", "23:00"),
            day(0, "19:00", "19:30"),
        )
        val facts = InsightFactsBuilder.build(inputs(sessions))
        assertEquals(30 * MINUTE, facts.today.millis)
        assertEquals(HOUR, facts.today.previousMillis)
        assertEquals(30 * MINUTE, facts.today.savedMillis)
    }

    @Test
    fun noComparison_withADayUnscrollDidntTrack() {
        // The first session ever was yesterday afternoon: yesterday's morning wasn't tracked.
        val sessions = listOf(day(1, "18:00", "19:00"), day(0, "19:00", "19:30"))
        val facts = InsightFactsBuilder.build(inputs(sessions))
        assertNull(facts.today.previousMillis)
        assertNull(facts.today.savedMillis)
        assertNull(facts.week.previousMillis)
    }

    @Test
    fun week_comparesWithTheSevenDaysBefore() {
        val facts = InsightFactsBuilder.build(inputs(evenings(20), UsagePeriod.WEEK))
        // Last 7 days: six full hours plus today's 30 minutes. The 7 before: seven hours.
        assertEquals(6 * HOUR + 30 * MINUTE, facts.week.millis)
        assertEquals(7 * HOUR, facts.week.previousMillis)
        assertEquals(-7, facts.week.percentChange)
        assertEquals(7 * HOUR, facts.combined.previousMillis)
    }

    @Test
    fun swipes_realWhenCountingCoveredThePeriod_elseEstimated() {
        val sessions = listOf(day(1, "18:00", "19:00", swipes = 100), day(0, "19:00", "19:30", swipes = 250))
        val counted = InsightFactsBuilder.build(
            inputs(sessions, countingSince = at("2026-10-01T00:00"), periodSwipes = mapOf(instagram to 250)),
        )
        assertEquals(SwipeCount(250, estimated = false), counted.combined.swipes)
        // Counting started this afternoon, after today began: estimate (30 minutes × 4).
        val partly = InsightFactsBuilder.build(inputs(sessions, countingSince = at("2026-10-14T15:00")))
        assertEquals(SwipeCount(120, estimated = true), partly.combined.swipes)
        assertEquals(SwipeCount(120, estimated = true), partly.today.swipes)
        val never = InsightFactsBuilder.build(inputs(sessions))
        assertEquals(SwipeCount(120, estimated = true), never.combined.swipes)
    }

    @Test
    fun streak_underTheGoal_todayIncludedWhileUnder() {
        // Goal 90 minutes; every day one hour, two days ago 2 hours (over).
        val sessions = evenings(10) + day(2, "08:00", "09:00")
        val facts = InsightFactsBuilder.build(inputs(sessions, goalMinutes = 90))
        assertEquals(2, facts.streak)
        assertEquals(true, facts.underLimitToday)
        assertEquals(true, facts.underLimitYesterday)
    }

    @Test
    fun streak_dailyLimitsPerApp_andNoneWithoutLimits() {
        val sessions = evenings(5) + day(0, "08:00", "08:40", packageName = tiktok)
        val facts = InsightFactsBuilder.build(inputs(sessions, limits = mapOf(tiktok to 30)))
        assertEquals(0, facts.streak)
        assertEquals(false, facts.underLimitToday)
        assertNull(InsightFactsBuilder.build(inputs(sessions)).streak)
        assertNull(InsightFactsBuilder.build(inputs(sessions)).underLimitToday)
    }

    @Test
    fun projection_onlyAfterSevenCompleteDays() {
        assertNull(InsightFactsBuilder.build(inputs(evenings(6))).projection)
        val projection = InsightFactsBuilder.build(inputs(evenings(7))).projection!!
        assertEquals(HOUR, projection.averageMillisPerDay)
    }

    @Test
    fun totalSaved_fromTheFirstWeeksPace() {
        // First week: 2 hours a day. Then 10 days at 1 hour: 10 hours reclaimed.
        val sessions = (11..17).map { day(it.toLong(), "08:00", "10:00") } + evenings(10)
        val facts = InsightFactsBuilder.build(inputs(sessions))
        assertEquals(10 * HOUR, facts.totalSavedMillis)
        assertEquals(2 * HOUR, facts.firstWeekDailyMillis)
        assertEquals(HOUR, facts.lastWeekDailyMillis)
    }

    @Test
    fun totalSaved_neverWhenUsageWentUp() {
        val sessions = (11..17).map { day(it.toLong(), "19:00", "20:00") } + (1..10).map { day(it.toLong(), "08:00", "11:00") }
        assertNull(InsightFactsBuilder.build(inputs(sessions)).totalSavedMillis)
    }

    @Test
    fun bestDay_isTheLightestOfTheLastSevenCompleteDays() {
        // Last Saturday (4 days before Wednesday) only 10 minutes.
        val sessions = (1..10).filter { it != 4 }.map { day(it.toLong(), "19:00", "20:00") } + day(4, "19:00", "19:10")
        assertEquals(DayOfWeek.SATURDAY, InsightFactsBuilder.build(inputs(sessions)).bestDay)
    }

    @Test
    fun notOpenedToday_onlyForAppsUsedMostDays() {
        val sessions = evenings(8, todayMinutes = 10) + (1..6).map { day(it.toLong(), "12:00", "12:20", packageName = tiktok) }
        val facts = InsightFactsBuilder.build(inputs(sessions))
        assertEquals(listOf("TikTok"), facts.notOpenedToday)
        assertTrue(facts.notOpenedYesterday.isEmpty())
    }

    @Test
    fun lateNight_isAfter22() {
        val sessions = listOf(day(0, "21:30", "22:30"))
        val late = InsightFactsBuilder.build(inputs(sessions).let { it.copy(now = at("2026-10-14T23:00")) })
        assertEquals(30 * MINUTE, late.lateMillis)
    }

    @Test
    fun firstDays_flagged() {
        assertTrue(InsightFactsBuilder.build(inputs(evenings(2))).inFirstDays)
        assertFalse(InsightFactsBuilder.build(inputs(evenings(3))).inFirstDays)
    }

    @Test
    fun singleApp_namedInsteadOfSocialApps() {
        assertEquals("Instagram", InsightFactsBuilder.build(inputs(evenings(3))).singleAppLabel)
        val two = evenings(3) + day(1, "08:00", "08:10", packageName = tiktok)
        assertNull(InsightFactsBuilder.build(inputs(two)).singleAppLabel)
    }
}
