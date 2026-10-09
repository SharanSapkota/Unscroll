package com.unscroll.app.ui.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unscroll.app.R
import com.unscroll.app.domain.goals.DailyGoal
import com.unscroll.app.domain.permission.HealthItem
import com.unscroll.app.domain.scroll.ScrollCountingStatus
import com.unscroll.app.ui.components.ChipGroup
import com.unscroll.app.ui.components.PermissionRow
import com.unscroll.app.ui.components.RowDivider
import com.unscroll.app.ui.components.SettingRow
import com.unscroll.app.ui.components.SettingSwitchRow
import com.unscroll.app.ui.components.SettingsGroup
import com.unscroll.app.ui.components.UnscrollSheet
import com.unscroll.app.ui.components.shortMinutes
import com.unscroll.app.ui.scroll.ScrollCountingPanel
import com.unscroll.app.ui.section.SectionBlockingPanel
import com.unscroll.app.ui.section.SectionBlockingViewModel
import com.unscroll.app.ui.section.sectionValueRes
import com.unscroll.app.ui.theme.UnscrollTheme
import com.unscroll.app.util.SystemSettings
import com.unscroll.app.util.openSettings

/** Settings › Tracking: the tracking switch, the daily goal, scroll counting and section blocking. */
@Composable
internal fun TrackingGroup(
    trackingEnabled: Boolean,
    onTracking: (Boolean) -> Unit,
    scrollStatus: ScrollCountingStatus,
    onScrollCountingSetUp: () -> Unit,
    onRestrictedHelp: () -> Unit,
    onSectionSetUp: () -> Unit = {},
    onOpenPlus: () -> Unit = {},
    dataViewModel: YourDataViewModel = hiltViewModel(),
    sectionViewModel: SectionBlockingViewModel = hiltViewModel(),
) {
    val goal by dataViewModel.dailyGoalMinutes.collectAsStateWithLifecycle()
    val sectionStatus by sectionViewModel.status.collectAsStateWithLifecycle()
    val sectionSettings by sectionViewModel.settings.collectAsStateWithLifecycle()
    val isPlus by sectionViewModel.isPlus.collectAsStateWithLifecycle()
    var goalOpen by rememberSaveable { mutableStateOf(false) }
    var scrollOpen by rememberSaveable { mutableStateOf(false) }
    var sectionOpen by rememberSaveable { mutableStateOf(false) }
    SettingsGroup {
        SettingSwitchRow(
            title = stringResource(R.string.settings_tracking),
            icon = R.drawable.ic_eye,
            checked = trackingEnabled,
            onCheckedChange = onTracking,
        )
        RowDivider()
        SettingRow(
            title = stringResource(R.string.settings_daily_goal),
            icon = R.drawable.ic_flag,
            value = goal?.let { shortMinutes(it) } ?: stringResource(R.string.value_off),
            onClick = { goalOpen = true },
        )
        SettingRow(
            title = stringResource(R.string.settings_scroll_counting),
            icon = R.drawable.ic_swipe,
            value = stringResource(scrollStatus.valueRes),
            valueColor = if (scrollStatus == ScrollCountingStatus.NEEDS_REENABLE) {
                UnscrollTheme.status.danger
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            onClick = { scrollOpen = true },
        )
        // Section blocking is part of Unscroll Plus: free users get the paywall.
        SettingRow(
            title = stringResource(R.string.section_title),
            icon = R.drawable.ic_layers,
            value = if (isPlus) {
                stringResource(sectionValueRes(sectionStatus, sectionSettings.turnedOff))
            } else {
                stringResource(R.string.plus_badge)
            },
            valueColor = if (isPlus && sectionStatus == ScrollCountingStatus.NEEDS_REENABLE) {
                UnscrollTheme.status.danger
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            onClick = { if (isPlus) sectionOpen = true else onOpenPlus() },
        )
    }
    if (goalOpen) {
        UnscrollSheet(title = stringResource(R.string.settings_daily_goal), onDismiss = { goalOpen = false }) {
            ChipGroup(
                options = listOf<Int?>(null) + DailyGoal.PRESETS,
                isSelected = { it == goal },
                label = { minutes -> minutes?.let { shortMinutes(it) } ?: stringResource(R.string.value_off) },
                onSelect = dataViewModel::setDailyGoal,
                contentPadding = PaddingValues(),
            )
        }
    }
    if (scrollOpen) {
        UnscrollSheet(title = stringResource(R.string.settings_scroll_counting), onDismiss = { scrollOpen = false }) {
            ScrollCountingPanel(
                onSetUp = {
                    scrollOpen = false
                    onScrollCountingSetUp()
                },
                onRestrictedHelp = {
                    scrollOpen = false
                    onRestrictedHelp()
                },
            )
        }
    }
    if (sectionOpen) {
        UnscrollSheet(title = stringResource(R.string.section_title), onDismiss = { sectionOpen = false }) {
            SectionBlockingPanel(
                onSetUp = {
                    sectionOpen = false
                    onSectionSetUp()
                },
                onRestrictedHelp = {
                    sectionOpen = false
                    onRestrictedHelp()
                },
                viewModel = sectionViewModel,
            )
        }
    }
}

private val ScrollCountingStatus.valueRes: Int
    get() = when (this) {
        ScrollCountingStatus.OFF -> R.string.value_off
        ScrollCountingStatus.NEEDS_CONSENT -> R.string.scroll_value_review
        ScrollCountingStatus.NEEDS_ENABLING -> R.string.scroll_value_setup
        ScrollCountingStatus.ACTIVE -> R.string.value_on
        ScrollCountingStatus.NEEDS_REENABLE -> R.string.scroll_value_reenable
    }

/** The checklist behind "Fix 2 issues": each line with a one-tap button to the right system screen. */
@Composable
internal fun PermissionChecklistSheet(health: PermissionHealthState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    UnscrollSheet(title = stringResource(R.string.settings_health_permissions), onDismiss = onDismiss) {
        SettingsGroup {
            health.checklist.forEachIndexed { index, (item, ok) ->
                if (index > 0) RowDivider()
                PermissionRow(
                    title = stringResource(item.titleRes),
                    icon = item.iconRes,
                    note = stringResource(item.noteRes),
                    granted = ok,
                    actionLabel = stringResource(R.string.action_fix),
                    onGrant = { context.openSettings(item.intent(context)) },
                )
            }
        }
    }
}

private val HealthItem.titleRes: Int
    get() = when (this) {
        HealthItem.USAGE_ACCESS -> R.string.permission_usage
        HealthItem.OVERLAY -> R.string.permission_overlay
        HealthItem.NOTIFICATIONS -> R.string.permission_notifications
        HealthItem.BATTERY -> R.string.permission_battery
        HealthItem.ACCESSIBILITY -> R.string.permission_accessibility
    }

private val HealthItem.noteRes: Int
    get() = when (this) {
        HealthItem.USAGE_ACCESS -> R.string.permission_usage_note
        HealthItem.OVERLAY -> R.string.permission_overlay_note
        HealthItem.NOTIFICATIONS -> R.string.permission_notifications_note
        HealthItem.BATTERY -> R.string.permission_battery_note
        HealthItem.ACCESSIBILITY -> R.string.permission_accessibility_note
    }

private val HealthItem.iconRes: Int
    get() = when (this) {
        HealthItem.USAGE_ACCESS -> R.drawable.ic_eye
        HealthItem.OVERLAY -> R.drawable.ic_layers
        HealthItem.NOTIFICATIONS -> R.drawable.ic_bell
        HealthItem.BATTERY -> R.drawable.ic_battery
        HealthItem.ACCESSIBILITY -> R.drawable.ic_accessibility
    }

private fun HealthItem.intent(context: Context): Intent = when (this) {
    HealthItem.USAGE_ACCESS -> SystemSettings.usageAccess()
    HealthItem.OVERLAY -> SystemSettings.overlay(context)
    HealthItem.NOTIFICATIONS -> SystemSettings.appNotifications(context)
    HealthItem.BATTERY -> SystemSettings.batteryOptimization()
    HealthItem.ACCESSIBILITY -> SystemSettings.accessibility()
}
