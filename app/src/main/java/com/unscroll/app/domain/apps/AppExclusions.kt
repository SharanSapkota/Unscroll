package com.unscroll.app.domain.apps

/**
 * Apps that can never be added to Unscroll or blocked: Unscroll itself, home screens, Settings,
 * the phone and emergency apps, the default SMS app, the Play Store and critical system packages.
 * Enforced by TrackedAppsRepository (adding), the picker (hidden) and BlockEvaluator/the enforcers
 * (blocking), so a bug in one layer can't lock anyone out of their phone.
 */
object AppExclusions {

    /** Always excluded, on every device. */
    val ALWAYS: Set<String> = setOf(
        // System and settings
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.samsung.android.settings",
        // Phone and dialers
        "com.android.phone",
        "com.android.dialer",
        "com.google.android.dialer",
        "com.samsung.android.dialer",
        "com.samsung.android.incallui",
        "com.android.server.telecom",
        "com.android.contacts",
        "com.google.android.contacts",
        "com.samsung.android.contacts",
        // Messaging used for emergencies (the default SMS app is added on top, per device)
        "com.android.mms",
        "com.android.messaging",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        // Emergency services
        "com.android.emergency",
        "com.google.android.apps.safetyhub",
        "com.android.cellbroadcastreceiver",
        "com.google.android.cellbroadcastreceiver",
        "com.samsung.android.emergency",
        // Play Store
        "com.android.vending",
    )

    /**
     * @param ownPackage Unscroll's package name
     * @param deviceExcluded per-device additions: home screens, the default dialer and SMS app
     */
    fun isExcluded(packageName: String, ownPackage: String, deviceExcluded: Set<String> = emptySet()): Boolean =
        packageName == ownPackage ||
            packageName in ALWAYS ||
            packageName in deviceExcluded ||
            packageName.startsWith(SYSTEM_UI_PREFIX)

    private const val SYSTEM_UI_PREFIX = "com.android.systemui."
}

/** Device-aware exclusions (own package, home screens, default dialer and SMS app). */
fun interface ExcludedApps {
    fun isExcluded(packageName: String): Boolean

    companion object {
        /** Tests: only the fixed list (with a placeholder for Unscroll's own package). */
        val STATIC = ExcludedApps { AppExclusions.isExcluded(it, ownPackage = "com.unscroll.app") }
    }
}
