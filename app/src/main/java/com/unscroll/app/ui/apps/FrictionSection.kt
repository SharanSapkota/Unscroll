package com.unscroll.app.ui.apps

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.unscroll.app.R
import com.unscroll.app.domain.friction.FrictionSettings
import com.unscroll.app.domain.scroll.SwipeBreakTracker
import kotlin.math.roundToInt

/**
 * Per-app "Pauses and nudges" settings, collapsed by default under each app card. Changes apply
 * immediately: they only add or remove reminders, they never unblock anything.
 */
@Composable
fun FrictionSection(
    settings: FrictionSettings,
    onChange: ((FrictionSettings) -> FrictionSettings) -> Unit,
    onReset: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    HorizontalDivider()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = expanded, role = Role.Button, onValueChange = { expanded = it })
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.friction_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(if (expanded) R.string.friction_hide else R.string.friction_show),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    if (!expanded) return

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // Pause screen
        ToggleRow(
            title = stringResource(R.string.friction_pause),
            description = stringResource(R.string.friction_pause_description),
            checked = settings.pauseEnabled,
            onCheckedChange = { on -> onChange { it.copy(pauseEnabled = on) } },
        )
        if (settings.pauseEnabled) {
            var seconds by remember(settings.pauseSeconds) { mutableFloatStateOf(settings.pauseSeconds.toFloat()) }
            Text(
                text = stringResource(R.string.friction_pause_seconds, seconds.roundToInt()),
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = seconds,
                onValueChange = { seconds = it },
                onValueChangeFinished = { onChange { it.copy(pauseSeconds = seconds.roundToInt()) } },
                valueRange = FrictionSettings.MIN_PAUSE_SECONDS.toFloat()..FrictionSettings.MAX_PAUSE_SECONDS.toFloat(),
                steps = FrictionSettings.MAX_PAUSE_SECONDS - FrictionSettings.MIN_PAUSE_SECONDS - 1,
            )
        }

        // Open-count nudges
        ToggleRow(
            title = stringResource(R.string.friction_nudges),
            description = stringResource(R.string.friction_nudges_description),
            checked = settings.nudgesEnabled,
            onCheckedChange = { on -> onChange { it.copy(nudgesEnabled = on) } },
        )
        if (settings.nudgesEnabled) {
            ChipRow(
                values = FrictionSettings.THRESHOLD_PRESETS,
                isSelected = { it in settings.nudgeThresholds },
                label = { stringResource(R.string.friction_opens_value, it) },
                onClick = { value ->
                    onChange {
                        val current = it.nudgeThresholds
                        it.copy(nudgeThresholds = if (value in current) current - value else current + value)
                    }
                },
            )
        }

        // Break reminders
        ToggleRow(
            title = stringResource(R.string.friction_breaks),
            description = stringResource(R.string.friction_breaks_description),
            checked = settings.breakRemindersEnabled,
            onCheckedChange = { on -> onChange { it.copy(breakRemindersEnabled = on) } },
        )
        if (settings.breakRemindersEnabled) {
            ChipRow(
                values = FrictionSettings.BREAK_PRESETS,
                isSelected = { it == settings.breakIntervalMinutes },
                label = { stringResource(R.string.friction_every_minutes, it) },
                onClick = { value -> onChange { it.copy(breakIntervalMinutes = value) } },
            )
        }

        // Limit warnings
        ToggleRow(
            title = stringResource(R.string.friction_limit_warnings),
            description = stringResource(R.string.friction_limit_warnings_description),
            checked = settings.limitWarningsEnabled,
            onCheckedChange = { on -> onChange { it.copy(limitWarningsEnabled = on) } },
        )

        // Experimental tint
        ToggleRow(
            title = stringResource(R.string.friction_tint),
            description = stringResource(R.string.friction_tint_description),
            checked = settings.tintEnabled,
            onCheckedChange = { on -> onChange { it.copy(tintEnabled = on) } },
        )

        // Take a break after N swipes (M7). Only does something while scroll counting is on.
        val swipeBreakAfter = settings.swipeBreakAfter
        ToggleRow(
            title = stringResource(R.string.friction_swipe_break),
            description = stringResource(R.string.friction_swipe_break_description),
            checked = swipeBreakAfter != null,
            onCheckedChange = { on ->
                onChange { it.copy(swipeBreakAfter = if (on) SwipeBreakTracker.DEFAULT_BREAK_AFTER else null) }
            },
        )
        if (swipeBreakAfter != null) {
            ChipRow(
                values = SwipeBreakTracker.PRESETS,
                isSelected = { it == swipeBreakAfter },
                label = { stringResource(R.string.friction_swipes_value, it) },
                onClick = { value -> onChange { it.copy(swipeBreakAfter = value) } },
            )
        }

        TextButton(onClick = onReset) {
            Text(stringResource(R.string.friction_reset))
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ChipRow(
    values: List<Int>,
    isSelected: (Int) -> Boolean,
    label: @Composable (Int) -> String,
    onClick: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        values.forEach { value ->
            FilterChip(
                selected = isSelected(value),
                onClick = { onClick(value) },
                label = { Text(label(value)) },
            )
        }
    }
}
