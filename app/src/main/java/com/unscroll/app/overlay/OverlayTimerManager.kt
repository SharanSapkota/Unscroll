package com.unscroll.app.overlay

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import com.unscroll.app.data.appearance.AppearancePreferences
import com.unscroll.app.data.blocking.LimitRepository
import com.unscroll.app.data.overlay.OverlayPreferences
import com.unscroll.app.data.overlay.PillTotalsRepository
import com.unscroll.app.data.reels.ReelsDetectionRepository
import com.unscroll.app.data.scroll.ScrollCountingRepository
import com.unscroll.app.domain.overlay.OverlaySettings
import com.unscroll.app.domain.overlay.PillPosition
import com.unscroll.app.domain.overlay.PillRules
import com.unscroll.app.domain.overlay.PillSwipes
import com.unscroll.app.domain.overlay.PillTarget
import com.unscroll.app.domain.overlay.PillToday
import com.unscroll.app.domain.overlay.PillTodayBase
import com.unscroll.app.domain.overlay.ShownTotal
import com.unscroll.app.domain.session.SessionSwipes
import com.unscroll.app.domain.time.Clock
import com.unscroll.app.service.SessionManager
import com.unscroll.app.service.SwipeLimitEnforcer
import com.unscroll.app.util.appLabel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the floating timer window. TrackingService runs [run] for as long as tracking is on, so
 * the overlay can never outlive the service: it is removed when [run] is cancelled, and when the
 * process dies the window dies with it.
 *
 * Shows the pill as soon as a tracked app is in the foreground ([SessionManager.foregroundSession])
 * and removes it as soon as the user leaves, the screen turns off, the overlay is switched off,
 * or the "Display over other apps" permission is missing or revoked.
 *
 * The pill shows today's total for the app. Its base (earlier sessions today) is read from the
 * database once per visit ([PillTotalsRepository]); the pill adds the open session's time from
 * the clock every second ([PillToday]). The last total shown per app is kept as a floor, so the
 * pill never shows less than before on the same day.
 *
 * Reels-only timer (Instagram, TikTok, [PillTarget]): while it runs for the app on screen, the pill
 * is only shown while the confirmed section is Reels, and shows today's time and swipes in Reels
 * (from the open REELS segment and the earlier ones today). Any other screen removes it at once
 * (no animation); the detector's debounce keeps it from flickering. A fallen-back app gets the
 * whole-app pill as before.
 */
@Singleton
class OverlayTimerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val preferences: OverlayPreferences,
    private val totals: PillTotalsRepository,
    private val limits: LimitRepository,
    private val messages: OverlayMessages,
    private val scrollCounting: ScrollCountingRepository,
    private val swipeLimitEnforcer: SwipeLimitEnforcer,
    private val clock: Clock,
    private val appearance: AppearancePreferences,
    private val reels: ReelsDetectionRepository,
) {
    private val windowManager: WindowManager? = context.getSystemService(WindowManager::class.java)
    private val collapsed = MutableStateFlow(false)

    // Main-thread only.
    private var window: OverlayWindow? = null
    private var scope: CoroutineScope? = null
    private var swipesShown: PillSwipes? = null
    private var base: PillTodayBase? = null
    private var baseIsReels = false
    private val shownToday = HashMap<String, ShownTotal>()

    /** What the pill follows: the whole app, Reels only, or nothing. */
    private val target = combine(
        sessionManager.foregroundSession,
        // Swipes change the segment on every swipe; they reach the pill through [watchSwipes].
        sessionManager.segment.map { it?.copy(swipes = 0) }.distinctUntilChanged(),
        reels.live,
    ) { session, segment, live ->
        PillTarget.of(session, segment, session?.let { live[it.packageName] })
    }.distinctUntilChanged()

    suspend fun run() = withContext(Dispatchers.Main.immediate) {
        try {
            coroutineScope {
                scope = this
                launch { watchSwipes() }
                combine(
                    target,
                    preferences.settings,
                    collapsed,
                    messages.message,
                    limits.observeLimits(),
                ) { following, settings, isCollapsed, message, limitMap ->
                    val packageName = following.packageName
                    // Only show a message for the app that is actually on screen.
                    val forThisApp = message?.takeIf { it.packageName == packageName }
                    val dailyLimit = packageName?.let { limitMap[it]?.settings?.dailyLimitMinutes }
                    OverlayInputs(following, settings, isCollapsed, forThisApp, dailyLimit?.let { it * MINUTE })
                }
                    .collectLatest { inputs ->
                        val following = inputs.target
                        val packageName = following.packageName
                        val show = PillRules.shouldShow(
                            foregroundPackage = packageName,
                            settings = inputs.settings,
                            canDrawOverlays = canDrawOverlays(),
                        )
                        if (following == PillTarget.None) retireBase()
                        if (!show || packageName == null) {
                            hide()
                            return@collectLatest
                        }
                        show(
                            OverlayUiState(
                                appName = context.packageManager.appLabel(packageName),
                                today = baseFor(following) ?: return@collectLatest,
                                dailyLimitMillis = inputs.dailyLimitMillis,
                                settings = inputs.settings,
                                collapsed = inputs.collapsed,
                                message = inputs.message,
                            ),
                        )
                        // Android sends no callback when the permission is revoked, so check.
                        while (window != null) {
                            delay(PERMISSION_CHECK_MILLIS)
                            if (!canDrawOverlays()) hide()
                        }
                    }
            }
        } finally {
            scope = null
            retireBase()
            hide()
        }
    }

    /**
     * Keeps the pill's swipe info current on every swipe: the count (shown if the user wants it,
     * always used for the color) and, near a swipe limit, the swipes left.
     */
    private suspend fun watchSwipes() {
        // On the Reels-only timer the open REELS segment's swipes stand in for the session's.
        val shownSwipes = combine(target, sessionManager.swipes, sessionManager.segment) { following, sessionSwipes, segment ->
            when (following) {
                is PillTarget.Reels -> (segment?.takeIf { it.id == following.segment.id } ?: following.segment)
                    .let { SessionSwipes(it.id, it.packageName, it.swipes) }
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
     * True if messages for [packageName] can be shown on its pill (overlay on for that app and
     * permitted); else use notifications.
     */
    suspend fun canShowMessages(packageName: String): Boolean =
        preferences.settings.first().showsPillFor(packageName) && canDrawOverlays()

    /** Removes the pill right away. Main thread. Safe to call when nothing is shown. */
    fun hide() {
        window?.detach()
        window = null
    }

    /** Called by TrackingService after a rotation: use the position saved for the new orientation. */
    fun onConfigurationChanged() {
        val current = window ?: return
        scope?.launch { current.reposition(preferences.position(context.screenOrientation())) }
    }

    private suspend fun show(state: OverlayUiState) {
        window?.let {
            it.update(state)
            return
        }
        val manager = windowManager ?: return
        val newWindow = OverlayWindow(
            context = context,
            windowManager = manager,
            initialState = state,
            now = clock::now,
            onTap = { collapsed.update { !it } },
            onMoved = ::savePosition,
            onMessageAction = ::onMessageAction,
            foxSettings = appearance.fox,
        )
        try {
            newWindow.updateSwipes(swipesShown)
            newWindow.attach(preferences.position(context.screenOrientation()))
            window = newWindow
        } catch (e: WindowManager.BadTokenException) {
            Log.w(TAG, "Overlay not shown: permission missing", e)
            newWindow.detach()
        } catch (e: SecurityException) {
            Log.w(TAG, "Overlay not shown: permission missing", e)
            newWindow.detach()
        }
    }

    private fun onMessageAction(action: PillAction) {
        messages.dismiss()
        if (action == PillAction.LEAVE) {
            // "Leave": straight to the home screen. Allowed from the background because our overlay
            // window is visible.
            context.startActivity(
                Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private fun savePosition(position: PillPosition) {
        val orientation = context.screenOrientation()
        scope?.launch { preferences.savePosition(orientation, position) }
    }

    /**
     * The base for [target]'s pill: reused while the same session (or Reels segment) stays on
     * screen on the same day, measured again (one query) when an app comes to the foreground, the
     * user enters Reels again, or the day changes. Null for [PillTarget.None].
     */
    private suspend fun baseFor(target: PillTarget): PillTodayBase? {
        val now = clock.now()
        val zone = ZoneId.systemDefault()
        val today = PillToday.dayStart(now, zone)
        val reelsMode = target is PillTarget.Reels
        val visitId = when (target) {
            is PillTarget.App -> target.session.id
            is PillTarget.Reels -> target.segment.id
            PillTarget.None -> return null
        }
        base?.let { if (it.sessionId == visitId && baseIsReels == reelsMode && it.dayStart == today) return it }
        retireBase()
        val floorKey = floorKey(target.packageName ?: return null, reelsMode)
        val floor = shownToday[floorKey]?.floorFor(today) ?: 0L
        val fresh = when (target) {
            is PillTarget.App -> totals.base(target.session, now, zone, floor)
            is PillTarget.Reels -> totals.reelsBase(target.segment, now, zone, floor)
            PillTarget.None -> return null
        }
        baseIsReels = reelsMode
        return fresh.also { base = it }
    }

    /** The app (or Reels) left the screen: remember the total it showed, as the floor for its next visit. */
    private fun retireBase() {
        val old = base ?: return
        shownToday[floorKey(old.packageName, baseIsReels)] = PillToday.shownAt(old, clock.now(), ZoneId.systemDefault())
        base = null
    }

    /** Whole-app and Reels totals keep separate floors. */
    private fun floorKey(packageName: String, reels: Boolean) = if (reels) "$packageName#reels" else packageName

    private fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    private companion object {
        const val TAG = "OverlayTimerManager"
        const val PERMISSION_CHECK_MILLIS = 2_000L
        const val MINUTE = 60_000L
    }
}

/** The app the pill belongs to, or null. */
private val PillTarget.packageName: String?
    get() = when (this) {
        is PillTarget.App -> session.packageName
        is PillTarget.Reels -> segment.packageName
        PillTarget.None -> null
    }

private data class OverlayInputs(
    val target: PillTarget,
    val settings: OverlaySettings,
    val collapsed: Boolean,
    val message: PillMessage?,
    val dailyLimitMillis: Long?,
)
