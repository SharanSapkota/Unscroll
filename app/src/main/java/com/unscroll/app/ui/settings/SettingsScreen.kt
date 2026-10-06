package com.unscroll.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unscroll.app.BuildConfig
import com.unscroll.app.R
import com.unscroll.app.domain.overlay.ColorThresholds
import com.unscroll.app.domain.overlay.OverlaySettings
import com.unscroll.app.domain.overlay.PillRules
import com.unscroll.app.domain.overlay.PillSize
import com.unscroll.app.overlay.TimerPill
import com.unscroll.app.overlay.TimerPillState
import com.unscroll.app.ui.durationText
import com.unscroll.app.ui.theme.UnscrollTheme
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val trackingEnabled by viewModel.trackingEnabled.collectAsStateWithLifecycle()
    val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
    val canDrawOverlays by viewModel.canDrawOverlays.collectAsStateWithLifecycle()
    val positionReset by viewModel.positionReset.collectAsStateWithLifecycle()
    val sampleSessionsAdded by viewModel.sampleSessionsAdded.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
    ) {
        Text(
            text = stringResource(R.string.nav_settings),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        SwitchRow(
            title = stringResource(R.string.settings_tracking_title),
            description = stringResource(R.string.settings_tracking_description),
            checked = trackingEnabled,
            onCheckedChange = viewModel::setTrackingEnabled,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        OverlaySection(
            settings = overlaySettings,
            canDrawOverlays = canDrawOverlays,
            positionReset = positionReset,
            actions = OverlayActions(
                onEnabledChange = viewModel::setOverlayEnabled,
                onShowTodayTotalChange = viewModel::setShowTodayTotal,
                onWarningMinutesChange = viewModel::setWarningMinutes,
                onDangerMinutesChange = viewModel::setDangerMinutes,
                onSizeChange = viewModel::setPillSize,
                onOpacityChange = viewModel::setOpacity,
                onResetPosition = viewModel::resetPosition,
            ),
        )
        if (BuildConfig.DEBUG) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            DebugTools(
                sampleSessionsAdded = sampleSessionsAdded,
                onInsertSampleData = viewModel::insertSampleData,
            )
        }
    }
}

private class OverlayActions(
    val onEnabledChange: (Boolean) -> Unit,
    val onShowTodayTotalChange: (Boolean) -> Unit,
    val onWarningMinutesChange: (Int) -> Unit,
    val onDangerMinutesChange: (Int) -> Unit,
    val onSizeChange: (PillSize) -> Unit,
    val onOpacityChange: (Float) -> Unit,
    val onResetPosition: () -> Unit,
)

@Composable
private fun OverlaySection(
    settings: OverlaySettings,
    canDrawOverlays: Boolean,
    positionReset: Boolean,
    actions: OverlayActions,
) {
    SectionTitle(stringResource(R.string.settings_overlay_title))
    if (!canDrawOverlays) {
        Text(
            text = stringResource(R.string.settings_overlay_permission_missing),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
    }
    PillPreview(settings)
    SwitchRow(
        title = stringResource(R.string.settings_overlay_enabled),
        description = stringResource(R.string.settings_overlay_enabled_description),
        checked = settings.enabled,
        onCheckedChange = actions.onEnabledChange,
    )
    SwitchRow(
        title = stringResource(R.string.settings_overlay_today_total),
        description = stringResource(R.string.settings_overlay_today_total_description),
        checked = settings.showTodayTotal,
        onCheckedChange = actions.onShowTodayTotalChange,
    )
    MinutesSlider(
        label = R.plurals.settings_overlay_warning_after,
        minutes = settings.thresholds.warningAfterMinutes,
        range = ColorThresholds.MIN_MINUTES..(ColorThresholds.MAX_MINUTES - 1),
        onMinutesChange = actions.onWarningMinutesChange,
    )
    MinutesSlider(
        label = R.plurals.settings_overlay_danger_after,
        minutes = settings.thresholds.dangerAfterMinutes,
        range = (ColorThresholds.MIN_MINUTES + 1)..ColorThresholds.MAX_MINUTES,
        onMinutesChange = actions.onDangerMinutesChange,
    )
    SizeSelector(settings.size, actions.onSizeChange)
    OpacitySlider(settings.opacity, actions.onOpacityChange)
    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedButton(onClick = actions.onResetPosition) {
            Text(stringResource(R.string.settings_overlay_reset_position))
        }
        if (positionReset) {
            Text(
                text = stringResource(R.string.settings_overlay_position_reset_done),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Live preview: cycles through a calm, warning and danger time for the current thresholds, so
 * every setting (colors, size, opacity, today's total) can be seen without opening another app.
 */
@Composable
private fun PillPreview(settings: OverlaySettings) {
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
    val elapsed = sampleMinutes[sampleIndex] * 60_000L + 41_000L
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_overlay_preview),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                TimerPill(
                    TimerPillState(
                        appName = stringResource(R.string.settings_overlay_preview_app),
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
                    ),
                )
            }
        }
    }
}

@Composable
private fun MinutesSlider(
    label: Int,
    minutes: Int,
    range: IntRange,
    onMinutesChange: (Int) -> Unit,
) {
    // Local value while dragging; saved once when the finger lifts.
    var value by remember(minutes) { mutableFloatStateOf(minutes.toFloat()) }
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
        Text(
            text = pluralStringResource(label, value.roundToInt(), value.roundToInt()),
            style = MaterialTheme.typography.bodyLarge,
        )
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onMinutesChange(value.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SizeSelector(selected: PillSize, onSelected: (PillSize) -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_overlay_size),
            style = MaterialTheme.typography.bodyLarge,
        )
        val sizes = PillSize.entries
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            sizes.forEachIndexed { index, size ->
                SegmentedButton(
                    selected = size == selected,
                    onClick = { onSelected(size) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = sizes.size),
                ) {
                    Text(
                        stringResource(
                            when (size) {
                                PillSize.SMALL -> R.string.settings_overlay_size_small
                                PillSize.MEDIUM -> R.string.settings_overlay_size_medium
                            },
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun OpacitySlider(opacity: Float, onOpacityChange: (Float) -> Unit) {
    var value by remember(opacity) { mutableFloatStateOf(opacity) }
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
        Text(
            text = stringResource(R.string.settings_overlay_opacity, (value * 100).roundToInt()),
            style = MaterialTheme.typography.bodyLarge,
        )
        Slider(
            value = value,
            onValueChange = { value = it },
            onValueChangeFinished = { onOpacityChange(value) },
            valueRange = OverlaySettings.MIN_OPACITY..OverlaySettings.MAX_OPACITY,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // The whole row handles the toggle, so the switch itself is not clickable.
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun DebugTools(sampleSessionsAdded: Int?, onInsertSampleData: () -> Unit) {
    SectionTitle(stringResource(R.string.settings_debug_title))
    Column(
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_debug_sample_data_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onInsertSampleData) {
            Text(stringResource(R.string.settings_debug_sample_data))
        }
        sampleSessionsAdded?.let { count ->
            Text(
                text = pluralStringResource(R.plurals.settings_debug_sample_data_added, count, count),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private const val PREVIEW_STEP_MILLIS = 2_000L
private const val PREVIEW_TODAY_EXTRA_MILLIS = 25 * 60_000L

@Preview(showBackground = true, heightDp = 1400)
@Composable
private fun OverlaySectionPreview() {
    UnscrollTheme {
        Column {
            OverlaySection(
                settings = OverlaySettings(showTodayTotal = true),
                canDrawOverlays = false,
                positionReset = true,
                actions = OverlayActions({}, {}, {}, {}, {}, {}, {}),
            )
        }
    }
}
