package com.unscroll.app.ui.home

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.unscroll.app.R
import com.unscroll.app.domain.permission.AppPermission
import com.unscroll.app.ui.theme.Dimens
import com.unscroll.app.ui.theme.UnscrollTheme
import com.unscroll.app.util.SystemSettings

/**
 * Red banner while tracking is on but a required permission is missing: says why nothing is being
 * tracked, with one button to the system screen for [permission].
 */
@Composable
internal fun TrackingPausedBanner(
    permission: AppPermission,
    onFix: (AppPermission) -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = UnscrollTheme.status
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(status.dangerContainer, MaterialTheme.shapes.large)
            .padding(Dimens.spaceXl)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceS),
    ) {
        Text(
            text = stringResource(R.string.home_tracking_paused_title),
            style = MaterialTheme.typography.titleMedium,
            color = status.onDangerContainer,
        )
        Text(
            text = stringResource(permission.missingRes),
            style = MaterialTheme.typography.bodyMedium,
            color = status.onDangerContainer,
        )
        Button(
            onClick = { onFix(permission) },
            colors = ButtonDefaults.buttonColors(
                containerColor = status.danger,
                contentColor = MaterialTheme.colorScheme.surface,
            ),
        ) {
            Text(stringResource(permission.fixRes))
        }
    }
}

private val AppPermission.missingRes: Int
    get() = when (this) {
        AppPermission.OVERLAY -> R.string.home_tracking_paused_overlay
        else -> R.string.home_tracking_paused_usage
    }

private val AppPermission.fixRes: Int
    get() = when (this) {
        AppPermission.OVERLAY -> R.string.home_tracking_fix_overlay
        else -> R.string.home_tracking_fix_usage
    }

/** The system screen that grants [AppPermission]. */
internal fun AppPermission.settingsIntent(context: Context): Intent = when (this) {
    AppPermission.USAGE_ACCESS -> SystemSettings.usageAccess()
    AppPermission.OVERLAY -> SystemSettings.overlay(context)
    AppPermission.NOTIFICATIONS -> SystemSettings.appNotifications(context)
    AppPermission.IGNORE_BATTERY_OPTIMIZATIONS -> SystemSettings.batteryOptimization()
}

@PreviewLightDark
@Composable
private fun TrackingPausedBannerPreview() {
    UnscrollTheme {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceL)) {
            TrackingPausedBanner(AppPermission.USAGE_ACCESS, onFix = {})
            TrackingPausedBanner(AppPermission.OVERLAY, onFix = {})
        }
    }
}
