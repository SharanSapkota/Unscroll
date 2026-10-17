package com.unscroll.app.data.diagnostics

import android.os.Build
import com.unscroll.app.BuildConfig
import com.unscroll.app.data.apps.InstalledApps
import com.unscroll.app.data.permission.PermissionRepository
import com.unscroll.app.data.reels.ReelsDetectionRepository
import com.unscroll.app.data.scroll.ScrollCountingRepository
import com.unscroll.app.domain.diagnostics.DiagnosticsInfo
import com.unscroll.app.domain.diagnostics.DiagnosticsReport
import com.unscroll.app.domain.permission.AppPermission
import com.unscroll.app.domain.plus.TrackedAppsSource
import com.unscroll.app.domain.reels.ReelsDebugFormat
import com.unscroll.app.domain.reels.ReelsOnlyPillConfig
import com.unscroll.app.overlay.OverlayCoordinator
import com.unscroll.app.service.TrackingService
import com.unscroll.app.util.AppLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * "Copy diagnostics" (Settings): a plain-text report the user previews and then shares through
 * the share sheet, if they want to. Nothing is sent automatically, nothing is written to disk.
 * It holds the app and Android versions, the phone model, permission states, the overlay and
 * detector states, the installed versions of the tracked Reels-capable apps and the last 200 log
 * lines ([AppLog]: technical messages only). Never usage data, screen text or content.
 */
@Singleton
class DiagnosticsRepository @Inject constructor(
    private val permissions: PermissionRepository,
    private val scrollCounting: ScrollCountingRepository,
    private val reels: ReelsDetectionRepository,
    private val trackedApps: TrackedAppsSource,
    private val installedApps: InstalledApps,
    private val overlay: OverlayCoordinator,
) {
    suspend fun report(): String = withContext(Dispatchers.IO) {
        val granted = permissions.permissions.value
        val tracked = trackedApps.state.first().active
        val info = DiagnosticsInfo(
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})${if (BuildConfig.DEBUG) " debug" else ""}",
            androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            permissions = AppPermission.entries.map { it.label() to if (granted.isGranted(it)) GRANTED else MISSING } +
                listOf(
                    "Accessibility (swipe counting)" to when {
                        scrollCounting.connected.value -> "running"
                        scrollCounting.serviceEnabled.value -> "enabled, not connected"
                        else -> "off"
                    },
                    "Swipe counting consent" to scrollCounting.record.first().consent.name,
                    "Reels screen-check consent" to scrollCounting.record.first().reelsConsent.name,
                    "Tracking service" to if (TrackingService.isRunning) "running" else "stopped",
                ),
            detector = reels.live.value.values.map(ReelsDebugFormat::line),
            trackedVersions = ReelsOnlyPillConfig.PACKAGES.filter { it in tracked }.map { pkg ->
                val version = installedApps.version(pkg)
                pkg to "${version.versionName ?: "?"} (${version.versionCode ?: "not installed"})"
            },
            overlayState = overlay.state.value.toString(),
        )
        DiagnosticsReport.build(info, AppLog.buffer.lines())
    }

    private fun AppPermission.label(): String = when (this) {
        AppPermission.USAGE_ACCESS -> "Usage access"
        AppPermission.OVERLAY -> "Display over other apps"
        AppPermission.NOTIFICATIONS -> "Notifications"
        AppPermission.IGNORE_BATTERY_OPTIMIZATIONS -> "Battery optimization off"
    }

    private companion object {
        const val GRANTED = "granted"
        const val MISSING = "missing"
    }
}
