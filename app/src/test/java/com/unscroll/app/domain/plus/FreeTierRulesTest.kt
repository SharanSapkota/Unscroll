package com.unscroll.app.domain.plus

import com.unscroll.app.domain.tracking.TrackedApps.FACEBOOK
import com.unscroll.app.domain.tracking.TrackedApps.INSTAGRAM
import com.unscroll.app.domain.tracking.TrackedApps.TIKTOK
import com.unscroll.app.domain.tracking.TrackedApps.TIKTOK_ASIA
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeTierRulesTest {

    private val apps = listOf(INSTAGRAM, TIKTOK, FACEBOOK)
    private val all = setOf(INSTAGRAM, TIKTOK, TIKTOK_ASIA, FACEBOOK)

    @Test
    fun freeTier_isOneApp() {
        assertEquals(1, FreeTier.FREE_APPS)
    }

    @Test
    fun free_withoutPicks_needsPick_andTracksOnlyTheMostUsedMeanwhile() {
        val state = FreeTierRules.resolve(false, apps, all, picked = emptySet(), mostUsed = listOf(TIKTOK, INSTAGRAM))

        assertTrue(state.needsPick)
        assertEquals(setOf(TIKTOK), state.active)
        assertEquals(listOf(INSTAGRAM, FACEBOOK), state.paused)
    }

    @Test
    fun free_withPick_tracksOnlyThePick() {
        val state = FreeTierRules.resolve(false, apps, all, picked = setOf(FACEBOOK), mostUsed = listOf(TIKTOK))

        assertFalse(state.needsPick)
        assertEquals(setOf(FACEBOOK), state.active)
        assertTrue(state.isPaused(INSTAGRAM))
        assertTrue(state.isPaused(TIKTOK))
    }

    @Test
    fun free_neverTracksMoreThanTheLimit_evenWithMorePicksStored() {
        // Picks made with Plus or another free count: only the limit is used, in display order.
        val state = FreeTierRules.resolve(false, apps, all, picked = setOf(FACEBOOK, INSTAGRAM), mostUsed = emptyList())

        assertEquals(setOf(INSTAGRAM), state.active)
        assertEquals(2, FreeTierRules.resolve(false, apps, all, setOf(FACEBOOK, INSTAGRAM), emptyList(), freeApps = 2).active.size)
    }

    @Test
    fun free_pickOfAnUninstalledApp_needsANewPick() {
        val state = FreeTierRules.resolve(false, apps, all, picked = setOf(TIKTOK_ASIA), mostUsed = emptyList())

        assertTrue(state.needsPick)
        assertEquals(setOf(INSTAGRAM), state.active)
    }

    @Test
    fun free_withOnlyOneAppInstalled_needsNoPick() {
        val state = FreeTierRules.resolve(false, listOf(INSTAGRAM), all, picked = emptySet(), mostUsed = emptyList())

        assertFalse(state.needsPick)
        assertEquals(setOf(INSTAGRAM), state.active)
    }

    @Test
    fun plus_tracksEveryApp_andNeedsNoPick() {
        val state = FreeTierRules.resolve(true, apps, all, picked = setOf(INSTAGRAM), mostUsed = emptyList())

        assertFalse(state.needsPick)
        assertEquals(all, state.active)
        assertEquals(emptyList<String>(), state.paused)
    }

    @Test
    fun downgrade_keepsThePicks_orTheMostUsed() {
        assertEquals(setOf(FACEBOOK), FreeTierRules.keepOnDowngrade(apps, setOf(FACEBOOK), listOf(TIKTOK)))
        assertEquals(setOf(TIKTOK), FreeTierRules.keepOnDowngrade(apps, emptySet(), listOf(TIKTOK, INSTAGRAM)))
        // Nothing used yet: the first app.
        assertEquals(setOf(INSTAGRAM), FreeTierRules.keepOnDowngrade(apps, emptySet(), emptyList()))
    }

    @Test
    fun upgradeThenDowngrade_restoresTheSamePausedApps() {
        val picked = setOf(INSTAGRAM)
        val free = FreeTierRules.resolve(false, apps, all, picked, mostUsed = emptyList())
        val plus = FreeTierRules.resolve(true, apps, all, picked, mostUsed = emptyList())
        val freeAgain = FreeTierRules.resolve(false, apps, all, picked, mostUsed = listOf(TIKTOK))

        assertEquals(listOf(TIKTOK, FACEBOOK), free.paused)
        assertEquals(emptyList<String>(), plus.paused)
        assertEquals(free.active, freeAgain.active)
    }

    @Test
    fun toggle_withOneFreeApp_actsLikeARadioButton() {
        var selection = emptyList<String>()
        selection = FreeTierRules.toggle(selection, INSTAGRAM)
        assertEquals(listOf(INSTAGRAM), selection)
        selection = FreeTierRules.toggle(selection, TIKTOK)
        assertEquals(listOf(TIKTOK), selection)
        selection = FreeTierRules.toggle(selection, TIKTOK)
        assertEquals(emptyList<String>(), selection)
    }

    @Test
    fun toggle_withTwoFreeApps_replacesTheOldestAtTheLimit() {
        var selection = FreeTierRules.toggle(emptyList(), INSTAGRAM, freeApps = 2)
        selection = FreeTierRules.toggle(selection, TIKTOK, freeApps = 2)
        assertEquals(listOf(INSTAGRAM, TIKTOK), selection)
        selection = FreeTierRules.toggle(selection, FACEBOOK, freeApps = 2)
        assertEquals(listOf(TIKTOK, FACEBOOK), selection)
    }

    @Test
    fun canConfirm_needsAFullSelection() {
        assertFalse(FreeTierRules.canConfirm(emptyList(), apps))
        assertTrue(FreeTierRules.canConfirm(listOf(TIKTOK), apps))
        assertFalse(FreeTierRules.canConfirm(listOf(TIKTOK), apps, freeApps = 2))
        assertTrue(FreeTierRules.canConfirm(listOf(TIKTOK), listOf(TIKTOK), freeApps = 2))
        assertFalse(FreeTierRules.canConfirm(listOf(TIKTOK_ASIA), apps))
    }

    @Test
    fun suggested_isTheMostUsed_thenDisplayOrder() {
        assertEquals(listOf(FACEBOOK), FreeTierRules.suggested(apps, listOf(FACEBOOK, INSTAGRAM)))
        assertEquals(listOf(INSTAGRAM), FreeTierRules.suggested(apps, emptyList()))
        assertEquals(listOf(INSTAGRAM), FreeTierRules.suggested(apps, listOf(TIKTOK_ASIA)))
    }
}
