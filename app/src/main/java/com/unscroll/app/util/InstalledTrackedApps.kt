package com.unscroll.app.util

import android.content.Context
import android.content.pm.PackageManager
import com.unscroll.app.domain.tracking.TrackedApps
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Which tracked apps are installed (visible through the manifest `<queries>`). */
@Singleton
class InstalledTrackedApps @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Installed tracked apps in [TrackedApps] order, or all of them if none is installed yet. */
    fun packages(): List<String> {
        val all = TrackedApps.packageNames.toList()
        return all.filter(::isInstalled).ifEmpty { all }
    }

    private fun isInstalled(packageName: String): Boolean = try {
        context.packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}
