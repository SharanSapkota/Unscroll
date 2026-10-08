package com.unscroll.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.unscroll.app.data.scroll.ScrollCountingRepository
import com.unscroll.app.domain.plus.TrackedAppsSource
import com.unscroll.app.domain.scroll.ScrollConsent
import com.unscroll.app.domain.scroll.SwipeDetector
import com.unscroll.app.domain.time.Clock
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Optional, opt-in scroll counting (M7). Turned on by the user in Accessibility settings, only
 * after agreeing on the in-app disclosure screen; without that agreement every event is ignored.
 *
 * Privacy: the service only ever looks at an event's type and package name.
 * - canRetrieveWindowContent is false in accessibility_service_config.xml, so Android does not
 *   even hand this service the screen's text or view hierarchy.
 * - It is limited to TYPE_VIEW_SCROLLED and TYPE_WINDOW_STATE_CHANGED events, and to the tracked
 *   apps: the XML config lists the default apps, and the list is replaced from
 *   TrackedAppsRepository when the service connects and every time the user adds, removes or
 *   pauses an app (serviceInfo.packageNames). Events from any other app are dropped on arrival
 *   as well, in case a device applies the list late. It never reads an event's text, content
 *   description or source node, and never logs events.
 * - Window changes of tracked apps (package name only) let the swipe limit cover an app that is
 *   already over its limit the moment it opens.
 * - Swipes go straight to [SessionManager], which owns sessions; this service never writes them
 *   itself. Nothing leaves the device.
 */
@AndroidEntryPoint
class ScrollAccessibilityService : AccessibilityService() {

    @Inject lateinit var sessionManager: SessionManager

    @Inject lateinit var repository: ScrollCountingRepository

    @Inject lateinit var clock: Clock

    @Inject lateinit var swipeLimitEnforcer: SwipeLimitEnforcer

    @Inject lateinit var trackedApps: TrackedAppsSource

    private lateinit var detector: SwipeDetector
    private var powerManager: PowerManager? = null
    private var scope: CoroutineScope? = null

    /** Cached so events can be filtered without waiting on DataStore. */
    @Volatile private var consented = false

    /** Tracked (active) apps, and those whose swipes count. Cached for the same reason. */
    @Volatile private var monitored: Set<String> = emptySet()

    @Volatile private var counted: Set<String> = emptySet()

    override fun onServiceConnected() {
        super.onServiceConnected()
        detector = SwipeDetector(clock, isCounted = { it in counted })
        powerManager = getSystemService(PowerManager::class.java)
        repository.onServiceConnected()
        repository.goHomeAction = { performGlobalAction(GLOBAL_ACTION_HOME) }
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = serviceScope
        serviceScope.launch {
            repository.record.map { it.consent == ScrollConsent.AGREED }.collect { consented = it }
        }
        serviceScope.launch { repository.turnOffRequests.collect { disableSelf() } }
        serviceScope.launch {
            // The tracked list lives in TrackedAppsRepository; follow every change.
            trackedApps.state.collect { state ->
                monitored = state.active
                counted = state.active.filterTo(mutableSetOf(), state::countsSwipes)
                updateMonitoredPackages(state.active)
            }
        }
        serviceScope.launch {
            // Switched on in system settings after the user said no in the app: switch off again.
            if (repository.record.first().consent == ScrollConsent.DECLINED) disableSelf()
        }
    }

    /**
     * Limits the events Android sends to [packages]. An empty list would mean "every app" to
     * Android, so with nothing tracked only Unscroll's own package is listed. If a device refuses
     * the update, the previous list stays and [onAccessibilityEvent] drops untracked events anyway.
     */
    private fun updateMonitoredPackages(packages: Set<String>) {
        val info = serviceInfo ?: return
        info.packageNames = packages.ifEmpty { setOf(packageName) }.toTypedArray()
        try {
            serviceInfo = info
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not update the monitored apps; filtering events instead", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !consented || !::detector.isInitialized) return
        // Only the type and the package name are read. Untracked apps are dropped at once.
        val packageName = event.packageName?.toString()
        if (packageName == null || packageName !in monitored) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                detector.reset()
                // A tracked app came up: cover it at once if its swipe limit is already used up.
                swipeLimitEnforcer.onAppWindow(packageName)
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val screenOn = powerManager?.isInteractive ?: true
                if (detector.onScrollEvent(packageName, screenOn)) {
                    scope?.launch { sessionManager.onSwipe(packageName) }
                }
            }
        }
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        stop()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
    }

    private companion object {
        const val TAG = "ScrollAccessibility"
    }

    private fun stop() {
        scope?.cancel()
        scope = null
        repository.goHomeAction = null
        repository.onServiceDisconnected()
    }
}
