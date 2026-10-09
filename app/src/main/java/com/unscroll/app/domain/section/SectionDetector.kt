package com.unscroll.app.domain.section

/** Where the user is in an app, as far as section blocking can tell. */
enum class SectionVerdict {
    /** Positively identified as the short-video section (Reels, For You, Shorts). */
    IN_BLOCKED_SECTION,

    /** Readable, and not the blocked section (chat, search, profiles, the rest). */
    IN_ALLOWED_SECTION,

    /** No rules for this app or version yet, or the screen couldn't be read. Never blocks. */
    UNKNOWN,
}

/** The short-video section an app's rules identify. Only used for wording. */
enum class BlockedSection { REELS, FOR_YOU, SHORTS }

/** Tells which section of one app (one or more package names) is open. */
interface SectionDetector {
    val packages: Set<String>
    val section: BlockedSection

    /** [versionCode] is the installed app's version, or null if unknown. */
    fun detect(screen: SectionScreen, versionCode: Long?): SectionVerdict
}

/**
 * The only detector: every app is described by data in [SectionRulesConfig], so updating
 * identifiers never touches this logic.
 *
 * Order, all on visible nodes only:
 * 1. No rule set for the package or version, or an empty one, or no readable tree: UNKNOWN.
 * 2. An allowed marker (window class or view ID, e.g. a chat screen): IN_ALLOWED_SECTION.
 *    Allowed always wins, so chat is never blocked even if a blocked marker is also on screen.
 * 3. A blocked window class, a selected tab from selectedTabViewIds, or a blocked view ID or
 *    class name: IN_BLOCKED_SECTION.
 * 4. Otherwise IN_ALLOWED_SECTION.
 */
class RuleBasedSectionDetector(private val rules: AppSectionRules) : SectionDetector {
    override val packages: Set<String> get() = rules.packages
    override val section: BlockedSection get() = rules.section

    override fun detect(screen: SectionScreen, versionCode: Long?): SectionVerdict {
        if (screen.packageName !in rules.packages) return SectionVerdict.UNKNOWN
        val set = rules.ruleSetFor(versionCode)?.takeUnless { it.isEmpty } ?: return SectionVerdict.UNKNOWN
        val root = screen.root ?: return SectionVerdict.UNKNOWN
        val window = screen.windowClassName
        val visible = root.visible().toList()

        if (window != null && window in set.allowedWindowClasses) return SectionVerdict.IN_ALLOWED_SECTION
        if (visible.any { matchesId(it.viewId, set.allowedViewIds) }) return SectionVerdict.IN_ALLOWED_SECTION

        val blocked = (window != null && window in set.blockedWindowClasses) ||
            visible.any { it.isSelected && matchesId(it.viewId, set.selectedTabViewIds) } ||
            visible.any { matchesId(it.viewId, set.blockedViewIds) } ||
            visible.any { it.className != null && it.className in set.blockedClassNames }
        return if (blocked) SectionVerdict.IN_BLOCKED_SECTION else SectionVerdict.IN_ALLOWED_SECTION
    }

    companion object {
        /**
         * [viewId] matches an entry written in full ("com.example.app:id/tab") or without the
         * package ("id/tab"), which then matches every package of the app (TikTok has two).
         */
        fun matchesId(viewId: String?, entries: Set<String>): Boolean {
            if (viewId.isNullOrEmpty() || entries.isEmpty()) return false
            if (viewId in entries) return true
            val withoutPackage = viewId.substringAfter(':', missingDelimiterValue = "")
            return withoutPackage.isNotEmpty() && withoutPackage in entries
        }
    }
}

/** Detectors for [config], looked up by package name. */
class SectionDetectors(private val config: List<AppSectionRules> = SectionRulesConfig.APPS) {
    private val byPackage: Map<String, SectionDetector> = config
        .flatMap { rules -> rules.packages.map { it to RuleBasedSectionDetector(rules) } }
        .toMap()

    /** Every package with an entry in the config (identifiers filled in or not). */
    val packages: Set<String> get() = byPackage.keys

    fun forPackage(packageName: String): SectionDetector? = byPackage[packageName]

    /** The config entry for [packageName], e.g. to tell whether its identifiers are filled in yet. */
    fun rulesFor(packageName: String): AppSectionRules? = config.firstOrNull { packageName in it.packages }
}
