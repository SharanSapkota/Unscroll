package com.unscroll.app.domain.tracking

/**
 * The apps seeded into the tracked list on a fresh install. They are ordinary entries after that:
 * the user can remove them like any other app. Which apps are tracked is decided only by
 * TrackedAppsRepository (Room), never by this list.
 */
object DefaultTrackedApps {
    const val INSTAGRAM = "com.instagram.android"

    /** TikTok ships under two package names depending on the region; both are seeded. */
    const val TIKTOK = "com.zhiliaoapp.musically"
    const val TIKTOK_ASIA = "com.ss.android.ugc.trill"
    const val FACEBOOK = "com.facebook.katana"

    val packageNames: List<String> = listOf(INSTAGRAM, TIKTOK, TIKTOK_ASIA, FACEBOOK)
}
