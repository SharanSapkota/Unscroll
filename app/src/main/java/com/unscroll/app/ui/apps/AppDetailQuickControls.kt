package com.unscroll.app.ui.apps

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.unscroll.app.R
import com.unscroll.app.domain.blocking.BlockDuration
import com.unscroll.app.domain.blocking.LimitSettings
import com.unscroll.app.domain.blocking.QuickControls
import com.unscroll.app.domain.blocking.QuickControlsState
import com.unscroll.app.domain.blocking.QuickToggleState
import com.unscroll.app.domain.blocking.ReelsAvailability
import com.unscroll.app.domain.blocking.ReelsToggleState
import com.unscroll.app.domain.blocking.SectionAccess
import com.unscroll.app.domain.blocking.TimedBlock
import com.unscroll.app.domain.section.BlockedSection
import com.unscroll.app.domain.tracking.DefaultTrackedApps
import com.unscroll.app.ui.components.ChipGroup
import com.unscroll.app.ui.components.RowDivider
import com.unscroll.app.ui.components.SectionHeader
import com.unscroll.app.ui.components.SettingSwitchRow
import com.unscroll.app.ui.components.SettingsGroup
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.Motion
import com.unscroll.app.ui.theme.UnscrollTheme
import com.unscroll.app.util.formatElapsed

/** Callbacks for the quick toggles at the top of App detail. Every one applies at once. */
internal class QuickActions(
    val onEntire: (Boolean) -> Unit = {},
    val onEntireDuration: (BlockDuration) -> Unit = {},
    /** The reels toggle: the view model decides whether it toggles or sets something up first. */
    val onReels: (Boolean) -> Unit = {},
    val onReelsDuration: (BlockDuration) -> Unit = {},
)

/**
 * The first thing on App detail: "Block reels only" (Reels-capable apps only) and "Block entire
 * app", each with its duration chips and a live countdown while a timed block runs.
 */
@Composable
internal fun QuickControlsSection(state: QuickControlsState, actions: QuickActions) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
        SectionHeader(title = stringResource(R.string.quick_title), info = stringResource(R.string.quick_info))
        SettingsGroup {
            state.reels?.let { reels ->
                ReelsRow(reels, actions)
                RowDivider()
            }
            QuickToggleRow(
                title = stringResource(R.string.quick_block_entire),
                icon = R.drawable.ic_block,
                toggle = state.entireApp,
                caption = null,
                captionColor = MaterialTheme.colorScheme.onSurfaceVariant,
                enabled = true,
                onCheckedChange = actions.onEntire,
                onDuration = actions.onEntireDuration,
            )
        }
    }
}

@Composable
private fun ReelsRow(reels: ReelsToggleState, actions: QuickActions) {
    val warn = UnscrollTheme.status.warn
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val (caption, color) = when {
        // "Block entire app" covers the reels too.
        reels.included -> stringResource(R.string.quick_included) to muted
        reels.availability == ReelsAvailability.NOT_AVAILABLE -> stringResource(R.string.quick_not_available) to muted
        reels.toggle.on && reels.availability == ReelsAvailability.TURNED_OFF ->
            stringResource(R.string.section_detail_turned_off) to warn
        reels.toggle.on && reels.availability == ReelsAvailability.NEEDS_SERVICE ->
            stringResource(R.string.section_detail_enable_service) to warn
        reels.toggle.on -> null to muted
        reels.availability == ReelsAvailability.NEEDS_PLUS -> stringResource(R.string.quick_needs_plus) to muted
        else -> null to muted
    }
    QuickToggleRow(
        title = stringResource(reels.section.quickLabelRes),
        icon = R.drawable.ic_layers,
        // Included: shown on and disabled.
        toggle = if (reels.included) reels.toggle.copy(on = true, remainingMillis = null) else reels.toggle,
        caption = caption,
        captionColor = color,
        enabled = !reels.included && (reels.availability != ReelsAvailability.NOT_AVAILABLE || reels.toggle.on),
        showUntilOff = !reels.included,
        onCheckedChange = actions.onReels,
        onDuration = actions.onReelsDuration,
    )
}

/** One toggle row with its duration chips underneath (they wrap by scrolling, never clip). */
@Composable
private fun QuickToggleRow(
    title: String,
    icon: Int,
    toggle: QuickToggleState,
    caption: String?,
    captionColor: Color,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onDuration: (BlockDuration) -> Unit,
    showUntilOff: Boolean = true,
) {
    val remaining = toggle.remainingMillis
    val countdown = when {
        !toggle.on -> null
        // Rounded up, so "0:00 left" is never shown while still blocked.
        remaining != null -> stringResource(R.string.quick_left, formatElapsed(remaining + SECOND - 1))
        showUntilOff -> stringResource(R.string.quick_until_off)
        else -> null
    }
    Column {
        SettingSwitchRow(
            title = title,
            icon = icon,
            checked = toggle.on,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            subtitle = caption ?: countdown,
            subtitleColor = if (caption == null && remaining != null) MaterialTheme.colorScheme.primary else captionColor,
        )
        ChipGroup(
            options = BlockDuration.entries,
            isSelected = { it == toggle.duration },
            label = { stringResource(it.labelRes) },
            onSelect = onDuration,
            enabled = enabled,
            contentPadding = PaddingValues(start = Dimens.spaceL, end = Dimens.spaceL, bottom = Dimens.spaceM),
        )
    }
}

/**
 * "Advanced options": collapsed by default, with a clear chevron. Everything that existed before
 * the quick toggles lives in [content].
 */
@Composable
internal fun AdvancedOptions(open: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    val rotation by animateFloatAsState(if (open) HALF_TURN else 0f, tween(Motion.MEDIUM), label = "chevron")
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceL)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.touchTarget)
                .clickable(role = Role.Button, onClick = onToggle)
                .semantics { heading() }
                .padding(horizontal = Dimens.spaceXs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.advanced_options),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.ic_expand_more),
                contentDescription = stringResource(if (open) R.string.cd_collapse else R.string.cd_expand),
                modifier = Modifier
                    .size(Dimens.icon)
                    .rotate(rotation),
            )
        }
        AnimatedVisibility(visible = open) {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceL)) { content() }
        }
    }
}

/** "Block Reels only", "Block For You feed", "Block Shorts only". */
@get:StringRes
internal val BlockedSection.quickLabelRes: Int
    get() = when (this) {
        BlockedSection.REELS -> R.string.quick_block_reels
        BlockedSection.FOR_YOU -> R.string.quick_block_for_you
        BlockedSection.SHORTS -> R.string.quick_block_shorts
    }

@get:StringRes
internal val BlockDuration.labelRes: Int
    get() = when (this) {
        BlockDuration.MIN_15 -> R.string.duration_15m
        BlockDuration.MIN_30 -> R.string.duration_30m
        BlockDuration.HOUR_1 -> R.string.duration_1h
        BlockDuration.HOUR_2 -> R.string.duration_2h
        BlockDuration.UNTIL_OFF -> R.string.duration_until_off
    }

private const val HALF_TURN = 180f
private const val SECOND = 1_000L

// Previews: a Reels-capable app and a normal app; off, on with a countdown, entire app on.

private const val PREVIEW_NOW = 1_000_000_000L
private val PREVIEW_ACCESS = SectionAccess(rulesAvailable = true, isPlus = true, consented = true, serviceOn = true, turnedOff = false)

@Composable
private fun QuickPreview(packageName: String, settings: LimitSettings, access: SectionAccess = PREVIEW_ACCESS) {
    UnscrollTheme {
        Surface {
            Column(Modifier.padding(Dimens.spaceL)) {
                QuickControlsSection(QuickControls.build(packageName, settings, PREVIEW_NOW, access), QuickActions())
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun QuickReelsOffPreview() = QuickPreview(DefaultTrackedApps.INSTAGRAM, LimitSettings())

@PreviewLightDark
@Composable
private fun QuickReelsCountdownPreview() = QuickPreview(
    DefaultTrackedApps.INSTAGRAM,
    LimitSettings(reelsBlockedUntil = PREVIEW_NOW + 12 * 60_000L + 41_000L, lastReelsDuration = BlockDuration.MIN_15),
)

@PreviewLightDark
@Composable
private fun QuickEntireOnPreview() = QuickPreview(
    "com.google.android.youtube",
    LimitSettings(entireAppBlockedUntil = TimedBlock.FOREVER, reelsBlockedUntil = null),
)

@PreviewLightDark
@Composable
private fun QuickNotAvailablePreview() = QuickPreview(
    DefaultTrackedApps.FACEBOOK,
    LimitSettings(),
    PREVIEW_ACCESS.copy(rulesAvailable = false),
)

@PreviewLightDark
@Composable
private fun QuickNormalAppOffPreview() = QuickPreview("com.reddit.frontpage", LimitSettings())

@PreviewLightDark
@Composable
private fun QuickNormalAppCountdownPreview() = QuickPreview(
    "com.reddit.frontpage",
    LimitSettings(entireAppBlockedUntil = PREVIEW_NOW + 95 * 60_000L, lastEntireDuration = BlockDuration.HOUR_2),
)

@PreviewLightDark
@Composable
private fun AdvancedOptionsPreview() {
    UnscrollTheme {
        Surface {
            Column(Modifier.padding(Dimens.spaceL)) {
                AdvancedOptions(open = false, onToggle = {}) {}
            }
        }
    }
}
