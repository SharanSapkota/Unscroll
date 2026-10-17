package com.unscroll.app.domain.section

/**
 * THE place where section blocking learns what Reels, For You and Shorts look like. Only data:
 * to support an app or a new app version, edit the sets here; the logic in
 * [RuleBasedSectionDetector] stays as it is.
 *
 * Capture identifiers with the debug-only Section Inspector (Settings › Debug, logcat tag
 * "SectionInspector"; see docs/SECTION_BLOCKING.md). Only use identifiers seen in those logs,
 * never guessed ones. Each rule set may be limited to a version range (minVersionCode /
 * maxVersionCode from the app's PackageInfo) when an update renames things.
 *
 * The apps themselves (package names) come from [ReelsCapableApps].
 *
 * Status (Section Inspector captures from a Pixel 8, Android 17, 2026-10-11; every rule set is checked against them in
 * SectionCaptureTest, which fails if any captured allowed screen would be blocked):
 * - Instagram: Reels tab and Reels viewer. Readable resource names, a combination of IDs.
 * - YouTube: the Shorts player. Readable resource names, a combination of IDs.
 * - TikTok: only for the Reels-only timer, pinned to the captured version name (47.0.3) and to
 *   com.zhiliaoapp.musically. Its IDs are obfuscated and change with releases, and For You can't
 *   be told from Following with what was captured. Section blocking looks rules up by version
 *   code only, so for blocking TikTok stays unsupported (UNKNOWN) as before.
 * - Facebook: unsupported (empty, UNKNOWN). Its resource names are stripped from the build, so
 *   only class names differ, and those are shared with Stories and in-feed videos.
 *
 * Version codes weren't part of the captures (the Inspector header only logs them since this
 * change), so the Instagram and YouTube rule sets have open version ranges. The identifiers are
 * readable names that have to match in combination; after a rename they simply stop matching
 * (fail open), and Instagram's tab-bar anchor turns an unrecognized screen into UNKNOWN.
 */
object SectionRulesConfig {

    private const val IG = "com.instagram.android:id"
    private const val YT = "com.google.android.youtube:id"
    private const val TT = "com.zhiliaoapp.musically:id"

    private fun capture(versionName: String) = SectionCapture(
        device = "Pixel 8",
        androidVersion = "17",
        appVersionName = versionName,
        appVersionCode = null,
        verifiedDate = "2026-10-11",
    )

    val INSTAGRAM = AppSectionRules(
        appName = "Instagram",
        packages = ReelsCapableApps.INSTAGRAM,
        section = BlockedSection.REELS,
        ruleSets = listOf(
            SectionRuleSet(
                verifiedOn = capture(versionName = "450.0.0.50.77"),
                // The Reels tab while it is the selected bottom tab, or the full-screen Reels
                // pager (also reached from a profile or a shared reel)...
                selectedTabViewIds = setOf("$IG/clips_tab"),
                blockedViewIds = setOf("$IG/clips_viewer_view_pager"),
                // ...and only with the Reels viewer itself on screen. clips_tab is on every
                // screen (unselected), so the tab alone never blocks.
                requiredViewIds = setOf("$IG/clips_viewer_container"),
                // Direct inbox and chat thread: never blocked, even with a reel inside.
                allowedViewIds = setOf(
                    "$IG/direct_inbox_action_bar",
                    "$IG/inbox_refreshable_thread_list_recyclerview",
                    "$IG/message_thread_container",
                    "$IG/row_thread_composer_container",
                ),
                // The bottom tab bar is on every captured screen (home, search, Reels, inbox,
                // profile). A screen with no rule match and no tab bar is one the captures don't
                // cover (or IDs were renamed by an update): UNKNOWN, so the timer falls back.
                anchorViewIds = setOf("$IG/tab_bar"),
            ),
        ),
    )

    val TIKTOK = AppSectionRules(
        appName = "TikTok",
        packages = ReelsCapableApps.TIKTOK,
        section = BlockedSection.FOR_YOU,
        ruleSets = listOf(
            // Captured TikTok 47.0.3 (com.zhiliaoapp.musically). Obfuscated IDs ("id/omq") change
            // with every release and the captures have no version code, so this set is pinned to
            // that exact version name and package: any other version is UNKNOWN (the timer falls
            // back to the whole app). For You: the Home bottom tab (omq) selected, with the video
            // pager's readable views (video_seek_bar, view_pager_layout_wrapper), which none of the
            // captured chat, friends, profile or upload screens have. Following (also under Home)
            // wasn't captured and would most likely match too. TODO(section-ids): section blocking
            // still needs a Following capture and the version code (now in the Inspector header).
            SectionRuleSet(
                verifiedOn = capture(versionName = "47.0.3"),
                versionNames = setOf("47.0.3"),
                packages = setOf("com.zhiliaoapp.musically"),
                selectedTabViewIds = setOf("$TT/omq"),
                requiredViewIds = setOf("$TT/video_seek_bar", "$TT/view_pager_layout_wrapper"),
            ),
        ),
    )

    val FACEBOOK = AppSectionRules(
        appName = "Facebook",
        packages = ReelsCapableApps.FACEBOOK,
        section = BlockedSection.REELS,
        ruleSets = listOf(
            // TODO(section-ids): unsupported. Captured Facebook 581.0.0.45.58: every resource
            // name is stripped ("id/(name removed)") and the Reels screen differs from the
            // allowed ones only by SurfaceView, SeekBar and RelativeLayout, which Stories (and
            // any in-feed video) have too. No safe rule without readable identifiers.
            SectionRuleSet(),
        ),
    )

    val YOUTUBE = AppSectionRules(
        appName = "YouTube",
        packages = ReelsCapableApps.YOUTUBE,
        section = BlockedSection.SHORTS,
        ruleSets = listOf(
            SectionRuleSet(
                verifiedOn = capture(versionName = "21.40.161"),
                // The Shorts player: its player view, inside the Shorts pager and its fragment.
                // Exact names only: reel_time_bar and reel_container also exist outside Shorts.
                blockedViewIds = setOf("$YT/reel_watch_player"),
                requiredViewIds = setOf("$YT/reel_recycler", "$YT/reel_watch_fragment_root"),
            ),
        ),
    )

    val APPS: List<AppSectionRules> = listOf(INSTAGRAM, TIKTOK, FACEBOOK, YOUTUBE)
}
