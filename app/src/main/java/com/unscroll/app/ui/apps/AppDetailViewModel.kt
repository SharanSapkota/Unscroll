package com.unscroll.app.ui.apps

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unscroll.app.data.blocking.LimitRepository
import com.unscroll.app.data.friction.FrictionRepository
import com.unscroll.app.data.overlay.OverlayPreferences
import com.unscroll.app.data.plus.TrackedAppsRepository
import com.unscroll.app.data.scroll.ScrollCountingRepository
import com.unscroll.app.data.section.SectionBlockingRepository
import com.unscroll.app.domain.apps.TrackedAppStatus
import com.unscroll.app.domain.blocking.AppLimit
import com.unscroll.app.domain.blocking.BlockDecision
import com.unscroll.app.domain.blocking.BlockEvaluator
import com.unscroll.app.domain.blocking.LimitSettings
import com.unscroll.app.domain.blocking.SwipeLimitRules
import com.unscroll.app.domain.blocking.SwipeLimitScope
import com.unscroll.app.domain.friction.FrictionSettings
import com.unscroll.app.domain.insights.DayUsage
import com.unscroll.app.domain.insights.TimeRange
import com.unscroll.app.domain.insights.UsageDataSource
import com.unscroll.app.domain.insights.UsageMath
import com.unscroll.app.domain.insights.localDate
import com.unscroll.app.domain.insights.startOfDay
import com.unscroll.app.domain.overlay.OverlaySettings
import com.unscroll.app.domain.scroll.ScrollConsent
import com.unscroll.app.domain.scroll.ScrollCountingStatus
import com.unscroll.app.domain.section.BlockedSection
import com.unscroll.app.domain.section.SectionBlockMode
import com.unscroll.app.domain.time.Clock
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.DayOfWeek
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppDetailUiState(
    val packageName: String,
    val isLoading: Boolean = true,
    val settings: LimitSettings = LimitSettings.NONE,
    val decision: BlockDecision = BlockDecision.Allowed(remainingMillis = null),
    val todayMillis: Long = 0,
    val friction: FrictionSettings = FrictionSettings.DEFAULT,
    val pillShown: Boolean = true,
    val swipesShown: Boolean = true,
    /** The global pill switches in Settings; when off, the per-app ones have no effect. */
    val pillEnabledGlobally: Boolean = true,
    /** Swipe limits and swipe breaks only work while the opt-in scroll counting runs. */
    val scrollCountingActive: Boolean = false,
    /** The last 7 days, oldest first; today is last. */
    val week: List<DayUsage> = emptyList(),
    val weekOpens: Int = 0,
    val weekAverageMillis: Long = 0,
    val weekLongestMillis: Long = 0,
)

/** The app's place in the tracked list: "Track this app", "Count swipes", installed or not. */
data class TrackingRowState(
    val inList: Boolean = true,
    val tracked: Boolean = true,
    val countSwipes: Boolean = true,
    val installed: Boolean = true,
)

/** Section blocking for this app (App detail › Limits), or none for apps without section rules. */
data class SectionAppState(
    val section: BlockedSection,
    /** The config has identifiers for this app; until then nothing can be detected or blocked. */
    val ready: Boolean,
    val isPlus: Boolean,
    /** The user agreed on the disclosure. */
    val consented: Boolean,
    /** The service runs (status ACTIVE). */
    val serviceOn: Boolean,
    /** The global kill switch is set. */
    val turnedOff: Boolean,
    val blocked: Boolean,
    val mode: SectionBlockMode,
)

/**
 * App detail: every setting of one app, saved the moment it changes (no Save button, no
 * cooldown), plus a few stats. [saved] emits after each change so the screen can confirm it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val limits: LimitRepository,
    private val friction: FrictionRepository,
    private val overlay: OverlayPreferences,
    private val usage: UsageDataSource,
    private val evaluator: BlockEvaluator,
    private val trackedApps: TrackedAppsRepository,
    scrollCounting: ScrollCountingRepository,
    private val sectionBlocking: SectionBlockingRepository,
    private val clock: Clock,
) : ViewModel() {

    val packageName: String = checkNotNull(savedStateHandle.get<String>(ARG_PACKAGE))

    private val _saved = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    private val ticks = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MILLIS)
        }
    }

    val uiState: StateFlow<AppDetailUiState> = combine(
        combine(limits.observeLimits(), friction.observeSettings()) { l, f -> l to f },
        overlay.settings,
        scrollCounting.isCounting,
        usage.observeChanges(),
        ticks,
    ) { (stored, frictionSettings), overlaySettings, counting, _, _ ->
        Inputs(stored[packageName] ?: AppLimit(packageName), frictionSettings[packageName], overlaySettings, counting)
    }
        .mapLatest { build(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppDetailUiState(packageName))

    /** "Track this app", "Count swipes" and whether it is installed. */
    val tracking: StateFlow<TrackingRowState> = trackedApps.trackedApps
        .map { apps ->
            apps.firstOrNull { it.packageName == packageName }?.let {
                TrackingRowState(
                    inList = true,
                    tracked = it.status == TrackedAppStatus.ACTIVE,
                    countSwipes = it.countSwipes,
                    installed = it.installed,
                )
            } ?: TrackingRowState(inList = false)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrackingRowState())

    /** Section blocking for this app; null when no app rules exist for it. */
    val section: StateFlow<SectionAppState?> = combine(
        sectionBlocking.settings,
        sectionBlocking.isPlus,
        sectionBlocking.status,
    ) { settings, plus, status ->
        sectionBlocking.detectors.rulesFor(packageName)?.let { rules ->
            SectionAppState(
                section = rules.section,
                ready = rules.isReady,
                isPlus = plus,
                consented = settings.consent == ScrollConsent.AGREED,
                serviceOn = status == ScrollCountingStatus.ACTIVE,
                turnedOff = settings.turnedOff,
                blocked = settings.isBlocked(packageName),
                mode = settings.modeFor(packageName),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setSectionBlocked(blocked: Boolean) = save { sectionBlocking.setBlocked(packageName, blocked) }

    fun setSectionMode(mode: SectionBlockMode) = save { sectionBlocking.setMode(packageName, mode) }

    fun setTracked(tracked: Boolean) = save { trackedApps.setPaused(packageName, paused = !tracked) }

    fun setCountSwipes(count: Boolean) = save { trackedApps.setCountSwipes(packageName, count) }

    /** "Remove from Unscroll": stops tracking; history and settings stay for a later re-add. */
    fun remove(onRemoved: () -> Unit) {
        viewModelScope.launch {
            trackedApps.remove(packageName)
            onRemoved()
        }
    }

    // Limits

    fun setDailyLimit(minutes: Int?) = change { it.copy(dailyLimitMinutes = minutes?.coerceIn(1, MAX_LIMIT_MINUTES)) }

    fun setSwipeLimit(swipes: Int?) =
        change { it.copy(swipeLimit = swipes?.coerceIn(SwipeLimitRules.MIN_LIMIT, SwipeLimitRules.MAX_LIMIT)) }

    fun setSwipeLimitScope(scope: SwipeLimitScope) = change { it.copy(swipeLimitScope = scope) }

    fun setSwipeSessionGap(minutes: Int) = change { it.copy(swipeSessionGapMinutes = minutes.coerceIn(1, 24 * 60)) }

    fun setSwipeAccessAllowed(allowed: Boolean) = change { it.copy(swipeAccessAllowed = allowed) }

    // Blocking

    fun setBlockedAlways(blocked: Boolean) = change { it.copy(blockedAlways = blocked) }

    fun setScheduleEnabled(enabled: Boolean) = change { it.copy(schedule = it.schedule.copy(enabled = enabled)) }

    fun toggleScheduleDay(day: DayOfWeek) = change {
        val days = it.schedule.days
        it.copy(schedule = it.schedule.copy(days = if (day in days) days - day else days + day))
    }

    fun setScheduleStart(minuteOfDay: Int) = change { it.copy(schedule = it.schedule.copy(startMinute = minuteOfDay)) }

    fun setScheduleEnd(minuteOfDay: Int) = change { it.copy(schedule = it.schedule.copy(endMinute = minuteOfDay)) }

    // Timer pill and nudges

    fun setPillShown(shown: Boolean) = save { overlay.setPillShownFor(packageName, shown) }

    fun setSwipesShown(shown: Boolean) = save { overlay.setSwipesShownFor(packageName, shown) }

    fun updateFriction(transform: (FrictionSettings) -> FrictionSettings) =
        save { friction.saveSettings(packageName, transform(friction.getSettings(packageName))) }

    fun resetFriction() = save { friction.resetToDefaults(packageName) }

    /** Limits apply at once, stronger or weaker (no cooldown). */
    private fun change(transform: (LimitSettings) -> LimitSettings) = save { limits.updateLimit(packageName, transform) }

    private fun save(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            _saved.tryEmit(Unit)
        }
    }

    private suspend fun build(inputs: Inputs): AppDetailUiState {
        val now = clock.now()
        val zone = ZoneId.systemDefault()
        val today = localDate(now, zone)
        val todayStart = startOfDay(today, zone)
        val firstDay = today.minusDays(WEEK_DAYS - 1L)
        val weekRange = TimeRange(startOfDay(firstDay, zone), startOfDay(today.plusDays(1), zone))
        val sessions = usage.sessionsOverlapping(weekRange).filter { it.packageName == packageName }
        val week = UsageMath.dailyTotals(sessions, firstDay, WEEK_DAYS, zone, now)
        val stats = usage.appSessionStats(weekRange, now).firstOrNull { it.packageName == packageName }
        val todayMillis = usage.appTotals(TimeRange(todayStart, now), now)[packageName] ?: 0L
        val settings = inputs.limit.settings
        return AppDetailUiState(
            packageName = packageName,
            isLoading = false,
            settings = settings,
            decision = evaluator.evaluate(
                settings = settings,
                usedTodayMillis = todayMillis,
                extensionUntil = limits.activeExtensionUntil(packageName, now),
                now = now,
                zone = zone,
                packageName = packageName,
            ),
            todayMillis = todayMillis,
            friction = inputs.friction ?: FrictionSettings.DEFAULT,
            pillShown = packageName !in inputs.overlay.pillHiddenFor,
            swipesShown = packageName !in inputs.overlay.swipesHiddenFor,
            pillEnabledGlobally = inputs.overlay.enabled,
            scrollCountingActive = inputs.counting,
            week = week,
            weekOpens = stats?.opens ?: 0,
            weekAverageMillis = stats?.let { if (it.opens > 0) it.totalDurationMillis / it.opens else 0L } ?: 0L,
            weekLongestMillis = stats?.longestMillis ?: 0L,
        )
    }

    private data class Inputs(
        val limit: AppLimit,
        val friction: FrictionSettings?,
        val overlay: OverlaySettings,
        val counting: Boolean,
    )

    companion object {
        const val ARG_PACKAGE = "packageName"
        private const val TICK_MILLIS = 15_000L
        private const val WEEK_DAYS = 7
        private const val MAX_LIMIT_MINUTES = 24 * 60
    }
}
