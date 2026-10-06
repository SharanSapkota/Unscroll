package com.unscroll.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unscroll.app.R
import com.unscroll.app.domain.device.DeviceManufacturer
import com.unscroll.app.domain.onboarding.OnboardingStep
import com.unscroll.app.domain.onboarding.PrimaryAction
import com.unscroll.app.domain.permission.PermissionState
import com.unscroll.app.ui.theme.UnscrollTheme
import com.unscroll.app.util.SystemSettings
import com.unscroll.app.util.openSettings

@Composable
fun OnboardingScreen(
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val state = uiState ?: return

    BackHandler(enabled = state.canGoBack) { viewModel.onBack() }

    OnboardingContent(
        state = state,
        onContinue = viewModel::onContinue,
        onBack = viewModel::onBack,
        onNotificationPermissionResult = viewModel::onPermissionResult,
        modifier = modifier,
    )
}

@Composable
private fun OnboardingContent(
    state: OnboardingUiState,
    onContinue: () -> Unit,
    onBack: () -> Unit,
    onNotificationPermissionResult: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            LinearProgressIndicator(
                progress = { state.stepNumber.toFloat() / state.stepCount },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(
                    R.string.onboarding_step_counter,
                    state.stepNumber,
                    state.stepCount,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (state.step) {
                    OnboardingStep.WELCOME -> WelcomeStep()
                    OnboardingStep.USAGE_ACCESS -> UsageAccessStep(state.isGranted)
                    OnboardingStep.OVERLAY -> OverlayStep(state.isGranted)
                    OnboardingStep.NOTIFICATIONS -> NotificationsStep(
                        granted = state.isGranted,
                        onPermissionResult = onNotificationPermissionResult,
                    )
                    OnboardingStep.BATTERY -> BatteryStep(state.isGranted)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (state.canGoBack) {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.onboarding_back))
                    }
                } else {
                    Spacer(Modifier)
                }
                Button(onClick = onContinue, enabled = state.canContinue) {
                    Text(stringResource(state.primaryAction.labelRes))
                }
            }
        }
    }
}

@get:StringRes
private val PrimaryAction.labelRes: Int
    get() = when (this) {
        PrimaryAction.GET_STARTED -> R.string.onboarding_get_started
        PrimaryAction.CONTINUE -> R.string.onboarding_continue
        PrimaryAction.SKIP -> R.string.onboarding_skip
        PrimaryAction.FINISH -> R.string.onboarding_finish
    }

@Composable
private fun WelcomeStep() {
    StepTitle(R.string.onboarding_welcome_title)
    BodyText(R.string.onboarding_welcome_body)
    BodyText(R.string.onboarding_welcome_privacy)
    BodyText(R.string.onboarding_welcome_next)
}

@Composable
private fun UsageAccessStep(granted: Boolean) {
    val context = LocalContext.current
    PermissionStep(
        titleRes = R.string.onboarding_usage_title,
        bodyRes = listOf(R.string.onboarding_usage_body, R.string.onboarding_usage_privacy),
        howToRes = R.string.onboarding_usage_how_to,
        optional = false,
        granted = granted,
        actionLabelRes = R.string.onboarding_open_settings,
        onAction = { context.openSettings(SystemSettings.usageAccess()) },
    )
}

@Composable
private fun OverlayStep(granted: Boolean) {
    val context = LocalContext.current
    PermissionStep(
        titleRes = R.string.onboarding_overlay_title,
        bodyRes = listOf(R.string.onboarding_overlay_body, R.string.onboarding_overlay_privacy),
        howToRes = R.string.onboarding_overlay_how_to,
        optional = false,
        granted = granted,
        actionLabelRes = R.string.onboarding_open_settings,
        onAction = { context.openSettings(SystemSettings.overlay(context)) },
    )
}

@Composable
private fun NotificationsStep(granted: Boolean, onPermissionResult: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        // When the user has denied twice, Android stops showing the dialog and the request fails
        // immediately. Send them to the notification settings instead.
        if (!isGranted &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.POST_NOTIFICATIONS,
            )
        ) {
            context.openSettings(SystemSettings.appNotifications(context))
        }
        onPermissionResult()
    }
    PermissionStep(
        titleRes = R.string.onboarding_notifications_title,
        bodyRes = listOf(R.string.onboarding_notifications_body),
        howToRes = R.string.onboarding_notifications_how_to,
        optional = true,
        granted = granted,
        actionLabelRes = R.string.onboarding_notifications_action,
        onAction = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.openSettings(SystemSettings.appNotifications(context))
            }
        },
    )
}

@Composable
private fun BatteryStep(granted: Boolean) {
    val context = LocalContext.current
    val manufacturer = remember { DeviceManufacturer.from(Build.MANUFACTURER) }
    PermissionStep(
        titleRes = R.string.onboarding_battery_title,
        bodyRes = listOf(R.string.onboarding_battery_body, R.string.onboarding_battery_cost),
        howToRes = R.string.onboarding_battery_how_to,
        optional = true,
        granted = granted,
        actionLabelRes = R.string.onboarding_open_settings,
        onAction = { context.openSettings(SystemSettings.batteryOptimization()) },
    )
    OutlinedButton(onClick = { context.openSettings(SystemSettings.appDetails(context)) }) {
        Text(stringResource(R.string.onboarding_battery_app_info))
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_battery_oem_title),
                style = MaterialTheme.typography.titleMedium,
            )
            manufacturer.hintRes?.let { BodyText(it) }
            BodyText(R.string.onboarding_battery_hint_other)
        }
    }
}

@get:StringRes
private val DeviceManufacturer.hintRes: Int?
    get() = when (this) {
        DeviceManufacturer.XIAOMI -> R.string.onboarding_battery_hint_xiaomi
        DeviceManufacturer.SAMSUNG -> R.string.onboarding_battery_hint_samsung
        DeviceManufacturer.HUAWEI -> R.string.onboarding_battery_hint_huawei
        DeviceManufacturer.ONEPLUS -> R.string.onboarding_battery_hint_oneplus
        DeviceManufacturer.OTHER -> null
    }

@Composable
private fun PermissionStep(
    @StringRes titleRes: Int,
    bodyRes: List<Int>,
    @StringRes howToRes: Int,
    optional: Boolean,
    granted: Boolean,
    @StringRes actionLabelRes: Int,
    onAction: () -> Unit,
) {
    StepTitle(titleRes)
    bodyRes.forEach { BodyText(it) }
    Text(
        text = stringResource(
            if (optional) R.string.onboarding_optional else R.string.onboarding_required,
        ),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    PermissionStatus(granted)
    if (!granted) {
        BodyText(howToRes)
        Button(onClick = onAction) {
            Text(stringResource(actionLabelRes))
        }
    }
}

@Composable
private fun PermissionStatus(granted: Boolean) {
    Text(
        text = stringResource(
            if (granted) R.string.onboarding_status_allowed else R.string.onboarding_status_not_allowed,
        ),
        style = MaterialTheme.typography.titleMedium,
        color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun StepTitle(@StringRes textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.headlineMedium,
    )
}

@Composable
private fun BodyText(@StringRes textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Preview(showBackground = true)
@Composable
private fun OnboardingUsageAccessPreview() {
    UnscrollTheme {
        OnboardingContent(
            state = OnboardingUiState(OnboardingStep.USAGE_ACCESS, PermissionState.NONE),
            onContinue = {},
            onBack = {},
            onNotificationPermissionResult = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingBatteryPreview() {
    UnscrollTheme {
        OnboardingContent(
            state = OnboardingUiState(OnboardingStep.BATTERY, PermissionState.ALL),
            onContinue = {},
            onBack = {},
            onNotificationPermissionResult = {},
        )
    }
}
