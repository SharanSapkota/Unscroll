package com.unscroll.app.domain.blocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrictionPolicyTest {

    private val now = 1_000_000L
    private val waitTen = BlockingSettings(FrictionMode.WAIT, 10)

    @Test
    fun weaker_isShorterCooldownOrSwitchingToPhrase() {
        assertTrue(FrictionPolicy.isWeaker(waitTen, FrictionMode.WAIT, 5))
        assertTrue(FrictionPolicy.isWeaker(waitTen, FrictionMode.TYPE_PHRASE, 10))
        assertFalse(FrictionPolicy.isWeaker(waitTen, FrictionMode.WAIT, 30))
        assertFalse(FrictionPolicy.isWeaker(BlockingSettings(FrictionMode.TYPE_PHRASE, 10), FrictionMode.WAIT, 10))
    }

    @Test
    fun strongerChange_appliesNow() {
        assertEquals(
            BlockingSettings(FrictionMode.WAIT, 60, pending = null),
            FrictionPolicy.request(waitTen, FrictionMode.WAIT, 60, now),
        )
    }

    @Test
    fun weakerChange_waitsTheCurrentCooldown() {
        val requested = FrictionPolicy.request(waitTen, FrictionMode.TYPE_PHRASE, 5, now)
        assertEquals(waitTen.copy(pending = PendingFriction(FrictionMode.TYPE_PHRASE, 5, now + 10 * 60_000L)), requested)

        assertEquals(requested, FrictionPolicy.resolve(requested, now + 10 * 60_000L - 1))
        assertEquals(
            BlockingSettings(FrictionMode.TYPE_PHRASE, 5, null),
            FrictionPolicy.resolve(requested, now + 10 * 60_000L),
        )
    }

    @Test
    fun debugShortCooldown_isTenSeconds_andSurvivesFrictionChanges() {
        val debug = BlockingSettings(debugShortCooldown = true)
        assertEquals(10_000L, debug.cooldownMillis)
        assertEquals(10 * 60_000L, BlockingSettings().cooldownMillis)

        val changed = FrictionPolicy.request(debug, FrictionMode.WAIT, cooldownMinutes = 30, now = 0)
        assertEquals(true, changed.debugShortCooldown)
        val weaker = FrictionPolicy.request(changed, FrictionMode.TYPE_PHRASE, cooldownMinutes = 30, now = 0)
        assertEquals(10_000L, weaker.pending?.appliesAt)
        assertEquals(true, FrictionPolicy.resolve(weaker, now = 10_000).debugShortCooldown)
    }
}
