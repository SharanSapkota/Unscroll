package com.unscroll.app.ui.apps

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.MaterialTheme
import com.unscroll.app.R
import com.unscroll.app.domain.blocking.BlockDecision
import com.unscroll.app.domain.blocking.BlockReason
import com.unscroll.app.domain.blocking.LimitSettings
import com.unscroll.app.domain.blocking.QuickBlockTarget
import com.unscroll.app.domain.blocking.QuickStatus
import com.unscroll.app.ui.components.ProgressLevel
import com.unscroll.app.ui.components.color
import com.unscroll.app.ui.section.nameRes
import java.util.Date
import kotlin.math.ceil

/**
 * "32 min left", "Blocked · 12 min left", "Reels blocked · until you turn it off", "Blocked until
 * 22:00", "No limits": the app's status in a few words. [quick] is the running quick block, if any.
 */
@Composable
fun limitStatusText(decision: BlockDecision, settings: LimitSettings, quick: QuickStatus? = null): String {
    val context = LocalContext.current
    return when (decision) {
        is BlockDecision.Allowed -> {
            val remaining = decision.remainingMillis
            when {
                quick != null && quick.target == QuickBlockTarget.REELS -> quickStatusText(quick)
                remaining != null -> {
                    val minutes = ceil(remaining / MINUTE).toInt()
                    pluralStringResource(R.plurals.status_minutes_left, minutes, minutes)
                }
                settings.hasAnyRule -> stringResource(R.string.status_allowed)
                else -> stringResource(R.string.status_no_limits)
            }
        }
        is BlockDecision.Blocked -> when (decision.reason) {
            BlockReason.BLOCKED_ENTIRE_APP -> quick?.takeIf { it.target == QuickBlockTarget.ENTIRE_APP }
                ?.let { quickStatusText(it) }
                ?: stringResource(R.string.status_blocked)
            BlockReason.DAILY_LIMIT_REACHED, BlockReason.SWIPE_LIMIT_REACHED -> stringResource(R.string.status_limit_reached)
            BlockReason.INSIDE_SCHEDULE -> decision.until?.let {
                stringResource(R.string.status_blocked_until, DateFormat.getTimeFormat(context).format(Date(it)))
            } ?: stringResource(R.string.status_blocked)
        }
    }
}

/** "Blocked · 12 min left", "Shorts blocked · until you turn it off". */
@Composable
private fun quickStatusText(quick: QuickStatus): String {
    val remaining = quick.remainingMillis
    val section = quick.section?.let { stringResource(it.nameRes) }
    return if (remaining != null) {
        val minutes = ceil(remaining / MINUTE).toInt()
        if (section != null) {
            pluralStringResource(R.plurals.status_section_blocked_left, minutes, section, minutes)
        } else {
            pluralStringResource(R.plurals.status_blocked_left, minutes, minutes)
        }
    } else {
        if (section != null) {
            stringResource(R.string.status_section_blocked_until_off, section)
        } else {
            stringResource(R.string.status_blocked_until_off)
        }
    }
}

/** Green while allowed, amber in the last 20 % of a daily limit or with reels blocked, red when blocked. */
@Composable
fun limitStatusColor(decision: BlockDecision, settings: LimitSettings, quick: QuickStatus? = null): Color = when {
    decision is BlockDecision.Blocked -> ProgressLevel.DANGER.color
    // Open, but its short-video section is blocked.
    quick?.target == QuickBlockTarget.REELS -> ProgressLevel.WARN.color
    else -> {
        val remaining = (decision as BlockDecision.Allowed).remainingMillis
        val limit = settings.dailyLimitMinutes
        if (remaining != null && limit != null && limit > 0) {
            ProgressLevel.of(1f - remaining / (limit * MINUTE).toFloat()).color
        } else if (settings.hasAnyRule) {
            ProgressLevel.GOOD.color
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
}

private const val MINUTE = 60_000.0
