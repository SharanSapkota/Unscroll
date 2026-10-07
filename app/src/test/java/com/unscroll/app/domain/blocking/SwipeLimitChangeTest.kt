package com.unscroll.app.domain.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Swipe limits follow the same cooldown rules as the daily time limit. */
class SwipeLimitChangeTest {

    private val delay = 10 * 60_000L
    private val now = 1_000_000L
    private val limited = LimitSettings(swipeLimit = 100)

    private fun weaker(old: LimitSettings, new: LimitSettings) = LimitChangePolicy.isWeaker(old, new)

    @Test
    fun addingOrLoweringIsStronger_raisingOrRemovingIsWeaker() {
        assertFalse(weaker(LimitSettings(), limited))
        assertFalse(weaker(limited, limited.copy(swipeLimit = 50)))
        assertTrue(weaker(limited, limited.copy(swipeLimit = 200)))
        assertTrue(weaker(limited, limited.copy(swipeLimit = null)))
    }

    @Test
    fun perDayToPerSession_isWeaker_andBackIsStronger() {
        val perSession = limited.copy(swipeLimitScope = SwipeLimitScope.SESSION)
        assertTrue(weaker(limited, perSession))
        assertFalse(weaker(perSession, limited))
    }

    @Test
    fun aShorterSessionGap_isWeaker_aLongerOneIsStronger() {
        val perSession = limited.copy(swipeLimitScope = SwipeLimitScope.SESSION, swipeSessionGapMinutes = 30)
        assertTrue(weaker(perSession, perSession.copy(swipeSessionGapMinutes = 10)))
        assertFalse(weaker(perSession, perSession.copy(swipeSessionGapMinutes = 60)))
        // Per day ignores the gap.
        assertFalse(weaker(limited, limited.copy(swipeSessionGapMinutes = 10)))
    }

    @Test
    fun allowingINeedAccess_isWeaker_turningItOffIsStronger() {
        assertTrue(weaker(limited, limited.copy(swipeAccessAllowed = true)))
        assertFalse(weaker(limited.copy(swipeAccessAllowed = true), limited))
    }

    @Test
    fun withoutASwipeLimit_itsOtherSettingsDontMatter() {
        val none = LimitSettings()
        assertFalse(weaker(none, none.copy(swipeAccessAllowed = true, swipeLimitScope = SwipeLimitScope.SESSION)))
    }

    @Test
    fun raisingWaitsOutTheCooldown_loweringAppliesAtOnce() {
        val current = AppLimit(PKG, limited)

        val raised = LimitChangePolicy.edit(current, { it.copy(swipeLimit = 300) }, now, delay)
        assertEquals(limited, raised.settings)
        assertEquals(PendingChange(limited.copy(swipeLimit = 300), now + delay), raised.pending)
        assertEquals(
            listOf(PendingPart.SwipeLimitRaised(300)),
            LimitChangePolicy.describe(raised.settings, raised.pending!!.settings),
        )

        val lowered = LimitChangePolicy.edit(current, { it.copy(swipeLimit = 25) }, now, delay)
        assertEquals(limited.copy(swipeLimit = 25), lowered.settings)
        assertNull(lowered.pending)
    }

    @Test
    fun removingWaits_andIsDescribed() {
        val removed = LimitChangePolicy.edit(AppLimit(PKG, limited), { it.copy(swipeLimit = null) }, now, delay)
        assertEquals(limited, removed.settings)
        assertEquals(listOf(PendingPart.SwipeLimitRemoved), LimitChangePolicy.describe(removed.settings, removed.pending!!.settings))
        // After the cooldown it applies.
        assertNull(LimitChangePolicy.resolve(removed, now + delay).settings.swipeLimit)
    }

    @Test
    fun loosenedRules_areDescribed() {
        val perSession = limited.copy(swipeLimitScope = SwipeLimitScope.SESSION)
        assertEquals(listOf(PendingPart.SwipeLimitLoosened), LimitChangePolicy.describe(limited, perSession))
    }

    @Test
    fun theTypedPhraseAppliesARaiseAtOnce() {
        val raised = LimitChangePolicy.edit(AppLimit(PKG, limited), { it.copy(swipeLimit = 300) }, now, delay)
        assertEquals(300, LimitChangePolicy.applyNow(raised).settings.swipeLimit)
    }

    private companion object {
        const val PKG = "com.instagram.android"
    }
}
