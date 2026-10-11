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
    /**
     * View IDs that must ALL be on visible nodes as well before anything above counts as the
     * blocked section, so a rule is a combination rather than one ID. Empty: no extra condition.
     */
    val requiredViewIds: Set<String> = emptySet(),
    /** Where and when the identifiers were captured and checked. Documentation only. */
    val verifiedOn: SectionCapture? = null,
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

/** The capture a rule set was built from, kept next to the identifiers. Never read at runtime. */
data class SectionCapture(
    val device: String,
    val androidVersion: String,
    /** The app's versionName, as shown in its App info page. */
    val appVersionName: String,
    /** The app's versionCode, or null when the capture didn't include it. */
    val appVersionCode: Long?,
    /** ISO date (yyyy-mm-dd) the captures were taken and the rules checked against them. */
    val verifiedDate: String,
)
