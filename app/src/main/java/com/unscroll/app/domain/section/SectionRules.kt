package com.unscroll.app.domain.section

/**
 * Identifiers for one app's versions in [range]. Every set holds technical identifiers only:
 * view IDs ("com.example.app:id/name", or "id/name" for every package of the app), class names
 * and window (activity) class names. Never text or content descriptions.
 */
data class SectionRuleSet(
    /** Inclusive version range; null bounds are open. */
    val minVersionCode: Long? = null,
    val maxVersionCode: Long? = null,
    /** Window/activity classes that are the blocked section. */
    val blockedWindowClasses: Set<String> = emptySet(),
    /** View IDs that only exist inside the blocked section. */
    val blockedViewIds: Set<String> = emptySet(),
    /** View classes that only exist inside the blocked section. */
    val blockedClassNames: Set<String> = emptySet(),
    /** Tab view IDs that mean the blocked section is open while they are selected (isSelected). */
    val selectedTabViewIds: Set<String> = emptySet(),
    /** Window classes that are positively not the blocked section (chat, ...). Win over blocked. */
    val allowedWindowClasses: Set<String> = emptySet(),
    /** View IDs that are positively not the blocked section (a chat thread, ...). Win over blocked. */
    val allowedViewIds: Set<String> = emptySet(),
) {
    /** No blocked identifiers: nothing can be detected, the detector returns UNKNOWN. */
    val isEmpty: Boolean
        get() = blockedWindowClasses.isEmpty() && blockedViewIds.isEmpty() &&
            blockedClassNames.isEmpty() && selectedTabViewIds.isEmpty()

    fun covers(versionCode: Long?): Boolean {
        if (versionCode == null) return minVersionCode == null && maxVersionCode == null
        return (minVersionCode == null || versionCode >= minVersionCode) &&
            (maxVersionCode == null || versionCode <= maxVersionCode)
    }
}

/** One app's entry in [SectionRulesConfig]. */
data class AppSectionRules(
    /** For logs and docs only. */
    val appName: String,
    val packages: Set<String>,
    val section: BlockedSection,
    /** Checked in order; the first that covers the installed version is used. */
    val ruleSets: List<SectionRuleSet>,
) {
    fun ruleSetFor(versionCode: Long?): SectionRuleSet? = ruleSets.firstOrNull { it.covers(versionCode) }

    /** True once at least one rule set has identifiers. */
    val isReady: Boolean get() = ruleSets.any { !it.isEmpty }
}
