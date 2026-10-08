package com.unscroll.app.service

import com.unscroll.app.domain.plus.TrackedAppsState
import com.unscroll.app.domain.session.Session
import com.unscroll.app.domain.time.Clock
import com.unscroll.app.domain.tracking.TrackedApps.FACEBOOK
import com.unscroll.app.domain.tracking.TrackedApps.INSTAGRAM
import com.unscroll.app.domain.tracking.TrackedApps.TIKTOK
import com.unscroll.app.testing.FakeForegroundAppDetector
import com.unscroll.app.testing.FakeHeartbeatStore
import com.unscroll.app.testing.FakeScreenState
import com.unscroll.app.testing.FakeSessionStore
import com.unscroll.app.testing.FakeTrackedAppsSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionManagerTest {

    private val detector = FakeForegroundAppDetector()
    private val screen = FakeScreenState()
    private val store = FakeSessionStore()
    private val heartbeats = FakeHeartbeatStore()
    private val trackedApps = FakeTrackedAppsSource()

    private lateinit var manager: SessionManager
    private lateinit var trackingJob: Job

    /** Starts SessionManager.run() the way TrackingService does. Time starts at 0. */
    private fun TestScope.startTracking() {
        // The fake clock is the test scheduler's virtual time.
        val clock = Clock { testScheduler.currentTime }
        manager = SessionManager(detector, screen, store, heartbeats, trackedApps, clock, backgroundScope)
        trackingJob = backgroundScope.launch { manager.run() }
        runCurrent()
    }

    private fun TestScope.advanceTo(timeMillis: Long) {
        advanceTimeBy(timeMillis - testScheduler.currentTime)
        runCurrent()
    }

    private fun TestScope.foreground(packageName: String?, atMillis: Long) {
        advanceTo(atMillis)
        detector.app.value = packageName
        runCurrent()
    }

    private fun TestScope.screenOff(atMillis: Long) {
        advanceTo(atMillis)
        // The real AppDetector also reports no foreground app once the screen is off.
        screen.screenOn.value = false
        detector.app.value = null
        runCurrent()
    }

    private fun session(id: Long, packageName: String, start: Long, end: Long?) =
        Session(id, packageName, start, end, scrollCount = 0)

    @Test
    fun normalSession_opensOnEnter_closesAtLeaveTimeAfterDebounce() = runTest {
        startTracking()

        foreground(INSTAGRAM, atMillis = 0)
        assertEquals(INSTAGRAM, manager.currentSession.value?.packageName)
        assertEquals(listOf(session(1, INSTAGRAM, 0, null)), store.sessions)

        foreground(LAUNCHER, atMillis = 60_000)
        // Still inside the debounce window: not closed yet.
        assertEquals(INSTAGRAM, manager.currentSession.value?.packageName)
        assertEquals(listOf(session(1, INSTAGRAM, 0, null)), store.sessions)

        advanceTo(60_000 + SessionManager.DEBOUNCE_MILLIS)
        assertNull(manager.currentSession.value)
        assertEquals(listOf(session(1, INSTAGRAM, 0, 60_000)), store.sessions)
    }

    @Test
    fun untrackedApps_neverOpenSessions() = runTest {
        startTracking()

        foreground(LAUNCHER, atMillis = 0)
        foreground(BROWSER, atMillis = 5_000)
        advanceTo(60_000)

        assertNull(manager.currentSession.value)
        assertEquals(emptyList<Session>(), store.sessions)
    }

    @Test
    fun debounce_returningWithinThreeSeconds_keepsOneSession() = runTest {
        startTracking()

        foreground(INSTAGRAM, atMillis = 0)
        foreground(LAUNCHER, atMillis = 10_000)
        foreground(INSTAGRAM, atMillis = 12_000)
        advanceTo(30_000)

        assertEquals(listOf(session(1, INSTAGRAM, 0, null)), store.sessions)
        assertEquals(1L, manager.currentSession.value?.id)

        foreground(LAUNCHER, atMillis = 40_000)
        advanceTo(50_000)
        assertEquals(listOf(session(1, INSTAGRAM, 0, 40_000)), store.sessions)
    }

    @Test
    fun debounce_returningAfterThreeSeconds_startsNewSession() = runTest {
        startTracking()

        foreground(INSTAGRAM, atMillis = 0)
        foreground(LAUNCHER, atMillis = 10_000)
        foreground(INSTAGRAM, atMillis = 13_500)

        assertEquals(
            listOf(session(1, INSTAGRAM, 0, 10_000), session(2, INSTAGRAM, 13_500, null)),
            store.sessions,
        )
    }

    @Test
    fun debounce_repeatedLeaveEvents_keepFirstLeaveTime() = runTest {
        startTracking()

        foreground(INSTAGRAM, atMillis = 0)
        foreground(LAUNCHER, atMillis = 10_000)
        foreground(BROWSER, atMillis = 11_000)
        advanceTo(20_000)

        assertEquals(listOf(session(1, INSTAGRAM, 0, 10_000)), store.sessions)
    }

    @Test
    fun switchingDirectlyToAnotherTrackedApp_splitsSessions() = runTest {
        startTracking()

        foreground(INSTAGRAM, atMillis = 0)
        foreground(TIKTOK, atMillis = 10_000)

        assertEquals(
            listOf(session(1, INSTAGRAM, 0, 10_000), session(2, TIKTOK, 10_000, null)),
            store.sessions,
        )
        assertEquals(TIKTOK, manager.currentSession.value?.packageName)
    }

    @Test
    fun switchingToAnotherTrackedAppDuringDebounce_endsOldSessionWhenUserLeft() = runTest {
        startTracking()

        foreground(INSTAGRAM, atMillis = 0)
        foreground(LAUNCHER, atMillis = 10_000)
        foreground(FACEBOOK, atMillis = 11_000)

        assertEquals(
            listOf(session(1, INSTAGRAM, 0, 10_000), session(2, FACEBOOK, 11_000, null)),
            store.sessions,
        )
    }

    @Test
    fun screenOff_closesSessionImmediately() = runTest {
        startTracking()

        foreground(INSTAGRAM, atMillis = 0)
        screenOff(atMillis = 30_000)

        // No debounce wait.
        assertNull(manager.currentSession.value)
        assertEquals(listOf(session(1, INSTAGRAM, 0, 30_000)), store.sessions)
    }

    @Test
    fun screenOff_duringDebounce_endsAtLeaveTime() = runTest {
        startTracking()

        foreground(INSTAGRAM, atMillis = 0)
        foreground(LAUNCHER, atMillis = 10_000)
        screenOff(atMillis = 11_000)

        assertEquals(listOf(session(1, INSTAGRAM, 0, 10_000)), store.sessions)
    }

    @Test
    fun screenOnAgainInSameApp_startsNewSession() = runTest {
        startTracking()

        foreground(INSTAGRAM, atMillis = 0)
        screenOff(atMillis = 30_000)
        advanceTo(31_000)
        screen.screenOn.value = true
        foreground(INSTAGRAM, atMillis = 31_000)

        assertEquals(
            listOf(session(1, INSTAGRAM, 0, 30_000), session(2, INSTAGRAM, 31_000, null)),
            store.sessions,
        )
    }

    @Test
    fun heartbeat_isSavedEveryFiveSecondsOnlyWhileSessionIsOpen() = runTest {
        startTracking()
        advanceTo(7_000)
        assertEquals(emptyList<Long>(), heartbeats.saved)

        foreground(INSTAGRAM, atMillis = 10_000)
        advanceTo(21_000)
        assertEquals(listOf(10_000L, 15_000L, 20_000L), heartbeats.saved)

        screenOff(atMillis = 22_000)
        advanceTo(60_000)
        assertEquals(listOf(10_000L, 15_000L, 20_000L), heartbeats.saved)
    }

    @Test
    fun orphanRecovery_closesSessionLeftOpenAtLastHeartbeat() = runTest {
        // A previous process died with a session open; its last heartbeat was at 5 s.
        store.sessions += session(1, INSTAGRAM, 1_000, null)
        heartbeats.last = 5_000

        startTracking()

        assertEquals(listOf(session(1, INSTAGRAM, 1_000, 5_000)), store.sessions)
    }

    @Test
    fun orphanRecovery_withoutHeartbeat_closesAtStartTime() = runTest {
        store.sessions += session(1, TIKTOK, 1_000, null)

        startTracking()

        assertEquals(listOf(session(1, TIKTOK, 1_000, 1_000)), store.sessions)
    }

    @Test
    fun orphanRecovery_thenTrackingContinuesNormally() = runTest {
        store.sessions += session(1, INSTAGRAM, 1_000, null)
        heartbeats.last = 5_000

        startTracking()
        foreground(FACEBOOK, atMillis = 10_000)

        assertEquals(
            listOf(session(1, INSTAGRAM, 1_000, 5_000), session(2, FACEBOOK, 10_000, null)),
            store.sessions,
        )
    }

    @Test
    fun orphanRecovery_leavesThisProcessesOpenSessionAlone() = runTest {
        startTracking()
        foreground(INSTAGRAM, atMillis = 0)

        assertEquals(0, manager.recoverOrphanedSessions())
        assertEquals(listOf(session(1, INSTAGRAM, 0, null)), store.sessions)
    }

    @Test
    fun stoppingTracking_closesOpenSession() = runTest {
        startTracking()
        foreground(INSTAGRAM, atMillis = 0)

        advanceTo(45_000)
        trackingJob.cancel()
        runCurrent()

        assertNull(manager.currentSession.value)
        assertEquals(listOf(session(1, INSTAGRAM, 0, 45_000)), store.sessions)
    }

    @Test
    fun foregroundSession_clearsImmediatelyOnLeave_andReturnsWithinDebounce() = runTest {
        startTracking()

        foreground(INSTAGRAM, atMillis = 0)
        assertEquals(INSTAGRAM, manager.foregroundSession.value?.packageName)

        foreground(LAUNCHER, atMillis = 10_000)
        // The session is still open (debounce), but nothing tracked is on screen.
        assertNull(manager.foregroundSession.value)
        assertEquals(1L, manager.currentSession.value?.id)

        foreground(INSTAGRAM, atMillis = 11_000)
        assertEquals(1L, manager.foregroundSession.value?.id)
    }

    @Test
    fun foregroundSession_followsDirectSwitch_andClearsOnScreenOffAndStop() = runTest {
        startTracking()

        foreground(INSTAGRAM, atMillis = 0)
        foreground(TIKTOK, atMillis = 5_000)
        assertEquals(TIKTOK, manager.foregroundSession.value?.packageName)

        screenOff(atMillis = 8_000)
        assertNull(manager.foregroundSession.value)

        screen.screenOn.value = true
        foreground(FACEBOOK, atMillis = 9_000)
        assertEquals(FACEBOOK, manager.foregroundSession.value?.packageName)

        trackingJob.cancel()
        runCurrent()
        assertNull(manager.foregroundSession.value)
    }

    @Test
    fun foregroundSession_staysNullAfterDebounceCloses() = runTest {
        startTracking()

        foreground(INSTAGRAM, atMillis = 0)
        foreground(LAUNCHER, atMillis = 10_000)
        advanceTo(20_000)

        assertNull(manager.foregroundSession.value)
        assertNull(manager.currentSession.value)
    }

    // --- Swipes from the accessibility service (M7) -------------------------------------------

    private fun TestScope.swipe(packageName: String): Boolean {
        var counted = false
        backgroundScope.launch { counted = manager.onSwipe(packageName) }
        runCurrent()
        return counted
    }

    @Test
    fun swipes_areCountedOnTheForegroundSession_andSaved() = runTest {
        startTracking()
        foreground(INSTAGRAM, atMillis = 0)
        assertEquals(0, manager.swipes.value?.count)

        repeat(3) { assertEquals(true, swipe(INSTAGRAM)) }

        assertEquals(3, manager.swipes.value?.count)
        assertEquals(1L, manager.swipes.value?.sessionId)
        assertEquals(3, store.sessions.single().scrollCount)
    }

    @Test
    fun swipes_withoutASession_orFromAnotherApp_areIgnored() = runTest {
        startTracking()
        assertEquals(false, swipe(INSTAGRAM))
        assertNull(manager.swipes.value)

        foreground(INSTAGRAM, atMillis = 0)
        assertEquals(false, swipe(FACEBOOK))
        assertEquals(0, store.sessions.single().scrollCount)
    }

    @Test
    fun swipes_duringTheDebounceWindow_areIgnored_butTheCountSurvivesAQuickReturn() = runTest {
        startTracking()
        foreground(INSTAGRAM, atMillis = 0)
        swipe(INSTAGRAM)
        swipe(INSTAGRAM)

        foreground(LAUNCHER, atMillis = 10_000)
        assertEquals(false, swipe(INSTAGRAM))

        foreground(INSTAGRAM, atMillis = 11_000)
        swipe(INSTAGRAM)
        assertEquals(3, manager.swipes.value?.count)
        assertEquals(3, store.sessions.single().scrollCount)
    }

    @Test
    fun newSession_startsCountingFromZero_andClosedSessionsKeepTheirCount() = runTest {
        startTracking()
        foreground(INSTAGRAM, atMillis = 0)
        swipe(INSTAGRAM)
        swipe(INSTAGRAM)
        foreground(TIKTOK, atMillis = 5_000)
        swipe(TIKTOK)

        assertEquals(1, manager.swipes.value?.count)
        assertEquals(listOf(2, 1), store.sessions.map { it.scrollCount })

        screenOff(atMillis = 6_000)
        assertNull(manager.swipes.value)
    }

    @Test
    fun pausedApp_freeTier_opensNoSession() = runTest {
        trackedApps.current.value = freeTier(active = INSTAGRAM)
        startTracking()

        foreground(TIKTOK, atMillis = 0)
        advanceTo(10_000)
        assertNull(manager.currentSession.value)
        assertEquals(false, swipe(TIKTOK))

        foreground(INSTAGRAM, atMillis = 20_000)
        assertEquals(listOf(session(1, INSTAGRAM, 20_000, null)), store.sessions)
    }

    @Test
    fun appPausedWhileOpen_endsItsSession_andResumingTracksItAgain() = runTest {
        startTracking()
        foreground(TIKTOK, atMillis = 0)

        // Plus ended while TikTok was on screen, and Instagram is the free app.
        advanceTo(30_000)
        trackedApps.current.value = freeTier(active = INSTAGRAM)
        runCurrent()
        assertNull(manager.currentSession.value)
        assertEquals(listOf(session(1, TIKTOK, 0, 30_000)), store.sessions)

        // Plus again: the next visit is tracked, the earlier session is kept as it was.
        trackedApps.current.value = FakeTrackedAppsSource().current.value
        foreground(LAUNCHER, atMillis = 40_000)
        foreground(TIKTOK, atMillis = 50_000)
        assertEquals(
            listOf(session(1, TIKTOK, 0, 30_000), session(2, TIKTOK, 50_000, null)),
            store.sessions,
        )
    }

    private fun freeTier(active: String) = TrackedAppsState(
        apps = listOf(INSTAGRAM, TIKTOK, FACEBOOK),
        active = setOf(active),
        isPlus = false,
        needsPick = false,
    )

    private companion object {
        const val LAUNCHER = "com.android.launcher3"
        const val BROWSER = "com.android.chrome"
    }
}
