package com.unscroll.app.util

import android.content.pm.PackageManager

/**
 * The user-visible name of an installed app, or the package name if it can't be read. Tracked
 * apps are declared under `<queries>` in the manifest, so they are visible on Android 11+.
 */
fun PackageManager.appLabel(packageName: String): String = try {
    getApplicationLabel(getApplicationInfo(packageName, 0)).toString()
} catch (e: PackageManager.NameNotFoundException) {
    packageName
}
