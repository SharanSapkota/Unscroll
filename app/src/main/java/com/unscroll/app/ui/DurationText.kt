package com.unscroll.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.unscroll.app.R

/** Short human duration: "2h 5m", "12m" or "40s". */
@Composable
fun durationText(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0) / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    return when {
        hours > 0 -> stringResource(R.string.duration_hours_minutes, hours, minutes)
        minutes > 0 -> stringResource(R.string.duration_minutes, minutes)
        else -> stringResource(R.string.duration_seconds, totalSeconds)
    }
}
