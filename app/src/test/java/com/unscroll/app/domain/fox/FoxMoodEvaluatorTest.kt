package com.unscroll.app.domain.fox

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FoxMoodEvaluatorTest {

    private val hour = 60 * 60_000L
    private val busy = 30 * 60_000L

    private fun evaluate(vararg apps: FoxAppUsage, tracking: Boolean = true) =
        FoxMoodEvaluator.evaluate(tracking, apps.toList())

    @Test
    fun trackingOff_isSleepy_whateverTheUsage() {
        assertEquals(FoxMood.SLEEPY, evaluate(FoxAppUsage(2 * hour, limitMillis = hour), tracking = false))
        assertEquals(FoxMood.SLEEPY, evaluate(tracking = false))
    }

    @Test
    fun littleUsageToday_isSleepy_andTheEdgeIsHappy() {
        assertEquals(FoxMood.SLEEPY, evaluate())
        assertEquals(FoxMood.SLEEPY, evaluate(FoxAppUsage(FoxMoodEvaluator.SLEEPY_BELOW_MILLIS - 1)))
        assertEquals(FoxMood.HAPPY, evaluate(FoxAppUsage(FoxMoodEvaluator.SLEEPY_BELOW_MILLIS)))
    }

    @Test
    fun underLimits_isHappy() {
        assertEquals(FoxMood.HAPPY, evaluate(FoxAppUsage(busy, limitMillis = hour)))
        assertEquals(FoxMood.HAPPY, evaluate(FoxAppUsage(busy)))
    }

    @Test
    fun timeLimit_exactly80Percent_isAlert_justBelowIsHappy() {
        assertEquals(FoxMood.ALERT, evaluate(FoxAppUsage(48 * 60_000L, limitMillis = hour)))
        assertEquals(FoxMood.HAPPY, evaluate(FoxAppUsage(48 * 60_000L - 1, limitMillis = hour)))
    }

    @Test
    fun timeLimit_exactly100Percent_isConcerned_justBelowIsAlert() {
        assertEquals(FoxMood.CONCERNED, evaluate(FoxAppUsage(hour, limitMillis = hour)))
        assertEquals(FoxMood.ALERT, evaluate(FoxAppUsage(hour - 1, limitMillis = hour)))
        assertEquals(FoxMood.CONCERNED, evaluate(FoxAppUsage(2 * hour, limitMillis = hour)))
    }

    @Test
    fun swipeLimit_exactly80And100Percent() {
        assertEquals(FoxMood.HAPPY, evaluate(FoxAppUsage(busy, swipes = 79, swipeAllowance = 100)))
        assertEquals(FoxMood.ALERT, evaluate(FoxAppUsage(busy, swipes = 80, swipeAllowance = 100)))
        assertEquals(FoxMood.ALERT, evaluate(FoxAppUsage(busy, swipes = 99, swipeAllowance = 100)))
        assertEquals(FoxMood.CONCERNED, evaluate(FoxAppUsage(busy, swipes = 100, swipeAllowance = 100)))
    }

    @Test
    fun limitsWinOverLittleUsage_andTheWorstAppDecides() {
        // A 4-minute limit reached with only 4 minutes used: concerned, not sleepy.
        assertEquals(FoxMood.CONCERNED, evaluate(FoxAppUsage(4 * 60_000L, limitMillis = 4 * 60_000L)))
        assertEquals(
            FoxMood.CONCERNED,
            evaluate(FoxAppUsage(busy, limitMillis = 2 * hour), FoxAppUsage(busy, swipes = 120, swipeAllowance = 100)),
        )
        assertEquals(
            FoxMood.ALERT,
            evaluate(FoxAppUsage(busy, limitMillis = 2 * hour), FoxAppUsage(50 * 60_000L, limitMillis = hour)),
        )
    }

    @Test
    fun zeroLimits_areIgnored() {
        assertEquals(FoxMood.HAPPY, evaluate(FoxAppUsage(busy, limitMillis = 0, swipes = 5, swipeAllowance = 0)))
    }

    @Test
    fun messages_neverRepeatTheLastOne_andCoverAll() {
        val random = Random(42)
        var last: Int? = null
        val seen = mutableSetOf<Int>()
        repeat(500) {
            val next = FoxMessages.nextIndex(20, last, random)
            assertTrue(next in 0 until 20)
            assertNotEquals(last, next)
            seen += next
            last = next
        }
        assertEquals(20, seen.size)
    }

    @Test
    fun messages_singleMessage_andStaleLast() {
        assertEquals(0, FoxMessages.nextIndex(1, last = 0))
        assertTrue(FoxMessages.nextIndex(3, last = 7, random = Random(1)) in 0 until 3)
    }
}
