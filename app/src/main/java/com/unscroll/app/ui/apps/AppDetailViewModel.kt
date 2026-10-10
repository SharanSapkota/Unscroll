package com.unscroll.app.ui.apps

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unscroll.app.data.apps.InstalledApps
import com.unscroll.app.data.blocking.LimitRepository
import com.unscroll.app.data.friction.FrictionRepository
import com.unscroll.app.data.overlay.OverlayPreferences
import com.unscroll.app.data.plus.TrackedAppsRepository
import com.unscroll.app.data.scroll.ScrollCountingRepository
import com.unscroll.app.data.section.SectionBlockingRepository
import com.unscroll.app.domain.apps.TrackedAppStatus
import com.unscroll.app.domain.blocking.AppLimit
import com.unscroll.app.domain.blocking.BlockDecision
import com.unscroll.app.domain.blocking.BlockDuration
import com.unscroll.app.domain.blocking.BlockEvaluator
import com.unscroll.app.domain.blocking.LimitSettings
import com.unscroll.app.domain.blocking.QuickBlockRules
import com.unscroll.app.domain.blocking.QuickBlockTarget
import com.unscroll.app.domain.blocking.QuickControls
import com.unscroll.app.domain.blocking.QuickControlsState
import com.unscroll.app.domain.blocking.QuickStatus
import com.unscroll.app.domain.blocking.ReelsTapAction
import com.unscroll.app.domain.blocking.SectionAccess
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
import com.unscroll.app.domain.section.ReelsCapableApps
import com.unscroll.app.domain.section.SectionBlockMode
import com.unscroll.app.domain.time.Clock
import com.unscroll.app.service.BlockExpiryScheduler
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
import kotlinx.coroutines.flow.distinctUntilChanged
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
    /** A running quick block, for the status line. */
    val quickStatus: QuickStatus? = null,
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

/** When the reels toggle covers the section (Advanced options), for Reels-capable apps only. */
data class SectionAppState(
    val section: BlockedSection,
    val mode: SectionBlockMode,
)

/**
 * App detail: every setting of one app, saved the moment it changes (no Save button, no
 * cooldown), plus a few stats. [saved] emits after each change so the screen can confirm it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AppDetailViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val limits: LimitRepository,
    private val friction: FrictionRepository,
    private val overlay: OverlayPreferences,
    private val usage: UsageDataSource,
    private val evaluator: BlockEvaluator,
    private val trackedApps: TrackedAppsRepository,
    scrollCounting: ScrollCountingRepository,
    private val sectionBlocking: SectionBlockingRepository,
    installedApps: InstalledApps,
    private val blockExpiry: BlockExpiryScheduler,
    private val clock: Clock,
) : ViewModel() {

    val packageName: String = checkNotNull(savedStateHandle.get<String>(ARG_PACKAGE))

    /** Identifiers exist for the installed version (read once per screen). */
    private val reelsRulesAvailable = sectionBlocking.detectors.isAvailable(packageName, installedApps.versionCode(packageName))

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

    /** "Always" / "After limit" for the reels toggle; null for apps that aren't Reels-capable. */
    val section: StateFlow<SectionAppState?> = sectionBlocking.settings
        .map { settings ->
            ReelsCapableApps.sectionFor(packageName)?.let { SectionAppState(it, settings.modeFor(packageName)) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val sectionAccess = combine(
        sectionBlocking.settings,
        sectionBlocking.isPlus,
        sectionBlocking.status,
    ) { settings, plus, status ->
        SectionAccess(
            rulesAvailable = reelsRulesAvailable,
            isPlus = plus,
            consented = settings.consent == ScrollConsent.AGREED,
            serviceOn = status == ScrollCountingStatus.ACTIVE,
            turnedOff = settings.turnedOff,
        )
    }

    private val secondTicks = flow {
        while (true) {
            emit(Unit)
            delay(COUNTDOWN_TICK_MILLIS)
        }
    }

    /** The quick toggles with their live countdowns (ticks every second while shown). */
    val quick: StateFlow<QuickControlsState?> = combine(
        limits.observeLimits().map { (it[packageName] ?: AppLimit(packageName)).settings },
        sectionAccess,
        secondTicks,
    ) { settings, access, _ ->
        val now = clock.now()
        // A block that just ran out: its toggle shows off already; tidy up the stored state.
        if (QuickBlockRules.clearExpired(settings, now) != settings) blockExpiry.syncAsync()
        QuickControls.build(packageName, settings, now, access)
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // "Block reels only" tapped before section blocking was set up: it turns on once the
        // disclosure was accepted and the service runs. "No thanks" drops the wish.
        // No ticker here: this only follows the setup state, also while the user is in Android settings.
        if (ReelsCapableApps.isReelsCapable(packageName)) {
            viewModelScope.launch {
                combine(sectionAccess, sectionBlocking.settings) { access, settings -> access to settings.consent }
                    .collect { (access, consent) ->
                        if (savedStateHandle.get<Boolean>(KEY_PENDING_REELS) != true) return@collect
                        when {
                            consent == ScrollConsent.DECLINED -> savedStateHandle[KEY_PENDING_REELS] = false
                            QuickControls.canCompletePendingEnable(QuickControls.availability(access)) -> {
                                savedStateHandle[KEY_PENDING_REELS] = false
                                val now = clock.now()
                                if (!limits.getLimit(packageName).settings.reelsBlocked(now)) {
                                    setQuickBlock(QuickBlockTarget.REELS, true)
                                }
                            }
                        }
                    }
            }
        }
    }

    /** A quick toggle on or off. Applies at once, no confirmation. */
    fun setQuickBlock(target: QuickBlockTarget, on: Boolean) =
        save { limits.setQuickBlock(packageName, target, on, clock.now()) }

    /** A duration chip: remembered per app and toggle; restarts the block if it is on. */
    fun setQuickDuration(target: QuickBlockTarget, duration: BlockDuration) =
        save { limits.setQuickBlockDuration(packageName, target, duration, clock.now()) }

    /**
     * The reels toggle was tapped. Toggles when it can; otherwise returns where to go first
     * (paywall, disclosure, Accessibility settings) and remembers to turn it on afterwards.
     */
    fun onReelsTapped(turnOn: Boolean): ReelsTapAction {
        val reels = quick.value?.reels ?: return ReelsTapAction.NONE
        val action = QuickControls.reelsTap(reels, turnOn)
        when (action) {
            ReelsTapAction.TOGGLE -> setQuickBlock(QuickBlockTarget.REELS, turnOn)
            ReelsTapAction.OPEN_DISCLOSURE, ReelsTapAction.OPEN_ACCESSIBILITY -> savedStateHandle[KEY_PENDING_REELS] = true
            ReelsTapAction.OPEN_PAYWALL, ReelsTapAction.NONE -> Unit
        }
        return action
    }

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
            quickStatus = QuickControls.status(packageName, settings, now),
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
        private const val KEY_PENDING_REELS = "pendingReelsEnable"
        private const val COUNTDOWN_TICK_MILLIS = 1_000L
        private const val TICK_MILLIS = 15_000L
        private const val WEEK_DAYS = 7
        private const val MAX_LIMIT_MINUTES = 24 * 60
    }
}
