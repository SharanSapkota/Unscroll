package com.unscroll.app.data.insights

import com.unscroll.app.data.apps.InstalledApps
import com.unscroll.app.data.blocking.LimitRepository
import com.unscroll.app.data.goals.GoalPreferences
import com.unscroll.app.data.scroll.ScrollCountingRepository
import com.unscroll.app.domain.insights.GetPeriodUsageUseCase
import com.unscroll.app.domain.insights.Insight
import com.unscroll.app.domain.insights.InsightEngine
import com.unscroll.app.domain.insights.InsightFacts
import com.unscroll.app.domain.insights.InsightFactsBuilder
import com.unscroll.app.domain.insights.InsightInputs
import com.unscroll.app.domain.insights.InsightSchedule
import com.unscroll.app.domain.insights.InsightTone
import com.unscroll.app.domain.insights.TimeRange
import com.unscroll.app.domain.insights.UsageDataSource
import com.unscroll.app.domain.insights.UsagePeriod
import com.unscroll.app.domain.insights.localDate
import com.unscroll.app.domain.insights.startOfDay
import com.unscroll.app.domain.plus.TrackedAppsSource
import com.unscroll.app.domain.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The Home card: today's message, whether "Next" can show another, and the weekly projection. */
data class InsightCardState(
    val insight: Insight? = null,
    val canShowNext: Boolean = false,
    /** The life projection, on the weekly day only. */
    val weekly: Insight? = null,
)

/**
 * Insights from the logged sessions: builds the facts (all on the phone), picks messages with the
 * rotation rules and remembers which were shown (template ids only). The Home card keeps its message
 * for the day, with fresh numbers, until "Next" (at most [InsightSchedule.DAILY_MAX] a day).
 */
@Singleton
class InsightRepository @Inject constructor(
    private val bank: InsightBankRepository,
    private val preferences: InsightPreferences,
    private val usage: UsageDataSource,
    private val getPeriodUsage: GetPeriodUsageUseCase,
    private val trackedApps: TrackedAppsSource,
    private val installedApps: InstalledApps,
    private val limits: LimitRepository,
    private val goals: GoalPreferences,
    private val scrollCounting: ScrollCountingRepository,
    private val swipeLength: SwipeLengthSource,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    /** The day "Next" found nothing else to show; it stays hidden for the rest of it. */
    private var exhaustedDay: Long? = null

    suspend fun card(period: UsagePeriod): InsightCardState = mutex.withLock {
        val settings = preferences.currentSettings()
        if (!settings.enabled) return@withLock InsightCardState()
        val engine = bank.engine()
        val now = clock.now()
        val zone = ZoneId.systemDefault()
        val day = localDate(now, zone).toEpochDay()
        val facts = facts(period, now, zone, settings.age)
        var state = preferences.currentRotation()

        var current = state.currentId?.takeIf { state.currentDay == day }
            ?.let { engine.render(it, facts, InsightSchedule.stableRandom(it, day)) }
        // A new day, or the message no longer fits the data (another period): pick one.
        if (current == null && state.shownOn(day) < InsightSchedule.DAILY_MAX) {
            current = engine.next(facts, state.history, settings.tone, Random.Default)?.let { picked ->
                preferences.recordShown(picked, day, now)
                engine.render(picked.templateId, facts, InsightSchedule.stableRandom(picked.templateId, day)) ?: picked
            }
            state = preferences.currentRotation()
        }
        InsightCardState(
            insight = current,
            canShowNext = current != null && state.shownOn(day) < InsightSchedule.DAILY_MAX && exhaustedDay != day,
            weekly = weekly(engine, facts, settings.tone, now, zone),
        )
    }

    /** "Next": another message under the rotation rules, if today's limit allows. */
    suspend fun next(period: UsagePeriod) = mutex.withLock {
        val settings = preferences.currentSettings()
        if (!settings.enabled) return@withLock
        val now = clock.now()
        val zone = ZoneId.systemDefault()
        val day = localDate(now, zone).toEpochDay()
        val state = preferences.currentRotation()
        if (state.shownOn(day) >= InsightSchedule.DAILY_MAX) return@withLock
        val facts = facts(period, now, zone, settings.age)
        val next = bank.engine().next(
            facts = facts,
            history = state.history,
            tone = settings.tone,
            random = Random.Default,
            exclude = state.currentId.takeIf { state.currentDay == day },
        )
        if (next == null) exhaustedDay = day else preferences.recordShown(next, day, now)
    }

    /**
     * The weekly notification's text: the same life projection as the card, at most once a week,
     * only when the user switched it on. Null when there is nothing to send.
     */
    suspend fun weeklyNotification(): String? = mutex.withLock {
        val settings = preferences.currentSettings()
        if (!settings.enabled || !settings.weeklyNotification) return@withLock null
        val now = clock.now()
        val zone = ZoneId.systemDefault()
        if (!InsightSchedule.isWeeklyDay(now, zone)) return@withLock null
        val day = localDate(now, zone).toEpochDay()
        val notified = preferences.currentRotation().notifiedDay
        if (notified != null && day - notified < DAYS_PER_WEEK) return@withLock null
        val facts = facts(UsagePeriod.WEEK, now, zone, settings.age)
        val insight = weekly(bank.engine(), facts, settings.tone, now, zone) ?: return@withLock null
        preferences.recordNotified(day)
        insight.text
    }

    /** On the weekly day: the day's life projection (picked once, then shown with fresh numbers). */
    private suspend fun weekly(
        engine: InsightEngine,
        facts: InsightFacts,
        tone: InsightTone,
        now: Long,
        zone: ZoneId,
    ): Insight? {
        if (!InsightSchedule.isWeeklyDay(now, zone)) return null
        val day = localDate(now, zone).toEpochDay()
        val state = preferences.currentRotation()
        if (state.weeklyDay == day) {
            return state.weeklyId?.let { engine.render(it, facts, InsightSchedule.stableRandom(it, day)) }
        }
        val picked = engine.weekly(facts, state.history, tone, now, Random.Default) ?: return null
        preferences.recordWeekly(picked, day, now)
        return engine.render(picked.templateId, facts, InsightSchedule.stableRandom(picked.templateId, day)) ?: picked
    }

    private suspend fun facts(period: UsagePeriod, now: Long, zone: ZoneId, age: Int?): InsightFacts {
        val today = localDate(now, zone)
        val range = period.range(now, zone)
        val tracked = trackedApps.state.first()
        val periodUsage = getPeriodUsage(period, now, zone)
        // Real swipe counts only while scroll counting runs and has covered the whole period.
        val countingSince = scrollCounting.countingSince.first()?.takeIf { scrollCounting.isCounting.value }
        val periodSwipes = if (countingSince != null && countingSince <= range.from) {
            usage.appScrollStats(range, now).associate { it.packageName to it.swipes }
        } else {
            null
        }
        val first = usage.firstSessionStart()
        val firstWeek = first?.let {
            val firstDay = localDate(it, zone)
            usage.appTotals(TimeRange(startOfDay(firstDay, zone), startOfDay(firstDay.plusDays(7), zone)), now).values.sum()
        }
        val history = TimeRange(
            startOfDay(today.minusDays(InsightFactsBuilder.HISTORY_DAYS.toLong()), zone),
            startOfDay(today.plusDays(1), zone),
        )
        val packages = tracked.entries.map { it.packageName } + periodUsage.apps.map { it.packageName }
        val storedLabels = tracked.entries.associate { it.packageName to it.label }
        val limitMap = limits.observeLimits().first()
        return InsightFactsBuilder.build(
            InsightInputs(
                now = now,
                zone = zone,
                period = period,
                periodUsage = periodUsage,
                periodSwipes = periodSwipes,
                countedApps = tracked.active.filter { tracked.countsSwipes(it) }.toSet(),
                countingSince = countingSince,
                sessions = usage.sessionsOverlapping(history),
                firstSessionStart = first,
                allTimeMillis = usage.appTotals(TimeRange(0L, Long.MAX_VALUE), now).values.sum(),
                firstWeekMillis = firstWeek,
                labels = packages.distinct().associateWith { installedApps.label(it) ?: storedLabels[it] ?: it },
                goalMinutes = goals.dailyGoalMinutes.first(),
                dailyLimitMinutes = tracked.active.mapNotNull { packageName ->
                    limitMap[packageName]?.settings?.dailyLimitMinutes?.let { packageName to it }
                }.toMap(),
                activeApps = tracked.active,
                swipeLengthMeters = swipeLength.swipeLengthMeters(),
                age = age,
            ),
        )
    }

    private companion object {
        const val DAYS_PER_WEEK = 7
    }
}
