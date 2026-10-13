package com.unscroll.app.ui.fox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unscroll.app.data.apps.InstalledApps
import com.unscroll.app.data.blocking.LimitRepository
import com.unscroll.app.data.blocking.SwipeLimitRepository
import com.unscroll.app.data.goals.GoalPreferences
import com.unscroll.app.data.insights.InsightBankRepository
import com.unscroll.app.data.scroll.ScrollCountingRepository
import com.unscroll.app.data.tracking.TrackingPreferences
import com.unscroll.app.domain.blocking.AppLimit
import com.unscroll.app.domain.fox.FoxAppUsage
import com.unscroll.app.domain.fox.FoxMood
import com.unscroll.app.domain.fox.FoxMoodEvaluator
import com.unscroll.app.domain.insights.FoxFacts
import com.unscroll.app.domain.insights.InsightEngine
import com.unscroll.app.domain.insights.TimeRange
import com.unscroll.app.domain.insights.UsageDataSource
import com.unscroll.app.domain.insights.localDate
import com.unscroll.app.domain.insights.startOfDay
import com.unscroll.app.domain.plus.TrackedAppsSource
import com.unscroll.app.domain.time.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The fox's mood from today's usage, limits, swipes and the tracking switch (FoxMoodEvaluator),
 * and what to say next: a line from the Insights bank's fox section for that mood, filled with
 * today's numbers. Read-only: the fox only mirrors usage. Updates only while a screen shows the fox.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FoxViewModel @Inject constructor(
    trackingPreferences: TrackingPreferences,
    private val limits: LimitRepository,
    private val swipeLimits: SwipeLimitRepository,
    private val usage: UsageDataSource,
    private val scrollCounting: ScrollCountingRepository,
    private val trackedApps: TrackedAppsSource,
    private val installedApps: InstalledApps,
    private val goals: GoalPreferences,
    insightBank: InsightBankRepository,
    private val clock: Clock,
) : ViewModel() {

    private var engine: InsightEngine? = null

    /** What the fox's lines can mention; updated with the mood. */
    @Volatile
    private var foxFacts = FoxFacts()

    init {
        viewModelScope.launch { engine = insightBank.engine() }
    }

    private val ticks = flow {
        while (true) {
            emit(Unit)
            delay(REFRESH_MILLIS)
        }
    }

    val mood: StateFlow<FoxMood> = combine(
        trackingPreferences.trackingEnabled,
        limits.observeLimits(),
        usage.observeChanges(),
        scrollCounting.isCounting,
        ticks,
    ) { tracking, stored, _, counting, _ -> Inputs(tracking, stored, counting) }
        .mapLatest { evaluate(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FoxMood.HAPPY)

    private var lastMessageId: String? = null

    /** The next line for [mood]; never the one shown just before. Null until the bank is loaded. */
    fun nextMessage(mood: FoxMood): String? {
        val line = engine?.foxLine(mood, foxFacts, lastMessageId, Random.Default) ?: return null
        lastMessageId = line.templateId
        return line.text
    }

    private suspend fun evaluate(inputs: Inputs): FoxMood {
        if (!inputs.tracking) {
            foxFacts = FoxFacts(trackingEnabled = false)
            return FoxMoodEvaluator.evaluate(trackingEnabled = false, apps = emptyList())
        }
        val now = clock.now()
        val zone = ZoneId.systemDefault()
        val todayStart = startOfDay(localDate(now, zone), zone)
        val today = usage.appTotals(TimeRange(todayStart, now), now)
        // Only the apps being tracked right now (any app the user added).
        val active = trackedApps.state.first().active
        val apps = active.associateWith { packageName ->
            val settings = (inputs.limits[packageName] ?: AppLimit(packageName)).settings
            val swipeStatus = if (inputs.counting) swipeLimits.status(packageName, settings, now, zone) else null
            FoxAppUsage(
                usedMillis = today[packageName] ?: 0L,
                limitMillis = settings.dailyLimitMinutes?.let { it * MINUTE },
                swipes = swipeStatus?.used ?: 0,
                swipeAllowance = swipeStatus?.allowance,
            )
        }
        val top = apps.entries.filter { it.value.usedMillis > 0 }.maxByOrNull { it.value.usedMillis }?.key
        foxFacts = FoxFacts(
            trackingEnabled = true,
            hasLimit = goals.dailyGoalMinutes.first() != null || apps.values.any { it.limitMillis != null },
            remainingMillis = apps.values.mapNotNull { app -> app.limitMillis?.let { it - app.usedMillis } }
                .filter { it > 0 }.minOrNull(),
            topAppLabel = top?.let { installedApps.label(it) ?: it },
        )
        return FoxMoodEvaluator.evaluate(trackingEnabled = true, apps = apps.values.toList())
    }

    private data class Inputs(val tracking: Boolean, val limits: Map<String, AppLimit>, val counting: Boolean)

    private companion object {
        const val REFRESH_MILLIS = 60_000L
        const val MINUTE = 60_000L
    }
}
