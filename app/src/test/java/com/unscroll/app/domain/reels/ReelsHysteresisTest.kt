package com.unscroll.app.domain.reels

import com.unscroll.app.domain.reels.ReelsHysteresis.Companion.CONFIRM_MILLIS
import com.unscroll.app.domain.reels.ReelsHysteresis.Companion.UNKNOWN_FALLBACK_MILLIS
import com.unscroll.app.domain.reels.ReelsState.IN_REELS
import com.unscroll.app.domain.reels.ReelsState.OTHER
import com.unscroll.app.domain.reels.ReelsState.UNKNOWN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReelsHysteresisTest {

    private val machine = ReelsHysteresis()

    @Test
    fun firstReading_isConfirmedAtOnce() {
        assertNull(machine.observe(IN_REELS, now = 1_000))
        assertEquals(IN_REELS, machine.confirmed)
        assertEquals(1_000, machine.confirmedSince)
    }

    @Test
    fun change_needsTheConfirmDelay_andStartsWhenFirstRead() {
        machine.observe(IN_REELS, now = 0)
        // Read OTHER at 10 s: pending, check again after the confirm delay.
        assertEquals(CONFIRM_MILLIS, machine.observe(OTHER, now = 10_000))
        assertEquals(IN_REELS, machine.confirmed)
        assertEquals(100L, machine.observe(OTHER, now = 10_300))
        assertEquals(IN_REELS, machine.confirmed)
        assertNull(machine.observe(OTHER, now = 10_400))
        assertEquals(OTHER, machine.confirmed)
        // The segment boundary is when OTHER was first read, not when it was confirmed.
        assertEquals(10_000, machine.confirmedSince)
    }

    @Test
    fun briefUnknownFrames_neverChangeTheState() {
        machine.observe(IN_REELS, now = 0)
        listOf(300L, 600L, 900L, 2_500L).forEach { t ->
            machine.observe(UNKNOWN, now = t)
            assertEquals(IN_REELS, machine.confirmed)
        }
        machine.observe(IN_REELS, now = 2_800)
        // The UNKNOWN run ended, so a new one starts the 3 s count again.
        machine.observe(UNKNOWN, now = 5_000)
        machine.observe(UNKNOWN, now = 7_900)
        assertEquals(IN_REELS, machine.confirmed)
    }

    @Test
    fun unknownFrames_duringAPendingChange_keepThePendingChange() {
        machine.observe(IN_REELS, now = 0)
        machine.observe(OTHER, now = 1_000)
        machine.observe(UNKNOWN, now = 1_200)
        machine.observe(OTHER, now = 1_450)
        assertEquals(OTHER, machine.confirmed)
        assertEquals(1_000, machine.confirmedSince)
    }

    @Test
    fun flickeringReadings_neverConfirm() {
        machine.observe(OTHER, now = 0)
        var t = 1_000L
        repeat(10) {
            machine.observe(IN_REELS, now = t)
            machine.observe(OTHER, now = t + 200)
            t += 300
        }
        assertEquals(OTHER, machine.confirmed)
        // Back and forth resets the candidate: IN_REELS must hold for the full delay.
        machine.observe(IN_REELS, now = t)
        machine.observe(IN_REELS, now = t + CONFIRM_MILLIS - 1)
        assertEquals(OTHER, machine.confirmed)
        machine.observe(IN_REELS, now = t + CONFIRM_MILLIS)
        assertEquals(IN_REELS, machine.confirmed)
    }

    @Test
    fun unknownForMoreThanThreeSeconds_fallsBack_thenRecovers() {
        machine.observe(IN_REELS, now = 0)
        assertEquals(UNKNOWN_FALLBACK_MILLIS, machine.observe(UNKNOWN, now = 1_000))
        assertEquals(1_000L, machine.observe(UNKNOWN, now = 3_000))
        assertEquals(IN_REELS, machine.confirmed)
        assertNull(machine.observe(UNKNOWN, now = 4_000))
        assertEquals(UNKNOWN, machine.confirmed)
        // Readable again: back after the confirm delay.
        machine.observe(OTHER, now = 9_000)
        assertEquals(UNKNOWN, machine.confirmed)
        machine.observe(OTHER, now = 9_000 + CONFIRM_MILLIS)
        assertEquals(OTHER, machine.confirmed)
    }

    @Test
    fun unknownFromTheStart_staysUnconfirmed_thenFallsBack() {
        machine.observe(UNKNOWN, now = 0)
        machine.observe(UNKNOWN, now = 2_999)
        assertNull(machine.confirmed)
        machine.observe(UNKNOWN, now = 3_000)
        assertEquals(UNKNOWN, machine.confirmed)
    }

    @Test
    fun reset_startsANewVisit() {
        machine.observe(IN_REELS, now = 0)
        machine.reset()
        assertNull(machine.confirmed)
        assertNull(machine.observe(OTHER, now = 100))
        assertEquals(OTHER, machine.confirmed)
    }
}
