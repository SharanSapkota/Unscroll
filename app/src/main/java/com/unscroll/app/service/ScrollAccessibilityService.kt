package com.unscroll.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.unscroll.app.data.reels.ReelsDetectionRepository
import com.unscroll.app.data.scroll.ScrollCountingRepository
import com.unscroll.app.domain.plus.TrackedAppsSource
import com.unscroll.app.domain.reels.DetectorWatchdog
import com.unscroll.app.domain.reels.ReelsHysteresis
import com.unscroll.app.domain.reels.ReelsOnlyRules
import com.unscroll.app.domain.reels.WatchdogAction
import com.unscroll.app.domain.scroll.ScrollConsent
import com.unscroll.app.domain.scroll.ScrollPosition
import com.unscroll.app.domain.scroll.SwipeDebugInfo
import com.unscroll.app.domain.scroll.SwipeDebugOutcome
import com.unscroll.app.domain.scroll.SwipeDetector
import com.unscroll.app.domain.section.SectionResult
import com.unscroll.app.domain.section.SectionScreen
import com.unscroll.app.domain.time.Clock
import com.unscroll.app.util.AppLog
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Optional, opt-in scroll counting (M7). Turned on by the user in Accessibility settings, only
 * after agreeing on the in-app disclosure screen; without that agreement every event is ignored.
 *
 * Privacy: from events the service only ever looks at the type, package name, the window class
 * (window changes) and, for scroll events, the numeric position values (fromIndex/toIndex, see
 * [ScrollPosition]).
 * - Reels-only timer (Instagram and TikTok, only after the user agreed to screen checks on the
 *   updated disclosure): canRetrieveWindowContent="true" and flagReportViewIds in
 *   accessibility_service_config.xml, so it can tell Reels from other screens. It looks up only the
 *   rule set's view IDs (targeted lookups by view ID, [SectionNodeSnapshot.probe]) and
 *   reads per node only viewIdResourceName, className, isSelected and isVisibleToUser. Never text,
 *   content descriptions, hints or any other content (SectionPrivacyGuardTest). At most one check
 *   per [ReelsHysteresis.CHECK_INTERVAL_MILLIS], only while the screen is on, only for those apps.
 *   Only the verdict is kept, in memory ([ReelsDetectionRepository]).
 * - flagIncludeNotImportantViews: full-screen video pagers (Reels, TikTok, Shorts) mark their
 *   scrolling view "not important for accessibility", and Android drops such views' events for
 *   services without this flag, so swipes there were never seen. The flag changes which events
 *   arrive, not what they contain.
 * - It is limited to TYPE_VIEW_SCROLLED and TYPE_WINDOW_STATE_CHANGED events (plus window content
 *   and view-selected changes while the Reels-only timer runs for an app), and to the tracked apps: the XML config lists the default apps, and the list is replaced from
 *   TrackedAppsRepository when the service connects and every time the user adds, removes or
 *   pauses an app (serviceInfo.packageNames). Events from any other app are dropped on arrival
 *   as well, in case a device applies the list late. It never reads an event's text, content
 *   description, and never logs events (debug builds log the type, package and outcome only).
 * - Window changes of tracked apps (package name only) let the swipe limit cover an app that is
 *   already over its limit the moment it opens.
 * - Swipes go straight to [SessionManager], which owns sessions; this service never writes them
 *   itself. Nothing leaves the device.
 *
 * Robustness: every callback is wrapped so an exception can never crash the service (it is
 * logged and the event dropped); screen checks run on [Dispatchers.Default], at most one per
 * [ReelsHysteresis.CHECK_INTERVAL_MILLIS], never on the callback thread, and touch no database;
 * every node is recycled ([SectionNodeSnapshot]); a watchdog
 * ([DetectorWatchdog]) re-reads the screen when events stop and forgets a state no reading has
 * confirmed for 3 s, so a cover can't outlive the detector.
 */
@AndroidEntryPoint
class ScrollAccessibilityService : AccessibilityService() {

    @Inject lateinit var sessionManager: SessionManager

    @Inject lateinit var repository: ScrollCountingRepository

    @Inject lateinit var clock: Clock

    @Inject lateinit var swipeLimitEnforcer: SwipeLimitEnforcer

    @Inject lateinit var trackedApps: TrackedAppsSource

    @Inject lateinit var reels: ReelsDetectionRepository

    private lateinit var detector: SwipeDetector
    private var powerManager: PowerManager? = null
    private var scope: CoroutineScope? = null

    /** Cached so events can be filtered without waiting on DataStore. */
    @Volatile private var consented = false

    /** Tracked (active) apps, and those whose swipes count. Cached for the same reason. */
    @Volatile private var monitored: Set<String> = emptySet()

    @Volatile private var counted: Set<String> = emptySet()

    /** Apps whose screen is checked for the Reels-only timer (cached like [monitored]). */
    @Volatile private var reelsDetected: Set<String> = emptySet()

    /** Last window (activity) class per package (written on events, read by checks). */
    private val windowClasses = ConcurrentHashMap<String, String>()

    /** Latest screen-check request; older ones are dropped (conflated), checks are throttled. */
    private val checks = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    @Volatile private var recheck: Job? = null

    /** Debug builds: the last logged reading per package. Checks only (one at a time). */
    private val lastReelsLog = HashMap<String, SectionResult>()

    /** When each app's screen check last ran (the watchdog's input). */
    private val lastCheckAt = ConcurrentHashMap<String, Long>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        detector = SwipeDetector(clock, isCounted = { it in counted })
        powerManager = getSystemService(PowerManager::class.java)
        repository.onServiceConnected()
        repository.goHomeAction = { performGlobalAction(GLOBAL_ACTION_HOME) }
        // The limit cover's "Take me back to chat" (Instagram/TikTok).
        repository.goBackAction = { performGlobalAction(GLOBAL_ACTION_BACK) }
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = serviceScope
        serviceScope.launch {
            repository.record.map { it.consent == ScrollConsent.AGREED }.collect { consented = it }
        }
        serviceScope.launch { repository.turnOffRequests.collect { disableSelf() } }
        reels.refresh()
        serviceScope.launch {
            // The tracked list lives in TrackedAppsRepository; follow every change. Screen checks
            // follow the Reels-only timer's switches (consent, per-app toggle, version).
            combine(trackedApps.state, reels.live.map { reels.detectedPackages() }.distinctUntilChanged()) { state, detected ->
                state to detected
            }.collect { (state, detected) ->
                monitored = state.active
                counted = state.active.filterTo(mutableSetOf(), state::countsSwipes)
                reelsDetected = detected intersect state.active
                updateMonitoredPackages(state.active, withContentEvents = reelsDetected.isNotEmpty())
            }
        }
        // Off the main thread: lookups are binder calls into the app, never on the UI path.
        serviceScope.launch(Dispatchers.Default) {
            checks.collect { pkg ->
                checkScreenSafely(pkg)
                delay(ReelsHysteresis.CHECK_INTERVAL_MILLIS)
            }
        }
        serviceScope.launch(Dispatchers.Default) { watchdog() }
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
    private fun updateMonitoredPackages(packages: Set<String>, withContentEvents: Boolean) {
        val info = serviceInfo ?: return
        info.packageNames = packages.ifEmpty { setOf(packageName) }.toTypedArray()
        // Content and selection events only while some app's screen is checked (Reels-only timer).
        info.eventTypes = if (withContentEvents) BASE_EVENTS or CONTENT_EVENTS else BASE_EVENTS
        // Also set in the XML; kept here so an OEM that rebuilds the info can't drop it.
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        try {
            serviceInfo = info
        } catch (e: RuntimeException) {
            AppLog.w(TAG, "Could not update the monitored apps; filtering events instead", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !::detector.isInitialized) return
        try {
            handleEvent(event)
        } catch (e: RuntimeException) {
            // Never let one bad event take the service down.
            AppLog.w(TAG, "Event dropped", e)
        }
    }

    private fun handleEvent(event: AccessibilityEvent) {
        // Only the type, the package name, the window class (window changes of apps on the
        // Reels-only timer) and (for scrolls) the position numbers are read.
        val packageName = event.packageName?.toString()
        val eventType = event.eventType
        if (!consented) return debug(eventType, packageName, SwipeDebugOutcome.NO_CONSENT)
        // Untracked apps are dropped at once.
        if (packageName == null || packageName !in monitored) return debug(eventType, packageName, SwipeDebugOutcome.UNTRACKED)
        if (packageName in reelsDetected) {
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                event.className?.toString()?.let { windowClasses[packageName] = it }
            }
            checks.tryEmit(packageName)
        }
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
                    newSwipe && !ReelsOnlyRules.countsSwipe(reels.live.value[packageName]) ->
                        debug(eventType, packageName, SwipeDebugOutcome.NOT_IN_REELS)
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

    private fun checkScreenSafely(pkg: String) {
        lastCheckAt[pkg] = clock.now()
        try {
            checkScreen(pkg)
        } catch (e: RuntimeException) {
            // A stale window or a binder error: no reading this time (UNKNOWN frames are ignored,
            // and the watchdog takes over if it keeps failing).
            AppLog.w(TAG, "Screen check failed for $pkg", e)
        }
    }

    /**
     * While Instagram or TikTok is on screen: re-reads the screen when no event triggered a check
     * for a while, and forgets a confirmed state that no reading has backed for
     * [DetectorWatchdog.STALE_MILLIS]. Checks are only requested, so they stay throttled.
     */
    private suspend fun watchdog() {
        while (true) {
            delay(DetectorWatchdog.TICK_MILLIS)
            val pkg = sessionManager.foregroundSession.value?.packageName ?: continue
            if (pkg !in reelsDetected || powerManager?.isInteractive == false) continue
            val (lastReading, confirmed) = reels.watchdogInputs(pkg)
            when (DetectorWatchdog.action(lastCheckAt[pkg], lastReading, confirmed, clock.now())) {
                WatchdogAction.NONE -> Unit
                WatchdogAction.RECHECK -> checks.tryEmit(pkg)
                WatchdogAction.RESET -> {
                    AppLog.i(TAG, "No screen reading for $pkg; state reset")
                    reels.resetStale(pkg)
                    checks.tryEmit(pkg)
                }
            }
        }
    }

    /**
     * Reads which section of [pkg] is open and reports it to [ReelsDetectionRepository]. Skipped
     * with the screen off, for apps whose screen isn't checked, and when another window is active.
     * A pending confirmation or UNKNOWN timeout schedules one more check.
     */
    private fun checkScreen(pkg: String) {
        if (powerManager?.isInteractive == false || pkg !in reelsDetected || !reels.shouldDetect(pkg)) return
        val version = reels.version(pkg)
        val sectionDetector = reels.detectors.forPackage(pkg) ?: return
        val ruleSet = reels.detectors.ruleSetFor(pkg, version) ?: return
        val root = try {
            rootInActiveWindow
        } catch (e: RuntimeException) {
            AppLog.w(TAG, "No active window", e)
            null
        }
        val rootPackage = root?.packageName?.toString()
        if (root != null && rootPackage != pkg) {
            // Another app or Unscroll's own window is active; tracking handles leaving the app.
            SectionNodeSnapshot.release(root)
            return
        }
        val tree = SectionNodeSnapshot.probe(root, pkg, ruleSet.allViewIds)
        SectionNodeSnapshot.release(root)
        val result = sectionDetector.explain(SectionScreen(pkg, windowClasses[pkg], tree), version)
        val again = reels.observe(pkg, result, clock.now())
        recheck?.cancel()
        recheck = again?.let { delayMillis ->
            scope?.launch {
                delay(delayMillis.coerceAtLeast(ReelsHysteresis.CHECK_INTERVAL_MILLIS))
                checks.tryEmit(pkg)
            }
        }
        // Debug builds only, and only when the reading changes (checks run several times a second).
        if (repository.debugEnabled && lastReelsLog[pkg] != result) {
            lastReelsLog[pkg] = result
            Log.d(REELS_DEBUG_TAG, "$pkg ${result.verdict} (${result.rule}) version=${version.versionCode}")
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
        const val REELS_DEBUG_TAG = "ReelsDebug"
        const val BASE_EVENTS = AccessibilityEvent.TYPE_VIEW_SCROLLED or AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        const val CONTENT_EVENTS = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or AccessibilityEvent.TYPE_VIEW_SELECTED
    }

    private fun stop() {
        recheck?.cancel()
        recheck = null
        scope?.cancel()
        scope = null
        lastCheckAt.clear()
        reels.onServiceStopped()
        repository.goHomeAction = null
        repository.goBackAction = null
        repository.onServiceDisconnected()
    }
}
