package com.unscroll.app.ui.apps

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unscroll.app.R
import com.unscroll.app.domain.blocking.BlockDecision
import com.unscroll.app.domain.blocking.BlockReason
import com.unscroll.app.domain.blocking.BlockSchedule
import com.unscroll.app.domain.blocking.LimitSettings
import com.unscroll.app.domain.friction.FrictionSettings
import com.unscroll.app.ui.theme.UnscrollTheme
import com.unscroll.app.util.appLabel
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

private val LIMIT_PRESETS = listOf(15, 30, 45, 60, 90, 120)

@Composable
fun AppsScreen(
    modifier: Modifier = Modifier,
    viewModel: AppsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    if (uiState.isLoading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.nav_apps),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = stringResource(R.string.apps_changes_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(uiState.apps, key = { it.packageName }) { app ->
            AppLimitCard(
                state = app,
                scrollCountingActive = uiState.scrollCountingActive,
                actions = AppCardActions(
                    onDailyLimit = { viewModel.setDailyLimit(app.packageName, it) },
                    onBlockedAlways = { viewModel.setBlockedAlways(app.packageName, it) },
                    onScheduleEnabled = { viewModel.setScheduleEnabled(app.packageName, it) },
                    onToggleDay = { viewModel.toggleScheduleDay(app.packageName, it) },
                    onScheduleStart = { viewModel.setScheduleStart(app.packageName, it) },
                    onScheduleEnd = { viewModel.setScheduleEnd(app.packageName, it) },
                    onFriction = { transform -> viewModel.updateFriction(app.packageName, transform) },
                    onResetFriction = { viewModel.resetFriction(app.packageName) },
                    swipeLimit = SwipeLimitActions(
                        onLimit = { viewModel.setSwipeLimit(app.packageName, it) },
                        onScope = { viewModel.setSwipeLimitScope(app.packageName, it) },
                        onSessionGap = { viewModel.setSwipeSessionGap(app.packageName, it) },
                        onAccessAllowed = { viewModel.setSwipeAccessAllowed(app.packageName, it) },
                    ),
                ),
            )
        }
    }
}

private class AppCardActions(
    val onDailyLimit: (Int?) -> Unit,
    val onBlockedAlways: (Boolean) -> Unit,
    val onScheduleEnabled: (Boolean) -> Unit,
    val onToggleDay: (DayOfWeek) -> Unit,
    val onScheduleStart: (Int) -> Unit,
    val onScheduleEnd: (Int) -> Unit,
    val onFriction: ((FrictionSettings) -> FrictionSettings) -> Unit = {},
    val onResetFriction: () -> Unit = {},
    val swipeLimit: SwipeLimitActions = SwipeLimitActions(),
)

@Composable
private fun AppLimitCard(state: AppCardState, actions: AppCardActions, scrollCountingActive: Boolean = false) {
    // The controls show the stored settings: every change applies at once, so they are also what
    // is enforced, and the status line under the app name updates with them.
    val settings = state.settings
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppHeader(state)
            HorizontalDivider()
            Text(
                text = stringResource(R.string.apps_daily_limit),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = settings.dailyLimitMinutes == null,
                    onClick = { actions.onDailyLimit(null) },
                    label = { Text(stringResource(R.string.apps_limit_none)) },
                )
                LIMIT_PRESETS.forEach { minutes ->
                    FilterChip(
                        selected = settings.dailyLimitMinutes == minutes,
                        onClick = { actions.onDailyLimit(minutes) },
                        label = { Text(stringResource(R.string.apps_limit_minutes, minutes)) },
                    )
                }
            }
            SwitchRow(
                title = stringResource(R.string.apps_block_completely),
                checked = settings.blockedAlways,
                onCheckedChange = actions.onBlockedAlways,
            )
            SwitchRow(
                title = stringResource(R.string.apps_block_schedule),
                checked = settings.schedule.enabled,
                onCheckedChange = actions.onScheduleEnabled,
            )
            if (settings.schedule.enabled) ScheduleEditor(settings.schedule, actions)
            SwipeLimitSection(settings, scrollCountingActive, actions.swipeLimit)
            FrictionSection(
                settings = state.friction,
                onChange = actions.onFriction,
                onReset = actions.onResetFriction,
            )
        }
    }
}

@Composable
private fun AppHeader(state: AppCardState) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val label = remember(state.packageName) { packageManager.appLabel(state.packageName) }
    val icon: ImageBitmap? = remember(state.packageName) {
        runCatching { packageManager.getApplicationIcon(state.packageName).toBitmap().asImageBitmap() }
            .getOrNull()
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon?.let {
            Image(bitmap = it, contentDescription = null, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column {
            Text(text = label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val blocked = state.decision is BlockDecision.Blocked
            Text(
                text = statusText(state.decision, state.settings),
                style = MaterialTheme.typography.bodyMedium,
                color = if (blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun statusText(decision: BlockDecision, settings: LimitSettings): String {
    val context = LocalContext.current
    return when (decision) {
        is BlockDecision.Allowed -> {
            val remaining = decision.remainingMillis
            when {
                remaining != null -> {
                    val minutes = ceil(remaining / 60_000.0).toInt()
                    pluralStringResource(R.plurals.apps_status_minutes_left, minutes, minutes)
                }
                settings.hasAnyRule -> stringResource(R.string.apps_status_allowed_now)
                else -> stringResource(R.string.apps_status_no_limits)
            }
        }
        is BlockDecision.Blocked -> when (decision.reason) {
            BlockReason.BLOCKED_ALWAYS -> stringResource(R.string.apps_status_blocked_always)
            BlockReason.DAILY_LIMIT_REACHED, BlockReason.SWIPE_LIMIT_REACHED ->
                stringResource(R.string.apps_status_limit_reached)
            BlockReason.INSIDE_SCHEDULE -> {
                val until = decision.until
                if (until == null) {
                    stringResource(R.string.apps_status_blocked_always)
                } else {
                    stringResource(
                        R.string.apps_status_blocked_until,
                        DateFormat.getTimeFormat(context).format(Date(until)),
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleEditor(schedule: BlockSchedule, actions: AppCardActions) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DayOfWeek.entries.forEach { day ->
            FilterChip(
                selected = day in schedule.days,
                onClick = { actions.onToggleDay(day) },
                label = { Text(day.getDisplayName(TextStyle.SHORT, locale)) },
            )
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { pickTime(context, schedule.startMinute, actions.onScheduleStart) }) {
            Text(stringResource(R.string.apps_schedule_from, formatMinuteOfDay(schedule.startMinute)))
        }
        OutlinedButton(onClick = { pickTime(context, schedule.endMinute, actions.onScheduleEnd) }) {
            Text(stringResource(R.string.apps_schedule_to, formatMinuteOfDay(schedule.endMinute)))
        }
    }
    if (schedule.crossesMidnight) {
        Text(
            text = stringResource(R.string.apps_schedule_overnight),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun pickTime(context: android.content.Context, minuteOfDay: Int, onPicked: (Int) -> Unit) {
    TimePickerDialog(
        context,
        { _, hour, minute -> onPicked(hour * 60 + minute) },
        minuteOfDay / 60,
        minuteOfDay % 60,
        DateFormat.is24HourFormat(context),
    ).show()
}

private fun formatMinuteOfDay(minuteOfDay: Int): String =
    LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Preview(showBackground = true)
@Composable
private fun AppLimitCardPreview() {
    UnscrollTheme {
        AppLimitCard(
            state = AppCardState(
                packageName = "com.instagram.android",
                settings = LimitSettings(dailyLimitMinutes = 30),
                decision = BlockDecision.Allowed(remainingMillis = 12 * 60_000L),
                now = 0L,
            ),
            actions = AppCardActions({}, {}, {}, {}, {}, {}),
        )
    }
}
