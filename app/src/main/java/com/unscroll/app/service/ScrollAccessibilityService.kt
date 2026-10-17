package com.unscroll.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.unscroll.app.data.scroll.ScrollCountingRepository
import com.unscroll.app.domain.plus.TrackedAppsSource
import com.unscroll.app.domain.scroll.ScrollConsent
import com.unscroll.app.domain.scroll.ScrollPosition
import com.unscroll.app.domain.scroll.SwipeDebugInfo
import com.unscroll.app.domain.scroll.SwipeDebugOutcome
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
 * Privacy: the service only ever looks at an event's type, package name and, for scroll events,
 * the numeric position values (fromIndex/toIndex, see [ScrollPosition]).
 * - canRetrieveWindowContent is false in accessibility_service_config.xml, so Android does not
 *   even hand this service the screen's text or view hierarchy.
 * - flagIncludeNotImportantViews: full-screen video pagers (Reels, TikTok, Shorts) mark their
 *   scrolling view "not important for accessibility", and Android drops such views' events for
 *   services without this flag, so swipes there were never seen. The flag changes which events
 *   arrive, not what they contain.
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
        // Also set in the XML; kept here so an OEM that rebuilds the info can't drop it.
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        try {
            serviceInfo = info
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not update the monitored apps; filtering events instead", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !::detector.isInitialized) return
        // Only the type, the package name and (for scrolls) the position numbers are read.
        val packageName = event.packageName?.toString()
        val eventType = event.eventType
        if (!consented) return debug(eventType, packageName, SwipeDebugOutcome.NO_CONSENT)
        // Untracked apps are dropped at once.
        if (packageName == null || packageName !in monitored) return debug(eventType, packageName, SwipeDebugOutcome.UNTRACKED)
        when (eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                detector.reset()
                // A tracked app came up: cover it at once if its swipe limit is already used up.
                swipeLimitEnforcer.onAppWindow(packageName)
                debug(eventType, packageName, SwipeDebugOutcome.WINDOW)
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val screenOn = powerManager?.isInteractive ?: true
                val position = ScrollPosition(fromIndex = event.fromIndex, toIndex = event.toIndex)
                val newSwipe = detector.onScrollEvent(packageName, screenOn, position)
                when {
                    newSwipe -> scope?.launch {
                        val added = sessionManager.onSwipe(packageName)
                        debug(eventType, packageName, if (added) SwipeDebugOutcome.COUNTED else SwipeDebugOutcome.NO_SESSION)
                    }
                    packageName in counted && screenOn -> debug(eventType, packageName, SwipeDebugOutcome.SAME_SWIPE)
                    else -> debug(eventType, packageName, SwipeDebugOutcome.NOT_COUNTED)
                }
            }
        }
    }

    /** Debug builds only (the repository ignores it otherwise): the SwipeDebug line and log. */
    private fun debug(eventType: Int, packageName: String?, outcome: SwipeDebugOutcome) {
        if (!repository.debugEnabled) return
        repository.reportDebug(
            SwipeDebugInfo(
                lastEventType = AccessibilityEvent.eventTypeToString(eventType),
                packageName = packageName,
                outcome = outcome,
                sessionSwipes = sessionManager.swipes.value?.takeIf { it.packageName == packageName }?.count,
            ),
        )
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
