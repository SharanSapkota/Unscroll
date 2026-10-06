package com.unscroll.app.domain.friction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NudgeRulesTest {

    private val thresholds = listOf(5, 10, 20)

    @Test
    fun opens_firesOnlyWhenThresholdReached() {
        assertEquals(emptyList<Int>(), NudgeRules.thresholdsToNotify(4, thresholds, emptySet()))
        assertEquals(listOf(5), NudgeRules.thresholdsToNotify(5, thresholds, emptySet()))
    }

    @Test
    fun opens_firesOncePerThresholdPerDay() {
        val sent = mutableSetOf<Int>()
        val fired = mutableListOf<Int>()
        for (opens in 1..25) {
            val due = NudgeRules.thresholdsToNotify(opens, thresholds, sent)
            if (due.isNotEmpty()) {
                fired += due.max()
                sent += due
            }
        }
        assertEquals(listOf(5, 10, 20), fired)
    }

    @Test
    fun opens_jumpingPastSeveralThresholds_returnsAllUnsent() {
        // e.g. nudges switched on mid-day at 12 opens: notify for 10 once, mark 5 and 10.
        assertEquals(listOf(5, 10), NudgeRules.thresholdsToNotify(12, thresholds, emptySet()))
        assertEquals(listOf(10), NudgeRules.thresholdsToNotify(12, thresholds, setOf(5)))
    }

    @Test
    fun opens_newDay_startsFresh() {
        // A new day has an empty "sent" set.
        assertEquals(listOf(5), NudgeRules.thresholdsToNotify(5, thresholds, alreadySent = emptySet()))
    }

    @Test
    fun limitWarnings_at80And100Percent_once() {
        val limit = 30 // minutes
        assertEquals(emptyList<Int>(), NudgeRules.limitLevelsToWarn(23 * MIN, limit, emptySet()))
        assertEquals(listOf(80), NudgeRules.limitLevelsToWarn(24 * MIN, limit, emptySet()))
        assertEquals(emptyList<Int>(), NudgeRules.limitLevelsToWarn(25 * MIN, limit, setOf(80)))
        assertEquals(listOf(100), NudgeRules.limitLevelsToWarn(30 * MIN, limit, setOf(80)))
        assertEquals(listOf(80, 100), NudgeRules.limitLevelsToWarn(31 * MIN, limit, emptySet()))
    }

    @Test
    fun nextLimitWarningAt_isWhenUsageCrossesTheNextLevel() {
        val now = 1_000L
        assertEquals(now + 4 * MIN, NudgeRules.nextLimitWarningAt(20 * MIN, 30, emptySet(), now))
        assertEquals(now + 10 * MIN, NudgeRules.nextLimitWarningAt(20 * MIN, 30, setOf(80), now))
        assertNull(NudgeRules.nextLimitWarningAt(40 * MIN, 30, setOf(80, 100), now))
    }

    private companion object {
        const val MIN = 60_000L
    }
}
