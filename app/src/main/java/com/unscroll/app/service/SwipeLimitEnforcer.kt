package com.unscroll.app.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import com.unscroll.app.data.blocking.LimitRepository
import com.unscroll.app.data.blocking.SwipeLimitRepository
import com.unscroll.app.data.scroll.ScrollCountingRepository
import com.unscroll.app.domain.blocking.BlockDecision
import com.unscroll.app.domain.blocking.BlockReason
import com.unscroll.app.domain.blocking.BlockSafety
import com.unscroll.app.domain.blocking.CoverAction
import com.unscroll.app.domain.blocking.LimitSettings
import com.unscroll.app.domain.blocking.SwipeCoverTracker
import com.unscroll.app.domain.blocking.SwipeLimitStatus
import com.unscroll.app.domain.insights.TimeRange
import com.unscroll.app.domain.insights.UsageDataSource
import com.unscroll.app.domain.insights.localDate
import com.unscroll.app.domain.insights.startOfDay
import com.unscroll.app.domain.plus.TrackedAppsSource
import com.unscroll.app.domain.time.Clock
import com.unscroll.app.overlay.SwipeCoverState
import com.unscroll.app.overlay.SwipeLimitCover
import com.unscroll.app.util.appLabel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The swipe-limit status of the app on screen, for the pill. */
data class ForegroundSwipeLimit(val packageName: String, val status: SwipeLimitStatus)

/**
 * The hard swipe limit. Runs inside TrackingService next to BlockEnforcer.
 *
 * Whenever a tracked app with a swipe limit is on screen (when it opens, on every swipe, and when
 * limits change) it works out the swipes in the current window. Once the limit is reached it
 * covers the app with [SwipeLimitCover], and covers it again every time the app comes back, for
 * the rest of the window (the day, or the swipe session). Without the overlay permission it falls
 * back to the block screen and the accessibility service's Home action.
 *
 * Swipes only exist while the opt-in scroll counting runs, so without it nothing is enforced and
 * everything else works as before.
 */
@Singleton
class SwipeLimitEnforcer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val limits: LimitRepository,
    private val swipeLimits: SwipeLimitRepository,
    private val scrollCounting: ScrollCountingRepository,
    private val usage: UsageDataSource,
    private val blockEnforcer: BlockEnforcer,
    private val cover: SwipeLimitCover,
    private val trackedApps: TrackedAppsSource,
    private val clock: Clock,
) {
    private val _foreground = MutableStateFlow<ForegroundSwipeLimit?>(null)

    /** The app on screen's swipe-limit status, or null (no limit, or not counting). */
    val foreground: StateFlow<ForegroundSwipeLimit?> = _foreground.asStateFlow()

    private val mutex = Mutex()
    private var scope: CoroutineScope? = null

    /** Show/hide rules for the cover; only touched under [mutex]. */
    private val tracker = SwipeCoverTracker()

    suspend fun run() = coroutineScope {
        scope = this
        withContext(Dispatchers.Main.immediate) {
            cover.onGoHome = ::goHome
            cover.onAccessGranted = { packageName -> scope?.launch { grantAccess(packageName) } }
            cover.onShowFailed = { packageName -> scope?.launch { showFailed(packageName) } }
        }
        try {
            launch { watchForeground() }
            launch { watchSwipes() }
            launch { limits.observeLimits().collect { recheckForeground() } }
            launch { scrollCounting.isCounting.collect { recheckForeground() } }
        } finally {
            scope = null
            withContext(Dispatchers.Main.immediate + NonCancellable) { cover.hide() }
            withContext(NonCancellable) { mutex.withLock { tracker.onHidden() } }
            _foreground.value = null
        }
    }

    /** Removes the cover right away. Main thread; used by TrackingService.onDestroy. */
    fun hideNow() = cover.hide()

    /**
     * A window of [packageName] just came up (accessibility event, faster than usage stats). If
     * it is over its limit, cover it right away rather than ~1 s later.
     */
    fun onAppWindow(packageName: String) {
        val running = scope ?: return
        running.launch {
            // A paused app (free tier) gets no limits; everything else keys off its sessions.
            if (!trackedApps.isActive(packageName) || !evaluate(packageName)) return@launch
            // If the app never becomes the tracked foreground app (the user left at once), take
            // the cover down again so it can't sit over the home screen.
            delay(PROVISIONAL_COVER_MILLIS)
            val foreground = sessionManager.foregroundSession.value?.packageName
            if (foreground != packageName) mutex.withLock { perform(tracker.onForeground(foreground)) }
        }
    }

    private suspend fun watchForeground() {
        sessionManager.foregroundSession
            .map { it?.packageName }
            .distinctUntilChanged()
            .collect { packageName ->
                // Left the covered app (home, Recents, another app): the cover goes with it.
                mutex.withLock { perform(tracker.onForeground(packageName)) }
                if (packageName == null) {
                    _foreground.value = null
                } else {
                    evaluate(packageName)
                }
            }
    }

    private suspend fun watchSwipes() {
        sessionManager.swipes.filterNotNull().collect { swipes ->
            if (sessionManager.foregroundSession.value?.packageName == swipes.packageName) {
                evaluate(swipes.packageName)
            }
        }
    }

    private suspend fun recheckForeground() {
        sessionManager.foregroundSession.value?.packageName?.let { evaluate(it) }
    }

    /**
     * Updates the status for [packageName] and covers it if over the limit. Returns true if it is
     * over the limit. The cover is added once per visit (see SwipeCoverTracker): more swipes or
     * window events while it is up do nothing.
     */
    private suspend fun evaluate(packageName: String): Boolean = mutex.withLock {
        val counting = scrollCounting.isCounting.value
        if (!counting || !BlockSafety.canBlock(packageName, context.packageName, homePackages())) {
            _foreground.value = null
            perform(tracker.onStatus(packageName, reached = false, canDrawOverlays = false))
            return@withLock false
        }
        val now = clock.now()
        val zone = ZoneId.systemDefault()
        val settings = limits.getLimit(packageName).settings
        val status = swipeLimits.status(packageName, settings, now, zone)
        _foreground.value = status?.let { ForegroundSwipeLimit(packageName, it) }
        val reached = status?.reached == true
        // Raised limit, extension or a new window: Hide lets the user back in.
        val action = tracker.onStatus(packageName, reached, Settings.canDrawOverlays(context))
        perform(action, settings, status?.used ?: 0, now, zone)
        reached
    }

    /** Carries out [action]. Call under [mutex]; the cover's window is added and removed on the main thread. */
    private suspend fun perform(
        action: CoverAction,
        settings: LimitSettings = LimitSettings.NONE,
        swipes: Int = 0,
        now: Long = clock.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        when (action) {
            CoverAction.None -> Unit
            CoverAction.Hide -> withContext(Dispatchers.Main.immediate) { cover.hide() }
            is CoverAction.Fallback -> fallback(action.packageName)
            is CoverAction.Show -> {
                val state = SwipeCoverState(
                    packageName = action.packageName,
                    appName = context.packageManager.appLabel(action.packageName),
                    swipes = swipes,
                    todayMillis = usedToday(action.packageName, now, zone),
                    accessAllowed = settings.swipeAccessAllowed,
                )
                val shown = withContext(Dispatchers.Main.immediate) { cover.show(state) }
                if (shown) tracker.onShown(action.packageName) else perform(tracker.onShowFailed(action.packageName))
            }
        }
    }

    /** A posted show() failed on the main thread. */
    private suspend fun showFailed(packageName: String) = mutex.withLock {
        perform(tracker.onShowFailed(packageName))
    }

    /**
     * Without the overlay permission, or when the system refused the window: Home via the
     * accessibility service, then the block screen (which can't be bypassed by switching back
     * without it opening again). Once per visit.
     */
    private fun fallback(packageName: String) {
        Log.i(TAG, "Swipe limit reached without a cover; falling back to the block screen")
        scrollCounting.goHomeAction?.invoke()
        blockEnforcer.showBlockScreen(packageName, BlockDecision.Blocked(BlockReason.SWIPE_LIMIT_REACHED, until = null))
    }

    private fun goHome() {
        cover.hide()
        scope?.launch { mutex.withLock { tracker.onHidden() } }
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val started = try {
            context.startActivity(home)
            true
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not go home", e)
            false
        }
        if (!started) scrollCounting.goHomeAction?.invoke()
    }

    /** "I need access" was tapped on the cover: +20 swipes, logged with the other extensions. */
    private suspend fun grantAccess(packageName: String) {
        val now = clock.now()
        if (!limits.getLimit(packageName).settings.swipeAccessAllowed) return
        limits.grantSwipeExtension(packageName, now)
        evaluate(packageName)
    }

    private suspend fun usedToday(packageName: String, now: Long, zone: ZoneId): Long {
        val todayStart = startOfDay(localDate(now, zone), zone)
        return usage.appTotals(TimeRange(todayStart, now), now)[packageName] ?: 0L
    }

    private fun homePackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
    }

    private companion object {
        const val TAG = "SwipeLimitEnforcer"
        const val PROVISIONAL_COVER_MILLIS = 3_000L
    }
}
