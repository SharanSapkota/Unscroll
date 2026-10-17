package com.unscroll.app.domain.scroll

import com.unscroll.app.domain.time.Clock
import com.unscroll.app.domain.tracking.DefaultTrackedApps.FACEBOOK
import com.unscroll.app.domain.tracking.DefaultTrackedApps.INSTAGRAM
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeDetectorTest {

    private var now = 0L
    private val counted = setOf(INSTAGRAM, FACEBOOK, ANY_APP)
    private val detector = SwipeDetector(Clock { now }, isCounted = { it in counted })

    /** Feeds scroll events at the given times and returns how many swipes they made. */
    private fun swipes(vararg times: Long, packageName: String = INSTAGRAM, screenOn: Boolean = true): Int =
        times.count { time ->
            now = time
            detector.onScrollEvent(packageName, screenOn)
        }

    @Test
    fun singleScrollEvent_isOneSwipe() {
        assertEquals(1, swipes(1_000))
    }

    @Test
    fun burstOfEventsCloseTogether_isOneSwipe() {
        // A fling: events every 50-250 ms, 1.2 s in total.
        assertEquals(1, swipes(1_000, 1_050, 1_200, 1_450, 1_700, 1_950, 2_200))
    }

    @Test
    fun eventExactlyAtTheGap_staysInTheSameSwipe() {
        assertEquals(1, swipes(1_000, 1_000 + SwipeDetector.SWIPE_BURST_GAP_MILLIS))
    }

    @Test
    fun slowSeparatedSwipes_countEachTime() {
        assertEquals(3, swipes(1_000, 2_000, 2_301 + 1_000))
        assertEquals(2, swipes(10_000, 10_000 + SwipeDetector.SWIPE_BURST_GAP_MILLIS + 1))
    }

    @Test
    fun twoBursts_areTwoSwipes() {
        assertEquals(2, swipes(1_000, 1_100, 1_200, 2_000, 2_100))
    }

    @Test
    fun screenOff_eventsAreIgnored_andEndTheBurst() {
        assertEquals(0, swipes(1_000, 1_100, screenOn = false))
        assertEquals(1, swipes(1_200))
        assertEquals(0, swipes(1_300, screenOn = false))
        // Screen back on within the gap: still a new swipe, because the burst ended.
        assertEquals(1, swipes(1_400))
    }

    @Test
    fun untrackedApps_areIgnored() {
        assertEquals(0, swipes(1_000, 2_000, packageName = "com.android.chrome"))
        now = 3_000
        assertFalse(detector.onScrollEvent(null, screenOn = true))
    }

    @Test
    fun switchingApps_startsANewSwipe() {
        assertEquals(1, swipes(1_000, packageName = INSTAGRAM))
        assertEquals(1, swipes(1_100, packageName = FACEBOOK))
    }

    @Test
    fun windowChange_startsANewSwipe() {
        now = 1_000
        assertTrue(detector.onScrollEvent(INSTAGRAM, screenOn = true))
        detector.reset()
        now = 1_100
        assertTrue(detector.onScrollEvent(INSTAGRAM, screenOn = true))
    }

    @Test
    fun clockGoingBackwards_countsAsANewSwipe() {
        assertEquals(2, swipes(5_000, 4_000))
    }

    @Test
    fun anyAddedApp_isCounted_whenTheRepositorySaysSo() {
        // Nothing is hardcoded: an added app counts like Instagram.
        assertEquals(1, swipes(1_000, packageName = ANY_APP))
        assertEquals(0, swipes(5_000, packageName = "com.example.notcounted"))
    }

    // --- Full-screen video pagers (Reels, TikTok, Shorts) ---------------------------------------

    /** One scroll event at [time] whose view shows items [from]..[to]. */
    private fun pager(time: Long, from: Int, to: Int = from): Boolean {
        now = time
        return detector.onScrollEvent(INSTAGRAM, screenOn = true, ScrollPosition(from, to))
    }

    @Test
    fun pagerSwipe_isOneSwipe_whileTwoPagesShowAndWhenItLands() {
        // Page 5 -> 6: two pages on screen while it moves, then it lands on 6.
        val results = listOf(pager(1_000, 5, 6), pager(1_100, 5, 6), pager(1_200, 5, 6), pager(1_300, 6))
        assertEquals(listOf(true, false, false, false), results)
    }

    @Test
    fun quickPagerSwipesWithoutAPause_countEachPage() {
        // Swiping again the moment each video lands: no 300 ms gap between the events at all.
        var count = 0
        var t = 1_000L
        for (page in 5 until 9) {
            if (pager(t, page, page + 1)) count++
            t += 100
            if (pager(t, page, page + 1)) count++
            t += 100
            if (pager(t, page + 1)) count++
            t += 100
        }
        assertEquals(4, count)
    }

    @Test
    fun pagerSwipingBack_countsToo() {
        var count = 0
        if (pager(1_000, 5, 6)) count++
        if (pager(1_100, 6)) count++
        if (pager(1_200, 5, 6)) count++
        if (pager(1_300, 5)) count++
        assertEquals(2, count)
    }

    @Test
    fun snappingBackToTheSamePage_isOneSwipe() {
        assertEquals(listOf(true, false, false), listOf(pager(1_000, 5, 6), pager(1_100, 5, 6), pager(1_200, 5)))
    }

    @Test
    fun pagerThatRestsAndScrollsInPlace_doesNotCountAgain() {
        // Events while resting on one page (e.g. the view re-reports its position) aren't swipes.
        assertEquals(listOf(true, false, false, false), listOf(pager(1_000, 5, 6), pager(1_100, 6), pager(1_200, 6), pager(1_300, 6)))
    }

    @Test
    fun listsShowingSeveralItems_onlyUseTheGap() {
        // A feed: several items on screen, indexes move during one fling, still one swipe.
        val results = listOf(pager(1_000, 3, 5), pager(1_100, 4, 6), pager(1_200, 5, 7), pager(1_300, 6, 8))
        assertEquals(listOf(true, false, false, false), results)
    }

    @Test
    fun viewsWithoutPositions_onlyUseTheGap() {
        assertEquals(null, ScrollPosition.UNKNOWN.settledPage)
        assertEquals(1, swipes(1_000, 1_100, 1_200))
    }

    @Test
    fun windowChange_forgetsThePage() {
        pager(1_000, 5, 6)
        pager(1_100, 6)
        detector.reset()
        // New window: the first event is a new swipe, its landing page doesn't count twice.
        assertEquals(listOf(true, false), listOf(pager(1_200, 0, 1), pager(1_300, 1)))
    }

    private companion object {
        const val ANY_APP = "com.example.anyapp"
    }
}
