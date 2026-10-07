package com.unscroll.app.ui.scroll

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.R
import kotlin.math.abs
import kotlin.math.cos

/** One full breath: about 4 s in, 4 s out. */
private const val BREATH_MILLIS = 8_000

/** The break screen's circle: grows for ~4 s (breathe in) and shrinks for ~4 s (breathe out). */
@Composable
internal fun BreathingCircle() {
    val transition = rememberInfiniteTransition(label = "breathing")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(BREATH_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "breathPhase",
    )
    // 0 → 0.5: in, 0.5 → 1: out. A smooth ease on each half.
    val half = 1f - abs(phase * 2f - 1f)
    val eased = (1f - cos(half * Math.PI.toFloat())) / 2f
    val scale = 0.55f + 0.45f * eased
    val breathingIn = phase < 0.5f
    val color = MaterialTheme.colorScheme.primary
    val label = stringResource(if (breathingIn) R.string.break_breathe_in else R.string.break_breathe_out)
    Box(
        modifier = Modifier
            .size(Dimens.breathingCircle)
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
