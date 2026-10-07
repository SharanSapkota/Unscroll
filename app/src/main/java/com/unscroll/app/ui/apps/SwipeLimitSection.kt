package com.unscroll.app.ui.apps

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.unscroll.app.R
import com.unscroll.app.domain.blocking.LimitSettings
import com.unscroll.app.domain.blocking.SwipeLimitRules
import com.unscroll.app.domain.blocking.SwipeLimitScope

/** Callbacks for the swipe-limit controls. Weaker changes wait out the cooldown like any limit. */
internal class SwipeLimitActions(
    val onLimit: (Int?) -> Unit = {},
    val onScope: (SwipeLimitScope) -> Unit = {},
    val onSessionGap: (Int) -> Unit = {},
    val onAccessAllowed: (Boolean) -> Unit = {},
)

/**
 * "Swipe limit" on an app card: off, a preset or a custom number; per day or per session (with the
 * reset gap); and whether the cover offers "I need access". [settings] is what the card shows
 * (the pending target while a loosening change waits).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeLimitSection(
    settings: LimitSettings,
    scrollCountingActive: Boolean,
    actions: SwipeLimitActions,
) {
    var customOpen by rememberSaveable { mutableStateOf(false) }
    val limit = settings.swipeLimit
    HorizontalDivider()
    Text(text = stringResource(R.string.swipe_limit_title), style = MaterialTheme.typography.titleSmall)
    Text(
        text = stringResource(
            if (scrollCountingActive) R.string.swipe_limit_description else R.string.swipe_limit_needs_counting,
        ),
        style = MaterialTheme.typography.bodySmall,
        color = if (scrollCountingActive) {
            MaterialTheme.colorScheme.onSurfaceVariant
        } else {
            MaterialTheme.colorScheme.tertiary
        },
    )
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = limit == null,
            onClick = { actions.onLimit(null) },
            label = { Text(stringResource(R.string.apps_limit_none)) },
        )
        SwipeLimitRules.PRESETS.forEach { swipes ->
            FilterChip(
                selected = limit == swipes,
                onClick = { actions.onLimit(swipes) },
                label = { Text(stringResource(R.string.friction_swipes_value, swipes)) },
            )
        }
        val custom = limit != null && limit !in SwipeLimitRules.PRESETS
        FilterChip(
            selected = custom,
            onClick = { customOpen = true },
            label = {
                Text(
                    if (custom) {
                        stringResource(R.string.swipe_limit_custom_value, limit ?: 0)
                    } else {
                        stringResource(R.string.swipe_limit_custom)
                    },
                )
            },
        )
    }
    if (limit != null) {
        val scopes = SwipeLimitScope.entries
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            scopes.forEachIndexed { index, scope ->
                SegmentedButton(
                    selected = scope == settings.swipeLimitScope,
                    onClick = { actions.onScope(scope) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = scopes.size),
                ) {
                    Text(
                        stringResource(
                            when (scope) {
                                SwipeLimitScope.DAY -> R.string.swipe_limit_per_day
                                SwipeLimitScope.SESSION -> R.string.swipe_limit_per_session
                            },
                        ),
                    )
                }
            }
        }
        if (settings.swipeLimitScope == SwipeLimitScope.SESSION) {
            Text(text = stringResource(R.string.swipe_limit_session_gap), style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SwipeLimitRules.SESSION_GAP_PRESETS.forEach { minutes ->
                    FilterChip(
                        selected = settings.swipeSessionGapMinutes == minutes,
                        onClick = { actions.onSessionGap(minutes) },
                        label = { Text(stringResource(R.string.apps_limit_minutes, minutes)) },
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = settings.swipeAccessAllowed,
                    role = Role.Switch,
                    onValueChange = actions.onAccessAllowed,
                )
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = stringResource(R.string.swipe_limit_access), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(R.string.swipe_limit_access_description, SwipeLimitRules.EXTENSION_SWIPES),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = settings.swipeAccessAllowed, onCheckedChange = null)
        }
    }
    if (customOpen) {
        CustomSwipeLimitDialog(
            initial = limit,
            onConfirm = {
                customOpen = false
                actions.onLimit(it)
            },
            onDismiss = { customOpen = false },
        )
    }
}

@Composable
private fun CustomSwipeLimitDialog(initial: Int?, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial?.toString().orEmpty()) }
    val value = text.toIntOrNull()?.takeIf { it in SwipeLimitRules.MIN_LIMIT..SwipeLimitRules.MAX_LIMIT }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.swipe_limit_custom_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { input -> text = input.filter(Char::isDigit).take(MAX_DIGITS) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text(stringResource(R.string.swipe_limit_custom_field)) },
            )
        },
        confirmButton = {
            TextButton(onClick = { value?.let(onConfirm) }, enabled = value != null) {
                Text(stringResource(R.string.swipe_limit_custom_set))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

private const val MAX_DIGITS = 5
