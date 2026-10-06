package com.unscroll.app.ui.pause

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.unscroll.app.R
import com.unscroll.app.ui.theme.UnscrollTheme
import com.unscroll.app.util.appLabel
import kotlin.math.abs

/** One full breath: about 4 s in, 4 s out. */
private const val BREATH_MILLIS = 8_000

@Composable
fun PauseScreen(
    state: PauseUiState,
    onContinue: () -> Unit,
    onNeverMind: () -> Unit,
) {
    val context = LocalContext.current
    val appName = remember(state.packageName) { context.packageManager.appLabel(state.packageName) }
    val prompts = stringArrayResource(R.array.pause_prompts)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.pause_title, appName),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            BreathingCircle()
            Text(
                text = prompts[state.promptIndex.mod(prompts.size)],
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            if (state.secondsLeft > 0) {
                Text(
                    text = stringResource(R.string.pause_countdown, state.secondsLeft),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(onClick = onNeverMind, modifier = Modifier.fillMaxWidth(), enabled = state.canDecide) {
                        Text(stringResource(R.string.pause_never_mind))
                    }
                    OutlinedButton(onClick = onContinue, modifier = Modifier.fillMaxWidth(), enabled = state.canDecide) {
                        Text(stringResource(R.string.pause_continue, appName))
                    }
                }
            }
        }
    }
}

/** A circle that grows for ~4 s (breathe in) and shrinks for ~4 s (breathe out). */
@Composable
private fun BreathingCircle() {
    val transition = rememberInfiniteTransition(label = "breathing")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BREATH_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "breathPhase",
    )
    // 0 → 0.5: in, 0.5 → 1: out. A smooth ease on each half.
    val half = 1f - abs(phase * 2f - 1f)
    val eased = (1f - kotlin.math.cos(half * Math.PI.toFloat())) / 2f
    val scale = 0.55f + 0.45f * eased
    val breathingIn = phase < 0.5f
    val color = MaterialTheme.colorScheme.primary
    val label = stringResource(if (breathingIn) R.string.pause_breathe_in else R.string.pause_breathe_out)
    Box(
        modifier = Modifier
            .size(220.dp)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = color.copy(alpha = 0.15f), radius = size.minDimension / 2)
            drawCircle(color = color.copy(alpha = 0.55f), radius = size.minDimension / 2 * scale)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PauseScreenPreview() {
    UnscrollTheme {
        PauseScreen(
            state = PauseUiState(packageName = "com.instagram.android", secondsLeft = 0, promptIndex = 0),
            onContinue = {},
            onNeverMind = {},
        )
    }
}
