package com.unscroll.app.domain.friction

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseGateTest {

    private val gate = PauseGate()
    private val start = 1_000_000L

    private fun open(pkg: String?, at: Long, enabled: Boolean = true, blocked: Boolean = false) =
        gate.onForegroundChanged(pkg, at, pauseEnabled = enabled, blocked = blocked)

    private fun started() = gate.onTrackingStarted(start)

    @Test
    fun openFromOutside_showsPause() {
        started()
        assertTrue(open(INSTAGRAM, start + 10_000))
    }

    @Test
    fun noPauseDuringFirstFiveSecondsAfterTrackingStarts() {
        started()
        assertFalse(open(INSTAGRAM, start + 4_999))
        // The user is now "in" Instagram, so leaving and coming back quickly doesn't pause either.
        open(null, start + 6_000)
        assertFalse(open(INSTAGRAM, start + 7_000))
    }

    @Test
    fun noPauseBeforeTrackingStarted() {
        assertFalse(open(INSTAGRAM, start))
    }

    @Test
    fun quickReturnWithin30s_noPause_butAfter30s_pausesAgain() {
        started()
        open(INSTAGRAM, start + 10_000) // pause shown
        gate.onContinued(INSTAGRAM, start + 20_000)
        assertFalse(open(INSTAGRAM, start + 21_000)) // through the gate, now using it

        open(null, start + 60_000) // left at 60 s
        assertFalse(open(INSTAGRAM, start + 90_000)) // back after exactly 30 s
        open(null, start + 100_000)
        assertTrue(open(INSTAGRAM, start + 130_001)) // more than 30 s later
    }

    @Test
    fun neverMind_thenQuickReopen_stillPauses() {
        started()
        assertTrue(open(INSTAGRAM, start + 10_000))
        open(null, start + 12_000) // pause screen in front, then "Never mind"
        assertTrue(open(INSTAGRAM, start + 15_000))
    }

    @Test
    fun continue_letsTheNextOpenThroughOnce() {
        started()
        assertTrue(open(INSTAGRAM, start + 10_000))
        open(null, start + 10_500) // pause screen
        gate.onContinued(INSTAGRAM, start + 25_000)
        assertFalse(open(INSTAGRAM, start + 25_500))
    }

    @Test
    fun blockedApp_noPause_blockScreenWins() {
        started()
        assertFalse(open(INSTAGRAM, start + 10_000, blocked = true))
        // Not counted as used, so it pauses once it is no longer blocked.
        open(null, start + 11_000)
        assertTrue(open(INSTAGRAM, start + 12_000))
    }

    @Test
    fun disabledPause_neverShows() {
        started()
        assertFalse(open(INSTAGRAM, start + 10_000, enabled = false))
    }

    @Test
    fun switchingBetweenApps_tracksEachSeparately() {
        started()
        open(INSTAGRAM, start + 10_000)
        gate.onContinued(INSTAGRAM, start + 11_000)
        open(INSTAGRAM, start + 12_000)
        // Straight to TikTok: TikTok was never used, so it pauses.
        assertTrue(open(TIKTOK, start + 20_000))
        open(null, start + 21_000)
        // Back to Instagram within 30 s of leaving it.
        assertFalse(open(INSTAGRAM, start + 40_000))
    }

    @Test
    fun sameAppReportedAgain_isNotANewOpen() {
        started()
        open(INSTAGRAM, start + 10_000, enabled = false)
        assertFalse(open(INSTAGRAM, start + 11_000))
    }

    private companion object {
        const val INSTAGRAM = "com.instagram.android"
        const val TIKTOK = "com.zhiliaoapp.musically"
    }
}
