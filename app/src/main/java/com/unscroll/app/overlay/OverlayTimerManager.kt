package com.unscroll.app.overlay

import android.content.Context
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import com.unscroll.app.data.overlay.OverlayPreferences
import com.unscroll.app.domain.insights.TimeRange
import com.unscroll.app.domain.insights.UsageDataSource
import com.unscroll.app.domain.insights.localDate
import com.unscroll.app.domain.insights.startOfDay
import com.unscroll.app.domain.overlay.PillPosition
import com.unscroll.app.domain.overlay.PillRules
import com.unscroll.app.domain.session.ActiveSession
import com.unscroll.app.domain.time.Clock
import com.unscroll.app.service.SessionManager
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
 */
@Singleton
class OverlayTimerManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val preferences: OverlayPreferences,
    private val usage: UsageDataSource,
    private val clock: Clock,
) {
    private val windowManager: WindowManager? = context.getSystemService(WindowManager::class.java)
    private val collapsed = MutableStateFlow(false)

    // Main-thread only.
    private var window: OverlayWindow? = null
    private var scope: CoroutineScope? = null

    suspend fun run() = withContext(Dispatchers.Main.immediate) {
        try {
            coroutineScope {
                scope = this
                combine(
                    sessionManager.foregroundSession,
                    preferences.settings,
                    collapsed,
                ) { session, settings, isCollapsed -> Triple(session, settings, isCollapsed) }
                    .collectLatest { (session, settings, isCollapsed) ->
                        val show = PillRules.shouldShow(
                            trackedAppInForeground = session != null,
                            settings = settings,
                            canDrawOverlays = canDrawOverlays(),
                        )
                        if (!show || session == null) {
                            hide()
                            return@collectLatest
                        }
                        val now = clock.now()
                        show(
                            OverlayUiState(
                                appName = context.packageManager.appLabel(session.packageName),
                                sessionStart = session.startTime,
                                settings = settings,
                                todayBaseMillis = if (settings.showTodayTotal) todayTotal(session, now) else null,
                                todayBaseTime = now,
                                collapsed = isCollapsed,
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
            hide()
        }
    }

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
        )
        try {
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

    private fun savePosition(position: PillPosition) {
        val orientation = context.screenOrientation()
        scope?.launch { preferences.savePosition(orientation, position) }
    }

    private suspend fun todayTotal(session: ActiveSession, now: Long): Long {
        val zone = ZoneId.systemDefault()
        val todayStart = startOfDay(localDate(now, zone), zone)
        return usage.appTotals(TimeRange(todayStart, now), now)[session.packageName] ?: 0L
    }

    private fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    private companion object {
        const val TAG = "OverlayTimerManager"
        const val PERMISSION_CHECK_MILLIS = 2_000L
    }
}
