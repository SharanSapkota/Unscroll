package com.unscroll.app.domain.reels

import com.unscroll.app.domain.overlay.PillTarget
import com.unscroll.app.domain.scroll.ScrollConsent
import com.unscroll.app.domain.session.ActiveSegment
import com.unscroll.app.domain.session.ActiveSession
import com.unscroll.app.domain.session.ScreenSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelsOnlyRulesTest {

    private val instagram = "com.instagram.android"

    private fun inputs(
        packageName: String = instagram,
        versionSupported: Boolean = true,
        consent: ScrollConsent = ScrollConsent.AGREED,
        serviceConnected: Boolean = true,
        live: ReelsState? = null,
    ) = ReelsInputs(packageName, versionSupported, consent, serviceConnected, live)

    @Test
    fun appliesOnlyToInstagramAndTikTok() {
        assertEquals(setOf("com.instagram.android", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill"), ReelsOnlyPillConfig.PACKAGES)
        listOf("com.facebook.katana", "com.google.android.youtube", "com.reddit.frontpage").forEach {
            assertEquals(it, ReelsTimerStatus.NOT_APPLICABLE, ReelsOnlyRules.availability(inputs(packageName = it)))
        }
        assertEquals(ReelsTimerStatus.ACTIVE, ReelsOnlyRules.availability(inputs()))
    }

    @Test
    fun masterSwitchOff_isNotApplicable() {
        assertEquals(ReelsTimerStatus.NOT_APPLICABLE, ReelsOnlyRules.availability(inputs(), enabled = false))
        assertFalse(ReelsOnlyRules.shouldDetect(inputs(), enabled = false))
    }

    @Test
    fun fallbackReasons_inOrder() {
        // Version out of range: falls back with the version note.
        assertEquals(ReelsTimerStatus.UNSUPPORTED_VERSION, ReelsOnlyRules.availability(inputs(versionSupported = false, consent = ScrollConsent.NOT_ASKED)))
        // Consent missing (existing users who agreed to swipe counting only).
        assertEquals(ReelsTimerStatus.NEEDS_CONSENT, ReelsOnlyRules.availability(inputs(consent = ScrollConsent.NOT_ASKED)))
        assertEquals(ReelsTimerStatus.NEEDS_CONSENT, ReelsOnlyRules.availability(inputs(consent = ScrollConsent.DECLINED)))
        assertEquals(ReelsTimerStatus.SERVICE_OFF, ReelsOnlyRules.availability(inputs(serviceConnected = false)))
        assertEquals(ReelsTimerStatus.SCREEN_UNREADABLE, ReelsOnlyRules.availability(inputs(live = ReelsState.UNKNOWN)))
        assertEquals(ReelsTimerStatus.ACTIVE, ReelsOnlyRules.availability(inputs(live = ReelsState.OTHER)))
    }

    @Test
    fun notes_onlyForReasonsTheUserCanAct_onOrKnowAbout() {
        assertEquals(
            setOf(
                ReelsTimerStatus.UNSUPPORTED_VERSION,
                ReelsTimerStatus.NEEDS_CONSENT,
                ReelsTimerStatus.SERVICE_OFF,
                ReelsTimerStatus.SCREEN_UNREADABLE,
            ),
            ReelsTimerStatus.entries.filter { it.showsNote }.toSet(),
        )
    }

    @Test
    fun detection_continuesWhileFallenBack_soItCanRecover() {
        assertTrue(ReelsOnlyRules.shouldDetect(inputs(live = ReelsState.UNKNOWN)))
        assertFalse(ReelsOnlyRules.shouldDetect(inputs(consent = ScrollConsent.NOT_ASKED)))
        assertFalse(ReelsOnlyRules.shouldDetect(inputs(versionSupported = false)))
    }

    @Test
    fun swipes_countOnlyInReels_forAppsOnTheTimer() {
        fun live(status: ReelsTimerStatus, state: ReelsState?) = ReelsLive(instagram, status, state)
        assertTrue(ReelsOnlyRules.countsSwipe(null))
        assertTrue(ReelsOnlyRules.countsSwipe(live(ReelsTimerStatus.ACTIVE, ReelsState.IN_REELS)))
        assertFalse(ReelsOnlyRules.countsSwipe(live(ReelsTimerStatus.ACTIVE, ReelsState.OTHER)))
        // Not confirmed yet (just opened): not counted.
        assertFalse(ReelsOnlyRules.countsSwipe(live(ReelsTimerStatus.ACTIVE, null)))
        // Fallback: every swipe, as before.
        assertTrue(ReelsOnlyRules.countsSwipe(live(ReelsTimerStatus.SCREEN_UNREADABLE, ReelsState.UNKNOWN)))
        assertTrue(ReelsOnlyRules.countsSwipe(live(ReelsTimerStatus.NEEDS_CONSENT, null)))
    }

    @Test
    fun pillTarget_followsReels_andPausesOnEveryOtherScreen() {
        val session = ActiveSession(id = 7, packageName = instagram, startTime = 0)
        val reels = ActiveSegment(id = 3, sessionId = 7, packageName = instagram, section = ScreenSection.REELS, startTime = 10)
        val other = reels.copy(id = 4, section = ScreenSection.OTHER)
        val active = ReelsLive(instagram, ReelsTimerStatus.ACTIVE, ReelsState.IN_REELS)

        assertEquals(PillTarget.None, PillTarget.of(null, null, null))
        assertEquals(PillTarget.Reels(reels), PillTarget.of(session, reels, active))
        // Messages, home, search...: the pill stays (paused), it is never dropped.
        assertEquals(PillTarget.ReelsPaused(session), PillTarget.of(session, other, active.copy(state = ReelsState.OTHER)))
        assertEquals(PillTarget.ReelsPaused(session), PillTarget.of(session, null, active.copy(state = null)))
        // A segment of an older session never counts.
        assertEquals(PillTarget.ReelsPaused(session), PillTarget.of(session, reels.copy(sessionId = 6), active))
        // Fallback and other apps: the whole-app pill as before.
        assertEquals(PillTarget.App(session), PillTarget.of(session, null, active.copy(availability = ReelsTimerStatus.SERVICE_OFF)))
        assertEquals(PillTarget.App(session), PillTarget.of(session, null, null))
        val youtube = session.copy(packageName = "com.google.android.youtube")
        assertEquals(PillTarget.App(youtube), PillTarget.of(youtube, null, null))
        assertEquals(instagram, PillTarget.ReelsPaused(session).packageName)
    }

    @Test
    fun sectionGate_onlyInReels_allowsInterruptions_forReelsApps() {
        fun live(status: ReelsTimerStatus, state: ReelsState?) = ReelsLive(instagram, status, state)
        assertEquals(SectionGate.WHOLE_APP, SectionGate.of("com.reddit.frontpage", null))
        assertEquals(SectionGate.WHOLE_APP, SectionGate.of("com.google.android.youtube", null))
        assertEquals(SectionGate.IN_REELS, SectionGate.of(instagram, live(ReelsTimerStatus.ACTIVE, ReelsState.IN_REELS)))
        assertEquals(SectionGate.OFF_REELS, SectionGate.of(instagram, live(ReelsTimerStatus.ACTIVE, ReelsState.OTHER)))
        assertEquals(SectionGate.OFF_REELS, SectionGate.of(instagram, live(ReelsTimerStatus.ACTIVE, null)))
        // Fail open: no detection, no interruptions at all.
        listOf(
            ReelsTimerStatus.SERVICE_OFF,
            ReelsTimerStatus.NEEDS_CONSENT,
            ReelsTimerStatus.UNSUPPORTED_VERSION,
            ReelsTimerStatus.SCREEN_UNREADABLE,
        ).forEach { status ->
            assertEquals(status.name, SectionGate.DETECTION_UNAVAILABLE, SectionGate.of(instagram, live(status, ReelsState.IN_REELS)))
        }
        assertEquals(SectionGate.DETECTION_UNAVAILABLE, SectionGate.of(instagram, null))
        // Live state of another app is never used.
        assertEquals(SectionGate.DETECTION_UNAVAILABLE, SectionGate.of("com.zhiliaoapp.musically", live(ReelsTimerStatus.ACTIVE, ReelsState.IN_REELS)))
        // Switch off: Instagram is a normal app again.
        assertEquals(SectionGate.WHOLE_APP, SectionGate.of(instagram, null, enabled = false))

        assertEquals(setOf(SectionGate.WHOLE_APP, SectionGate.IN_REELS), SectionGate.entries.filter { it.allowsInterruptions }.toSet())
    }

    @Test
    fun debugLine_showsStateRuleAndVersion() {
        val line = ReelsDebugFormat.line(
            ReelsLive(instagram, ReelsTimerStatus.ACTIVE, ReelsState.IN_REELS, lastReading = ReelsState.OTHER, rule = "allowed message_thread_container", versionCode = 123),
        )
        assertEquals("com.instagram.android · ACTIVE · IN_REELS · reading OTHER · allowed message_thread_container · v=123", line)
    }
}
