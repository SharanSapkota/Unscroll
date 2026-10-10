package com.unscroll.app.domain.section

import com.unscroll.app.domain.scroll.ScrollConsent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Consent gating, the kill switch, Plus, the reels toggle and mode, and fail-open verdicts. */
class SectionBlockingRulesTest {

    private val app = "com.example.social"

    private val on = SectionBlockingSettings(consent = ScrollConsent.AGREED)

    private fun inputs(
        settings: SectionBlockingSettings = on,
        isPlus: Boolean = true,
        tracked: Boolean = true,
        excluded: Boolean = false,
        limitReached: Boolean = false,
        verdict: SectionVerdict = SectionVerdict.IN_BLOCKED_SECTION,
        sectionBlocked: Boolean = verdict == SectionVerdict.IN_BLOCKED_SECTION,
    ) = SectionCoverInputs(settings, isPlus, app, tracked, excluded, limitReached, verdict, sectionBlocked)

    @Test
    fun covers_aPositivelyIdentifiedSection() {
        assertTrue(SectionBlockingRules.shouldCover(inputs()))
    }

    @Test
    fun neverCovers_unknownOrAllowed() {
        assertFalse(SectionBlockingRules.shouldCover(inputs(verdict = SectionVerdict.UNKNOWN)))
        assertFalse(SectionBlockingRules.shouldCover(inputs(verdict = SectionVerdict.IN_ALLOWED_SECTION)))
    }

    @Test
    fun consentGating() {
        ScrollConsent.entries.filter { it != ScrollConsent.AGREED }.forEach { consent ->
            val settings = on.copy(consent = consent)
            assertFalse(consent.name, SectionBlockingRules.isActive(settings, isPlus = true))
            assertFalse(consent.name, SectionBlockingRules.shouldCover(inputs(settings = settings)))
        }
    }

    @Test
    fun killSwitch_stopsEverything() {
        val off = on.copy(turnedOff = true)
        assertFalse(SectionBlockingRules.isActive(off, isPlus = true))
        assertFalse(SectionBlockingRules.shouldCover(inputs(settings = off)))
        // Nothing is listened to either (no app in serviceInfo.packageNames).
        assertEquals(emptySet<String>(), monitored(off))
    }

    @Test
    fun plusOnly() {
        assertFalse(SectionBlockingRules.isActive(on, isPlus = false))
        assertFalse(SectionBlockingRules.shouldCover(inputs(isPlus = false)))
        assertEquals(emptySet<String>(), monitored(on, isPlus = false))
    }

    @Test
    fun perApp_offByDefault_andOnlyTrackedNotExcludedApps() {
        // The reels toggle is off (BlockEvaluator.blocksSection said no).
        assertFalse(SectionBlockingRules.shouldCover(inputs(sectionBlocked = false)))
        assertFalse(SectionBlockingRules.shouldCover(inputs(tracked = false)))
        assertFalse(SectionBlockingRules.shouldCover(inputs(excluded = true)))
    }

    @Test
    fun afterLimitMode_waitsForTheDailyLimit() {
        val afterLimit = on.copy(afterLimitApps = setOf(app))
        assertEquals(SectionBlockMode.AFTER_LIMIT, afterLimit.modeFor(app))
        assertEquals(SectionBlockMode.ALWAYS, on.modeFor(app))
        assertFalse(SectionBlockingRules.shouldCover(inputs(settings = afterLimit, limitReached = false)))
        assertTrue(SectionBlockingRules.shouldCover(inputs(settings = afterLimit, limitReached = true)))
    }

    @Test
    fun limitReached() {
        assertFalse(SectionBlockingRules.limitReached(dailyLimitMinutes = null, usedTodayMillis = Long.MAX_VALUE))
        assertFalse(SectionBlockingRules.limitReached(30, 30 * 60_000L - 1))
        assertTrue(SectionBlockingRules.limitReached(30, 30 * 60_000L))
    }

    @Test
    fun monitoredPackages() {
        val tracked = setOf(app, "com.other.app", "com.example.video")
        val rulePackages = setOf(app, "com.example.video")
        // Only tracked apps that have rules and are chosen by the user.
        assertEquals(setOf(app), monitored(on, trackedActive = tracked, rulePackages = rulePackages))
        // Only apps whose reels toggle is on.
        assertEquals(
            emptySet<String>(),
            monitored(on, trackedActive = tracked, rulePackages = rulePackages, reelsBlocked = emptySet()),
        )
        assertEquals(
            setOf(app, "com.example.video"),
            monitored(on, trackedActive = tracked, rulePackages = rulePackages, reelsBlocked = tracked),
        )
        // Without consent: nothing, even with the inspector on.
        assertEquals(
            emptySet<String>(),
            monitored(on.copy(consent = ScrollConsent.NOT_ASKED, inspector = true), inspectorAllowed = true),
        )
        // Debug inspector: every tracked app, regardless of Plus and the kill switch.
        val inspecting = on.copy(inspector = true, turnedOff = true)
        assertEquals(tracked, monitored(inspecting, isPlus = false, trackedActive = tracked, inspectorAllowed = true))
        // Release builds ignore the inspector.
        assertEquals(emptySet<String>(), monitored(inspecting, trackedActive = tracked, inspectorAllowed = false))
    }

    private fun monitored(
        settings: SectionBlockingSettings,
        isPlus: Boolean = true,
        trackedActive: Set<String> = setOf(app),
        rulePackages: Set<String> = setOf(app),
        inspectorAllowed: Boolean = false,
        reelsBlocked: Set<String> = setOf(app),
    ) = SectionBlockingRules.monitoredPackages(settings, isPlus, trackedActive, rulePackages, inspectorAllowed, reelsBlocked)
}
