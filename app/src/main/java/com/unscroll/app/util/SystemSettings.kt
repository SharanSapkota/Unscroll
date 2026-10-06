package com.unscroll.app.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/** Intents for the system settings screens the user is sent to during onboarding. */
object SystemSettings {

    fun usageAccess(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun overlay(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri(context))

    fun appNotifications(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /**
     * The battery optimization list. We deliberately avoid ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
     * which needs a permission Google Play restricts to a few app categories.
     */
    fun batteryOptimization(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))

    private fun packageUri(context: Context): Uri = Uri.fromParts("package", context.packageName, null)
}

/**
 * Opens a settings screen, falling back to the app's details page and then the main settings
 * screen, because some OEM builds remove or rename individual settings activities.
 */
fun Context.openSettings(intent: Intent) {
    val candidates = listOf(
        intent,
        SystemSettings.appDetails(this),
        Intent(Settings.ACTION_SETTINGS),
    )
    for (candidate in candidates) {
        try {
            startActivity(candidate)
            return
        } catch (e: ActivityNotFoundException) {
            // Try the next one.
        } catch (e: SecurityException) {
            // Some OEMs protect individual settings screens. Try the next one.
        }
    }
}
