package com.unscroll.app.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.provider.Telephony
import android.telecom.TelecomManager
import com.unscroll.app.domain.apps.AppExclusions
import com.unscroll.app.domain.apps.ExcludedApps
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AppExclusions] plus this phone's own critical apps: every home screen (the current default
 * included), the default dialer and the default SMS app. Those are read at most every
 * [REFRESH_MILLIS], since the enforcers ask on every foreground change.
 */
@Singleton
class AndroidExcludedApps @Inject constructor(
    @ApplicationContext private val context: Context,
) : ExcludedApps {

    @Volatile private var cachedDevicePackages: Set<String> = emptySet()

    @Volatile private var readAt = Long.MIN_VALUE

    override fun isExcluded(packageName: String): Boolean =
        AppExclusions.isExcluded(packageName, context.packageName, deviceExcluded())

    private fun deviceExcluded(): Set<String> {
        val now = SystemClock.elapsedRealtime()
        if (readAt != Long.MIN_VALUE && now - readAt < REFRESH_MILLIS) return cachedDevicePackages
        val packages = buildSet {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            runCatching { context.packageManager.queryIntentActivities(home, PackageManager.MATCH_DEFAULT_ONLY) }
                .getOrDefault(emptyList())
                .forEach { info -> info.activityInfo?.packageName?.let { add(it) } }
            runCatching { context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage }
                .getOrNull()?.let { add(it) }
            runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull()?.let { add(it) }
        }
        cachedDevicePackages = packages
        readAt = now
        return packages
    }

    private companion object {
        const val REFRESH_MILLIS = 30_000L
    }
}
