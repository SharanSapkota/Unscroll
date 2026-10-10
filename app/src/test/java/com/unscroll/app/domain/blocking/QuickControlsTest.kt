package com.unscroll.app.domain.blocking

import com.unscroll.app.domain.section.BlockedSection
import com.unscroll.app.domain.section.ReelsCapableApps
import com.unscroll.app.domain.section.SectionDetectors
import com.unscroll.app.domain.section.SectionRulesConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The quick toggles: durations, on/off, expiry, the reels toggle's setup steps and labels. */
class QuickControlsTest {

    private val now = 1_000_000_000L
    private val ready = SectionAccess(rulesAvailable = true, isPlus = true, consented = true, serviceOn = true, turnedOff = false)

    @Test
    fun reelsCapableApps_areExactlyTheFive() {
        assertEquals(
            setOf(
                "com.instagram.android",
                "com.zhiliaoapp.musically",
                "com.ss.android.ugc.trill",
                "com.facebook.katana",
                "com.google.android.youtube",
            ),
            ReelsCapableApps.packages,
        )
        assertEquals(BlockedSection.SHORTS, ReelsCapableApps.sectionFor("com.google.android.youtube"))
        assertEquals(BlockedSection.FOR_YOU, ReelsCapableApps.sectionFor("com.ss.android.ugc.trill"))
        assertEquals(BlockedSection.REELS, ReelsCapableApps.sectionFor("com.facebook.katana"))
    }

    @Test
    fun sectionRules_coverExactlyTheReelsCapableApps() {
        assertEquals(ReelsCapableApps.packages, SectionRulesConfig.APPS.flatMap { it.packages }.toSet())
        SectionRulesConfig.APPS.forEach { rules ->
            rules.packages.forEach { assertEquals(rules.section, ReelsCapableApps.sectionFor(it)) }
        }
    }

    @Test
    fun nonReelsApps_neverExposeTheReelsToggle() {
        listOf("com.reddit.frontpage", "com.twitter.android", "com.android.chrome", "com.whatsapp").forEach { pkg ->
            // Even with a stored reels block, a normal app only gets "Block entire app".
            val state = QuickControls.build(pkg, LimitSettings(reelsBlockedUntil = TimedBlock.FOREVER), now, ready)
            assertNull(pkg, state.reels)
            assertNull(pkg, QuickControls.status(pkg, LimitSettings(reelsBlockedUntil = TimedBlock.FOREVER), now))
        }
        ReelsCapableApps.packages.forEach { pkg ->
            assertNotNull(pkg, QuickControls.build(pkg, LimitSettings(), now, ready).reels)
        }
    }

    @Test
    fun defaults_areOff_untilTurnedOff() {
        val state = QuickControls.build("com.instagram.android", LimitSettings(), now, ready)
        assertEquals(QuickToggleState(on = false, remainingMillis = null, duration = BlockDuration.UNTIL_OFF), state.entireApp)
        assertEquals(QuickToggleState(on = false, remainingMillis = null, duration = BlockDuration.UNTIL_OFF), state.reels?.toggle)
    }

    @Test
    fun turningOn_usesTheRememberedDuration_perToggle() {
        val settings = LimitSettings(lastReelsDuration = BlockDuration.MIN_30, lastEntireDuration = BlockDuration.HOUR_2)
        val reels = QuickBlockRules.setOn(settings, QuickBlockTarget.REELS, true, now)
        assertEquals(now + 30 * MINUTE, reels.reelsBlockedUntil)
        assertNull(reels.entireAppBlockedUntil)
        val entire = QuickBlockRules.setOn(settings, QuickBlockTarget.ENTIRE_APP, true, now)
        assertEquals(now + 120 * MINUTE, entire.entireAppBlockedUntil)
        val forever = QuickBlockRules.setOn(LimitSettings(), QuickBlockTarget.ENTIRE_APP, true, now)
        assertEquals(TimedBlock.FOREVER, forever.entireAppBlockedUntil)
    }

    @Test
    fun turningOff_isImmediate_andKeepsTheDuration() {
        val on = QuickBlockRules.setOn(LimitSettings(lastReelsDuration = BlockDuration.MIN_15), QuickBlockTarget.REELS, true, now)
        val off = QuickBlockRules.setOn(on, QuickBlockTarget.REELS, false, now + 1)
        assertNull(off.reelsBlockedUntil)
        assertEquals(BlockDuration.MIN_15, off.lastReelsDuration)
        assertFalse(QuickBlockRules.isOn(off, QuickBlockTarget.REELS, now + 1))
    }

    @Test
    fun changingTheDuration_whileOff_onlyRemembersIt() {
        val changed = QuickBlockRules.setDuration(LimitSettings(), QuickBlockTarget.REELS, BlockDuration.HOUR_1, now)
        assertNull(changed.reelsBlockedUntil)
        assertEquals(BlockDuration.HOUR_1, changed.lastReelsDuration)
    }

    @Test
    fun changingTheDuration_whileOn_restartsIt() {
        val on = QuickBlockRules.setOn(LimitSettings(lastReelsDuration = BlockDuration.MIN_15), QuickBlockTarget.REELS, true, now)
        val later = now + 10 * MINUTE
        val changed = QuickBlockRules.setDuration(on, QuickBlockTarget.REELS, BlockDuration.MIN_30, later)
        assertEquals(later + 30 * MINUTE, changed.reelsBlockedUntil)
        val untilOff = QuickBlockRules.setDuration(on, QuickBlockTarget.REELS, BlockDuration.UNTIL_OFF, later)
        assertEquals(TimedBlock.FOREVER, untilOff.reelsBlockedUntil)
    }

    @Test
    fun countdown_andExpiry() {
        val settings = LimitSettings(reelsBlockedUntil = now + 12 * MINUTE + 41_000L)
        val state = QuickControls.build("com.instagram.android", settings, now, ready)
        assertEquals(12 * MINUTE + 41_000L, state.reels?.toggle?.remainingMillis)
        assertTrue(state.reels!!.toggle.on)

        // At the exact end the toggle shows off; clearExpired then turns it off for good.
        val atEnd = QuickControls.build("com.instagram.android", settings, settings.reelsBlockedUntil!!, ready)
        assertFalse(atEnd.reels!!.toggle.on)
        assertNull(QuickBlockRules.clearExpired(settings, settings.reelsBlockedUntil!!).reelsBlockedUntil)
        // Still running: untouched (same object).
        assertTrue(QuickBlockRules.clearExpired(settings, now) === settings)
        // "Until I turn it off" never expires.
        val forever = LimitSettings(entireAppBlockedUntil = TimedBlock.FOREVER)
        assertTrue(QuickBlockRules.clearExpired(forever, Long.MAX_VALUE - 1) === forever)
    }

    @Test
    fun nextExpiry_isTheEarliestTimedEnd() {
        val settings = LimitSettings(entireAppBlockedUntil = now + 60 * MINUTE, reelsBlockedUntil = now + 15 * MINUTE)
        assertEquals(now + 15 * MINUTE, QuickBlockRules.nextExpiry(settings, now))
        assertNull(QuickBlockRules.nextExpiry(LimitSettings(entireAppBlockedUntil = TimedBlock.FOREVER), now))
        assertNull(QuickBlockRules.nextExpiry(LimitSettings(reelsBlockedUntil = now - 1), now))
    }

    @Test
    fun entireApp_showsReelsIncluded_andTurningItOffRestoresTheReelsToggle() {
        val reelsOff = LimitSettings(entireAppBlockedUntil = TimedBlock.FOREVER)
        val included = QuickControls.build("com.instagram.android", reelsOff, now, ready).reels!!
        assertTrue(included.included)
        assertFalse(included.toggle.on)
        assertEquals(ReelsTapAction.NONE, QuickControls.reelsTap(included, turnOn = false))

        val reelsOn = reelsOff.copy(reelsBlockedUntil = now + 15 * MINUTE)
        val afterEntireOff = QuickBlockRules.setOn(reelsOn, QuickBlockTarget.ENTIRE_APP, false, now)
        val reels = QuickControls.build("com.instagram.android", afterEntireOff, now, ready).reels!!
        assertFalse(reels.included)
        assertTrue(reels.toggle.on)
        val afterEntireOffWithoutReels = QuickBlockRules.setOn(reelsOff, QuickBlockTarget.ENTIRE_APP, false, now)
        assertFalse(QuickControls.build("com.instagram.android", afterEntireOffWithoutReels, now, ready).reels!!.toggle.on)
    }

    @Test
    fun reelsAvailability_inOrder() {
        assertEquals(ReelsAvailability.AVAILABLE, QuickControls.availability(ready))
        assertEquals(ReelsAvailability.NOT_AVAILABLE, QuickControls.availability(ready.copy(rulesAvailable = false, isPlus = false)))
        assertEquals(ReelsAvailability.NEEDS_PLUS, QuickControls.availability(ready.copy(isPlus = false, consented = false)))
        assertEquals(ReelsAvailability.NEEDS_CONSENT, QuickControls.availability(ready.copy(consented = false, serviceOn = false)))
        assertEquals(ReelsAvailability.NEEDS_SERVICE, QuickControls.availability(ready.copy(serviceOn = false)))
        assertEquals(ReelsAvailability.TURNED_OFF, QuickControls.availability(ready.copy(turnedOff = true)))
    }

    @Test
    fun reelsTap_goesThroughPlusConsentAndTheService_butOffAlwaysWorks() {
        fun tap(access: SectionAccess, turnOn: Boolean) = QuickControls.reelsTap(
            QuickControls.build("com.instagram.android", LimitSettings(), now, access).reels!!,
            turnOn,
        )
        assertEquals(ReelsTapAction.TOGGLE, tap(ready, true))
        assertEquals(ReelsTapAction.TOGGLE, tap(ready.copy(turnedOff = true), true))
        assertEquals(ReelsTapAction.NONE, tap(ready.copy(rulesAvailable = false), true))
        assertEquals(ReelsTapAction.OPEN_PAYWALL, tap(ready.copy(isPlus = false), true))
        assertEquals(ReelsTapAction.OPEN_DISCLOSURE, tap(ready.copy(consented = false), true))
        assertEquals(ReelsTapAction.OPEN_ACCESSIBILITY, tap(ready.copy(serviceOn = false), true))
        listOf(ready.copy(isPlus = false), ready.copy(rulesAvailable = false), ready.copy(serviceOn = false)).forEach {
            assertEquals(ReelsTapAction.TOGGLE, tap(it, false))
        }
        assertTrue(QuickControls.canCompletePendingEnable(ReelsAvailability.AVAILABLE))
        assertFalse(QuickControls.canCompletePendingEnable(ReelsAvailability.NEEDS_SERVICE))
    }

    @Test
    fun placeholderRules_meanNotAvailable() {
        // Every app ships with empty identifiers until they are captured with the Section Inspector.
        val detectors = SectionDetectors()
        ReelsCapableApps.packages.forEach { assertFalse(it, detectors.isAvailable(it, versionCode = 1L)) }
        assertFalse(detectors.isAvailable("com.reddit.frontpage", versionCode = 1L))
    }

    @Test
    fun status_entireFirst_thenReels() {
        val pkg = "com.google.android.youtube"
        assertNull(QuickControls.status(pkg, LimitSettings(), now))
        assertEquals(
            QuickStatus(QuickBlockTarget.REELS, BlockedSection.SHORTS, 12 * MINUTE),
            QuickControls.status(pkg, LimitSettings(reelsBlockedUntil = now + 12 * MINUTE), now),
        )
        assertEquals(
            QuickStatus(QuickBlockTarget.ENTIRE_APP, null, null),
            QuickControls.status(
                pkg,
                LimitSettings(entireAppBlockedUntil = TimedBlock.FOREVER, reelsBlockedUntil = now + 12 * MINUTE),
                now,
            ),
        )
    }

    @Test
    fun durations_roundTripThroughStorage() {
        BlockDuration.entries.forEach { assertEquals(it, BlockDuration.fromMinutes(it.minutes)) }
        assertEquals(BlockDuration.UNTIL_OFF, BlockDuration.fromMinutes(7))
        assertEquals(BlockDuration.UNTIL_OFF, BlockDuration.DEFAULT)
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
