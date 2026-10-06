package com.unscroll.app.domain.tracking

/**
 * The apps Unscroll tracks. Keep this the single source of truth: later milestones will let users
 * add any installed app. Any package added here must also be listed under `<queries>` in
 * AndroidManifest.xml so its name and icon are visible on Android 11+.
 */
object TrackedApps {
    const val INSTAGRAM = "com.instagram.android"
    const val TIKTOK = "com.zhiliaoapp.musically"
    const val TIKTOK_ASIA = "com.ss.android.ugc.trill"
    const val FACEBOOK = "com.facebook.katana"

    val packageNames: Set<String> = setOf(INSTAGRAM, TIKTOK, TIKTOK_ASIA, FACEBOOK)

    fun isTracked(packageName: String?): Boolean = packageName in packageNames
}
