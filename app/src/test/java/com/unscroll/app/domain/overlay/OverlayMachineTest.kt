package com.unscroll.app.domain.overlay

import com.unscroll.app.domain.overlay.OverlayMachine.pill
import com.unscroll.app.domain.reels.ReelsHysteresis
import com.unscroll.app.domain.reels.ReelsLive
import com.unscroll.app.domain.reels.ReelsState
import com.unscroll.app.domain.reels.ReelsTimerStatus
import com.unscroll.app.domain.reels.SectionGate
import com.unscroll.app.domain.reels.TimerVisibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayMachineTest {

    private val instagram = "com.instagram.android"
    private val tiktok = "com.zhiliaoapp.musically"
    private val reddit = "com.reddit.frontpage"

    private fun signals(
        foreground: String? = instagram,
        gate: SectionGate = SectionGate.IN_REELS,
        visibility: TimerVisibility = TimerVisibility.ALWAYS,
        cover: CoverReason? = null,
        pillEnabled: Boolean = true,
        screenOn: Boolean = true,
        canDraw: Boolean = true,
        trackingOn: Boolean = true,
        snoozed: Boolean = false,
    ) = OverlaySignals(foreground, screenOn, trackingOn, canDraw, pillEnabled, gate, visibility, cover, snoozed)

    // --- Target states --------------------------------------------------------------------------

    @Test
    fun pillVisibilityModes() {
        assertEquals(OverlayState.PillActive(instagram), OverlayMachine.target(signals(gate = SectionGate.IN_REELS)))
        assertEquals(OverlayState.PillDimmed(instagram), OverlayMachine.target(signals(gate = SectionGate.OFF_REELS)))
        assertEquals(
            OverlayState.PillParked(instagram),
            OverlayMachine.target(signals(gate = SectionGate.OFF_REELS, visibility = TimerVisibility.ONLY_REELS)),
        )
        // Fail open: the whole-app pill, ticking.
        assertEquals(OverlayState.PillActive(instagram), OverlayMachine.target(signals(gate = SectionGate.DETECTION_UNAVAILABLE)))
        assertEquals(OverlayState.PillActive(reddit), OverlayMachine.target(signals(foreground = reddit, gate = SectionGate.WHOLE_APP)))
        // Visibility only matters off Reels.
        assertEquals(
            OverlayState.PillActive(instagram),
            OverlayMachine.target(signals(gate = SectionGate.IN_REELS, visibility = TimerVisibility.ONLY_REELS)),
        )
    }

    @Test
    fun coverOnlyInReels_forReelsApps() {
        assertEquals(
            OverlayState.CoverShowing(instagram, CoverReason.TIME_LIMIT, PillMode.ACTIVE),
            OverlayMachine.target(signals(gate = SectionGate.IN_REELS, cover = CoverReason.TIME_LIMIT)),
        )
        // Chat, home, search, profile, stories: never a cover, the pill stays dimmed.
        assertEquals(
            OverlayState.PillDimmed(instagram),
            OverlayMachine.target(signals(gate = SectionGate.OFF_REELS, cover = CoverReason.TIME_LIMIT)),
        )
        assertEquals(
            OverlayState.PillParked(instagram),
            OverlayMachine.target(signals(gate = SectionGate.OFF_REELS, visibility = TimerVisibility.ONLY_REELS, cover = CoverReason.SWIPE_LIMIT)),
        )
    }

    @Test
    fun failOpen_noCoverWithoutDetection() {
        assertEquals(
            OverlayState.PillActive(instagram),
            OverlayMachine.target(signals(gate = SectionGate.DETECTION_UNAVAILABLE, cover = CoverReason.SWIPE_LIMIT)),
        )
        assertEquals(OverlayState.Hidden, OverlayMachine.target(signals(gate = SectionGate.DETECTION_UNAVAILABLE, cover = CoverReason.TIME_LIMIT, pillEnabled = false)))
    }

    @Test
    fun otherApps_getTheCoverAnywhere_asBefore() {
        assertEquals(
            OverlayState.CoverShowing(reddit, CoverReason.SWIPE_LIMIT, PillMode.ACTIVE),
            OverlayMachine.target(signals(foreground = reddit, gate = SectionGate.WHOLE_APP, cover = CoverReason.SWIPE_LIMIT)),
        )
        assertEquals(
            OverlayState.CoverShowing(reddit, CoverReason.SWIPE_LIMIT, null),
            OverlayMachine.target(signals(foreground = reddit, gate = SectionGate.WHOLE_APP, cover = CoverReason.SWIPE_LIMIT, pillEnabled = false)),
        )
    }

    @Test
    fun hidden_whenLeftScreenOffTrackingStopped_orPermissionRevoked() {
        val cover = CoverReason.TIME_LIMIT
        listOf(
            signals(foreground = null, cover = cover),
            signals(screenOn = false, cover = cover),
            signals(trackingOn = false, cover = cover),
            signals(canDraw = false, cover = cover),
            signals(pillEnabled = false),
        ).forEach { assertEquals(it.toString(), OverlayState.Hidden, OverlayMachine.target(it)) }
    }

    @Test
    fun snoozedCover_afterBackToChat() {
        assertEquals(
            OverlayState.PillActive(instagram),
            OverlayMachine.target(signals(cover = CoverReason.TIME_LIMIT, snoozed = true)),
        )
    }

    // --- Transitions ----------------------------------------------------------------------------

    @Test
    fun everyTransition_hasTheDocumentedOps() {
        val hidden = OverlayState.Hidden
        val active = OverlayState.PillActive(instagram)
        val dimmed = OverlayState.PillDimmed(instagram)
        val parked = OverlayState.PillParked(instagram)
        val cover = OverlayState.CoverShowing(instagram, CoverReason.TIME_LIMIT, PillMode.ACTIVE)
        val coverNoPill = OverlayState.CoverShowing(instagram, CoverReason.TIME_LIMIT, null)
        val swipeCover = cover.copy(reason = CoverReason.SWIPE_LIMIT)
        val otherApp = OverlayState.PillActive(tiktok)

        assertEquals(listOf(WindowOp.AddPill(instagram, PillMode.ACTIVE)), OverlayMachine.ops(hidden, active))
        assertEquals(listOf(WindowOp.AddPill(instagram, PillMode.DIMMED)), OverlayMachine.ops(hidden, dimmed))
        assertEquals(listOf(WindowOp.AddPill(instagram, PillMode.PARKED)), OverlayMachine.ops(hidden, parked))
        assertEquals(
            listOf(WindowOp.AddPill(instagram, PillMode.ACTIVE), WindowOp.AddCover(instagram, CoverReason.TIME_LIMIT)),
            OverlayMachine.ops(hidden, cover),
        )
        assertEquals(listOf(WindowOp.AddCover(instagram, CoverReason.TIME_LIMIT)), OverlayMachine.ops(hidden, coverNoPill))

        // Tab changes: only the mode, never a remove/add.
        assertEquals(listOf(WindowOp.SetPillMode(PillMode.DIMMED)), OverlayMachine.ops(active, dimmed))
        assertEquals(listOf(WindowOp.SetPillMode(PillMode.ACTIVE)), OverlayMachine.ops(dimmed, active))
        assertEquals(listOf(WindowOp.SetPillMode(PillMode.PARKED)), OverlayMachine.ops(active, parked))
        assertEquals(listOf(WindowOp.SetPillMode(PillMode.ACTIVE)), OverlayMachine.ops(parked, active))
        assertEquals(listOf(WindowOp.SetPillMode(PillMode.PARKED)), OverlayMachine.ops(dimmed, parked))

        // Direct app switch: the same window follows the new app.
        assertEquals(listOf(WindowOp.RetargetPill(tiktok, PillMode.ACTIVE)), OverlayMachine.ops(active, otherApp))

        assertEquals(listOf(WindowOp.AddCover(instagram, CoverReason.TIME_LIMIT)), OverlayMachine.ops(active, cover))
        assertEquals(
            listOf(WindowOp.SetPillMode(PillMode.ACTIVE), WindowOp.AddCover(instagram, CoverReason.TIME_LIMIT)),
            OverlayMachine.ops(dimmed, cover),
        )
        assertEquals(listOf(WindowOp.RemoveCover, WindowOp.SetPillMode(PillMode.DIMMED)), OverlayMachine.ops(cover, dimmed))
        assertEquals(listOf(WindowOp.RemoveCover), OverlayMachine.ops(cover, active))
        assertEquals(listOf(WindowOp.UpdateCover(instagram, CoverReason.SWIPE_LIMIT)), OverlayMachine.ops(cover, swipeCover))
        assertEquals(listOf(WindowOp.RemoveCover, WindowOp.RemovePill), OverlayMachine.ops(cover, hidden))
        assertEquals(listOf(WindowOp.RemoveCover), OverlayMachine.ops(coverNoPill, hidden))

        listOf(active, dimmed, parked).forEach { assertEquals(listOf(WindowOp.RemovePill), OverlayMachine.ops(it, hidden)) }
        listOf(hidden, active, dimmed, parked, cover).forEach { assertTrue(OverlayMachine.ops(it, it).isEmpty()) }
    }

    @Test
    fun coverAlwaysAddedLast_soItIsOnTop() {
        val ops = OverlayMachine.ops(OverlayState.PillDimmed(instagram), OverlayState.CoverShowing(tiktok, CoverReason.SWIPE_LIMIT, PillMode.ACTIVE))
        assertEquals(listOf(WindowOp.RetargetPill(tiktok, PillMode.ACTIVE), WindowOp.AddCover(tiktok, CoverReason.SWIPE_LIMIT)), ops)
    }

    // --- Scenarios: fake clock + fake detector (hysteresis), windows counted ---------------------

    /** Feeds detector readings through the real hysteresis and the machine, counting window ops. */
    private class Scenario(var visibility: TimerVisibility = TimerVisibility.ALWAYS) {
        var now = 0L
        var foreground: String? = "com.instagram.android"
        var screenOn = true
        var canDraw = true
        var cover: CoverReason? = null
        val hysteresis = ReelsHysteresis()
        var state: OverlayState = OverlayState.Hidden
        val applied = mutableListOf<WindowOp>()

        fun live(): ReelsLive? = foreground?.let { ReelsLive(it, ReelsTimerStatus.ACTIVE, hysteresis.confirmed) }

        fun read(reading: ReelsState) {
            hysteresis.observe(reading, now)
            step()
        }

        fun advance(millis: Long) {
            now += millis
            step()
        }

        fun step() {
            val pkg = foreground
            val gate = if (pkg == null) SectionGate.WHOLE_APP else {
                val live = live()
                // The repository reports UNKNOWN as unreadable (fallback) once confirmed.
                if (hysteresis.confirmed == ReelsState.UNKNOWN) SectionGate.DETECTION_UNAVAILABLE else SectionGate.of(pkg, live)
            }
            val transition = OverlayMachine.transition(
                state,
                OverlaySignals(pkg, screenOn, true, canDraw, true, gate, visibility, cover),
            )
            applied += transition.ops
            state = transition.to
        }

        fun count(predicate: (WindowOp) -> Boolean) = applied.count(predicate)
    }

    @Test
    fun reelsToMessagesToReels_neverReAddsThePill() {
        val s = Scenario()
        s.read(ReelsState.IN_REELS)
        assertEquals(OverlayState.PillActive(s.foreground!!), s.state)
        repeat(5) {
            s.advance(1_000)
            s.read(ReelsState.OTHER)
            s.advance(450)
            s.read(ReelsState.OTHER)
            assertEquals(OverlayState.PillDimmed(s.foreground!!), s.state)
            s.advance(1_000)
            s.read(ReelsState.IN_REELS)
            s.advance(450)
            s.read(ReelsState.IN_REELS)
            assertEquals(OverlayState.PillActive(s.foreground!!), s.state)
        }
        assertEquals(1, s.count { it is WindowOp.AddPill })
        assertEquals(0, s.count { it == WindowOp.RemovePill })
        assertEquals(10, s.count { it is WindowOp.SetPillMode })
    }

    @Test
    fun rapidTabSwitching_underTheHysteresis_doesNotFlicker() {
        val s = Scenario()
        s.read(ReelsState.IN_REELS)
        repeat(20) {
            s.advance(100)
            s.read(if (it % 2 == 0) ReelsState.OTHER else ReelsState.IN_REELS)
        }
        assertEquals(OverlayState.PillActive(s.foreground!!), s.state)
        assertEquals(0, s.count { it is WindowOp.SetPillMode })
    }

    @Test
    fun transientUnknown_keepsTheLastState_upTo3s_thenFailsOpen() {
        val s = Scenario()
        s.cover = CoverReason.TIME_LIMIT
        s.read(ReelsState.IN_REELS)
        assertTrue(s.state is OverlayState.CoverShowing)
        s.advance(500)
        s.read(ReelsState.UNKNOWN)
        s.advance(2_000)
        s.read(ReelsState.UNKNOWN)
        assertTrue("held through a short UNKNOWN", s.state is OverlayState.CoverShowing)
        s.advance(1_100)
        s.read(ReelsState.UNKNOWN)
        // Unreadable for over 3 s: no cover (fail open), the whole-app pill keeps ticking.
        assertEquals(OverlayState.PillActive(s.foreground!!), s.state)
        assertEquals(1, s.count { it == WindowOp.RemoveCover })
        assertEquals(0, s.count { it == WindowOp.RemovePill })
    }

    @Test
    fun coverInReels_goesAwayAbout500msAfterLeavingForChat() {
        val s = Scenario()
        s.cover = CoverReason.SWIPE_LIMIT
        s.read(ReelsState.IN_REELS)
        assertTrue(s.state is OverlayState.CoverShowing)
        // The user used Back / a gesture: chat is on screen.
        s.advance(100)
        s.read(ReelsState.OTHER)
        assertTrue("not removed on one frame", s.state is OverlayState.CoverShowing)
        s.advance(ReelsHysteresis.CONFIRM_MILLIS)
        s.read(ReelsState.OTHER)
        assertEquals(OverlayState.PillDimmed(s.foreground!!), s.state)
        // Back in Reels with the limit still reached: the cover returns.
        s.advance(1_000)
        s.read(ReelsState.IN_REELS)
        s.advance(ReelsHysteresis.CONFIRM_MILLIS)
        s.read(ReelsState.IN_REELS)
        assertTrue(s.state is OverlayState.CoverShowing)
        assertEquals(2, s.count { it is WindowOp.AddCover })
    }

    @Test
    fun limitReachedInReels_thenOpeningChat_neverCoversChat() {
        val s = Scenario()
        s.read(ReelsState.IN_REELS)
        s.advance(1_000)
        s.cover = CoverReason.TIME_LIMIT
        s.step()
        assertTrue(s.state is OverlayState.CoverShowing)
        s.advance(100)
        s.read(ReelsState.OTHER)
        s.advance(500)
        s.read(ReelsState.OTHER)
        repeat(10) {
            s.advance(1_000)
            s.read(ReelsState.OTHER)
            assertEquals(OverlayState.PillDimmed(s.foreground!!), s.state)
        }
    }

    @Test
    fun appSwitching_retargetsOrRemoves() {
        val s = Scenario()
        s.read(ReelsState.IN_REELS)
        s.foreground = "com.reddit.frontpage"
        s.hysteresis.reset()
        s.step()
        assertEquals(OverlayState.PillActive("com.reddit.frontpage"), s.state)
        assertEquals(WindowOp.RetargetPill("com.reddit.frontpage", PillMode.ACTIVE), s.applied.last())
        s.foreground = null
        s.step()
        assertEquals(OverlayState.Hidden, s.state)
        assertEquals(WindowOp.RemovePill, s.applied.last())
    }

    @Test
    fun screenOffAndOn() {
        val s = Scenario()
        s.cover = CoverReason.TIME_LIMIT
        s.read(ReelsState.IN_REELS)
        s.screenOn = false
        s.step()
        assertEquals(OverlayState.Hidden, s.state)
        assertEquals(listOf(WindowOp.RemoveCover, WindowOp.RemovePill), s.applied.takeLast(2))
        s.screenOn = true
        s.step()
        assertTrue(s.state is OverlayState.CoverShowing)
    }

    @Test
    fun permissionRevokedMidCover_removesEverything_once() {
        val s = Scenario()
        s.cover = CoverReason.SWIPE_LIMIT
        s.read(ReelsState.IN_REELS)
        s.canDraw = false
        s.step()
        s.step()
        assertEquals(OverlayState.Hidden, s.state)
        assertEquals(1, s.count { it == WindowOp.RemoveCover })
        assertEquals(1, s.count { it == WindowOp.RemovePill })
    }

    @Test
    fun onlyOnReels_parksThePill_withoutRemovingIt() {
        val s = Scenario(TimerVisibility.ONLY_REELS)
        s.read(ReelsState.OTHER)
        assertEquals(OverlayState.PillParked(s.foreground!!), s.state)
        s.advance(1_000)
        s.read(ReelsState.IN_REELS)
        s.advance(ReelsHysteresis.CONFIRM_MILLIS)
        s.read(ReelsState.IN_REELS)
        assertEquals(OverlayState.PillActive(s.foreground!!), s.state)
        assertEquals(1, s.count { it is WindowOp.AddPill })
        assertEquals(0, s.count { it == WindowOp.RemovePill })
    }

    @Test
    fun pillOf_state() {
        assertNull(OverlayState.Hidden.pill())
        assertEquals("a" to PillMode.PARKED, OverlayState.PillParked("a").pill())
        assertNull(OverlayState.CoverShowing("a", CoverReason.SCHEDULE, null).pill())
    }
}
