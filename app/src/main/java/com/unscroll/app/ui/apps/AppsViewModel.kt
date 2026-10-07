package com.unscroll.app.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unscroll.app.data.blocking.LimitRepository
import com.unscroll.app.data.friction.FrictionRepository
import com.unscroll.app.data.scroll.ScrollCountingRepository
import com.unscroll.app.domain.blocking.AppLimit
import com.unscroll.app.domain.blocking.BlockDecision
import com.unscroll.app.domain.blocking.BlockEvaluator
import com.unscroll.app.domain.blocking.LimitSettings
import com.unscroll.app.domain.blocking.SwipeLimitRules
import com.unscroll.app.domain.blocking.SwipeLimitScope
import com.unscroll.app.domain.friction.FrictionSettings
import com.unscroll.app.domain.insights.TimeRange
import com.unscroll.app.domain.insights.UsageDataSource
import com.unscroll.app.domain.insights.localDate
import com.unscroll.app.domain.insights.startOfDay
import com.unscroll.app.domain.time.Clock
import com.unscroll.app.util.InstalledTrackedApps
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppCardState(
    val packageName: String,
    val settings: LimitSettings,
    val decision: BlockDecision,
    val now: Long,
    val friction: FrictionSettings = FrictionSettings.DEFAULT,
)

data class AppsUiState(
    val isLoading: Boolean = true,
    val apps: List<AppCardState> = emptyList(),
    /** Swipe limits only work while the opt-in scroll counting runs. */
    val scrollCountingActive: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppsViewModel @Inject constructor(
    private val installedApps: InstalledTrackedApps,
    private val limits: LimitRepository,
    private val usage: UsageDataSource,
    private val evaluator: BlockEvaluator,
    private val friction: FrictionRepository,
    private val scrollCounting: ScrollCountingRepository,
    private val clock: Clock,
) : ViewModel() {

    /** Ticks once a second while the Apps tab is visible, for "min left". */
    private val ticks = flow {
        while (true) {
            emit(Unit)
            delay(1_000)
        }
    }

    val uiState: StateFlow<AppsUiState> = combine(
        limits.observeLimits(),
        friction.observeSettings(),
        ticks,
        scrollCounting.isCounting,
    ) { stored, frictionSettings, _, counting -> Triple(stored, frictionSettings, counting) }
        .mapLatest { (stored, frictionSettings, counting) ->
            build(stored, frictionSettings).copy(scrollCountingActive = counting)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState())

    fun setDailyLimit(packageName: String, minutes: Int?) =
        change(packageName) { it.copy(dailyLimitMinutes = minutes) }

    fun setBlockedAlways(packageName: String, blocked: Boolean) =
        change(packageName) { it.copy(blockedAlways = blocked) }

    fun setScheduleEnabled(packageName: String, enabled: Boolean) =
        change(packageName) { it.copy(schedule = it.schedule.copy(enabled = enabled)) }

    fun toggleScheduleDay(packageName: String, day: DayOfWeek) = change(packageName) {
        val days = it.schedule.days
        it.copy(schedule = it.schedule.copy(days = if (day in days) days - day else days + day))
    }

    fun setScheduleStart(packageName: String, minuteOfDay: Int) =
        change(packageName) { it.copy(schedule = it.schedule.copy(startMinute = minuteOfDay)) }

    fun setScheduleEnd(packageName: String, minuteOfDay: Int) =
        change(packageName) { it.copy(schedule = it.schedule.copy(endMinute = minuteOfDay)) }

    /** Off (null), a preset, or a custom number. */
    fun setSwipeLimit(packageName: String, swipes: Int?) = change(packageName) {
        it.copy(swipeLimit = swipes?.coerceIn(SwipeLimitRules.MIN_LIMIT, SwipeLimitRules.MAX_LIMIT))
    }

    fun setSwipeLimitScope(packageName: String, scope: SwipeLimitScope) =
        change(packageName) { it.copy(swipeLimitScope = scope) }

    fun setSwipeSessionGap(packageName: String, minutes: Int) =
        change(packageName) { it.copy(swipeSessionGapMinutes = minutes.coerceIn(1, 24 * 60)) }

    fun setSwipeAccessAllowed(packageName: String, allowed: Boolean) =
        change(packageName) { it.copy(swipeAccessAllowed = allowed) }

    /**
     * Writes the change straight to the stored settings: it applies at once, stronger or weaker.
     * The Apps card, BlockEnforcer and SwipeLimitEnforcer all observe the limits and update with it.
     */
    private fun change(packageName: String, transform: (LimitSettings) -> LimitSettings) {
        viewModelScope.launch { limits.updateLimit(packageName, transform) }
    }

    /** Nudges, break reminders, warnings, tint and swipe breaks apply immediately. */
    fun updateFriction(packageName: String, transform: (FrictionSettings) -> FrictionSettings) {
        viewModelScope.launch {
            friction.saveSettings(packageName, transform(friction.getSettings(packageName)))
        }
    }

    fun resetFriction(packageName: String) {
        viewModelScope.launch { friction.resetToDefaults(packageName) }
    }

    private suspend fun build(stored: Map<String, AppLimit>, frictionSettings: Map<String, FrictionSettings>): AppsUiState {
        val now = clock.now()
        val zone = ZoneId.systemDefault()
        val todayStart = startOfDay(localDate(now, zone), zone)
        val usedToday = usage.appTotals(TimeRange(todayStart, now), now)
        val cards = installedApps.packages().map { packageName ->
            val limit = stored[packageName] ?: AppLimit(packageName)
            AppCardState(
                packageName = packageName,
                settings = limit.settings,
                decision = evaluator.evaluate(
                    settings = limit.settings,
                    usedTodayMillis = usedToday[packageName] ?: 0L,
                    extensionUntil = limits.activeExtensionUntil(packageName, now),
                    now = now,
                    zone = zone,
                ),
                now = now,
                friction = frictionSettings[packageName] ?: FrictionSettings.DEFAULT,
            )
        }
        return AppsUiState(isLoading = false, apps = cards)
    }
}
