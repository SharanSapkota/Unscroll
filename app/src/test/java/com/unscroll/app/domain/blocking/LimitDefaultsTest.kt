package com.unscroll.app.domain.blocking

import com.unscroll.app.domain.overlay.CoverReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LimitDefaultsTest {

    @Test
    fun newApps_startWithA30MinuteTimeLimit_andNoSwipeLimit() {
        assertEquals(30, LimitDefaults.NEW_APP.dailyLimitMinutes)
        assertNull(LimitDefaults.NEW_APP.swipeLimit)
        assertEquals(listOf(15, 30, 45, 60, 120), LimitDefaults.TIME_PRESETS)
        assertEquals(listOf(50, 100, 200, 300), LimitDefaults.SWIPE_PRESETS)
    }

    @Test
    fun timeSwitch_keepsTheValue_orStartsAt30_andOffClearsIt() {
        assertEquals(30, LimitDefaults.withTimeLimit(LimitSettings(), on = true).dailyLimitMinutes)
        assertEquals(45, LimitDefaults.withTimeLimit(LimitSettings(dailyLimitMinutes = 45), on = true).dailyLimitMinutes)
        assertNull(LimitDefaults.withTimeLimit(LimitSettings(dailyLimitMinutes = 45), on = false).dailyLimitMinutes)
    }

    @Test
    fun swipeSwitch_startsAt100PerDay_andOffClearsOnlyTheLimit() {
        val on = LimitDefaults.withSwipeLimit(LimitSettings(dailyLimitMinutes = 30), on = true)
        assertEquals(100, on.swipeLimit)
        assertEquals(SwipeLimitScope.DAY, on.swipeLimitScope)
        assertEquals(30, on.dailyLimitMinutes)
        val session = LimitSettings(swipeLimit = 200, swipeLimitScope = SwipeLimitScope.SESSION)
        assertEquals(session, LimitDefaults.withSwipeLimit(session, on = true))
        val off = LimitDefaults.withSwipeLimit(session, on = false)
        assertNull(off.swipeLimit)
        assertEquals(SwipeLimitScope.SESSION, off.swipeLimitScope)
    }

    @Test
    fun firstLimitReached_wins() {
        // Swipe limit first, then the time limit too: the cover keeps naming the swipe limit.
        assertEquals(CoverReason.SWIPE_LIMIT, LimitCoverRules.reason(null, timeReached = false, swipeReached = true, inSchedule = false))
        assertEquals(CoverReason.SWIPE_LIMIT, LimitCoverRules.reason(CoverReason.SWIPE_LIMIT, timeReached = true, swipeReached = true, inSchedule = false))
        // And the other way round.
        assertEquals(CoverReason.TIME_LIMIT, LimitCoverRules.reason(CoverReason.TIME_LIMIT, timeReached = true, swipeReached = true, inSchedule = false))
        // The first one lifted (extension, new day): the other one takes over.
        assertEquals(CoverReason.TIME_LIMIT, LimitCoverRules.reason(CoverReason.SWIPE_LIMIT, timeReached = true, swipeReached = false, inSchedule = false))
        // Both at once with nothing up yet: time, then swipes, then the schedule.
        assertEquals(CoverReason.TIME_LIMIT, LimitCoverRules.reason(null, timeReached = true, swipeReached = true, inSchedule = true))
        assertEquals(CoverReason.SCHEDULE, LimitCoverRules.reason(null, timeReached = false, swipeReached = false, inSchedule = true))
        assertNull(LimitCoverRules.reason(CoverReason.TIME_LIMIT, timeReached = false, swipeReached = false, inSchedule = false))
    }

    @Test
    fun coverRequests_mergeSources_firstReachedWins_andTurningOffAppliesAtOnce() {
        val requests = CoverRequests()
        val app = "com.instagram.android"
        requests.set(app, CoverReason.SWIPE_LIMIT, holds = true)
        assertEquals(CoverReason.SWIPE_LIMIT, requests.reasons.value[app])
        requests.set(app, CoverReason.TIME_LIMIT, holds = true)
        assertEquals(CoverReason.SWIPE_LIMIT, requests.reasons.value[app])
        // Swipe limit switched off: the time limit is what holds now.
        requests.set(app, CoverReason.SWIPE_LIMIT, holds = false)
        assertEquals(CoverReason.TIME_LIMIT, requests.reasons.value[app])
        requests.set(app, CoverReason.TIME_LIMIT, holds = false)
        assertNull(requests.reasons.value[app])

        requests.set(app, CoverReason.SCHEDULE, holds = true)
        requests.set("com.reddit.frontpage", CoverReason.SWIPE_LIMIT, holds = true)
        requests.clear(app)
        assertEquals(mapOf("com.reddit.frontpage" to CoverReason.SWIPE_LIMIT), requests.reasons.value)
        requests.clearAll()
        assertEquals(emptyMap<String, CoverReason>(), requests.reasons.value)
    }
}
