package com.unscroll.app.util

import java.util.Locale

/**
 * Formats an elapsed duration as a stopwatch string: `m:ss` under an hour, `h:mm:ss` otherwise.
 * Negative durations are treated as zero.
 */
fun formatElapsed(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}
