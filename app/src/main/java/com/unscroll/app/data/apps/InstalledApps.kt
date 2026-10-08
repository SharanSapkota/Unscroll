package com.unscroll.app.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.unscroll.app.domain.apps.LaunchableApp
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What's installed on the phone. An interface so the repository can be tested without Android. */
interface InstalledApps {
    fun isInstalled(packageName: String): Boolean

    /** The app's name, or null if it isn't installed (or not visible). */
    fun label(packageName: String): String?

    /** Every app with a launcher icon, one entry per launcher activity (the picker dedupes). */
    suspend fun launchable(): List<LaunchableApp>
}

/**
 * Package visibility (Android 11+) comes from the manifest's `<queries>` entry for the launcher
 * intent (ACTION_MAIN + CATEGORY_LAUNCHER): every app with an icon in the app drawer is visible,
 * without QUERY_ALL_PACKAGES.
 */
@Singleton
class AndroidInstalledApps @Inject constructor(
    @ApplicationContext private val context: Context,
) : InstalledApps {

    override fun isInstalled(packageName: String): Boolean = try {
        context.packageManager.getApplicationInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    override fun label(packageName: String): String? = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    override suspend fun launchable(): List<LaunchableApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0).mapNotNull { info ->
            val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
            LaunchableApp(packageName, info.loadLabel(pm).toString())
        }
    }
}
