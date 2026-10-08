package com.unscroll.app.ui.fox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unscroll.app.data.blocking.LimitRepository
import com.unscroll.app.data.blocking.SwipeLimitRepository
import com.unscroll.app.data.scroll.ScrollCountingRepository
import com.unscroll.app.data.tracking.TrackingPreferences
import com.unscroll.app.domain.blocking.AppLimit
import com.unscroll.app.domain.fox.FoxAppUsage
import com.unscroll.app.domain.fox.FoxMessages
import com.unscroll.app.domain.fox.FoxMood
import com.unscroll.app.domain.fox.FoxMoodEvaluator
import com.unscroll.app.domain.insights.TimeRange
import com.unscroll.app.domain.insights.UsageDataSource
import com.unscroll.app.domain.insights.localDate
import com.unscroll.app.domain.insights.startOfDay
import com.unscroll.app.domain.plus.TrackedAppsSource
import com.unscroll.app.domain.time.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * The fox's mood from today's usage, limits, swipes and the tracking switch (FoxMoodEvaluator),
 * and which message to say next. Read-only: the fox only mirrors usage. Updates only while a
 * screen shows the fox.
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
    private val clock: Clock,
) : ViewModel() {

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

    private var lastMessage: Pair<FoxMood, Int>? = null

    /** The index of the next message for [mood] out of [count]; never the one shown just before. */
    fun nextMessage(mood: FoxMood, count: Int): Int {
        val last = lastMessage?.takeIf { it.first == mood }?.second
        return FoxMessages.nextIndex(count, last).also { lastMessage = mood to it }
    }

    private suspend fun evaluate(inputs: Inputs): FoxMood {
        if (!inputs.tracking) return FoxMoodEvaluator.evaluate(trackingEnabled = false, apps = emptyList())
        val now = clock.now()
        val zone = ZoneId.systemDefault()
        val todayStart = startOfDay(localDate(now, zone), zone)
        val today = usage.appTotals(TimeRange(todayStart, now), now)
        // Only the apps being tracked right now (any app the user added).
        val apps = trackedApps.state.first().active.map { packageName ->
            val settings = (inputs.limits[packageName] ?: AppLimit(packageName)).settings
            val swipeStatus = if (inputs.counting) swipeLimits.status(packageName, settings, now, zone) else null
            FoxAppUsage(
                usedMillis = today[packageName] ?: 0L,
                limitMillis = settings.dailyLimitMinutes?.let { it * MINUTE },
                swipes = swipeStatus?.used ?: 0,
                swipeAllowance = swipeStatus?.allowance,
            )
        }
        return FoxMoodEvaluator.evaluate(trackingEnabled = true, apps = apps)
    }

    private data class Inputs(val tracking: Boolean, val limits: Map<String, AppLimit>, val counting: Boolean)

    private companion object {
        const val REFRESH_MILLIS = 60_000L
        const val MINUTE = 60_000L
    }
}
