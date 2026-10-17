package com.unscroll.app.domain.reels

import org.junit.Assert.assertEquals
import org.junit.Test

class DetectorWatchdogTest {

    @Test
    fun rechecks_whenEventsStop() {
        assertEquals(WatchdogAction.RECHECK, DetectorWatchdog.action(lastCheckAt = null, lastReadingAt = null, hasConfirmed = false, now = 0))
        assertEquals(WatchdogAction.NONE, DetectorWatchdog.action(lastCheckAt = 500, lastReadingAt = 500, hasConfirmed = true, now = 900))
        assertEquals(WatchdogAction.RECHECK, DetectorWatchdog.action(lastCheckAt = 0, lastReadingAt = 0, hasConfirmed = true, now = 1_000))
    }

    @Test
    fun resets_aStaleConfirmedState_soNoCoverOutlivesTheDetector() {
        assertEquals(WatchdogAction.RESET, DetectorWatchdog.action(lastCheckAt = 2_900, lastReadingAt = 0, hasConfirmed = true, now = 3_000))
        assertEquals(WatchdogAction.RESET, DetectorWatchdog.action(lastCheckAt = 2_900, lastReadingAt = null, hasConfirmed = true, now = 3_000))
        // Nothing confirmed: nothing to reset, keep trying.
        assertEquals(WatchdogAction.RECHECK, DetectorWatchdog.action(lastCheckAt = 0, lastReadingAt = 0, hasConfirmed = false, now = 5_000))
    }
}
