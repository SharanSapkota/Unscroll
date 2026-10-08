package com.unscroll.app.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unscroll.app.BuildConfig
import com.unscroll.app.R
import com.unscroll.app.domain.friction.QuietHours
import com.unscroll.app.domain.permission.AppPermission
import com.unscroll.app.domain.permission.PermissionState
import com.unscroll.app.domain.plus.FreeTier
import com.unscroll.app.ui.components.RowDivider
import com.unscroll.app.ui.components.SectionHeader
import com.unscroll.app.ui.components.SettingRow
import com.unscroll.app.ui.components.SettingSwitchRow
import com.unscroll.app.ui.components.SettingsGroup
import com.unscroll.app.ui.components.formatMinuteOfDay
import com.unscroll.app.ui.components.pickTime
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.UnscrollTheme
import com.unscroll.app.util.SystemSettings
import com.unscroll.app.util.openSettings

/**
 * Settings: permission health at the top, then Unscroll Plus and four groups (Tracking, Timer pill,
 * Notifications, Data & privacy), plus Appearance (the fox; dynamic color on Android 12+) and debug tools in
 * debug builds. Details open in bottom sheets, never more than one level deep.
 */
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    onScrollCountingSetUp: () -> Unit = {},
    onRestrictedSettingHelp: () -> Unit = {},
    onOpenPlus: () -> Unit = {},
    onPickApps: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val trackingEnabled by viewModel.trackingEnabled.collectAsStateWithLifecycle()
    val overlaySettings by viewModel.overlaySettings.collectAsStateWithLifecycle()
    val positionReset by viewModel.positionReset.collectAsStateWithLifecycle()
    val sampleSessionsAdded by viewModel.sampleSessionsAdded.collectAsStateWithLifecycle()
    val quietHours by viewModel.quietHours.collectAsStateWithLifecycle()
    val health by viewModel.health.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val scrollStatus by viewModel.scrollStatus.collectAsStateWithLifecycle()
    val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val fox by viewModel.fox.collectAsStateWithLifecycle()
    var checklistOpen by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.screenPadding, vertical = Dimens.spaceL),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceS),
    ) {
        Text(
            text = stringResource(R.string.nav_settings),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .padding(bottom = Dimens.spaceS)
                .semantics { heading() },
        )
        SettingsGroup {
            val ok = health.issues == 0
            SettingRow(
                title = if (ok) {
                    stringResource(R.string.settings_health_ok)
                } else {
                    pluralStringResource(R.plurals.settings_health_issues, health.issues, health.issues)
                },
                icon = if (ok) R.drawable.ic_check else R.drawable.ic_warning,
                titleColor = if (ok) UnscrollTheme.status.good else UnscrollTheme.status.danger,
                value = stringResource(R.string.settings_health_permissions),
                onClick = { checklistOpen = true },
            )
        }

        SectionHeader(
            title = stringResource(R.string.plus_title),
            info = pluralStringResource(R.plurals.settings_plus_info, FreeTier.FREE_APPS, FreeTier.FREE_APPS),
        )
        PlusGroup(onOpenPlus = onOpenPlus, onPickApps = onPickApps)

        SectionHeader(title = stringResource(R.string.settings_group_tracking))
        TrackingGroup(
            trackingEnabled = trackingEnabled,
            onTracking = viewModel::setTrackingEnabled,
            scrollStatus = scrollStatus,
            onScrollCountingSetUp = onScrollCountingSetUp,
            onRestrictedHelp = onRestrictedSettingHelp,
        )

        SectionHeader(
            title = stringResource(R.string.settings_group_pill),
            info = stringResource(R.string.settings_pill_info),
        )
        TimerPillGroup(
            settings = overlaySettings,
            canDrawOverlays = permissions.isGranted(AppPermission.OVERLAY),
            positionReset = positionReset,
            actions = PillActions(
                onEnabled = viewModel::setOverlayEnabled,
                onSessionTime = viewModel::setShowSessionTime,
                onSwipes = viewModel::setShowSwipes,
                onSize = viewModel::setPillSize,
                onOpacity = viewModel::setOpacity,
                onTimeColors = viewModel::setTimeThresholds,
                onSwipeColors = viewModel::setSwipeThresholds,
                onResetPosition = viewModel::resetPosition,
            ),
        )

        SectionHeader(
            title = stringResource(R.string.settings_group_notifications),
            info = stringResource(R.string.settings_quiet_info),
        )
        NotificationsGroup(permissions, quietHours, viewModel::setQuietHours)

        SectionHeader(
            title = stringResource(R.string.settings_group_data),
            info = stringResource(R.string.settings_privacy_info),
        )
        DataPrivacyGroup()

        SectionHeader(title = stringResource(R.string.settings_group_appearance))
        SettingsGroup {
            SettingSwitchRow(
                title = stringResource(R.string.settings_show_fox),
                icon = R.drawable.ic_fox,
                checked = fox.showFox,
                onCheckedChange = viewModel::setShowFox,
            )
            SettingSwitchRow(
                title = stringResource(R.string.settings_fox_messages),
                icon = R.drawable.ic_bell,
                checked = fox.messages,
                onCheckedChange = viewModel::setFoxMessages,
                enabled = fox.showFox,
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SettingSwitchRow(
                    title = stringResource(R.string.settings_dynamic_color),
                    icon = R.drawable.ic_palette,
                    checked = dynamicColor,
                    onCheckedChange = viewModel::setDynamicColor,
                )
            }
        }

        if (BuildConfig.DEBUG) {
            SectionHeader(title = stringResource(R.string.settings_group_debug))
            SettingsGroup {
                SettingRow(
                    title = stringResource(R.string.settings_debug_sample_data),
                    icon = R.drawable.ic_bug,
                    value = sampleSessionsAdded?.let {
                        pluralStringResource(R.plurals.settings_debug_sample_added, it, it)
                    },
                    showChevron = false,
                    onClick = viewModel::insertSampleData,
                )
                PlusDebugRow()
            }
        }
    }

    if (checklistOpen) {
        PermissionChecklistSheet(health = health, onDismiss = { checklistOpen = false })
    }
}

@Composable
private fun NotificationsGroup(
    permissions: PermissionState,
    quietHours: QuietHours,
    onQuietHours: ((QuietHours) -> QuietHours) -> Unit,
) {
    val context = LocalContext.current
    SettingsGroup {
        val granted = permissions.isGranted(AppPermission.NOTIFICATIONS)
        SettingRow(
            title = stringResource(R.string.settings_notifications_system),
            icon = R.drawable.ic_bell,
            value = stringResource(if (granted) R.string.value_on else R.string.value_off),
            valueColor = if (granted) MaterialTheme.colorScheme.onSurfaceVariant else UnscrollTheme.status.danger,
            onClick = { context.openSettings(SystemSettings.appNotifications(context)) },
        )
        RowDivider()
        SettingSwitchRow(
            title = stringResource(R.string.settings_quiet_hours),
            icon = R.drawable.ic_bedtime,
            checked = quietHours.enabled,
            onCheckedChange = { on -> onQuietHours { it.copy(enabled = on) } },
        )
        if (quietHours.enabled) {
            SettingRow(
                title = stringResource(R.string.detail_schedule_from),
                value = formatMinuteOfDay(quietHours.startMinute),
                onClick = {
                    pickTime(context, quietHours.startMinute) { minute -> onQuietHours { it.copy(startMinute = minute) } }
                },
            )
            SettingRow(
                title = stringResource(R.string.detail_schedule_to),
                value = formatMinuteOfDay(quietHours.endMinute),
                onClick = {
                    pickTime(context, quietHours.endMinute) { minute -> onQuietHours { it.copy(endMinute = minute) } }
                },
            )
        }
    }
}

@PreviewLightDark
@Composable
private fun NotificationsGroupPreview() {
    UnscrollTheme {
        Surface {
            Column(modifier = Modifier.padding(Dimens.screenPadding)) {
                SettingsGroup {
                    SettingRow(
                        title = pluralStringResource(R.plurals.settings_health_issues, 2, 2),
                        icon = R.drawable.ic_warning,
                        titleColor = UnscrollTheme.status.danger,
                        value = stringResource(R.string.settings_health_permissions),
                        onClick = {},
                    )
                }
                SectionHeader(title = stringResource(R.string.settings_group_notifications))
                NotificationsGroup(PermissionState.NONE, QuietHours(enabled = true)) {}
            }
        }
    }
}
