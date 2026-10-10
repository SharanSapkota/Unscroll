package com.unscroll.app.domain.section

/**
 * THE list of apps whose short-video section can be blocked on its own ("Block reels only"), by
 * package name. Every other app can only be blocked entirely. [SectionRulesConfig] holds the
 * identifiers for exactly these packages (a test checks they match).
 */
object ReelsCapableApps {
    val INSTAGRAM = setOf("com.instagram.android")
    val TIKTOK = setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")
    val FACEBOOK = setOf("com.facebook.katana")
    val YOUTUBE = setOf("com.google.android.youtube")

    private val sections: Map<String, BlockedSection> =
        INSTAGRAM.associateWith { BlockedSection.REELS } +
            TIKTOK.associateWith { BlockedSection.FOR_YOU } +
            FACEBOOK.associateWith { BlockedSection.REELS } +
            YOUTUBE.associateWith { BlockedSection.SHORTS }

    val packages: Set<String> get() = sections.keys

    fun isReelsCapable(packageName: String): Boolean = packageName in sections

    /** Which section the app's toggle blocks (for its label), or null for every other app. */
    fun sectionFor(packageName: String): BlockedSection? = sections[packageName]
}
