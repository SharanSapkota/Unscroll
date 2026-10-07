package com.unscroll.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.unscroll.app.R
import com.unscroll.app.domain.overlay.ColorThresholds
import com.unscroll.app.domain.overlay.OverlaySettings
import com.unscroll.app.domain.overlay.PillRules
import com.unscroll.app.domain.overlay.PillSize
import com.unscroll.app.domain.overlay.SwipeColorThresholds
import com.unscroll.app.overlay.TimerPill
import com.unscroll.app.overlay.TimerPillState
import com.unscroll.app.ui.components.RowDivider
import com.unscroll.app.ui.components.SegmentedControl
import com.unscroll.app.ui.components.SettingRow
import com.unscroll.app.ui.components.SettingSwitchRow
import com.unscroll.app.ui.components.SettingsGroup
import com.unscroll.app.ui.durationText
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.UnscrollTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

internal class PillActions(
    val onEnabled: (Boolean) -> Unit = {},
    val onTodayTotal: (Boolean) -> Unit = {},
    val onSwipes: (Boolean) -> Unit = {},
    val onSize: (PillSize) -> Unit = {},
    val onOpacity: (Float) -> Unit = {},
    val onTimeColors: (Int, Int) -> Unit = { _, _ -> },
    val onSwipeColors: (Int, Int) -> Unit = { _, _ -> },
    val onResetPosition: () -> Unit = {},
)

/**
 * Settings › Timer pill. A live preview at the top follows the sliders while they move; each
 * value is saved when the finger lifts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimerPillGroup(
    settings: OverlaySettings,
    canDrawOverlays: Boolean,
    positionReset: Boolean,
    actions: PillActions,
) {
    var opacity by remember(settings.opacity) { mutableFloatStateOf(settings.opacity) }
    var time by remember(settings.thresholds) {
        mutableStateOf(settings.thresholds.warningAfterMinutes.toFloat()..settings.thresholds.dangerAfterMinutes.toFloat())
    }
    var swipes by remember(settings.swipeThresholds) {
        mutableStateOf(
            settings.swipeThresholds.warningAfterSwipes.toFloat()..settings.swipeThresholds.dangerAfterSwipes.toFloat(),
        )
    }
    val draft = settings.copy(
        opacity = opacity,
        thresholds = ColorThresholds(time.start.roundToInt(), time.endInclusive.roundToInt()).normalized(),
        swipeThresholds = SwipeColorThresholds(swipes.start.roundToInt(), swipes.endInclusive.roundToInt()).normalized(),
    )
    SettingsGroup {
        PillPreview(draft)
        if (!canDrawOverlays) {
            Text(
                text = stringResource(R.string.settings_pill_no_permission),
                style = MaterialTheme.typography.bodySmall,
                color = UnscrollTheme.status.danger,
                modifier = Modifier.padding(horizontal = Dimens.spaceL),
            )
        }
        SettingSwitchRow(
            title = stringResource(R.string.settings_pill_show),
            icon = R.drawable.ic_layers,
            checked = settings.enabled,
            onCheckedChange = actions.onEnabled,
        )
        SettingSwitchRow(
            title = stringResource(R.string.settings_pill_today),
            icon = R.drawable.ic_timer,
            checked = settings.showTodayTotal,
            onCheckedChange = actions.onTodayTotal,
        )
        SettingSwitchRow(
            title = stringResource(R.string.settings_pill_swipes),
            icon = R.drawable.ic_swipe,
            checked = settings.showSwipes,
            onCheckedChange = actions.onSwipes,
        )
        RowDivider()
        Column(
            modifier = Modifier.padding(Dimens.spaceL),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceM),
        ) {
            SegmentedControl(
                options = PillSize.entries,
                selected = settings.size,
                label = {
                    stringResource(
                        when (it) {
                            PillSize.SMALL -> R.string.settings_pill_size_small
                            PillSize.MEDIUM -> R.string.settings_pill_size_medium
                        },
                    )
                },
                onSelect = actions.onSize,
            )
            SliderLabel(
                title = stringResource(R.string.settings_pill_opacity),
                value = stringResource(R.string.value_percent, (opacity * PERCENT).roundToInt()),
            )
            Slider(
                value = opacity,
                onValueChange = { opacity = it },
                onValueChangeFinished = { actions.onOpacity(opacity) },
                valueRange = OverlaySettings.MIN_OPACITY..OverlaySettings.MAX_OPACITY,
            )
            SliderLabel(
                title = stringResource(R.string.settings_pill_time_colors),
                value = stringResource(
                    R.string.value_minutes_range,
                    draft.thresholds.warningAfterMinutes,
                    draft.thresholds.dangerAfterMinutes,
                ),
            )
            RangeSlider(
                value = time,
                onValueChange = { time = it },
                onValueChangeFinished = {
                    actions.onTimeColors(time.start.roundToInt(), time.endInclusive.roundToInt())
                },
                valueRange = ColorThresholds.MIN_MINUTES.toFloat()..ColorThresholds.MAX_MINUTES.toFloat(),
            )
            SliderLabel(
                title = stringResource(R.string.settings_pill_swipe_colors),
                value = stringResource(
                    R.string.value_count_range,
                    draft.swipeThresholds.warningAfterSwipes,
                    draft.swipeThresholds.dangerAfterSwipes,
                ),
            )
            RangeSlider(
                value = swipes,
                onValueChange = { swipes = it },
                onValueChangeFinished = {
                    actions.onSwipeColors(swipes.start.roundToInt(), swipes.endInclusive.roundToInt())
                },
                valueRange = SwipeColorThresholds.MIN_SWIPES.toFloat()..SwipeColorThresholds.MAX_SWIPES.toFloat(),
            )
        }
        RowDivider()
        SettingRow(
            title = stringResource(R.string.settings_pill_reset_position),
            value = if (positionReset) stringResource(R.string.value_done) else null,
            showChevron = false,
            onClick = actions.onResetPosition,
        )
    }
}

@Composable
private fun SliderLabel(title: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * Live preview: cycles through a calm, warning and danger time for the current thresholds, so
 * every setting (colors, size, opacity, today's total, swipes) shows without opening another app.
 */
@Composable
internal fun PillPreview(settings: OverlaySettings) {
    val thresholds = settings.thresholds
    val sampleMinutes = listOf(
        thresholds.warningAfterMinutes / 2,
        (thresholds.warningAfterMinutes + thresholds.dangerAfterMinutes) / 2,
        thresholds.dangerAfterMinutes + 2,
    )
    var sampleIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(PREVIEW_STEP_MILLIS)
            sampleIndex = (sampleIndex + 1) % sampleMinutes.size
        }
    }
    val elapsed = sampleMinutes[sampleIndex] * MINUTE + PREVIEW_SECONDS_MILLIS
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(Dimens.pillPreviewHeight),
        contentAlignment = Alignment.Center,
    ) {
        TimerPill(
            TimerPillState(
                appName = stringResource(R.string.settings_pill_preview_app),
                elapsedText = PillRules.formatTime(elapsed),
                todayText = if (settings.showTodayTotal) {
                    stringResource(R.string.overlay_today_total, durationText(elapsed + PREVIEW_TODAY_EXTRA_MILLIS))
                } else {
                    null
                },
                level = PillRules.levelFor(elapsed, thresholds),
                size = settings.size,
                opacity = settings.opacity,
                collapsed = false,
                swipesText = if (settings.showSwipes) {
                    pluralStringResource(R.plurals.overlay_swipes, PREVIEW_SWIPES, PREVIEW_SWIPES)
                } else {
                    null
                },
            ),
        )
    }
}

private const val PERCENT = 100
private const val MINUTE = 60_000L
private const val PREVIEW_STEP_MILLIS = 2_000L
private const val PREVIEW_SECONDS_MILLIS = 41_000L
private const val PREVIEW_TODAY_EXTRA_MILLIS = 25 * 60_000L
private const val PREVIEW_SWIPES = 86

@PreviewLightDark
@Composable
private fun TimerPillGroupPreview() {
    UnscrollTheme {
        Surface {
            Column(modifier = Modifier.padding(Dimens.screenPadding)) {
                TimerPillGroup(
                    settings = OverlaySettings(showTodayTotal = true),
                    canDrawOverlays = false,
                    positionReset = true,
                    actions = PillActions(),
                )
            }
        }
    }
}
