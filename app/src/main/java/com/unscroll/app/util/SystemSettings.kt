package com.unscroll.app.util

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import com.unscroll.app.data.scroll.scrollServiceComponent

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

    /** The system Accessibility list, where the user switches on Unscroll's scroll counting. */
    fun accessibility(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    /**
     * The Accessibility list with "Unscroll swipe counter" highlighted where the Settings app
     * supports it (the extras are ignored elsewhere).
     */
    fun accessibilityHighlighting(component: ComponentName): Intent =
        accessibility()
            .putExtra(EXTRA_FRAGMENT_ARG_KEY, component.flattenToString())
            .putExtra(EXTRA_SHOW_FRAGMENT_ARGS, Bundle().apply { putString(EXTRA_FRAGMENT_ARG_KEY, component.flattenToString()) })

    /**
     * Android 11+: the swipe counter's own page in Accessibility settings, with its on/off switch.
     * Built from context.packageName ([scrollServiceComponent]). Null on older versions.
     */
    fun accessibilityDetails(component: ComponentName): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(ACTION_ACCESSIBILITY_DETAILS_SETTINGS).putExtra(Intent.EXTRA_COMPONENT_NAME, component.flattenToString())
        } else {
            null
        }

    fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))

    private fun packageUri(context: Context): Uri = Uri.fromParts("package", context.packageName, null)

    /** Settings.ACTION_ACCESSIBILITY_DETAILS_SETTINGS, spelled out: not every SDK stub exposes it. */
    private const val ACTION_ACCESSIBILITY_DETAILS_SETTINGS = "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"
    private const val EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key"
    private const val EXTRA_SHOW_FRAGMENT_ARGS = ":settings:show_fragment_args"
}

/**
 * Opens the swipe counter's switch in Accessibility settings: its own page (Android 11+), else the
 * Accessibility list with it highlighted, else the plain list; never a wrong screen.
 */
fun Context.openAccessibilitySettings() {
    val component = scrollServiceComponent(this)
    val candidates = listOfNotNull(
        SystemSettings.accessibilityDetails(component),
        SystemSettings.accessibilityHighlighting(component),
        SystemSettings.accessibility(),
    )
    for (candidate in candidates) {
        try {
            startActivity(candidate)
            return
        } catch (e: ActivityNotFoundException) {
            // Try the next one.
        } catch (e: SecurityException) {
            // Try the next one.
        }
    }
    openSettings(Intent(Settings.ACTION_SETTINGS))
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
