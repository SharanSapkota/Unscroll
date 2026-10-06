package com.unscroll.app.domain.goals

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakRulesTest {

    private val goal = DailyGoal.millis(60)
    private val under = DailyGoal.millis(30)
    private val over = DailyGoal.millis(90)

    @Test
    fun noHistory_noStreak() {
        assertEquals(StreakHistory(0, 0), StreakRules.history(emptyList(), goal))
    }

    @Test
    fun runEndingYesterday_andBestRun() {
        val history = StreakRules.history(listOf(under, under, under, over, under, under), goal)
        assertEquals(StreakHistory(endingYesterday = 2, bestCompleted = 3), history)
    }

    @Test
    fun exactlyAtTheGoal_counts() {
        assertEquals(StreakHistory(2, 2), StreakRules.history(listOf(goal, goal), goal))
    }

    @Test
    fun yesterdayOverTheGoal_endsTheStreak() {
        assertEquals(StreakHistory(0, 2), StreakRules.history(listOf(under, under, over), goal))
    }

    @Test
    fun todayWithinTheGoal_extendsTheStreak_andCanSetANewBest() {
        val streak = StreakRules.withToday(StreakHistory(endingYesterday = 3, bestCompleted = 3), under, goal)
        assertEquals(4, streak.current)
        assertEquals(4, streak.best)
        assertTrue(streak.todayOnTrack)
    }

    @Test
    fun todayOverTheGoal_breaksTheStreak_butKeepsTheBest() {
        val streak = StreakRules.withToday(StreakHistory(endingYesterday = 3, bestCompleted = 5), over, goal)
        assertEquals(0, streak.current)
        assertEquals(5, streak.best)
        assertFalse(streak.todayOnTrack)
    }
}
