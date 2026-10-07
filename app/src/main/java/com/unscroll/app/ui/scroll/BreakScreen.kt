package com.unscroll.app.ui.scroll

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.R
import com.unscroll.app.ui.durationText
import com.unscroll.app.ui.theme.UnscrollTheme
import com.unscroll.app.util.appLabel

@Composable
fun BreakScreen(
    state: BreakUiState,
    onKeepScrolling: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val appName = remember(state.packageName) { context.packageManager.appLabel(state.packageName) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(Dimens.spaceXl),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceXl, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.break_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = pluralStringResource(
                    R.plurals.break_summary,
                    state.swipes,
                    state.swipes,
                    appName,
                    durationText(state.sessionMillis),
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            BreathingCircle()
            if (state.secondsLeft > 0) {
                Text(
                    text = stringResource(R.string.break_countdown, state.secondsLeft),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spaceS),
                ) {
                    Button(onClick = onDone, modifier = Modifier.fillMaxWidth(), enabled = state.canDecide) {
                        Text(stringResource(R.string.break_done))
                    }
                    OutlinedButton(onClick = onKeepScrolling, modifier = Modifier.fillMaxWidth(), enabled = state.canDecide) {
                        Text(stringResource(R.string.break_keep_scrolling))
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BreakScreenPreview() {
    UnscrollTheme {
        BreakScreen(
            state = BreakUiState("com.instagram.android", swipes = 100, sessionMillis = 754_000, secondsLeft = 0),
            onKeepScrolling = {},
            onDone = {},
        )
    }
}
