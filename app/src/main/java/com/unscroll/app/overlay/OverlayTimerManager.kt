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
import com.unscroll.app.data.scroll.ScrollCountingRepository
import com.unscroll.app.domain.overlay.OverlaySettings
import com.unscroll.app.domain.overlay.PillPosition
import com.unscroll.app.domain.overlay.PillRules
import com.unscroll.app.domain.overlay.PillToday
import com.unscroll.app.domain.overlay.PillTodayBase
import com.unscroll.app.domain.overlay.ShownTotal
import com.unscroll.app.domain.session.ActiveSession
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
import kotlinx.coroutines.flow.first
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
) {
    private val windowManager: WindowManager? = context.getSystemService(WindowManager::class.java)
    private val collapsed = MutableStateFlow(false)

    // Main-thread only.
    private var window: OverlayWindow? = null
    private var scope: CoroutineScope? = null
    private var swipesShown: PillSwipes? = null
    private var base: PillTodayBase? = null
    private val shownToday = HashMap<String, ShownTotal>()

    suspend fun run() = withContext(Dispatchers.Main.immediate) {
        try {
            coroutineScope {
                scope = this
                launch { watchSwipes() }
                combine(
                    sessionManager.foregroundSession,
                    preferences.settings,
                    collapsed,
                    messages.message,
                    limits.observeLimits(),
                ) { session, settings, isCollapsed, message, limitMap ->
                    // Only show a message for the app that is actually on screen.
                    val forThisApp = message?.takeIf { it.packageName == session?.packageName }
                    val dailyLimit = session?.let { limitMap[it.packageName]?.settings?.dailyLimitMinutes }
                    OverlayInputs(session, settings, isCollapsed, forThisApp, dailyLimit?.let { it * MINUTE })
                }
                    .collectLatest { inputs ->
                        val session = inputs.session
                        val show = PillRules.shouldShow(
                            foregroundPackage = session?.packageName,
                            settings = inputs.settings,
                            canDrawOverlays = canDrawOverlays(),
                        )
                        if (session == null) retireBase()
                        if (!show || session == null) {
                            hide()
                            return@collectLatest
                        }
                        show(
                            OverlayUiState(
                                appName = context.packageManager.appLabel(session.packageName),
                                today = baseFor(session),
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
        combine(
            sessionManager.swipes,
            preferences.settings,
            scrollCounting.isCounting,
            swipeLimitEnforcer.foreground,
        ) { swipes, settings, counting, limit ->
            if (!counting || swipes == null) return@combine null
            PillSwipes(
                sessionCount = swipes.count,
                showCount = settings.showsSwipesFor(swipes.packageName),
                remaining = limit?.takeIf { it.packageName == swipes.packageName && it.status.showRemaining }
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
     * The base for [session]'s pill: reused while the same session stays on screen on the same
     * day, measured again (one query) when an app comes to the foreground or the day changes.
     */
    private suspend fun baseFor(session: ActiveSession): PillTodayBase {
        val now = clock.now()
        val zone = ZoneId.systemDefault()
        val today = PillToday.dayStart(now, zone)
        base?.let { if (it.sessionId == session.id && it.dayStart == today) return it }
        retireBase()
        val floor = shownToday[session.packageName]?.floorFor(today) ?: 0L
        return totals.base(session, now, zone, floor).also { base = it }
    }

    /** The app left the screen: remember the total it showed, as the floor for its next visit. */
    private fun retireBase() {
        val old = base ?: return
        shownToday[old.packageName] = PillToday.shownAt(old, clock.now(), ZoneId.systemDefault())
        base = null
    }

    private fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    private companion object {
        const val TAG = "OverlayTimerManager"
        const val PERMISSION_CHECK_MILLIS = 2_000L
        const val MINUTE = 60_000L
    }
}

private data class OverlayInputs(
    val session: ActiveSession?,
    val settings: OverlaySettings,
    val collapsed: Boolean,
    val message: PillMessage?,
    val dailyLimitMillis: Long?,
)
