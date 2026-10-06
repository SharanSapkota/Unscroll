package com.unscroll.app.service

import com.unscroll.app.domain.ApplicationScope
import com.unscroll.app.domain.session.ActiveSession
import com.unscroll.app.domain.session.HeartbeatStore
import com.unscroll.app.domain.session.SessionStore
import com.unscroll.app.domain.time.Clock
import com.unscroll.app.domain.tracking.ForegroundAppDetector
import com.unscroll.app.domain.tracking.ScreenStateSource
import com.unscroll.app.domain.tracking.TrackedApps
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Turns foreground-app and screen changes into logged sessions. Pure Kotlin: Android specifics
 * live behind [ForegroundAppDetector], [ScreenStateSource], [SessionStore] and [HeartbeatStore].
 *
 * - A session opens when a tracked app comes to the foreground.
 * - Leaving the app closes it [DEBOUNCE_MILLIS] later, ending at the moment the user left. Coming
 *   back to the same app within that window keeps the same session.
 * - Switching straight to another tracked app closes the old session and opens a new one.
 * - Turning the screen off closes the session immediately.
 * - While a session is open a heartbeat is saved every [HEARTBEAT_INTERVAL_MILLIS], so a session
 *   left open by a killed process can be closed at the last known time on the next start.
 */
@Singleton
class SessionManager @Inject constructor(
    private val detector: ForegroundAppDetector,
    private val screenState: ScreenStateSource,
    private val store: SessionStore,
    private val heartbeatStore: HeartbeatStore,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val mutex = Mutex()

    private val _currentSession = MutableStateFlow<ActiveSession?>(null)

    /** The session in progress, or null. Stays set during the debounce window after leaving. */
    val currentSession: StateFlow<ActiveSession?> = _currentSession.asStateFlow()

    // All mutable state below is only touched while holding [mutex].
    private var leftAt: Long? = null
    private var pendingClose: Job? = null
    private var heartbeat: Job? = null

    /**
     * Recovers orphaned sessions, then tracks until cancelled. Cancelling closes the open session.
     * Called by TrackingService.
     */
    suspend fun run() {
        try {
            recoverOrphanedSessions()
            coroutineScope {
                launch {
                    screenState.isScreenOn.collect { isOn -> if (!isOn) onScreenOff() }
                }
                detector.foregroundApp.collect { onForegroundApp(it) }
            }
        } finally {
            withContext(NonCancellable) { endCurrentSession() }
        }
    }

    /**
     * Closes sessions left open by a previous process at the last heartbeat. Does nothing while
     * this process has a session open, so it is safe to call on every service start.
     */
    suspend fun recoverOrphanedSessions(): Int = mutex.withLock {
        if (_currentSession.value != null) return@withLock 0
        store.closeOrphanedSessions(heartbeatStore.lastHeartbeat() ?: 0L)
    }

    suspend fun onForegroundApp(packageName: String?) = mutex.withLock {
        val now = clock.now()
        val current = _currentSession.value
        val tracked = packageName?.takeIf(TrackedApps::isTracked)
        when {
            current == null -> if (tracked != null) open(tracked, now)
            // Still in, or back in, the same app: keep the session.
            tracked == current.packageName -> cancelPendingClose()
            // Switched straight to another tracked app.
            tracked != null -> {
                close(current, endTime = leftAt ?: now)
                open(tracked, now)
            }
            else -> scheduleClose(current, now)
        }
    }

    suspend fun onScreenOff() = mutex.withLock {
        val current = _currentSession.value ?: return@withLock
        close(current, endTime = leftAt ?: clock.now())
    }

    /** Closes the open session now, e.g. because tracking was turned off. */
    suspend fun endCurrentSession() = mutex.withLock {
        val current = _currentSession.value ?: return@withLock
        close(current, endTime = leftAt ?: clock.now())
    }

    private suspend fun open(packageName: String, now: Long) {
        val id = store.openSession(packageName, now)
        _currentSession.value = ActiveSession(id, packageName, now)
        heartbeat = scope.launch {
            while (isActive) {
                heartbeatStore.saveHeartbeat(clock.now())
                delay(HEARTBEAT_INTERVAL_MILLIS)
            }
        }
    }

    private fun scheduleClose(session: ActiveSession, now: Long) {
        if (pendingClose != null) return // Keep the time the user first left.
        leftAt = now
        pendingClose = scope.launch {
            delay(DEBOUNCE_MILLIS)
            mutex.withLock {
                // Clear first so close() doesn't cancel this job while it is still writing.
                pendingClose = null
                if (_currentSession.value?.id == session.id) close(session, endTime = now)
            }
        }
    }

    private fun cancelPendingClose() {
        pendingClose?.cancel()
        pendingClose = null
        leftAt = null
    }

    private suspend fun close(session: ActiveSession, endTime: Long) {
        cancelPendingClose()
        heartbeat?.cancel()
        heartbeat = null
        _currentSession.value = null
        store.closeSession(session.id, endTime)
    }

    companion object {
        const val DEBOUNCE_MILLIS = 3_000L
        const val HEARTBEAT_INTERVAL_MILLIS = 5_000L
    }
}
