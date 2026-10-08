package com.unscroll.app.domain.fox

import kotlin.random.Random

/** How the fox looks. It only mirrors today's usage; it never asks for anything. */
enum class FoxMood {
    /** Under every limit. */
    HAPPY,

    /** Past 80 % of a daily time limit or a swipe limit. */
    ALERT,

    /** A limit is reached or exceeded. */
    CONCERNED,

    /** Very little usage today, or tracking is off. */
    SLEEPY,

    /** Calm and plain, for neutral news ("Plus ended"). Never chosen from usage. */
    NEUTRAL,
}

/** Today's usage of one tracked app, against its limits (null: no such limit). */
data class FoxAppUsage(
    val usedMillis: Long,
    val limitMillis: Long? = null,
    val swipes: Int = 0,
    /** Swipes allowed in the current swipe window (extensions included), or null without a swipe limit. */
    val swipeAllowance: Int? = null,
)

/** Picks the fox's mood from today's usage. Pure; the thresholds are the constants below. */
object FoxMoodEvaluator {

    /** From this share of a limit on, the fox is ALERT. */
    const val ALERT_FROM = 0.8

    /** At or past a limit, the fox is CONCERNED. */
    const val CONCERNED_FROM = 1.0

    /** Under this much time across tracked apps today, the fox is SLEEPY. */
    const val SLEEPY_BELOW_MILLIS = 5 * 60_000L

    fun evaluate(trackingEnabled: Boolean, apps: List<FoxAppUsage>): FoxMood {
        if (!trackingEnabled) return FoxMood.SLEEPY
        val highest = apps.maxOfOrNull { highestShare(it) } ?: 0.0
        return when {
            highest >= CONCERNED_FROM -> FoxMood.CONCERNED
            highest >= ALERT_FROM -> FoxMood.ALERT
            apps.sumOf { it.usedMillis } < SLEEPY_BELOW_MILLIS -> FoxMood.SLEEPY
            else -> FoxMood.HAPPY
        }
    }

    /** The larger of the time and swipe shares used; 0 without limits. */
    private fun highestShare(app: FoxAppUsage): Double {
        val time = app.limitMillis?.takeIf { it > 0 }?.let { app.usedMillis.toDouble() / it } ?: 0.0
        val swipes = app.swipeAllowance?.takeIf { it > 0 }?.let { app.swipes.toDouble() / it } ?: 0.0
        return maxOf(time, swipes)
    }
}

/** Which of a mood's messages to show next: random, but never the same one twice in a row. */
object FoxMessages {

    /** An index in 0 until [count], different from [last] when there is more than one message. */
    fun nextIndex(count: Int, last: Int?, random: Random = Random): Int {
        require(count > 0) { "No messages" }
        if (count == 1) return 0
        if (last == null || last !in 0 until count) return random.nextInt(count)
        // Pick among the others: skip over the last one.
        val pick = random.nextInt(count - 1)
        return if (pick >= last) pick + 1 else pick
    }
}

/** The fox settings: show it at all, and show its speech bubbles. Both on by default. */
data class FoxSettings(val showFox: Boolean = true, val messages: Boolean = true)
