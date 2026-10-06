package com.unscroll.app.domain.blocking

import com.unscroll.app.domain.tracking.TrackedApps

/**
 * The last line of defence against blocking something the user needs: only tracked apps can ever
 * be blocked, and never Unscroll itself, a home screen, Settings, the phone or emergency apps,
 * even if one of them somehow ended up in the tracked list.
 */
object BlockSafety {

    private val PROTECTED = setOf(
        "com.android.settings",
        "com.android.phone",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.android.server.telecom",
        "com.android.emergency",
        "com.google.android.apps.safetyhub",
        "com.android.systemui",
    )

    fun canBlock(packageName: String, ownPackage: String, homePackages: Set<String>): Boolean =
        TrackedApps.isTracked(packageName) &&
            packageName != ownPackage &&
            packageName !in homePackages &&
            packageName !in PROTECTED
}
