package com.unscroll.app.domain.scroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeBreakTrackerTest {

    private val tracker = SwipeBreakTracker()

    /** The swipe counts (1..to) at which a break is due. */
    private fun breaksUpTo(to: Int, breakAfter: Int?, sessionId: Long = 1, from: Int = 1): List<Int> =
        (from..to).filter { tracker.onSwipeCount(sessionId, it, breakAfter) }

    @Test
    fun off_neverBreaks() {
        assertEquals(emptyList<Int>(), breaksUpTo(500, breakAfter = null))
        assertNull(tracker.swipesUntilBreak(1, 10, breakAfter = null))
    }

    @Test
    fun breaksExactlyAtN_thenEveryNMore() {
        assertEquals(listOf(25, 50, 75), breaksUpTo(80, breakAfter = 25))
    }

    @Test
    fun skippedCounts_stillBreakOnce() {
        // A StateFlow collector may miss a value; 49 → 51 still breaks once.
        assertFalse(tracker.onSwipeCount(1, 49, 50))
        assertTrue(tracker.onSwipeCount(1, 51, 50))
        assertFalse(tracker.onSwipeCount(1, 52, 50))
    }

    @Test
    fun keepScrolling_resetsTheCounter() {
        assertEquals(listOf(50), breaksUpTo(60, breakAfter = 50))
        // The user stayed on the break screen while the session went on to 70 swipes.
        tracker.onKeepScrolling(sessionId = 1, count = 70)
        assertEquals(50, tracker.swipesUntilBreak(1, 70, 50))
        assertEquals(listOf(120), breaksUpTo(130, breakAfter = 50, from = 71))
    }

    @Test
    fun newSession_startsFromZero() {
        assertEquals(listOf(25), breaksUpTo(40, breakAfter = 25, sessionId = 1))
        assertEquals(listOf(25), breaksUpTo(30, breakAfter = 25, sessionId = 2))
    }

    @Test
    fun swipesUntilBreak_countsDown() {
        assertEquals(100, tracker.swipesUntilBreak(1, 0, 100))
        tracker.onSwipeCount(1, 40, 100)
        assertEquals(60, tracker.swipesUntilBreak(1, 40, 100))
    }

    @Test
    fun changingN_midSession_usesTheNewValue() {
        assertFalse(tracker.onSwipeCount(1, 30, 50))
        assertTrue(tracker.onSwipeCount(1, 31, 25))
    }
}
