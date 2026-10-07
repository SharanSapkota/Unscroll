package com.unscroll.app.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewFontScale
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.core.app.ActivityCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.unscroll.app.R
import com.unscroll.app.domain.device.DeviceManufacturer
import com.unscroll.app.domain.fox.FoxMood
import com.unscroll.app.domain.onboarding.OnboardingStep
import com.unscroll.app.domain.onboarding.PrimaryAction
import com.unscroll.app.domain.permission.AppPermission
import com.unscroll.app.domain.permission.PermissionState
import com.unscroll.app.ui.components.InfoButton
import com.unscroll.app.ui.components.InfoSheet
import com.unscroll.app.ui.components.PermissionRow
import com.unscroll.app.ui.components.RowDivider
import com.unscroll.app.ui.components.SettingsGroup
import com.unscroll.app.ui.components.TrustFooter
import com.unscroll.app.ui.components.TrustPromise
import com.unscroll.app.ui.components.TrustSheet
import com.unscroll.app.ui.fox.FoxMascot
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.Motion
import com.unscroll.app.ui.theme.UnscrollTheme
import com.unscroll.app.util.SystemSettings
import com.unscroll.app.util.openSettings
import kotlinx.coroutines.flow.distinctUntilChanged

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
        onPageChanged = viewModel::onPageChanged,
        onNotificationPermissionResult = viewModel::onPermissionResult,
        modifier = modifier,
    )
}

/** Two swipeable pages: welcome, then the permissions with a "Grant" button each. */
@Composable
private fun OnboardingContent(
    state: OnboardingUiState,
    onContinue: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onNotificationPermissionResult: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(initialPage = state.page) { OnboardingStep.entries.size }
    // The view model owns the page: follow it, and report swipes back.
    LaunchedEffect(state.page) {
        if (pagerState.currentPage != state.page) pagerState.animateScrollToPage(state.page)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.distinctUntilChanged().collect { onPageChanged(it) }
    }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(Dimens.screenPadding),
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                when (OnboardingStep.entries[page]) {
                    OnboardingStep.WELCOME -> WelcomePage()
                    OnboardingStep.PERMISSIONS -> PermissionsPage(state.permissions, onNotificationPermissionResult)
                }
            }
            PageDots(current = state.page)
            Button(
                onClick = onContinue,
                enabled = state.canContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Dimens.spaceL),
            ) {
                Text(
                    text = stringResource(state.primaryAction.labelRes),
                    modifier = Modifier.padding(vertical = Dimens.spaceS),
                )
            }
        }
    }
}

@get:StringRes
private val PrimaryAction.labelRes: Int
    get() = when (this) {
        PrimaryAction.GET_STARTED -> R.string.onboarding_get_started
        PrimaryAction.FINISH -> R.string.onboarding_done
    }

/**
 * Welcome: the happy fox (idle blink, ear twitch and tail wag, still with "Remove animations"),
 * fading and sliding in gently, then the name and one short line.
 */
@Composable
private fun WelcomePage() {
    // Starts hidden and turns visible right away, so the fox animates in when the page opens.
    val foxVisible = remember { MutableTransitionState(false).apply { targetState = true } }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceXl, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedVisibility(
            visibleState = foxVisible,
            enter = fadeIn(tween(Motion.MEDIUM)) +
                slideInVertically(tween(Motion.MEDIUM)) { height -> height / WELCOME_SLIDE_FRACTION },
        ) {
            FoxMascot(mood = FoxMood.HAPPY, modifier = Modifier.size(Dimens.foxWelcome))
        }
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.onboarding_welcome_line),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** The fox starts this fraction of its height lower and slides up into place. */
private const val WELCOME_SLIDE_FRACTION = 8

@Composable
private fun PermissionsPage(permissions: PermissionState, onNotificationPermissionResult: () -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val manufacturer = remember { DeviceManufacturer.from(Build.MANUFACTURER) }
    var batteryInfo by rememberSaveable { mutableStateOf(false) }
    var trustInfo by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        // When the user has denied twice, Android stops showing the dialog and the request fails
        // immediately. Send them to the notification settings instead.
        if (!isGranted &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            context.openSettings(SystemSettings.appNotifications(context))
        }
        onNotificationPermissionResult()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceL, Alignment.CenterVertically),
    ) {
        // Why these permissions are safe to give, before asking for them.
        TrustPromise(fox = true)
        Text(text = stringResource(R.string.onboarding_permissions_title), style = MaterialTheme.typography.headlineMedium)
        SettingsGroup {
            PermissionRow(
                title = stringResource(R.string.permission_usage),
                note = stringResource(R.string.onboarding_why_usage),
                icon = R.drawable.ic_eye,
                granted = permissions.isGranted(AppPermission.USAGE_ACCESS),
                onGrant = { context.openSettings(SystemSettings.usageAccess()) },
            )
            RowDivider()
            PermissionRow(
                title = stringResource(R.string.permission_overlay),
                note = stringResource(R.string.onboarding_why_overlay),
                icon = R.drawable.ic_layers,
                granted = permissions.isGranted(AppPermission.OVERLAY),
                onGrant = { context.openSettings(SystemSettings.overlay(context)) },
            )
            RowDivider()
            PermissionRow(
                title = stringResource(R.string.permission_notifications),
                note = stringResource(R.string.onboarding_why_notifications),
                icon = R.drawable.ic_bell,
                granted = permissions.isGranted(AppPermission.NOTIFICATIONS),
                onGrant = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.openSettings(SystemSettings.appNotifications(context))
                    }
                },
            )
        }
        // Battery: optional, but OEM battery savers kill tracking without it.
        SettingsGroup {
            PermissionRow(
                title = stringResource(R.string.permission_battery),
                note = stringResource(R.string.permission_battery_note),
                icon = R.drawable.ic_battery,
                granted = permissions.isGranted(AppPermission.IGNORE_BATTERY_OPTIMIZATIONS),
                onGrant = { context.openSettings(SystemSettings.batteryOptimization()) },
                trailingInfo = { InfoButton(onClick = { batteryInfo = true }) },
            )
        }
        TrustFooter(onHowWeProtect = { trustInfo = true })
    }
    if (trustInfo) {
        TrustSheet(onDismiss = { trustInfo = false })
    }
    if (batteryInfo) {
        val hint = manufacturer.hintRes?.let { stringResource(it) + "\n\n" }.orEmpty()
        InfoSheet(
            title = stringResource(R.string.permission_battery),
            body = hint + stringResource(R.string.onboarding_battery_hint_other),
            onDismiss = { batteryInfo = false },
        )
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
private fun PageDots(current: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Dimens.spaceL),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spaceS, Alignment.CenterHorizontally),
    ) {
        OnboardingStep.entries.forEach { step ->
            Box(
                modifier = Modifier
                    .size(Dimens.dot)
                    .clip(CircleShape)
                    .background(
                        if (step.ordinal == current) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
            )
        }
    }
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun OnboardingWelcomePreview() {
    UnscrollTheme {
        OnboardingContent(
            state = OnboardingUiState(OnboardingStep.WELCOME, PermissionState.NONE),
            onContinue = {},
            onPageChanged = {},
            onNotificationPermissionResult = {},
        )
    }
}

@PreviewLightDark
@PreviewFontScale
@Composable
private fun OnboardingPermissionsPreview() {
    UnscrollTheme {
        OnboardingContent(
            state = OnboardingUiState(
                OnboardingStep.PERMISSIONS,
                PermissionState(setOf(AppPermission.USAGE_ACCESS)),
            ),
            onContinue = {},
            onPageChanged = {},
            onNotificationPermissionResult = {},
        )
    }
}
