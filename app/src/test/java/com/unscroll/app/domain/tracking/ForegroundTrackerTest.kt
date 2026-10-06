package com.unscroll.app.domain.tracking

import com.unscroll.app.domain.tracking.ForegroundEventType.PAUSED
import com.unscroll.app.domain.tracking.ForegroundEventType.RESUMED
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundTrackerTest {

    private val tracker = ForegroundTracker()

    private fun event(packageName: String, type: ForegroundEventType, time: Long) =
        ForegroundEvent(packageName, type, time)

    @Test
    fun noEvents_isNull() {
        assertNull(tracker.process(emptyList()))
    }

    @Test
    fun resumed_becomesForeground_andPersistsAcrossEmptyPolls() {
        assertEquals("a", tracker.process(listOf(event("a", RESUMED, 1))))
        assertEquals("a", tracker.process(emptyList()))
    }

    @Test
    fun pauseOfForegroundApp_clearsIt() {
        tracker.process(listOf(event("a", RESUMED, 1)))
        assertNull(tracker.process(listOf(event("a", PAUSED, 2))))
    }

    @Test
    fun switchingApps_lateOldPauseDoesNotClearNewApp() {
        val result = tracker.process(
            listOf(event("a", RESUMED, 1), event("b", RESUMED, 2), event("a", PAUSED, 3)),
        )
        assertEquals("b", result)
    }

    @Test
    fun activityChangeInsideSameApp_staysInApp() {
        tracker.process(listOf(event("a", RESUMED, 1)))
        assertEquals("a", tracker.process(listOf(event("a", PAUSED, 2), event("a", RESUMED, 3))))
    }

    @Test
    fun overlappingWindows_doNotReplayOlderEvents() {
        tracker.process(listOf(event("a", RESUMED, 1), event("a", PAUSED, 5)))
        tracker.process(listOf(event("b", RESUMED, 6)))
        // The next query overlaps and returns old events again, plus nothing new.
        val result = tracker.process(
            listOf(event("a", RESUMED, 1), event("a", PAUSED, 5), event("b", RESUMED, 6)),
        )
        assertEquals("b", result)
    }

    @Test
    fun lateEventInsideOverlap_isStillApplied() {
        tracker.process(listOf(event("a", RESUMED, 10)))
        // An event at 12 shows up only in a later, overlapping query.
        assertNull(tracker.process(listOf(event("a", RESUMED, 10), event("a", PAUSED, 12))))
    }

    @Test
    fun reset_forgetsState() {
        tracker.process(listOf(event("a", RESUMED, 10)))
        tracker.reset()
        assertNull(tracker.foregroundPackage)
        assertEquals("b", tracker.process(listOf(event("b", RESUMED, 1))))
    }
}
