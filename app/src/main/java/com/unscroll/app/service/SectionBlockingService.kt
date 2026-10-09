package com.unscroll.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.PowerManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.pm.PackageInfoCompat
import com.unscroll.app.data.appearance.AppearancePreferences
import com.unscroll.app.data.blocking.LimitRepository
import com.unscroll.app.data.section.SectionBlockingRepository
import com.unscroll.app.domain.apps.ExcludedApps
import com.unscroll.app.domain.insights.TimeRange
import com.unscroll.app.domain.insights.UsageDataSource
import com.unscroll.app.domain.insights.localDate
import com.unscroll.app.domain.insights.startOfDay
import com.unscroll.app.domain.plus.TrackedAppsSource
import com.unscroll.app.domain.scroll.ScrollConsent
import com.unscroll.app.domain.section.InspectorLabel
import com.unscroll.app.domain.section.SectionBlockMode
import com.unscroll.app.domain.section.SectionBlockingRules
import com.unscroll.app.domain.section.SectionCoverInputs
import com.unscroll.app.domain.section.SectionInspectorFormat
import com.unscroll.app.domain.section.SectionScreen
import com.unscroll.app.domain.section.SectionVerdict
import com.unscroll.app.domain.time.Clock
import com.unscroll.app.overlay.SectionCover
import com.unscroll.app.overlay.SectionCoverState
import com.unscroll.app.overlay.SectionInspectorButton
import dagger.hilt.android.AndroidEntryPoint
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
 * Optional, opt-in section blocking (Unscroll Plus): covers only the short-video section of an
 * app (Instagram Reels, TikTok For You, Facebook Reels, YouTube Shorts) while chat, search,
 * profiles and the rest keep working.
 *
 * A separate service from [ScrollAccessibilityService] on purpose: reading which section is open
 * needs canRetrieveWindowContent="true", which is a static flag in the service's XML. Scroll
 * counting keeps its own service with the flag off, unchanged; this one is only switched on by
 * users who agreed on the section-blocking disclosure.
 *
 * Privacy:
 * - Without consent every event is ignored, and with the kill switch, without Plus or with no
 *   app chosen the service listens to no app at all (only Unscroll's own package).
 * - It listens only to tracked apps the user blocks a section in (or, debug builds with the
 *   inspector on, every tracked app), and drops events from any other app on arrival.
 * - Per screen it reads only the event's package and window class and, through
 *   [SectionNodeSnapshot], each node's viewIdResourceName, className, isSelected and visibility.
 *   Never text, content descriptions or hints. Nothing is stored or sent; the release build
 *   logs nothing about screens.
 * - It fails open: no rules, an unknown screen or an unreadable tree never blocks anything.
 */
@AndroidEntryPoint
class SectionBlockingService : AccessibilityService() {

    @Inject lateinit var repository: SectionBlockingRepository

    @Inject lateinit var trackedApps: TrackedAppsSource

    @Inject lateinit var excludedApps: ExcludedApps

    @Inject lateinit var limits: LimitRepository

    @Inject lateinit var usage: UsageDataSource

    @Inject lateinit var sessionManager: SessionManager

    @Inject lateinit var appearance: AppearancePreferences

    @Inject lateinit var clock: Clock

    private var scope: CoroutineScope? = null
    private var cover: SectionCover? = null
    private var inspectorButton: SectionInspectorButton? = null
    private var powerManager: PowerManager? = null

    /** Cached so events can be filtered without waiting on DataStore. */
    @Volatile private var consented = false

    @Volatile private var monitored: Set<String> = emptySet()

    /** Last window (activity) class per package; main thread only. */
    private val windowClasses = mutableMapOf<String, String>()
    private val versionCodes = mutableMapOf<String, Long?>()

    /** Latest evaluation request; older ones are dropped (conflated). */
    private val requests = MutableSharedFlow<Request>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private var unreadablePolls = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        repository.onServiceConnected()
        powerManager = getSystemService(PowerManager::class.java)
        cover = SectionCover(this, appearance.fox).apply {
            onBack = ::takeMeBack
            onGoHome = ::goHome
        }
        inspectorButton = SectionInspectorButton(this).apply { onMark = ::mark }
        val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = serviceScope
        serviceScope.launch {
            repository.settings.map { it.consent == ScrollConsent.AGREED }.collect { consented = it }
        }
        serviceScope.launch {
            repository.turnOffRequests.collect {
                hideAll()
                disableSelf()
            }
        }
        serviceScope.launch {
            // Switched on in system settings after the user said no in the app: switch off again.
            if (repository.settings.first().consent == ScrollConsent.DECLINED) disableSelf()
        }
        serviceScope.launch {
            combine(repository.settings, repository.isPlus, trackedApps.state) { settings, plus, state ->
                SectionBlockingRules.monitoredPackages(
                    settings = settings,
                    isPlus = plus,
                    trackedActive = state.active,
                    rulePackages = repository.detectors.packages,
                    inspectorAllowed = repository.inspectorAllowed,
                )
            }.distinctUntilChanged().collect { packages ->
                monitored = packages
                versionCodes.clear()
                updateMonitoredPackages(packages)
                recheckCovered()
            }
        }
        serviceScope.launch {
            // Any settings change (kill switch, per-app switch, mode, Plus): re-check the cover.
            combine(repository.settings, repository.isPlus) { s, p -> s to p }.collect {
                recheckCovered()
                if (!inspectorOn()) inspectorButton?.hide()
            }
        }
        serviceScope.launch {
            // Leaving the covered app (as seen by tracking) takes the cover down.
            sessionManager.foregroundSession.map { it?.packageName }.distinctUntilChanged().collect { foreground ->
                val covered = cover?.coveredPackage
                if (covered != null && foreground != covered) cover?.hide()
            }
        }
        serviceScope.launch {
            requests.collect { request ->
                evaluate(request)
                delay(MIN_EVALUATION_GAP_MILLIS)
            }
        }
        serviceScope.launch { watchWhileShowing() }
    }

    /** Limits the events Android sends; an empty list would mean "every app", so ours stands in. */
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
        if (event == null || !consented) return
        // Only the package and, for window changes, the window class are read from the event.
        val eventPackage = event.packageName?.toString() ?: return
        if (eventPackage == packageName || eventPackage !in monitored) return
        val windowChange = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        if (windowChange) event.className?.toString()?.let { windowClasses[eventPackage] = it }
        requests.tryEmit(Request(eventPackage, windowChange))
    }

    /** Reads the screen of [request]'s app and covers or uncovers its section. */
    private suspend fun evaluate(request: Request) {
        val pkg = request.packageName
        if (!consented || pkg !in monitored) return
        val screen = readScreen(pkg) ?: return
        if (inspectorOn()) inspect(screen, request.windowChange)

        val settings = repository.settings.value
        val isPlus = repository.isPlus.value
        val detector = repository.detectors.forPackage(pkg)
        val verdict = detector?.detect(screen, versionCode(pkg)) ?: SectionVerdict.UNKNOWN
        val afterLimit = settings.modeFor(pkg) == SectionBlockMode.AFTER_LIMIT
        val inputs = SectionCoverInputs(
            settings = settings,
            isPlus = isPlus,
            packageName = pkg,
            tracked = trackedApps.isActive(pkg),
            excluded = excludedApps.isExcluded(pkg),
            limitReached = afterLimit && verdict == SectionVerdict.IN_BLOCKED_SECTION && limitReached(pkg),
            verdict = verdict,
        )
        val coverView = cover ?: return
        if (detector != null && SectionBlockingRules.shouldCover(inputs)) {
            if (!coverView.show(SectionCoverState(pkg, detector.section))) Log.w(TAG, "Section cover refused")
        } else if (coverView.coveredPackage == pkg) {
            coverView.hide()
        }
    }

    /**
     * The screen of [pkg], or null when another window is active (our own overlay, another app).
     * A root that can't be read gives a screen without a tree (UNKNOWN: never blocks).
     */
    private fun readScreen(pkg: String): SectionScreen? {
        val root = try {
            rootInActiveWindow
        } catch (e: RuntimeException) {
            null
        }
        val rootPackage = root?.packageName?.toString()
        if (rootPackage == packageName) return null
        if (rootPackage != null && rootPackage != pkg) return null
        return SectionScreen(pkg, windowClasses[pkg], SectionNodeSnapshot.capture(root))
    }

    /**
     * While the cover or the inspector button is up: re-evaluate the covered app (Back may have
     * left the section) and take the cover down when another app, the home screen or a dark
     * screen comes up. Unsure (no readable window twice in a row) means take it down: fail open.
     */
    private suspend fun watchWhileShowing() {
        while (true) {
            delay(WATCH_MILLIS)
            val covered = cover?.coveredPackage
            val inspecting = inspectorButton?.isShowing == true
            if (covered == null && !inspecting) continue
            if (powerManager?.isInteractive == false) {
                hideAll()
                continue
            }
            val rootPackage = try {
                rootInActiveWindow?.packageName?.toString()
            } catch (e: RuntimeException) {
                null
            }
            if (rootPackage == packageName) continue
            unreadablePolls = if (rootPackage == null) unreadablePolls + 1 else 0
            if (rootPackage == null && unreadablePolls < MAX_UNREADABLE_POLLS) continue
            if (covered != null) {
                if (rootPackage == covered) requests.tryEmit(Request(covered, windowChange = false)) else cover?.hide()
            }
            if (inspecting && (rootPackage == null || rootPackage !in monitored)) inspectorButton?.hide()
        }
    }

    private fun recheckCovered() {
        val covered = cover?.coveredPackage ?: return
        if (covered !in monitored) cover?.hide() else requests.tryEmit(Request(covered, windowChange = false))
    }

    /** "Take me back to chat": the cover goes, then Back for the app, which leaves the section. */
    private fun takeMeBack() {
        cover?.hide()
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    private fun goHome() {
        cover?.hide()
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private suspend fun limitReached(pkg: String): Boolean {
        val now = clock.now()
        val zone = ZoneId.systemDefault()
        val todayStart = startOfDay(localDate(now, zone), zone)
        val used = usage.appTotals(TimeRange(todayStart, now), now)[pkg] ?: 0L
        return SectionBlockingRules.limitReached(limits.getLimit(pkg).settings.dailyLimitMinutes, used)
    }

    private fun versionCode(pkg: String): Long? = versionCodes.getOrPut(pkg) {
        try {
            PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(pkg, 0))
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    // Debug-only Section Inspector

    private fun inspectorOn(): Boolean = repository.inspectorAllowed && repository.settings.value.inspector

    private val lastDump = mutableMapOf<String, List<String>>()

    private fun inspect(screen: SectionScreen, windowChange: Boolean) {
        inspectorButton?.show()
        if (!windowChange) return
        val lines = SectionInspectorFormat.lines(screen)
        if (lastDump[screen.packageName] == lines) return
        lastDump[screen.packageName] = lines
        lines.forEach { Log.i(INSPECTOR_TAG, it) }
    }

    /** "Mark this screen as": logs the label with the identifiers of the screen underneath. */
    private fun mark(label: InspectorLabel) {
        if (!inspectorOn()) return
        val rootPackage = try {
            rootInActiveWindow?.packageName?.toString()
        } catch (e: RuntimeException) {
            null
        }
        val pkg = rootPackage?.takeIf { it in monitored }
        if (pkg == null) {
            Log.i(INSPECTOR_TAG, "MARK ${label.name} (no tracked app on screen)")
            return
        }
        val screen = readScreen(pkg) ?: return
        SectionInspectorFormat.lines(screen, label).forEach { Log.i(INSPECTOR_TAG, it) }
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

    private fun hideAll() {
        cover?.hide()
        inspectorButton?.hide()
    }

    private fun stop() {
        hideAll()
        scope?.cancel()
        scope = null
        repository.onServiceDisconnected()
    }

    private data class Request(val packageName: String, val windowChange: Boolean)

    private companion object {
        const val TAG = "SectionBlocking"
        const val INSPECTOR_TAG = "SectionInspector"
        const val MIN_EVALUATION_GAP_MILLIS = 150L
        const val WATCH_MILLIS = 700L
        const val MAX_UNREADABLE_POLLS = 2
    }
}
