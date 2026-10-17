package com.unscroll.app.overlay

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.WindowManager
import com.unscroll.app.data.appearance.AppearancePreferences
import com.unscroll.app.data.blocking.LimitRepository
import com.unscroll.app.data.overlay.OverlayPreferences
import com.unscroll.app.data.overlay.PillTotalsRepository
import com.unscroll.app.data.reels.ReelsDetectionRepository
import com.unscroll.app.data.scroll.ScrollCountingRepository
import com.unscroll.app.domain.blocking.AccessExtension
import com.unscroll.app.domain.blocking.AppLimit
import com.unscroll.app.domain.blocking.BlockDecision
import com.unscroll.app.domain.blocking.BlockReason
import com.unscroll.app.domain.blocking.CoverRequests
import com.unscroll.app.domain.overlay.CoverReason
import com.unscroll.app.domain.overlay.OverlayMachine
import com.unscroll.app.domain.overlay.OverlayMachine.pill
import com.unscroll.app.domain.overlay.OverlaySettings
import com.unscroll.app.domain.overlay.OverlaySignals
import com.unscroll.app.domain.overlay.OverlayState
import com.unscroll.app.domain.overlay.OverlayTransition
import com.unscroll.app.domain.overlay.PillMode
import com.unscroll.app.domain.overlay.PillPosition
import com.unscroll.app.domain.overlay.PillRules
import com.unscroll.app.domain.overlay.PillSwipes
import com.unscroll.app.domain.overlay.PillTarget
import com.unscroll.app.domain.overlay.PillToday
import com.unscroll.app.domain.overlay.PillTodayBase
import com.unscroll.app.domain.overlay.ShownTotal
import com.unscroll.app.domain.overlay.WindowOp
import com.unscroll.app.domain.reels.ReelsOnlyPillConfig
import com.unscroll.app.domain.reels.SectionGate
import com.unscroll.app.domain.reels.TimerVisibility
import com.unscroll.app.domain.section.ReelsCapableApps
import com.unscroll.app.domain.session.SessionSwipes
import com.unscroll.app.domain.time.Clock
import com.unscroll.app.domain.tracking.ScreenStateSource
import com.unscroll.app.service.BlockEnforcer
import com.unscroll.app.service.SessionManager
import com.unscroll.app.service.SwipeLimitEnforcer
import com.unscroll.app.util.AppLog
import com.unscroll.app.util.appLabel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The single owner of everything Unscroll draws over other apps: the pill, the limit cover, the
 * pill's messages (break reminders, limit warnings) and the tint. Nothing else adds or removes
 * these windows. TrackingService runs [run] for as long as tracking is on, so no window can
 * outlive the service: everything is removed when [run] ends, and the process dying takes the
 * windows with it.
 *
 * It is one state machine ([OverlayMachine]: Hidden, PillActive, PillDimmed, PillParked,
 * CoverShowing; the transition table is documented there). Every input change (foreground app,
 * Reels state, settings, limits, cover requests, screen, permission) becomes one [OverlaySignals]
 * snapshot, the machine returns the transition and its window ops, and they are applied here, on
 * the main thread, in order, through [WindowOps]. Applying the same snapshot twice does nothing.
 *
 * Instagram and TikTok ([SectionGate]):
 * - The pill counts only Reels time and swipes. On every other tab it stays on screen, dimmed and
 *   frozen ("Always while in the app", the default) or invisible in place ("Only on Reels"). Tab
 *   changes never remove the window, they only change its mode.
 * - The cover, pill messages and the tint only appear while Reels is confirmed on screen. Leaving
 *   Reels (Back, a gesture, a tab) takes the cover down once the detector confirms it (~400 ms).
 * - Detection unavailable: the whole-app pill and no interruptions at all (fail open).
 *
 * Windows are removed only when the app leaves the foreground, the screen turns off, tracking
 * stops, the pill is switched off, or "Display over other apps" is revoked (polled every
 * [PERMISSION_CHECK_MILLIS] while a tracked app is on screen; Android sends no callback).
 */
@Singleton
class OverlayCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val preferences: OverlayPreferences,
    private val totals: PillTotalsRepository,
    private val limits: LimitRepository,
    private val messages: OverlayMessages,
    private val scrollCounting: ScrollCountingRepository,
    private val swipeLimitEnforcer: SwipeLimitEnforcer,
    private val blockEnforcer: BlockEnforcer,
    private val clock: Clock,
    private val appearance: AppearancePreferences,
    private val reels: ReelsDetectionRepository,
    private val coverRequests: CoverRequests,
    private val screenState: ScreenStateSource,
    private val cover: LimitCover,
    private val tint: TintOverlay,
) {
    /** A window context (Android 11+) for the pill; made on first use, on the main thread. */
    private val windowContext: Context by lazy { context.overlayWindowContext() }
    private val windowManager: WindowManager? by lazy { windowContext.getSystemService(WindowManager::class.java) }
    private val collapsed = MutableStateFlow(false)
    private val canDraw = MutableStateFlow(true)
    private val coverSnoozed = MutableStateFlow(false)
    private val tintFor = MutableStateFlow<String?>(null)

    private val _state = MutableStateFlow<OverlayState>(OverlayState.Hidden)

    /** The machine's current state (diagnostics). */
    val state: StateFlow<OverlayState> = _state.asStateFlow()

    // Main-thread only.
    private var window: OverlayWindow? = null
    private var scope: CoroutineScope? = null
    private var swipesShown: PillSwipes? = null
    private var base: PillTodayBase? = null
    private var baseKey: BaseKey? = null
    private val shownToday = HashMap<String, ShownTotal>()
    private var visit: String? = null
    private var snoozeJob: Job? = null

    /** The app whose cover could not be drawn during this visit (it fell back or fails open). */
    private var coverFailedFor: String? = null

    /** The app the block-screen fallback already fired for during this visit. */
    private var fallbackFiredFor: String? = null

    /** What the pill follows, and the Reels gate of the app on screen. */
    private val following = combine(
        sessionManager.foregroundSession,
        // Swipes change the segment on every swipe; they reach the pill through [watchSwipes].
        sessionManager.segment.map { it?.copy(swipes = 0) }.distinctUntilChanged(),
        reels.live,
    ) { session, segment, live ->
        val live1 = session?.let { live[it.packageName] }
        Following(
            target = PillTarget.of(session, segment, live1),
            gate = session?.let { SectionGate.of(it.packageName, live1) } ?: SectionGate.WHOLE_APP,
        )
    }.distinctUntilChanged()

    suspend fun run() = withContext(Dispatchers.Main.immediate) {
        try {
            coroutineScope {
                scope = this
                cover.onGoHome = ::goHome
                cover.onBackToChat = ::backToChat
                cover.onAccess = { state -> scope?.launch { grantAccess(state) } }
                launch { watchSwipes() }
                launch { watchPermission() }
                val environment = combine(screenState.isScreenOn, canDraw, coverSnoozed, tintFor) { screenOn, draw, snoozed, tinted ->
                    Environment(screenOn, draw, snoozed, tinted)
                }
                combine(
                    following,
                    preferences.settings,
                    combine(collapsed, messages.message) { isCollapsed, message -> isCollapsed to message },
                    combine(limits.observeLimits(), coverRequests.reasons) { limitMap, reasons -> limitMap to reasons },
                    environment,
                ) { follow, settings, (isCollapsed, message), (limitMap, reasons), env ->
                    RenderInputs(follow, settings, isCollapsed, message, limitMap, reasons, env)
                }.collectLatest { render(it) }
            }
        } finally {
            withContext(NonCancellable) { hide() }
            scope = null
        }
    }

    /** Removes every window right away. Main thread; used by TrackingService.onDestroy. Idempotent. */
    fun hide() {
        snoozeJob?.cancel()
        tint.hide()
        cover.hide()
        retireBase()
        window?.detach()
        window = null
        _state.value = OverlayState.Hidden
    }

    /** Called by TrackingService after a rotation: use the position saved for the new orientation. */
    fun onConfigurationChanged() {
        val current = window ?: return
        scope?.launch { current.reposition(preferences.position(windowContext.screenOrientation())) }
    }

    /**
     * True if a message for [packageName] can be shown on its pill right now: the pill is on for
     * it, the overlay is permitted, and interruptions are allowed (for Instagram/TikTok only while
     * Reels is on screen). Otherwise FrictionCoordinator waits (Reels apps) or uses a notification.
     */
    suspend fun canShowMessages(packageName: String): Boolean =
        preferences.settings.first().showsPillFor(packageName) && canDrawOverlays() && interruptionsAllowed(packageName)

    /** Covers, messages, the tint and the break screen may appear for [packageName] right now. */
    fun interruptionsAllowed(packageName: String): Boolean =
        SectionGate.of(packageName, reels.live.value[packageName]).allowsInterruptions

    /** FrictionCoordinator's gray tint over the limit (shown only where interruptions are allowed). */
    fun setTint(packageName: String, wanted: Boolean) {
        tintFor.update { current ->
            when {
                wanted -> packageName
                current == packageName -> null
                else -> current
            }
        }
    }

    // --- Rendering ------------------------------------------------------------------------------

    private suspend fun render(inputs: RenderInputs) {
        val target = inputs.following.target
        val gate = inputs.following.gate
        val pkg = target.packageName
        if (pkg != visit) {
            // A new visit: a cover that failed for the last one may be tried again.
            visit = pkg
            coverFailedFor = null
            fallbackFiredFor = null
        }
        if (target == PillTarget.None) retireBase()
        val env = inputs.environment
        val request = pkg?.let { inputs.reasons[it] }?.takeIf { pkg != coverFailedFor }
        val signals = OverlaySignals(
            foreground = pkg,
            screenOn = env.screenOn,
            trackingOn = true,
            canDrawOverlays = env.canDraw,
            pillEnabled = pkg != null && inputs.settings.showsPillFor(pkg),
            gate = gate,
            visibility = pkg?.let { inputs.settings.visibilityFor(it) } ?: TimerVisibility.ALWAYS,
            coverRequest = request,
            coverSnoozed = env.coverSnoozed,
        )
        // No overlay permission: a cover can't be drawn. Other apps fall back to the block screen
        // once per visit (as before); Instagram/TikTok fail open.
        if (pkg != null && request != null && !env.canDraw && env.screenOn && gate == SectionGate.WHOLE_APP) {
            fallback(pkg, request)
        }
        val transition = OverlayMachine.transition(_state.value, signals)
        val pillState = if (transition.to.hasPill()) pillUiState(inputs) else null
        val reached = applyOps(transition, signals, pillState)
        if (reached != transition.to) AppLog.i(TAG, "Overlay settled at $reached instead of ${transition.to}")
        _state.value = reached
        if (pillState != null) window?.update(pillState)
        syncTint(pkg, gate, reached, env)
    }

    /** Carries out [transition]'s ops in order. Returns the state actually reached. */
    private suspend fun applyOps(transition: OverlayTransition, signals: OverlaySignals, pillState: OverlayUiState?): OverlayState {
        var reached = transition.to
        for (op in transition.ops) {
            when (op) {
                is WindowOp.AddPill -> if (pillState == null || !addPill(pillState, op.mode)) reached = reached.withoutPill()
                is WindowOp.RetargetPill -> window?.setParked(op.mode == PillMode.PARKED)
                is WindowOp.SetPillMode -> window?.setParked(op.mode == PillMode.PARKED)
                WindowOp.RemovePill -> removePill()
                is WindowOp.AddCover, is WindowOp.UpdateCover -> {
                    val (pkg, reason) = when (op) {
                        is WindowOp.AddCover -> op.packageName to op.reason
                        is WindowOp.UpdateCover -> op.packageName to op.reason
                        else -> error("unreachable")
                    }
                    if (!cover.show(coverState(pkg, reason))) {
                        AppLog.w(TAG, "Cover not shown for $pkg ($reason)")
                        coverFailedFor = pkg
                        cover.hide()
                        if (signals.gate == SectionGate.WHOLE_APP) fallback(pkg, reason)
                        reached = OverlayMachine.target(signals.copy(coverRequest = null)).let { pillOnly ->
                            if (reached.hasPill()) pillOnly else pillOnly.withoutPill()
                        }
                    }
                }
                WindowOp.RemoveCover -> cover.hide()
            }
        }
        return reached
    }

    private suspend fun addPill(state: OverlayUiState, mode: PillMode): Boolean {
        val manager = windowManager ?: return false
        val newWindow = OverlayWindow(
            context = windowContext,
            windowManager = manager,
            initialState = state,
            now = clock::now,
            onTap = { collapsed.update { !it } },
            onMoved = ::savePosition,
            onMessageAction = ::onMessageAction,
            foxSettings = appearance.fox,
        )
        newWindow.updateSwipes(swipesShown)
        newWindow.setParked(mode == PillMode.PARKED)
        if (!newWindow.attach(preferences.position(windowContext.screenOrientation()))) {
            newWindow.detach()
            return false
        }
        window = newWindow
        return true
    }

    private fun removePill() {
        retireBase()
        window?.detach()
        window = null
    }

    /** The pill's content for the app on screen: ticking, or frozen while Reels is off screen. */
    private suspend fun pillUiState(inputs: RenderInputs): OverlayUiState? {
        val target = inputs.following.target
        val pkg = target.packageName ?: return null
        val today = baseFor(target) ?: return null
        val interruptions = inputs.following.gate.allowsInterruptions
        return OverlayUiState(
            appName = context.packageManager.appLabel(pkg),
            today = today,
            dailyLimitMillis = inputs.limits[pkg]?.settings?.dailyLimitMinutes?.let { it * MINUTE },
            settings = inputs.settings,
            collapsed = inputs.collapsed,
            // Only for the app on screen, and never off Reels in Instagram/TikTok.
            message = inputs.message?.takeIf { it.packageName == pkg && interruptions },
            frozenAt = if (target is PillTarget.ReelsPaused) today.sessionStart else null,
        )
    }

    private suspend fun coverState(packageName: String, reason: CoverReason): LimitCoverState {
        val settings = limits.getLimit(packageName).settings
        val reelsApp = ReelsOnlyPillConfig.appliesTo(packageName)
        return LimitCoverState(
            packageName = packageName,
            appName = context.packageManager.appLabel(packageName),
            reason = reason,
            swipes = swipeLimitEnforcer.foreground.value?.takeIf { it.packageName == packageName }?.status?.used ?: 0,
            todayMillis = blockEnforcer.usedTodayMillis(packageName, clock.now()),
            accessAllowed = when (reason) {
                CoverReason.SWIPE_LIMIT -> settings.swipeAccessAllowed
                CoverReason.TIME_LIMIT -> true
                CoverReason.SCHEDULE -> false
            },
            reelsApp = reelsApp,
            forYou = ReelsCapableApps.saysForYou(packageName),
        )
    }

    private fun syncTint(pkg: String?, gate: SectionGate, reached: OverlayState, env: Environment) {
        val show = pkg != null && env.tintFor == pkg && gate.allowsInterruptions && env.canDraw && env.screenOn &&
            reached !is OverlayState.CoverShowing && reached != OverlayState.Hidden
        if (show) tint.show() else tint.hide()
    }

    // --- Pill numbers ---------------------------------------------------------------------------

    /**
     * Keeps the pill's swipe info current on every swipe: the count (shown if the user wants it,
     * always used for the color) and, near a swipe limit, the swipes left.
     */
    private suspend fun watchSwipes() {
        // In Reels the open REELS segment's swipes stand in for the session's; off Reels nothing is
        // added to today's Reels swipes (frozen).
        val shownSwipes = combine(following, sessionManager.swipes, sessionManager.segment) { follow, sessionSwipes, segment ->
            when (val target = follow.target) {
                is PillTarget.Reels -> (segment?.takeIf { it.id == target.segment.id } ?: target.segment)
                    .let { SessionSwipes(it.id, it.packageName, it.swipes) }
                is PillTarget.ReelsPaused -> SessionSwipes(target.session.id, target.session.packageName, 0)
                else -> sessionSwipes
            }
        }
        combine(
            shownSwipes,
            preferences.settings,
            scrollCounting.isCounting,
            swipeLimitEnforcer.foreground,
        ) { swipes, settings, counting, limit ->
            // Null while the accessibility service isn't connected: the pill shows the fox and the
            // time only, never a "0" that just means "not counting".
            PillRules.swipes(
                counting = counting,
                session = swipes,
                showCount = swipes != null && settings.showsSwipesFor(swipes.packageName),
                remaining = limit?.takeIf { it.packageName == swipes?.packageName && it.status.showRemaining }
                    ?.status?.remaining,
            )
        }.collect { info ->
            swipesShown = info
            window?.updateSwipes(info)
        }
    }

    /**
     * The base for [target]'s pill: reused while the same session (or Reels segment, or paused
     * visit) stays on screen on the same day, measured again (one query) when an app comes to the
     * foreground, Reels starts or stops, or the day changes. Null for [PillTarget.None].
     */
    private suspend fun baseFor(target: PillTarget): PillTodayBase? {
        val now = clock.now()
        val zone = ZoneId.systemDefault()
        val today = PillToday.dayStart(now, zone)
        val pkg = target.packageName ?: return null
        val key = when (target) {
            is PillTarget.App -> BaseKey(BaseKind.APP, target.session.id, today)
            is PillTarget.Reels -> BaseKey(BaseKind.REELS, target.segment.id, today)
            is PillTarget.ReelsPaused -> BaseKey(BaseKind.PAUSED, target.session.id, today)
            PillTarget.None -> return null
        }
        if (key == baseKey) base?.let { return it }
        retireBase()
        val floor = shownToday[floorKey(pkg, key.kind != BaseKind.APP)]?.floorFor(today) ?: 0L
        val fresh = when (target) {
            is PillTarget.App -> totals.base(target.session, now, zone, floor)
            is PillTarget.Reels -> totals.reelsBase(target.segment, now, zone, floor)
            is PillTarget.ReelsPaused -> totals.reelsPaused(pkg, now, zone, floor)
            PillTarget.None -> return null
        }
        baseKey = key
        base = fresh
        return fresh
    }

    /** The app (or Reels) left the pill: remember the total it showed, as the floor for its next base today. */
    private fun retireBase() {
        val old = base ?: return
        val kind = baseKey?.kind ?: BaseKind.APP
        // A paused base is frozen at its start; it never ran on.
        val at = if (kind == BaseKind.PAUSED) old.sessionStart else clock.now()
        shownToday[floorKey(old.packageName, kind != BaseKind.APP)] = PillToday.shownAt(old, at, ZoneId.systemDefault())
        base = null
        baseKey = null
    }

    /** Whole-app and Reels totals keep separate floors. */
    private fun floorKey(packageName: String, reels: Boolean) = if (reels) "$packageName#reels" else packageName

    // --- Cover actions --------------------------------------------------------------------------

    /** "Go home": the cover goes at once, then the launcher. */
    private fun goHome() {
        snoozeCover()
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val started = try {
            context.startActivity(home)
            true
        } catch (e: RuntimeException) {
            AppLog.w(TAG, "Could not go home", e)
            false
        }
        if (!started) scrollCounting.goHomeAction?.invoke()
    }

    /**
     * "Take me back to chat" (Instagram/TikTok): the cover goes at once and Back is sent to the
     * app. If the app is still in Reels afterwards, the cover returns after the snooze.
     */
    private fun backToChat() {
        snoozeCover()
        val done = scrollCounting.goBackAction?.invoke() ?: false
        if (!done) AppLog.w(TAG, "Back action unavailable")
    }

    private fun snoozeCover() {
        coverSnoozed.value = true
        snoozeJob?.cancel()
        snoozeJob = scope?.launch {
            delay(COVER_SNOOZE_MILLIS)
            coverSnoozed.value = false
        }
    }

    /** "I need access" on the cover: +20 swipes or +5 minutes, logged; the enforcers re-check. */
    private suspend fun grantAccess(state: LimitCoverState) {
        val now = clock.now()
        when (state.reason) {
            CoverReason.SWIPE_LIMIT ->
                if (limits.getLimit(state.packageName).settings.swipeAccessAllowed) limits.grantSwipeExtension(state.packageName, now)
            CoverReason.TIME_LIMIT -> limits.grantExtension(state.packageName, now, AccessExtension.MILLIS)
            CoverReason.SCHEDULE -> Unit
        }
    }

    /**
     * A cover that can't be drawn over an app that isn't Reels-capable: Home via the
     * accessibility service and the block screen, once per visit. Never for Instagram/TikTok.
     */
    private fun fallback(packageName: String, reason: CoverReason) {
        if (fallbackFiredFor == packageName) return
        fallbackFiredFor = packageName
        AppLog.i(TAG, "Limit reached without a cover; falling back to the block screen")
        scrollCounting.goHomeAction?.invoke()
        val blockReason = if (reason == CoverReason.SWIPE_LIMIT) BlockReason.SWIPE_LIMIT_REACHED else BlockReason.DAILY_LIMIT_REACHED
        blockEnforcer.showBlockScreen(packageName, BlockDecision.Blocked(blockReason, until = null))
    }

    // --- Misc -----------------------------------------------------------------------------------

    private suspend fun watchPermission() {
        sessionManager.foregroundSession.map { it != null }.distinctUntilChanged().collectLatest { inApp ->
            canDraw.value = canDrawOverlays()
            while (inApp) {
                delay(PERMISSION_CHECK_MILLIS)
                canDraw.value = canDrawOverlays()
            }
        }
    }

    private fun onMessageAction(action: PillAction) {
        messages.dismiss()
        if (action == PillAction.LEAVE) {
            // "Leave": straight to the home screen. Allowed from the background because our overlay
            // window is visible.
            try {
                context.startActivity(
                    Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (e: RuntimeException) {
                AppLog.w(TAG, "Could not go home", e)
            }
        }
    }

    private fun savePosition(position: PillPosition) {
        val orientation = windowContext.screenOrientation()
        scope?.launch { preferences.savePosition(orientation, position) }
    }

    private fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    private fun OverlayState.hasPill(): Boolean = pill() != null

    private fun OverlayState.withoutPill(): OverlayState = when (this) {
        is OverlayState.CoverShowing -> copy(pill = null)
        else -> OverlayState.Hidden
    }

    private companion object {
        const val TAG = "OverlayCoordinator"
        const val PERMISSION_CHECK_MILLIS = 2_000L
        const val MINUTE = 60_000L

        /** After "Go home" or "Take me back to chat": no cover for this long. */
        const val COVER_SNOOZE_MILLIS = 2_000L
    }
}

private data class Following(val target: PillTarget, val gate: SectionGate)

private data class Environment(
    val screenOn: Boolean,
    val canDraw: Boolean,
    val coverSnoozed: Boolean,
    val tintFor: String?,
)

private data class RenderInputs(
    val following: Following,
    val settings: OverlaySettings,
    val collapsed: Boolean,
    val message: PillMessage?,
    val limits: Map<String, AppLimit>,
    val reasons: Map<String, CoverReason>,
    val environment: Environment,
)

private enum class BaseKind { APP, REELS, PAUSED }

private data class BaseKey(val kind: BaseKind, val id: Long, val dayStart: Long)
