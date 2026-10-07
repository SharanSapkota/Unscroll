package com.unscroll.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.Motion

/**
 * A small capsule: the trend chip ("-18% vs yesterday"), the tracking status, "32 min left".
 * Colored by [level], or neutral when it is null. Clickable when [onClick] is set.
 */
@Composable
fun ProgressPill(
    text: String,
    modifier: Modifier = Modifier,
    level: ProgressLevel? = null,
    @DrawableRes icon: Int? = null,
    onClick: (() -> Unit)? = null,
) {
    val background by animateColorAsState(
        targetValue = level?.container ?: MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(Motion.SHORT),
        label = "pillBackground",
    )
    val content by animateColorAsState(
        targetValue = level?.onContainer ?: MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(Motion.SHORT),
        label = "pillContent",
    )
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .then(
                if (onClick != null) {
                    Modifier
                        .heightIn(min = Dimens.touchTarget)
                        .clickable(role = Role.Button, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = Dimens.spaceM, vertical = Dimens.spaceXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(Dimens.iconSmall),
            )
        } else if (level != null) {
            Box(
                modifier = Modifier
                    .size(Dimens.dot)
                    .clip(CircleShape)
                    .background(level.color),
            )
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
    }
}

/** A thin rounded bar toward a limit: green, amber, red. [progress] is clamped to 0..1. */
@Composable
fun LimitBar(progress: Float, modifier: Modifier = Modifier, level: ProgressLevel = ProgressLevel.of(progress)) {
    val color by animateColorAsState(level.color, tween(Motion.MEDIUM), label = "limitBar")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.progressBar)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(CircleShape)
                .background(color),
        )
    }
}

/** Neutral background for decorative boxes; exposed for previews. */
val placeholderColor: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
