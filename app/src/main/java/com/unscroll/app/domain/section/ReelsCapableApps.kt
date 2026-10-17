package com.unscroll.app.domain.section

/**
 * THE list of Reels-capable apps, by package name: Instagram and TikTok (both packages). Only
 * these get Reels-specific behavior and controls: the Reels-only timer and "Timer visibility",
 * covers and nudges only while Reels / For You is on screen, Reels time and swipes for their
 * limits, and "Block reels only" (while SectionBlockingConfig allows it). Every other app,
 * Facebook and YouTube included, only has whole-app controls and whole-app limits.
 *
 * [FACEBOOK] and [YOUTUBE] stay here only because SectionRulesConfig keeps their captured
 * section-blocking identifiers; they are not Reels-capable.
 */
object ReelsCapableApps {
    val INSTAGRAM = setOf("com.instagram.android")
    val TIKTOK = setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")

    /** Captured for section blocking only (SectionRulesConfig); not Reels-capable. */
    val FACEBOOK = setOf("com.facebook.katana")
    val YOUTUBE = setOf("com.google.android.youtube")

    private val sections: Map<String, BlockedSection> =
        INSTAGRAM.associateWith { BlockedSection.REELS } +
            TIKTOK.associateWith { BlockedSection.FOR_YOU }

    val packages: Set<String> get() = sections.keys

    fun isReelsCapable(packageName: String): Boolean = packageName in sections

    /** Which section the app's Reels controls are about (for labels), or null for every other app. */
    fun sectionFor(packageName: String): BlockedSection? = sections[packageName]

    /** TikTok says "For You" where Instagram says "Reels". */
    fun saysForYou(packageName: String): Boolean = sectionFor(packageName) == BlockedSection.FOR_YOU
}
