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

/** A verdict and, for the debug line, which rule produced it (identifiers only, never content). */
data class SectionResult(val verdict: SectionVerdict, val rule: String)

/** Tells which section of one app (one or more package names) is open. */
interface SectionDetector {
    val packages: Set<String>
    val section: BlockedSection

    /** [versionCode] is the installed app's version, or null if unknown. */
    fun detect(screen: SectionScreen, versionCode: Long?): SectionVerdict

    /** Like [detect] with the full installed [version] (code and name), plus the matched rule. */
    fun explain(screen: SectionScreen, version: AppVersion): SectionResult
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
 *    class name, AND every requiredViewIds entry on a visible node: IN_BLOCKED_SECTION.
 * 4. The rule set has anchorViewIds and none is visible: UNKNOWN (not a screen the rules know).
 * 5. Otherwise IN_ALLOWED_SECTION.
 *
 * [detect] (section blocking) looks rule sets up by version code only, so rule sets pinned to
 * version names or to some packages ([SectionRuleSet.versionNames], [SectionRuleSet.packages])
 * only apply through [explain] (the Reels-only timer); for blocking they stay UNKNOWN as before.
 */
class RuleBasedSectionDetector(private val rules: AppSectionRules) : SectionDetector {
    override val packages: Set<String> get() = rules.packages
    override val section: BlockedSection get() = rules.section

    override fun detect(screen: SectionScreen, versionCode: Long?): SectionVerdict {
        if (screen.packageName !in rules.packages) return SectionVerdict.UNKNOWN
        val set = rules.ruleSetFor(versionCode) ?: return SectionVerdict.UNKNOWN
        return evaluate(set, screen).verdict
    }

    override fun explain(screen: SectionScreen, version: AppVersion): SectionResult {
        if (screen.packageName !in rules.packages) return SectionResult(SectionVerdict.UNKNOWN, "not this app")
        val set = rules.ruleSetFor(screen.packageName, version)
            ?: return SectionResult(SectionVerdict.UNKNOWN, "no rules for this version")
        return evaluate(set, screen)
    }

    private fun evaluate(set: SectionRuleSet, screen: SectionScreen): SectionResult {
        if (set.isEmpty) return SectionResult(SectionVerdict.UNKNOWN, "no rules for this app")
        val root = screen.root ?: return SectionResult(SectionVerdict.UNKNOWN, "screen unreadable")
        val window = screen.windowClassName
        val visible = root.visible().toList()
        fun visibleId(entries: Set<String>): String? =
            visible.firstOrNull { matchesId(it.viewId, entries) }?.viewId?.let(::shortId)

        if (window != null && window in set.allowedWindowClasses) {
            return SectionResult(SectionVerdict.IN_ALLOWED_SECTION, "allowed window")
        }
        visibleId(set.allowedViewIds)?.let { return SectionResult(SectionVerdict.IN_ALLOWED_SECTION, "allowed $it") }

        val marker = when {
            window != null && window in set.blockedWindowClasses -> "window"
            else -> visible.firstOrNull { it.isSelected && matchesId(it.viewId, set.selectedTabViewIds) }
                ?.let { "selected ${shortId(it.viewId)}" }
                ?: visibleId(set.blockedViewIds)
                ?: visible.firstOrNull { it.className != null && it.className in set.blockedClassNames }?.className
        }
        val missing = set.requiredViewIds.filterNot { required -> visible.any { matchesId(it.viewId, setOf(required)) } }
        if (marker != null && missing.isEmpty()) {
            val with = set.requiredViewIds.joinToString(" + ") { shortId(it) }
            return SectionResult(SectionVerdict.IN_BLOCKED_SECTION, if (with.isEmpty()) marker else "$marker + $with")
        }
        if (set.anchorViewIds.isNotEmpty() && visibleId(set.anchorViewIds) == null) {
            return SectionResult(SectionVerdict.UNKNOWN, "no known screen")
        }
        return SectionResult(SectionVerdict.IN_ALLOWED_SECTION, if (marker == null) "no section marker" else "missing ${missing.joinToString { shortId(it) }}")
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

        /** "com.example.app:id/tab" → "tab", for the debug line. */
        fun shortId(viewId: String?): String = viewId.orEmpty().substringAfter(":id/").substringAfter("id/")
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

    /**
     * True when the config has identifiers for [packageName] at [versionCode]. Otherwise the
     * detector could only answer UNKNOWN, so the reels toggle is shown as not available.
     */
    fun isAvailable(packageName: String, versionCode: Long?): Boolean =
        rulesFor(packageName)?.ruleSetFor(versionCode)?.isEmpty == false

    /**
     * The Reels-only timer's version check: identifiers exist for [packageName] at [version]
     * (code and name; rule sets may be pinned to version names or packages).
     */
    fun isAvailable(packageName: String, version: AppVersion): Boolean =
        ruleSetFor(packageName, version)?.isEmpty == false

    /** The rule set the detector uses for [packageName] at [version], or null. */
    fun ruleSetFor(packageName: String, version: AppVersion): SectionRuleSet? =
        rulesFor(packageName)?.ruleSetFor(packageName, version)

    /** The config entry for [packageName], e.g. to tell whether its identifiers are filled in yet. */
    fun rulesFor(packageName: String): AppSectionRules? = config.firstOrNull { packageName in it.packages }
}
