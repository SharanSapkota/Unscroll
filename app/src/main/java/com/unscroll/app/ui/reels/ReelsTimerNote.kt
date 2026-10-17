package com.unscroll.app.ui.reels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.unscroll.app.R
import com.unscroll.app.domain.reels.ReelsTimerStatus
import com.unscroll.app.ui.durationText
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.UnscrollTheme

/**
 * Why the Reels-only timer falls back to the whole-app timer for [appName] (Settings › Timer pill,
 * App detail). Nothing for the reasons that need no note. "Review" opens the updated disclosure.
 */
@Composable
fun ReelsTimerNote(
    appName: String,
    availability: ReelsTimerStatus,
    onReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!availability.showsNote) return
    val text = when (availability) {
        ReelsTimerStatus.UNSUPPORTED_VERSION -> stringResource(R.string.reels_note_unsupported_version, appName)
        ReelsTimerStatus.NEEDS_CONSENT -> stringResource(R.string.reels_note_needs_consent, appName)
        ReelsTimerStatus.SERVICE_OFF -> stringResource(R.string.reels_note_service_off, appName)
        else -> stringResource(R.string.reels_note_unreadable, appName)
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs)) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (availability == ReelsTimerStatus.NEEDS_CONSENT) {
            TextButton(onClick = onReview) { Text(stringResource(R.string.reels_review)) }
        }
    }
}

/** "Reels 12 min · 86 swipes" ("For You" for TikTok): today's part of the app's time spent in Reels. */
@Composable
fun ReelsLine(
    forYou: Boolean,
    millis: Long,
    swipes: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodySmall,
) {
    val swipesText = pluralStringResource(R.plurals.reels_swipes, swipes, swipes)
    Text(
        text = stringResource(if (forYou) R.string.reels_line_tiktok else R.string.reels_line, durationText(millis), swipesText),
        style = style,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
        maxLines = 1,
    )
}

@PreviewLightDark
@Composable
private fun ReelsTimerNotePreview() {
    UnscrollTheme {
        Column {
            ReelsLine(forYou = false, millis = 12 * 60_000L, swipes = 86)
            ReelsTimerStatus.entries.forEach { ReelsTimerNote("TikTok", it, onReview = {}) }
        }
    }
}
