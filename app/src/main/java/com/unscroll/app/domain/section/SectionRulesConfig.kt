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
 * - TikTok: unsupported (empty, UNKNOWN). Its IDs are obfuscated and change with releases, and
 *   For You can't be told from Following with what was captured.
 * - Facebook: unsupported (empty, UNKNOWN). Its resource names are stripped from the build, so
 *   only class names differ, and those are shared with Stories and in-feed videos.
 *
 * Version codes weren't part of the captures (the Inspector header only logs them since this
 * change), so the two rule sets have open version ranges. The identifiers are readable names
 * that have to match in combination; after a rename they simply stop matching (fail open).
 */
object SectionRulesConfig {

    private const val IG = "com.instagram.android:id"
    private const val YT = "com.google.android.youtube:id"

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
            ),
        ),
    )

    val TIKTOK = AppSectionRules(
        appName = "TikTok",
        packages = ReelsCapableApps.TIKTOK,
        section = BlockedSection.FOR_YOU,
        ruleSets = listOf(
            // TODO(section-ids): unsupported. Captured TikTok 47.0.3: the IDs are obfuscated
            // ("id/omq"), so they need an exact version-code range, which the captures lack. The
            // selected IDs (the Home bottom tab and the top tab "android:id/text1") would be
            // the same on Following, which wasn't captured. Needed: a Following capture and the
            // version code (now in the Inspector header).
            SectionRuleSet(),
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
