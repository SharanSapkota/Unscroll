package com.unscroll.app.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.unscroll.app.R
import com.unscroll.app.domain.blocking.SwipeLimitRules
import com.unscroll.app.ui.block.PhraseInput
import com.unscroll.app.ui.durationText
import com.unscroll.app.ui.theme.UnscrollTheme

/** "Swipe limit reached": the count, today's time, and "Go home". No countdown, no animation. */
@Composable
internal fun SwipeLimitCoverContent(
    state: SwipeCoverState,
    onGoHome: () -> Unit,
    onAccessGranted: () -> Unit,
) {
    var typingPhrase by rememberSaveable(state.packageName) { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.swipe_cover_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = pluralStringResource(R.plurals.swipe_cover_count, state.swipes, state.swipes, state.appName),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.swipe_cover_today, durationText(state.todayMillis)),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onGoHome, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.swipe_cover_go_home))
            }
            if (state.accessAllowed) {
                if (typingPhrase) {
                    Text(
                        text = stringResource(R.string.swipe_cover_access_explanation, SwipeLimitRules.EXTENSION_SWIPES),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PhraseInput(onUnlocked = onAccessGranted, modifier = Modifier.fillMaxWidth())
                    TextButton(onClick = { typingPhrase = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                } else {
                    TextButton(onClick = { typingPhrase = true }) {
                        Text(stringResource(R.string.swipe_cover_need_access))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SwipeLimitCoverPreview() {
    UnscrollTheme {
        SwipeLimitCoverContent(
            state = SwipeCoverState("com.instagram.android", "Instagram", 100, 42 * 60_000L, accessAllowed = true),
            onGoHome = {},
            onAccessGranted = {},
        )
    }
}
