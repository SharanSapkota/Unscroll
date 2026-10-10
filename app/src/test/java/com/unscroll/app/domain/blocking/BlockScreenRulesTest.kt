package com.unscroll.app.domain.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlockScreenRulesTest {

    private val allowed = BlockDecision.Allowed(remainingMillis = null)

    @Test
    fun unblocked_closesTheScreen() {
        assertNull(BlockScreenRules.current(BlockReason.BLOCKED_ENTIRE_APP, allowed, swipeLimitReached = false))
        assertNull(BlockScreenRules.current(BlockReason.INSIDE_SCHEDULE, allowed, swipeLimitReached = false))
        assertNull(
            BlockScreenRules.current(
                BlockReason.DAILY_LIMIT_REACHED,
                BlockDecision.Allowed(remainingMillis = 20 * 60_000L),
                swipeLimitReached = false,
            ),
        )
    }

    @Test
    fun stillBlockedForAnotherReason_staysWithThatReason() {
        val schedule = BlockDecision.Blocked(BlockReason.INSIDE_SCHEDULE, until = 1_000L)
        assertEquals(schedule, BlockScreenRules.current(BlockReason.BLOCKED_ENTIRE_APP, schedule, swipeLimitReached = false))
    }

    @Test
    fun swipeLimit_staysWhileReached_closesWhenRaisedOrRemoved() {
        assertEquals(
            BlockDecision.Blocked(BlockReason.SWIPE_LIMIT_REACHED, until = null),
            BlockScreenRules.current(BlockReason.SWIPE_LIMIT_REACHED, allowed, swipeLimitReached = true),
        )
        assertNull(BlockScreenRules.current(BlockReason.SWIPE_LIMIT_REACHED, allowed, swipeLimitReached = false))
    }

    @Test
    fun aReachedSwipeLimitDoesNotKeepATimeBlockScreen() {
        // The swipe limit has its own cover; a time block screen closes once its rule is gone.
        assertNull(BlockScreenRules.current(BlockReason.BLOCKED_ENTIRE_APP, allowed, swipeLimitReached = true))
    }
}
