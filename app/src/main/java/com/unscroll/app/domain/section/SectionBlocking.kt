package com.unscroll.app.domain.section

import com.unscroll.app.domain.scroll.ScrollConsent

/** When a section is blocked, per app. */
enum class SectionBlockMode {
    /** Whenever the section is open. */
    ALWAYS,

    /** Only once today's time in the app reached its daily limit. */
    AFTER_LIMIT,
}

/** The user's section-blocking settings, as stored. */
data class SectionBlockingSettings(
    /** The answer on the section-blocking disclosure (same states as scroll counting). */
    val consent: ScrollConsent = ScrollConsent.NOT_ASKED,
    /** The global kill switch: "Turn off section blocking". Nothing is read or covered while set. */
    val turnedOff: Boolean = false,
    /**
     * Before the quick toggles, the per-app "Block Reels" switch lived here. Now the reels block
     * is `reelsBlockedUntil` in `app_limits`; this set is only read once to move it there
     * (SectionBlockingRepository.migrateLegacyBlockedApps) and then cleared.
     */
    val legacyBlockedApps: Set<String> = emptySet(),
    /** Apps in [SectionBlockMode.AFTER_LIMIT]; the rest are [SectionBlockMode.ALWAYS]. */
    val afterLimitApps: Set<String> = emptySet(),
    /** Debug builds only: the Section Inspector logs identifiers to logcat. */
    val inspector: Boolean = false,
) {
    fun modeFor(packageName: String): SectionBlockMode =
        if (packageName in afterLimitApps) SectionBlockMode.AFTER_LIMIT else SectionBlockMode.ALWAYS
}

/** Everything that decides whether the section on screen gets covered. */
data class SectionCoverInputs(
    val settings: SectionBlockingSettings,
    val isPlus: Boolean,
    val packageName: String,
    /** Tracked and active (not paused, removed or over the free tier). */
    val tracked: Boolean,
    /** A safety exclusion (ExcludedApps): never blocked. */
    val excluded: Boolean,
    /** Today's time in the app reached its daily limit. */
    val limitReached: Boolean,
    val verdict: SectionVerdict,
    /**
     * The app's reels toggle (or "Block entire app") is running and the verdict is the blocked
     * section: BlockEvaluator.blocksSection.
     */
    val sectionBlocked: Boolean,
)

/** Pure rules for section blocking: consent, the kill switch, Plus and the per-app settings. */
object SectionBlockingRules {

    /** Blocking may run at all: the user agreed, didn't turn it off, and has Plus. */
    fun isActive(settings: SectionBlockingSettings, isPlus: Boolean): Boolean =
        settings.consent == ScrollConsent.AGREED && !settings.turnedOff && isPlus

    /**
     * Cover only what was positively identified as the blocked section (fail open): UNKNOWN and
     * IN_ALLOWED_SECTION never cover, and neither do excluded or untracked apps.
     */
    fun shouldCover(inputs: SectionCoverInputs): Boolean = with(inputs) {
        isActive(settings, isPlus) &&
            tracked &&
            !excluded &&
            sectionBlocked &&
            verdict == SectionVerdict.IN_BLOCKED_SECTION &&
            (settings.modeFor(packageName) == SectionBlockMode.ALWAYS || limitReached)
    }

    /** True when [usedTodayMillis] reached [dailyLimitMinutes]; never without a limit. */
    fun limitReached(dailyLimitMinutes: Int?, usedTodayMillis: Long): Boolean =
        dailyLimitMinutes != null && usedTodayMillis >= dailyLimitMinutes * 60_000L

    /**
     * The packages the section service listens to. Nothing without consent; while active, the
     * tracked apps whose reels toggle is on ([reelsBlockedApps]) and that have rules; in debug
     * builds with the inspector on, every tracked app (so identifiers can be captured for any).
     */
    fun monitoredPackages(
        settings: SectionBlockingSettings,
        isPlus: Boolean,
        trackedActive: Set<String>,
        rulePackages: Set<String>,
        inspectorAllowed: Boolean,
        reelsBlockedApps: Set<String>,
    ): Set<String> = when {
        settings.consent != ScrollConsent.AGREED -> emptySet()
        inspectorAllowed && settings.inspector -> trackedActive
        isActive(settings, isPlus) -> trackedActive intersect reelsBlockedApps intersect rulePackages
        else -> emptySet()
    }
}
