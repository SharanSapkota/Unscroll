package com.unscroll.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.Motion

/** An icon in a soft circle and one short line. */
@Composable
fun EmptyState(
    @DrawableRes icon: Int,
    text: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.spaceXxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceL),
    ) {
        Box(
            modifier = Modifier
                .size(Dimens.illustration)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(Dimens.appIcon),
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action()
    }
}

/** Soft pulsing blocks while data loads. */
@Composable
fun LoadingPlaceholder(modifier: Modifier = Modifier, blocks: Int = PLACEHOLDER_BLOCKS) {
    val pulse by rememberInfiniteTransition(label = "placeholder").animateFloat(
        initialValue = PULSE_LOW,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(Motion.MEDIUM * 3), RepeatMode.Reverse),
        label = "pulse",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.screenPadding)
            .alpha(pulse),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceL),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(HALF)
                .height(Dimens.placeholderLine)
                .clip(CircleShape)
                .background(placeholderColor),
        )
        repeat(blocks) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.placeholderBlock)
                    .clip(MaterialTheme.shapes.large)
                    .background(placeholderColor),
            )
        }
    }
}

private const val PLACEHOLDER_BLOCKS = 3
private const val PULSE_LOW = 0.4f
private const val HALF = 0.5f
