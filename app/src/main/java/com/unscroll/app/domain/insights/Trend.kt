package com.unscroll.app.domain.insights

import kotlin.math.abs
import kotlin.math.roundToInt

enum class TrendDirection { UP, DOWN, FLAT }

/** Change in usage from [previousMillis] to [currentMillis]. More usage is "UP". */
data class Trend(val currentMillis: Long, val previousMillis: Long) {

    val deltaMillis: Long get() = currentMillis - previousMillis

    /** Rounded percentage change, or null when there was no previous usage to compare with. */
    val percentChange: Int?
        get() = if (previousMillis == 0L) null else (deltaMillis * 100.0 / previousMillis).roundToInt()

    /** Differences under a minute count as no change. */
    val direction: TrendDirection
        get() = when {
            abs(deltaMillis) < FLAT_THRESHOLD_MILLIS -> TrendDirection.FLAT
            deltaMillis > 0 -> TrendDirection.UP
            else -> TrendDirection.DOWN
        }

    companion object {
        const val FLAT_THRESHOLD_MILLIS = 60_000L
    }
}
