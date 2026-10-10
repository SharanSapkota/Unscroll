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
 * Every entry below is still empty, so every detector returns UNKNOWN and nothing is blocked
 * until real identifiers are filled in.
 */
object SectionRulesConfig {

    val INSTAGRAM = AppSectionRules(
        appName = "Instagram",
        packages = ReelsCapableApps.INSTAGRAM,
        section = BlockedSection.REELS,
        ruleSets = listOf(
            SectionRuleSet(
                // TODO(section-ids): Reels tab and full-screen Reels viewer, from Section Inspector captures.
                blockedWindowClasses = emptySet(),
                blockedViewIds = emptySet(),
                blockedClassNames = emptySet(),
                selectedTabViewIds = emptySet(),
                // TODO(section-ids): Direct inbox and chat thread markers (never blocked).
                allowedWindowClasses = emptySet(),
                allowedViewIds = emptySet(),
            ),
        ),
    )

    val TIKTOK = AppSectionRules(
        appName = "TikTok",
        packages = ReelsCapableApps.TIKTOK,
        section = BlockedSection.FOR_YOU,
        ruleSets = listOf(
            SectionRuleSet(
                // TODO(section-ids): For You feed; use "id/…" entries if both packages share them.
                blockedWindowClasses = emptySet(),
                blockedViewIds = emptySet(),
                blockedClassNames = emptySet(),
                selectedTabViewIds = emptySet(),
                // TODO(section-ids): Inbox and chat markers (never blocked).
                allowedWindowClasses = emptySet(),
                allowedViewIds = emptySet(),
            ),
        ),
    )

    val FACEBOOK = AppSectionRules(
        appName = "Facebook",
        packages = ReelsCapableApps.FACEBOOK,
        section = BlockedSection.REELS,
        ruleSets = listOf(
            SectionRuleSet(
                // TODO(section-ids): Reels / Video tab and the full-screen Reels viewer.
                blockedWindowClasses = emptySet(),
                blockedViewIds = emptySet(),
                blockedClassNames = emptySet(),
                selectedTabViewIds = emptySet(),
                // TODO(section-ids): chat (if any in-app) and other never-blocked markers.
                allowedWindowClasses = emptySet(),
                allowedViewIds = emptySet(),
            ),
        ),
    )

    val YOUTUBE = AppSectionRules(
        appName = "YouTube",
        packages = ReelsCapableApps.YOUTUBE,
        section = BlockedSection.SHORTS,
        ruleSets = listOf(
            SectionRuleSet(
                // TODO(section-ids): Shorts tab and the Shorts player.
                blockedWindowClasses = emptySet(),
                blockedViewIds = emptySet(),
                blockedClassNames = emptySet(),
                selectedTabViewIds = emptySet(),
                // TODO(section-ids): regular video player and other never-blocked markers.
                allowedWindowClasses = emptySet(),
                allowedViewIds = emptySet(),
            ),
        ),
    )

    val APPS: List<AppSectionRules> = listOf(INSTAGRAM, TIKTOK, FACEBOOK, YOUTUBE)
}
