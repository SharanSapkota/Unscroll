package com.unscroll.app.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.unscroll.app.R
import com.unscroll.app.domain.blocking.LimitSettings
import com.unscroll.app.domain.blocking.SwipeLimitRules
import com.unscroll.app.domain.blocking.SwipeLimitScope
import com.unscroll.app.domain.friction.FrictionSettings
import com.unscroll.app.domain.scroll.SwipeBreakTracker
import com.unscroll.app.ui.components.ChipGroup
import com.unscroll.app.ui.components.CustomNumberDialog
import com.unscroll.app.ui.components.RowDivider
import com.unscroll.app.ui.components.SectionHeader
import com.unscroll.app.ui.components.SegmentedControl
import com.unscroll.app.ui.components.SettingRow
import com.unscroll.app.ui.components.SettingSwitchRow
import com.unscroll.app.ui.components.SettingsGroup
import com.unscroll.app.ui.components.UnscrollSheet
import com.unscroll.app.ui.components.formatMinuteOfDay
import com.unscroll.app.ui.components.pickTime
import com.unscroll.app.ui.components.shortMinutes
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.UnscrollTheme
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/** Callbacks for App detail. Every one saves at once. */
internal class DetailActions(
    val onDailyLimit: (Int?) -> Unit = {},
    val onSwipeLimit: (Int?) -> Unit = {},
    val onSwipeScope: (SwipeLimitScope) -> Unit = {},
    val onSwipeGap: (Int) -> Unit = {},
    val onSwipeAccess: (Boolean) -> Unit = {},
    val onSchedule: (Boolean) -> Unit = {},
    val onScheduleDay: (DayOfWeek) -> Unit = {},
    val onScheduleStart: (Int) -> Unit = {},
    val onScheduleEnd: (Int) -> Unit = {},
    val onPillShown: (Boolean) -> Unit = {},
    val onSwipesShown: (Boolean) -> Unit = {},
    val onFriction: ((FrictionSettings) -> FrictionSettings) -> Unit = {},
    val onResetFriction: () -> Unit = {},
)

/** A chip value: a preset, "Off" (null), or "Custom". */
private sealed interface LimitChoice {
    data object Off : LimitChoice
    data class Preset(val value: Int) : LimitChoice
    data object Custom : LimitChoice
}

private val TIME_PRESETS = listOf(15, 30, 45, 60, 120)
private val SWIPE_PRESETS = listOf(25, 50, 100, 200)

private fun choices(presets: List<Int>): List<LimitChoice> =
    listOf(LimitChoice.Off) + presets.map { LimitChoice.Preset(it) } + LimitChoice.Custom

private fun LimitChoice.isSelected(current: Int?, presets: List<Int>): Boolean = when (this) {
    LimitChoice.Off -> current == null
    is LimitChoice.Preset -> current == value
    LimitChoice.Custom -> current != null && current !in presets
}

@Composable
internal fun LimitsSection(state: AppDetailUiState, actions: DetailActions) {
    val settings = state.settings
    var customTime by rememberSaveable { mutableStateOf(false) }
    var customSwipes by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
        SectionHeader(
            title = stringResource(R.string.detail_limits),
            info = stringResource(R.string.detail_limits_info),
        )
        SettingsGroup {
            SettingRow(
                title = stringResource(R.string.detail_daily_limit),
                icon = R.drawable.ic_timer,
                value = settings.dailyLimitMinutes?.let { shortMinutes(it) } ?: stringResource(R.string.value_off),
            )
            ChipGroup(
                options = choices(TIME_PRESETS),
                isSelected = { it.isSelected(settings.dailyLimitMinutes, TIME_PRESETS) },
                label = { choice ->
                    when (choice) {
                        LimitChoice.Off -> stringResource(R.string.value_off)
                        is LimitChoice.Preset -> shortMinutes(choice.value)
                        LimitChoice.Custom -> stringResource(R.string.value_custom)
                    }
                },
                onSelect = { choice ->
                    when (choice) {
                        LimitChoice.Off -> actions.onDailyLimit(null)
                        is LimitChoice.Preset -> actions.onDailyLimit(choice.value)
                        LimitChoice.Custom -> customTime = true
                    }
                },
                contentPadding = PaddingValues(start = Dimens.spaceL, end = Dimens.spaceL, bottom = Dimens.spaceM),
            )
            RowDivider()
            SettingRow(
                title = stringResource(R.string.detail_swipe_limit),
                icon = R.drawable.ic_swipe,
                value = settings.swipeLimit?.let { pluralStringResource(R.plurals.value_swipes, it, it) }
                    ?: stringResource(R.string.value_off),
                subtitle = if (state.scrollCountingActive) null else stringResource(R.string.detail_needs_counting),
                subtitleColor = UnscrollTheme.status.warn,
            )
            ChipGroup(
                options = choices(SWIPE_PRESETS),
                isSelected = { it.isSelected(settings.swipeLimit, SWIPE_PRESETS) },
                label = { choice ->
                    when (choice) {
                        LimitChoice.Off -> stringResource(R.string.value_off)
                        is LimitChoice.Preset -> choice.value.toString()
                        LimitChoice.Custom -> stringResource(R.string.value_custom)
                    }
                },
                onSelect = { choice ->
                    when (choice) {
                        LimitChoice.Off -> actions.onSwipeLimit(null)
                        is LimitChoice.Preset -> actions.onSwipeLimit(choice.value)
                        LimitChoice.Custom -> customSwipes = true
                    }
                },
                contentPadding = PaddingValues(start = Dimens.spaceL, end = Dimens.spaceL, bottom = Dimens.spaceM),
            )
            if (settings.swipeLimit != null) SwipeLimitOptions(settings, actions)
        }
    }
    if (customTime) {
        CustomNumberDialog(
            title = stringResource(R.string.detail_daily_limit),
            fieldLabel = stringResource(R.string.detail_custom_minutes),
            initial = settings.dailyLimitMinutes,
            range = 1..MAX_LIMIT_MINUTES,
            onConfirm = {
                customTime = false
                actions.onDailyLimit(it)
            },
            onDismiss = { customTime = false },
        )
    }
    if (customSwipes) {
        CustomNumberDialog(
            title = stringResource(R.string.detail_swipe_limit),
            fieldLabel = stringResource(R.string.detail_custom_swipes),
            initial = settings.swipeLimit,
            range = SwipeLimitRules.MIN_LIMIT..SwipeLimitRules.MAX_LIMIT,
            onConfirm = {
                customSwipes = false
                actions.onSwipeLimit(it)
            },
            onDismiss = { customSwipes = false },
        )
    }
}

@Composable
private fun SwipeLimitOptions(settings: LimitSettings, actions: DetailActions) {
    Column(
        modifier = Modifier.padding(horizontal = Dimens.spaceL),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceS),
    ) {
        SegmentedControl(
            options = SwipeLimitScope.entries,
            selected = settings.swipeLimitScope,
            label = {
                stringResource(
                    when (it) {
                        SwipeLimitScope.DAY -> R.string.detail_per_day
                        SwipeLimitScope.SESSION -> R.string.detail_per_session
                    },
                )
            },
            onSelect = actions.onSwipeScope,
        )
    }
    if (settings.swipeLimitScope == SwipeLimitScope.SESSION) {
        SettingRow(
            title = stringResource(R.string.detail_reset_after),
            value = shortMinutes(settings.swipeSessionGapMinutes),
        )
        ChipGroup(
            options = SwipeLimitRules.SESSION_GAP_PRESETS,
            isSelected = { it == settings.swipeSessionGapMinutes },
            label = { shortMinutes(it) },
            onSelect = actions.onSwipeGap,
            contentPadding = PaddingValues(start = Dimens.spaceL, end = Dimens.spaceL, bottom = Dimens.spaceS),
        )
    }
    SettingSwitchRow(
        title = stringResource(R.string.detail_allow_access),
        subtitle = stringResource(R.string.detail_allow_access_hint, SwipeLimitRules.EXTENSION_SWIPES),
        checked = settings.swipeAccessAllowed,
        onCheckedChange = actions.onSwipeAccess,
    )
}

@Composable
internal fun BlockingSection(settings: LimitSettings, actions: DetailActions) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
        // "Block entire app" moved to the quick toggles at the top; the schedule stays here.
        SectionHeader(title = stringResource(R.string.detail_blocking))
        SettingsGroup {
            SettingSwitchRow(
                title = stringResource(R.string.detail_schedule),
                icon = R.drawable.ic_bedtime,
                checked = settings.schedule.enabled,
                onCheckedChange = actions.onSchedule,
                subtitle = if (settings.schedule.enabled && settings.schedule.crossesMidnight) {
                    stringResource(R.string.detail_schedule_overnight)
                } else {
                    null
                },
            )
            if (settings.schedule.enabled) {
                ChipGroup(
                    options = DayOfWeek.entries,
                    isSelected = { it in settings.schedule.days },
                    label = { it.getDisplayName(TextStyle.NARROW, locale) },
                    onSelect = actions.onScheduleDay,
                    contentPadding = PaddingValues(start = Dimens.spaceL, end = Dimens.spaceL, bottom = Dimens.spaceS),
                )
                SettingRow(
                    title = stringResource(R.string.detail_schedule_from),
                    value = formatMinuteOfDay(settings.schedule.startMinute),
                    onClick = { pickTime(context, settings.schedule.startMinute, actions.onScheduleStart) },
                )
                SettingRow(
                    title = stringResource(R.string.detail_schedule_to),
                    value = formatMinuteOfDay(settings.schedule.endMinute),
                    onClick = { pickTime(context, settings.schedule.endMinute, actions.onScheduleEnd) },
                )
            }
        }
    }
}

/** Which nudge picker sheet is open. */
private enum class NudgeSheet { OPENS, BREAKS, SWIPE_BREAK }

@Composable
internal fun TimerAndNudgesSection(state: AppDetailUiState, actions: DetailActions) {
    val friction = state.friction
    var sheet by rememberSaveable { mutableStateOf<NudgeSheet?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceS)) {
        SectionHeader(
            title = stringResource(R.string.detail_pill_nudges),
            info = stringResource(R.string.detail_pill_nudges_info),
        )
        SettingsGroup {
            SettingSwitchRow(
                title = stringResource(R.string.detail_show_timer),
                icon = R.drawable.ic_layers,
                checked = state.pillShown,
                onCheckedChange = actions.onPillShown,
                subtitle = if (state.pillEnabledGlobally) null else stringResource(R.string.detail_pill_off_globally),
                subtitleColor = UnscrollTheme.status.warn,
            )
            SettingSwitchRow(
                title = stringResource(R.string.detail_show_swipes),
                icon = R.drawable.ic_swipe,
                checked = state.swipesShown,
                onCheckedChange = actions.onSwipesShown,
            )
            RowDivider()
            SettingRow(
                title = stringResource(R.string.detail_open_nudges),
                icon = R.drawable.ic_bell,
                value = if (friction.nudgesEnabled) {
                    friction.nudgeThresholds.joinToString(", ")
                } else {
                    stringResource(R.string.value_off)
                },
                onClick = { sheet = NudgeSheet.OPENS },
            )
            SettingRow(
                title = stringResource(R.string.detail_break_reminders),
                icon = R.drawable.ic_timer,
                value = if (friction.breakRemindersEnabled) {
                    stringResource(R.string.value_every, shortMinutes(friction.breakIntervalMinutes))
                } else {
                    stringResource(R.string.value_off)
                },
                onClick = { sheet = NudgeSheet.BREAKS },
            )
            SettingSwitchRow(
                title = stringResource(R.string.detail_limit_warnings),
                icon = R.drawable.ic_warning,
                checked = friction.limitWarningsEnabled,
                onCheckedChange = { on -> actions.onFriction { it.copy(limitWarningsEnabled = on) } },
            )
            SettingSwitchRow(
                title = stringResource(R.string.detail_tint),
                icon = R.drawable.ic_eye,
                checked = friction.tintEnabled,
                onCheckedChange = { on -> actions.onFriction { it.copy(tintEnabled = on) } },
            )
            SettingRow(
                title = stringResource(R.string.detail_swipe_break),
                icon = R.drawable.ic_hourglass,
                value = friction.swipeBreakAfter?.let { pluralStringResource(R.plurals.value_swipes, it, it) }
                    ?: stringResource(R.string.value_off),
                subtitle = if (state.scrollCountingActive) null else stringResource(R.string.detail_needs_counting),
                subtitleColor = UnscrollTheme.status.warn,
                onClick = { sheet = NudgeSheet.SWIPE_BREAK },
            )
            RowDivider()
            SettingRow(
                title = stringResource(R.string.detail_reset_nudges),
                titleColor = MaterialTheme.colorScheme.primary,
                showChevron = false,
                onClick = actions.onResetFriction,
            )
        }
    }
    when (sheet) {
        NudgeSheet.OPENS -> UnscrollSheet(
            title = stringResource(R.string.detail_open_nudges),
            onDismiss = { sheet = null },
        ) {
            SettingSwitchRow(
                title = stringResource(R.string.detail_open_nudges),
                subtitle = stringResource(R.string.detail_open_nudges_hint),
                checked = friction.nudgesEnabled,
                onCheckedChange = { on -> actions.onFriction { it.copy(nudgesEnabled = on) } },
            )
            if (friction.nudgesEnabled) {
                ChipGroup(
                    options = FrictionSettings.THRESHOLD_PRESETS,
                    isSelected = { it in friction.nudgeThresholds },
                    label = { pluralStringResource(R.plurals.value_opens, it, it) },
                    onSelect = { value ->
                        actions.onFriction {
                            val current = it.nudgeThresholds
                            it.copy(nudgeThresholds = if (value in current) current - value else current + value)
                        }
                    },
                    contentPadding = PaddingValues(),
                )
            }
        }
        NudgeSheet.BREAKS -> UnscrollSheet(
            title = stringResource(R.string.detail_break_reminders),
            onDismiss = { sheet = null },
        ) {
            SettingSwitchRow(
                title = stringResource(R.string.detail_break_reminders),
                subtitle = stringResource(R.string.detail_break_reminders_hint),
                checked = friction.breakRemindersEnabled,
                onCheckedChange = { on -> actions.onFriction { it.copy(breakRemindersEnabled = on) } },
            )
            if (friction.breakRemindersEnabled) {
                ChipGroup(
                    options = FrictionSettings.BREAK_PRESETS,
                    isSelected = { it == friction.breakIntervalMinutes },
                    label = { stringResource(R.string.value_every, shortMinutes(it)) },
                    onSelect = { value -> actions.onFriction { it.copy(breakIntervalMinutes = value) } },
                    contentPadding = PaddingValues(),
                )
            }
        }
        NudgeSheet.SWIPE_BREAK -> UnscrollSheet(
            title = stringResource(R.string.detail_swipe_break),
            onDismiss = { sheet = null },
        ) {
            Text(
                text = stringResource(R.string.detail_swipe_break_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChipGroup(
                options = listOf<Int?>(null) + SwipeBreakTracker.PRESETS,
                isSelected = { it == friction.swipeBreakAfter },
                label = { value ->
                    value?.let { pluralStringResource(R.plurals.value_swipes, it, it) } ?: stringResource(R.string.value_off)
                },
                onSelect = { value -> actions.onFriction { it.copy(swipeBreakAfter = value) } },
                contentPadding = PaddingValues(),
            )
        }
        null -> Unit
    }
}

private const val MAX_LIMIT_MINUTES = 24 * 60
