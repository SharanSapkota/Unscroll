package com.unscroll.app.domain.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwipeCoverTrackerTest {

    private val tracker = SwipeCoverTracker()

    private fun showFor(packageName: String) {
        assertEquals(CoverAction.Show(packageName), tracker.onStatus(packageName, reached = true, canDrawOverlays = true))
        tracker.onShown(packageName)
    }

    @Test
    fun belowTheLimit_nothingHappens() {
        assertEquals(CoverAction.None, tracker.onStatus(IG, reached = false, canDrawOverlays = true))
        assertNull(tracker.shownFor)
    }

    @Test
    fun reached_showsOnce_noDoubleShowOnMoreSwipesOrWindowEvents() {
        showFor(IG)
        repeat(5) {
            assertEquals(CoverAction.None, tracker.onStatus(IG, reached = true, canDrawOverlays = true))
        }
        assertEquals(IG, tracker.shownFor)
    }

    @Test
    fun hides_whenTheLimitIsLifted() {
        showFor(IG)
        assertEquals(CoverAction.Hide, tracker.onStatus(IG, reached = false, canDrawOverlays = true))
        assertNull(tracker.shownFor)
        // Lifting it again changes nothing.
        assertEquals(CoverAction.None, tracker.onStatus(IG, reached = false, canDrawOverlays = true))
    }

    @Test
    fun hides_whenTheAppLeavesTheForeground_andShowsAgainWhenItComesBack() {
        showFor(IG)
        assertEquals(CoverAction.None, tracker.onForeground(IG))
        assertEquals(CoverAction.Hide, tracker.onForeground(null))
        assertNull(tracker.shownFor)
        assertEquals(CoverAction.None, tracker.onForeground(TIKTOK))

        assertEquals(CoverAction.None, tracker.onForeground(IG))
        showFor(IG)
    }

    @Test
    fun anotherAppOverItsLimit_getsTheCover() {
        showFor(IG)
        assertEquals(CoverAction.Hide, tracker.onForeground(TIKTOK))
        showFor(TIKTOK)
    }

    @Test
    fun withoutOverlayPermission_fallbackOncePerVisit() {
        assertEquals(CoverAction.Fallback(IG), tracker.onStatus(IG, reached = true, canDrawOverlays = false))
        assertEquals(CoverAction.None, tracker.onStatus(IG, reached = true, canDrawOverlays = false))
        assertNull(tracker.shownFor)

        // Sent home, then back into the app: the fallback fires again.
        tracker.onForeground(null)
        assertEquals(CoverAction.Fallback(IG), tracker.onStatus(IG, reached = true, canDrawOverlays = false))
    }

    @Test
    fun fallbackIsResetWhenTheLimitIsLifted() {
        tracker.onStatus(IG, reached = true, canDrawOverlays = false)
        tracker.onStatus(IG, reached = false, canDrawOverlays = false)
        assertNull(tracker.fallbackFor)
    }

    @Test
    fun failedShow_fallsBack_andDoesNotRetryOnEverySwipe() {
        assertEquals(CoverAction.Show(IG), tracker.onStatus(IG, reached = true, canDrawOverlays = true))
        assertEquals(CoverAction.Fallback(IG), tracker.onShowFailed(IG))
        assertNull(tracker.shownFor)
        assertEquals(CoverAction.None, tracker.onStatus(IG, reached = true, canDrawOverlays = true))
    }

    @Test
    fun hiddenFromOutside_showsAgainOnTheNextStatus() {
        showFor(IG)
        tracker.onHidden() // "Go home"
        assertNull(tracker.shownFor)
        showFor(IG)
    }

    private companion object {
        const val IG = "com.instagram.android"
        const val TIKTOK = "com.zhiliaoapp.musically"
    }
}
