package com.unscroll.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.unscroll.app.R
import com.unscroll.app.domain.fox.FoxMood
import com.unscroll.app.domain.insights.Insight
import com.unscroll.app.domain.insights.InsightCategory
import com.unscroll.app.domain.insights.InsightTone
import com.unscroll.app.ui.components.UnscrollCard
import com.unscroll.app.ui.fox.FoxMascot
import com.unscroll.app.ui.fox.LocalFoxSettings
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.Motion
import com.unscroll.app.ui.theme.UnscrollTheme

/**
 * One insight at a time: a small muted label, the big number in bold, the line, and the fox in
 * the message's mood. "Share" makes an image of it; "Next" shows another (while today allows).
 */
@Composable
internal fun InsightCard(
    insight: Insight,
    canShowNext: Boolean,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = stringResource(insight.category.labelRes),
) {
    var sharing by rememberSaveable { mutableStateOf(false) }
    UnscrollCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(Dimens.spaceXl),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceS),
        ) {
            AnimatedContent(
                targetState = insight,
                transitionSpec = { fadeIn(tween(Motion.MEDIUM)) togetherWith fadeOut(tween(Motion.SHORT)) },
                contentKey = { it.templateId },
                label = "insight",
            ) { shown -> InsightBody(shown, label) }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = { sharing = true }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_share),
                        contentDescription = null,
                        modifier = Modifier.size(Dimens.iconSmall),
                    )
                    Spacer(modifier = Modifier.width(Dimens.spaceS))
                    Text(stringResource(R.string.insights_share))
                }
                if (canShowNext) {
                    Spacer(modifier = Modifier.width(Dimens.spaceS))
                    FilledTonalButton(onClick = onNext) { Text(stringResource(R.string.insights_next)) }
                }
            }
        }
    }
    if (sharing) {
        InsightShareSheet(insight = insight, onDismiss = { sharing = false })
    }
}

/** The weekly life projection, on its day: the same card with its own label, no "Next". */
@Composable
internal fun WeeklyInsightCard(insight: Insight, modifier: Modifier = Modifier) {
    InsightCard(
        insight = insight,
        canShowNext = false,
        onNext = {},
        modifier = modifier,
        label = stringResource(R.string.insights_weekly_label),
    )
}

@Composable
private fun InsightBody(insight: Insight, label: String) {
    val fox = LocalFoxSettings.current.showFox
    Row(verticalAlignment = Alignment.Top) {
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceS),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            insight.highlight?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(text = insight.text, style = MaterialTheme.typography.bodyLarge)
        }
        if (fox) {
            Spacer(modifier = Modifier.width(Dimens.spaceM))
            FoxMascot(mood = insight.mood, showTail = false, animate = false, modifier = Modifier.size(Dimens.foxInsight))
        }
    }
}

internal val InsightCategory.labelRes: Int
    get() = when (this) {
        InsightCategory.THUMB -> R.string.insights_category_thumb
        InsightCategory.COULD_HAVE -> R.string.insights_category_could_have
        InsightCategory.LIFE -> R.string.insights_category_life
        InsightCategory.TRENDS -> R.string.insights_category_trends
        InsightCategory.WINS -> R.string.insights_category_wins
        InsightCategory.MILESTONES, InsightCategory.FOX -> R.string.insights_category_milestones
    }

@PreviewLightDark
@Composable
private fun InsightCardPreview() {
    UnscrollTheme {
        Surface {
            Column(modifier = Modifier.padding(Dimens.screenPadding), verticalArrangement = Arrangement.spacedBy(Dimens.spaceL)) {
                InsightCard(
                    insight = Insight(
                        templateId = "T01",
                        category = InsightCategory.THUMB,
                        tone = InsightTone.HONEST,
                        text = "Your thumb scrolled 1.4 km today. That's 4 Eiffel Towers stacked up.",
                        highlight = "1.4 km",
                        mood = FoxMood.ALERT,
                    ),
                    canShowNext = true,
                    onNext = {},
                )
                WeeklyInsightCard(
                    Insight(
                        templateId = "L06",
                        category = InsightCategory.LIFE,
                        tone = InsightTone.GENTLE,
                        text = "Projection: 6.7 years at this pace. Cut it in half and you get 3.3 years back.",
                        highlight = "6.7 years",
                        mood = FoxMood.HAPPY,
                    ),
                )
            }
        }
    }
}
