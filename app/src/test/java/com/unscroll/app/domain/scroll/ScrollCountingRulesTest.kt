package com.unscroll.app.domain.scroll

import com.unscroll.app.domain.scroll.ScrollConsent.AGREED
import com.unscroll.app.domain.scroll.ScrollConsent.DECLINED
import com.unscroll.app.domain.scroll.ScrollConsent.NOT_ASKED
import com.unscroll.app.domain.scroll.ScrollCountingStatus.ACTIVE
import com.unscroll.app.domain.scroll.ScrollCountingStatus.NEEDS_CONSENT
import com.unscroll.app.domain.scroll.ScrollCountingStatus.NEEDS_ENABLING
import com.unscroll.app.domain.scroll.ScrollCountingStatus.NEEDS_REENABLE
import com.unscroll.app.domain.scroll.ScrollCountingStatus.OFF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollCountingRulesTest {

    private fun status(
        consent: ScrollConsent,
        enabled: Boolean = false,
        connected: Boolean = false,
        everConnected: Boolean = false,
        sinceStart: Long = 60_000,
    ) = ScrollCountingRules.status(ScrollCountingInputs(consent, enabled, connected, everConnected, sinceStart))

    @Test
    fun withoutConsent_itIsOff_andEverythingKeepsWorking() {
        assertEquals(OFF, status(NOT_ASKED))
        assertEquals(OFF, status(DECLINED))
        assertEquals(OFF, status(DECLINED, everConnected = true))
    }

    @Test
    fun enabledInSystemSettingsWithoutConsent_needsConsent_andCountsNothing() {
        assertEquals(NEEDS_CONSENT, status(NOT_ASKED, enabled = true))
        assertEquals(NEEDS_CONSENT, status(DECLINED, enabled = true, connected = true))
        assertFalse(ScrollCountingRules.shouldCount(NOT_ASKED, connected = true))
        assertFalse(ScrollCountingRules.shouldCount(DECLINED, connected = true))
    }

    @Test
    fun agreed_butNotSwitchedOn_needsEnabling() {
        assertEquals(NEEDS_ENABLING, status(AGREED))
        assertFalse(ScrollCountingRules.shouldCount(AGREED, connected = false))
    }

    @Test
    fun agreed_andConnected_isActive() {
        assertEquals(ACTIVE, status(AGREED, enabled = true, connected = true, everConnected = true))
        assertTrue(ScrollCountingRules.shouldCount(AGREED, connected = true))
    }

    @Test
    fun switchedOffBySystemAfterWorking_needsReenable() {
        // Removed from the enabled list (some OEMs do this when the app is killed).
        assertEquals(NEEDS_REENABLE, status(AGREED, enabled = false, everConnected = true))
        // Still listed, but not running ("not working" on some phones).
        assertEquals(NEEDS_REENABLE, status(AGREED, enabled = true, everConnected = true))
    }

    @Test
    fun rightAfterProcessStart_enabledButNotYetBound_doesNotWarn() {
        assertEquals(ACTIVE, status(AGREED, enabled = true, everConnected = true, sinceStart = 1_000))
        assertEquals(NEEDS_ENABLING, status(AGREED, enabled = true, everConnected = false, sinceStart = 1_000))
        // Not in the enabled list at all: warn right away.
        assertEquals(NEEDS_REENABLE, status(AGREED, enabled = false, everConnected = true, sinceStart = 1_000))
    }

    @Test
    fun isServiceEnabled_parsesTheSecureSetting() {
        val pkg = "com.unscroll.app"
        val cls = "com.unscroll.app.service.ScrollAccessibilityService"
        assertFalse(ScrollCountingRules.isServiceEnabled(null, pkg, cls))
        assertFalse(ScrollCountingRules.isServiceEnabled("", pkg, cls))
        assertTrue(ScrollCountingRules.isServiceEnabled("$pkg/$cls", pkg, cls))
        assertTrue(
            ScrollCountingRules.isServiceEnabled(
                "com.google.android.marvin.talkback/.TalkBackService:$pkg/.service.ScrollAccessibilityService",
                pkg,
                cls,
            ),
        )
        assertFalse(ScrollCountingRules.isServiceEnabled("$pkg.debug/$cls", pkg, cls))
        assertFalse(ScrollCountingRules.isServiceEnabled("$pkg/com.other.Service", pkg, cls))
    }

    @Test
    fun isServiceEnabled_withTheApplicationIdDifferentFromTheNamespace() {
        // The real app: application ID com.sharansapkota.unscroll, classes in com.unscroll.app.
        val pkg = "com.sharansapkota.unscroll"
        val cls = "com.unscroll.app.service.ScrollAccessibilityService"
        assertTrue(ScrollCountingRules.isServiceEnabled("$pkg/$cls", pkg, cls))
        assertFalse(ScrollCountingRules.isServiceEnabled("com.unscroll.app/$cls", pkg, cls))
        assertFalse(ScrollCountingRules.isServiceEnabled("$pkg/.service.ScrollAccessibilityService", pkg, cls))
    }

    // --- Home banner and restricted-setting help ------------------------------------------------

    private val day = 24 * 60 * 60 * 1_000L

    @Test
    fun banner_neverEnabled_asksToSetUp() {
        assertEquals(SwipeBanner.SET_UP, ScrollCountingRules.banner(ScrollCountingStatus.OFF, everEnabled = false, dismissedAt = null, now = 0))
        // Agreed but not switched on yet: still "Turn on" (the button then opens Accessibility settings).
        assertEquals(
            SwipeBanner.SET_UP,
            ScrollCountingRules.banner(ScrollCountingStatus.NEEDS_ENABLING, everEnabled = false, dismissedAt = null, now = 0),
        )
    }

    @Test
    fun banner_dismissed_staysHiddenForSevenDays() {
        val dismissed = 10 * day
        assertEquals(SwipeBanner.NONE, ScrollCountingRules.banner(ScrollCountingStatus.OFF, false, dismissed, dismissed + 6 * day))
        assertEquals(SwipeBanner.NONE, ScrollCountingRules.banner(ScrollCountingStatus.OFF, false, dismissed, dismissed + 7 * day - 1))
        assertEquals(SwipeBanner.SET_UP, ScrollCountingRules.banner(ScrollCountingStatus.OFF, false, dismissed, dismissed + 7 * day))
    }

    @Test
    fun banner_activeOrEnabledBefore_showsNothing_unlessThePhoneSwitchedItOff() {
        assertEquals(SwipeBanner.NONE, ScrollCountingRules.banner(ScrollCountingStatus.ACTIVE, false, null, 0))
        // Had it, turned it off on purpose: not asked again.
        assertEquals(SwipeBanner.NONE, ScrollCountingRules.banner(ScrollCountingStatus.OFF, true, null, 0))
        // Had it, the phone switched it off: always shown, even if the set-up banner was dismissed.
        assertEquals(SwipeBanner.NEEDS_REENABLE, ScrollCountingRules.banner(ScrollCountingStatus.NEEDS_REENABLE, true, 0, 1))
    }

    @Test
    fun banner_onInAndroidWithoutConsent_asksToFinishSetUp() {
        assertEquals(SwipeBanner.FINISH_CONSENT, ScrollCountingRules.banner(ScrollCountingStatus.NEEDS_CONSENT, false, null, 0))
        assertEquals(SwipeBanner.FINISH_CONSENT, ScrollCountingRules.banner(ScrollCountingStatus.NEEDS_CONSENT, true, null, 0))
        assertEquals(SwipeBanner.NONE, ScrollCountingRules.banner(ScrollCountingStatus.NEEDS_CONSENT, false, 0, day))
    }

    @Test
    fun restrictedHelp_onlyAfterAgreeing_withoutTheService_onAndroid13Plus() {
        assertTrue(ScrollCountingRules.showRestrictedHelp(ScrollCountingStatus.NEEDS_ENABLING, sdkInt = 33))
        assertTrue(ScrollCountingRules.showRestrictedHelp(ScrollCountingStatus.NEEDS_ENABLING, sdkInt = 36))
        assertFalse(ScrollCountingRules.showRestrictedHelp(ScrollCountingStatus.NEEDS_ENABLING, sdkInt = 32))
        ScrollCountingStatus.entries.filter { it != ScrollCountingStatus.NEEDS_ENABLING }.forEach {
            assertFalse(it.name, ScrollCountingRules.showRestrictedHelp(it, sdkInt = 36))
        }
    }

    @Test
    fun swipeDebugLine_hasTheConnectionTheEventThePackageAndTheCount() {
        assertEquals(
            "connected yes · TYPE_VIEW_SCROLLED · com.instagram.android · counted · 86 swipes",
            SwipeDebugFormat.line(
                connected = true,
                info = SwipeDebugInfo("TYPE_VIEW_SCROLLED", "com.instagram.android", SwipeDebugOutcome.COUNTED, 86),
            ),
        )
        assertEquals("connected no · no event yet · no session", SwipeDebugFormat.line(false, SwipeDebugInfo()))
    }
}
