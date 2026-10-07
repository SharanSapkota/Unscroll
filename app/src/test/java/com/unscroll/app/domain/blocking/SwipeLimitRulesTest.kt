package com.unscroll.app.domain.blocking

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeLimitRulesTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private fun at(hour: Int, minute: Int = 0) =
        LocalDateTime.of(2026, 10, 7, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun span(startHour: Int, startMinute: Int, endHour: Int?, endMinute: Int = 0) =
        SessionSpan(at(startHour, startMinute), endHour?.let { at(it, endMinute) })

    // --- Status: reached, remaining, the 80 % warning, extensions ---------------------------------

    @Test
    fun belowTheWarning_noRemainingShown() {
        val status = SwipeLimitRules.status(limit = 100, used = 79, extensions = 0)
        assertFalse(status.reached)
        assertFalse(status.showRemaining)
        assertEquals(21, status.remaining)
    }

    @Test
    fun fromEightyPercent_theRemainingSwipesShow() {
        val status = SwipeLimitRules.status(limit = 100, used = 80, extensions = 0)
        assertTrue(status.showRemaining)
        assertEquals(20, status.remaining)
        // Rounds up: 80 % of 25 is 20.
        assertFalse(SwipeLimitRules.status(limit = 25, used = 19, extensions = 0).showRemaining)
        assertTrue(SwipeLimitRules.status(limit = 25, used = 20, extensions = 0).showRemaining)
    }

    @Test
    fun atTheLimit_itIsReached_andNothingRemains() {
        val status = SwipeLimitRules.status(limit = 50, used = 50, extensions = 0)
        assertTrue(status.reached)
        assertFalse(status.showRemaining)
        assertEquals(0, status.remaining)
        assertTrue(SwipeLimitRules.status(limit = 50, used = 70, extensions = 0).reached)
    }

    @Test
    fun anExtension_adds20Swipes() {
        val status = SwipeLimitRules.status(limit = 50, used = 55, extensions = 1)
        assertEquals(70, status.allowance)
        assertFalse(status.reached)
        assertEquals(15, status.remaining)
        assertTrue(SwipeLimitRules.status(limit = 50, used = 70, extensions = 1).reached)
        assertEquals(90, SwipeLimitRules.status(limit = 50, used = 0, extensions = 2).allowance)
    }

    // --- Windows: per day vs per session ----------------------------------------------------------

    @Test
    fun perDay_countsFromLocalMidnight() {
        val start = SwipeLimitRules.windowStart(SwipeLimitScope.DAY, listOf(span(9, 0, 9, 30)), 30, at(15), zone)
        assertEquals(at(0), start)
    }

    @Test
    fun perSession_shortBreaksDontReset_theCountRunsAcrossThem() {
        val sessions = listOf(
            span(9, 0, 9, 10),
            span(9, 20, 9, 25), // 10 min after the first: same swipe session (gap 30).
            span(9, 40, null), // 15 min later, open now.
        )
        assertEquals(at(9, 0), SwipeLimitRules.windowStart(SwipeLimitScope.SESSION, sessions, 30, at(9, 45), zone))
    }

    @Test
    fun perSession_resetsAfterStayingAwayForTheGap() {
        val sessions = listOf(
            span(8, 0, 8, 30),
            span(9, 0, null), // 30 min away: a new swipe session.
        )
        assertEquals(at(9, 0), SwipeLimitRules.windowStart(SwipeLimitScope.SESSION, sessions, 30, at(9, 10), zone))
        // With a 60 min gap the same break doesn't reset it.
        assertEquals(at(8, 0), SwipeLimitRules.windowStart(SwipeLimitScope.SESSION, sessions, 60, at(9, 10), zone))
    }

    @Test
    fun perSession_comingBackAfterTheGap_startsFreshEvenBeforeTheNewSessionIsLogged() {
        val sessions = listOf(span(8, 0, 8, 30))
        assertEquals(at(9, 5), SwipeLimitRules.windowStart(SwipeLimitScope.SESSION, sessions, 30, at(9, 5), zone))
        // Back within the gap: still the old window.
        assertEquals(at(8, 0), SwipeLimitRules.windowStart(SwipeLimitScope.SESSION, sessions, 30, at(8, 50), zone))
    }

    @Test
    fun perSession_noSessions_startsNow() {
        assertEquals(at(10), SwipeLimitRules.windowStart(SwipeLimitScope.SESSION, emptyList(), 30, at(10), zone))
    }
}
