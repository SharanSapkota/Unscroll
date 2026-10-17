package com.unscroll.app.service

import com.unscroll.app.data.blocking.LimitRepository
import com.unscroll.app.data.blocking.SwipeLimitRepository
import com.unscroll.app.data.scroll.ScrollCountingRepository
import com.unscroll.app.domain.apps.ExcludedApps
import com.unscroll.app.domain.blocking.CoverRequests
import com.unscroll.app.domain.blocking.SwipeLimitStatus
import com.unscroll.app.domain.overlay.CoverReason
import com.unscroll.app.domain.plus.TrackedAppsSource
import com.unscroll.app.domain.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
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
 * limits or extensions change) it works out the swipes in the current window and reports whether
 * the limit is reached ([CoverRequests]). OverlayCoordinator owns the cover: it shows it over
 * the app (for Instagram and TikTok only while Reels is on screen), takes it down when the app
 * leaves, and falls back to the block screen when it can't be drawn.
 *
 * For Instagram and TikTok only swipes in Reels / For You are counted (SessionManager), so their
 * swipe limit is a Reels swipe limit. Swipes only exist while the opt-in scroll counting runs, so
 * without it nothing is enforced and everything else works as before.
 */
@Singleton
class SwipeLimitEnforcer @Inject constructor(
    private val excludedApps: ExcludedApps,
    private val sessionManager: SessionManager,
    private val limits: LimitRepository,
    private val swipeLimits: SwipeLimitRepository,
    private val scrollCounting: ScrollCountingRepository,
    private val trackedApps: TrackedAppsSource,
    private val coverRequests: CoverRequests,
    private val clock: Clock,
) {
    private val _foreground = MutableStateFlow<ForegroundSwipeLimit?>(null)

    /** The app on screen's swipe-limit status, or null (no limit, or not counting). */
    val foreground: StateFlow<ForegroundSwipeLimit?> = _foreground.asStateFlow()

    private val mutex = Mutex()
    private var scope: CoroutineScope? = null

    suspend fun run() {
        try {
            // The scope lives as long as run() (the launched children keep it open), so
            // onAppWindow can use it; it is cleared only once they are all cancelled.
            coroutineScope {
                scope = this
                launch { watchForeground() }
                launch { watchSwipes() }
                launch { limits.observeLimits().collect { recheckForeground() } }
                launch { limits.extensionChanges.collect { recheckForeground() } }
                launch { scrollCounting.isCounting.collect { recheckForeground() } }
            }
        } finally {
            scope = null
        }
    }

    /**
     * A window of [packageName] just came up (accessibility event, faster than usage stats): work
     * out its status now, so the cover is ready the moment the app is the tracked foreground app.
     */
    fun onAppWindow(packageName: String) {
        val running = scope ?: return
        running.launch {
            if (trackedApps.isActive(packageName)) evaluate(packageName)
        }
    }

    private suspend fun watchForeground() {
        var previous: String? = null
        try {
            sessionManager.foregroundSession
                .map { it?.packageName }
                .distinctUntilChanged()
                .collect { packageName ->
                    // Left the app: its request goes (the cover goes with the app anyway).
                    previous?.takeIf { it != packageName }?.let { coverRequests.set(it, CoverReason.SWIPE_LIMIT, holds = false) }
                    previous = packageName
                    if (packageName == null) {
                        _foreground.value = null
                    } else {
                        evaluate(packageName)
                    }
                }
        } finally {
            withContext(NonCancellable) {
                previous?.let { coverRequests.set(it, CoverReason.SWIPE_LIMIT, holds = false) }
                _foreground.value = null
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

    /** Updates the status for [packageName] and reports whether its limit is reached. */
    private suspend fun evaluate(packageName: String): Boolean = mutex.withLock {
        val counting = scrollCounting.isCounting.value
        if (!counting || excludedApps.isExcluded(packageName) || !trackedApps.countsSwipes(packageName)) {
            if (_foreground.value?.packageName == packageName) _foreground.value = null
            coverRequests.set(packageName, CoverReason.SWIPE_LIMIT, holds = false)
            return@withLock false
        }
        val now = clock.now()
        val settings = limits.getLimit(packageName).settings
        val status = swipeLimits.status(packageName, settings, now, ZoneId.systemDefault())
        // Only the app on screen feeds the pill's "N left".
        if (sessionManager.foregroundSession.value?.packageName == packageName) {
            _foreground.value = status?.let { ForegroundSwipeLimit(packageName, it) }
        }
        val reached = status?.reached == true
        // A raised limit, an extension or a new window lifts it at once.
        coverRequests.set(packageName, CoverReason.SWIPE_LIMIT, holds = reached)
        reached
    }
}
