package com.unscroll.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.unscroll.app.ui.theme.UnscrollTheme

/** Green, amber or red: the same escalation for time, swipes and limits everywhere. */
enum class ProgressLevel {
    GOOD,
    WARN,
    DANGER,
    ;

    companion object {
        /** Amber from this share of a limit on. */
        const val WARN_FROM = 0.8f

        /** [fraction] of a limit used: amber from 80 %, red once it is reached. */
        fun of(fraction: Float): ProgressLevel = when {
            fraction >= 1f -> DANGER
            fraction >= WARN_FROM -> WARN
            else -> GOOD
        }
    }
}

val ProgressLevel.color: Color
    @Composable get() = when (this) {
        ProgressLevel.GOOD -> UnscrollTheme.status.good
        ProgressLevel.WARN -> UnscrollTheme.status.warn
        ProgressLevel.DANGER -> UnscrollTheme.status.danger
    }

val ProgressLevel.container: Color
    @Composable get() = when (this) {
        ProgressLevel.GOOD -> UnscrollTheme.status.goodContainer
        ProgressLevel.WARN -> UnscrollTheme.status.warnContainer
        ProgressLevel.DANGER -> UnscrollTheme.status.dangerContainer
    }

val ProgressLevel.onContainer: Color
    @Composable get() = when (this) {
        ProgressLevel.GOOD -> UnscrollTheme.status.onGoodContainer
        ProgressLevel.WARN -> UnscrollTheme.status.onWarnContainer
        ProgressLevel.DANGER -> UnscrollTheme.status.onDangerContainer
    }
