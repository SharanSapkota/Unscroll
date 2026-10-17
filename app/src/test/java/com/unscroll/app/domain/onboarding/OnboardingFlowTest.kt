package com.unscroll.app.domain.onboarding

import com.unscroll.app.domain.onboarding.OnboardingStep.PERMISSIONS
import com.unscroll.app.domain.onboarding.OnboardingStep.SWIPES
import com.unscroll.app.domain.onboarding.OnboardingStep.WELCOME
import com.unscroll.app.domain.permission.AppPermission
import com.unscroll.app.domain.permission.PermissionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingFlowTest {

    private val none = PermissionState.NONE
    private val requiredOnly = PermissionState(setOf(AppPermission.USAGE_ACCESS, AppPermission.OVERLAY))
    private val usageOnly = PermissionState(setOf(AppPermission.USAGE_ACCESS))

    @Test
    fun threePages_welcomePermissionsThenSwipes() {
        assertEquals(listOf(WELCOME, PERMISSIONS, SWIPES), OnboardingStep.entries)
        assertEquals(3, OnboardingFlow.stepCount)
    }

    @Test
    fun startStep_firstLaunch_isWelcome_evenIfPermissionsAlreadyGranted() {
        assertEquals(WELCOME, OnboardingFlow.startStep(false, PermissionState.ALL))
        assertEquals(WELCOME, OnboardingFlow.startStep(false, none))
    }

    @Test
    fun startStep_afterRevokingRequiredPermission_isThePermissionsPage() {
        assertEquals(PERMISSIONS, OnboardingFlow.startStep(true, usageOnly))
        assertEquals(WELCOME, OnboardingFlow.startStep(true, requiredOnly))
    }

    @Test
    fun permissionsPage_blocksUntilBothRequiredAreGranted_optionalOnesNeverBlock() {
        assertFalse(OnboardingFlow.canContinue(PERMISSIONS, none))
        assertFalse(OnboardingFlow.canContinue(PERMISSIONS, usageOnly))
        assertTrue(OnboardingFlow.canContinue(PERMISSIONS, requiredOnly))
        assertTrue(OnboardingFlow.canContinue(WELCOME, none))
    }

    @Test
    fun swipesPage_isOptional_butCanOnlyFinishWithTheRequiredPermissions() {
        // Swipe counting never blocks; the required permissions still do (the page can be swiped to early).
        assertTrue(OnboardingFlow.canContinue(SWIPES, requiredOnly))
        assertFalse(OnboardingFlow.canContinue(SWIPES, usageOnly))
        assertEquals(OnboardingAdvance.Finish, OnboardingFlow.advance(SWIPES, requiredOnly))
        assertEquals(OnboardingAdvance.ToStep(PERMISSIONS), OnboardingFlow.advance(SWIPES, usageOnly))
    }

    @Test
    fun advance_welcomeGoesToPermissions_permissionsGoToSwipesOnlyWhenRequiredGranted() {
        assertEquals(OnboardingAdvance.ToStep(PERMISSIONS), OnboardingFlow.advance(WELCOME, none))
        assertEquals(OnboardingAdvance.ToStep(PERMISSIONS), OnboardingFlow.advance(PERMISSIONS, usageOnly))
        assertEquals(OnboardingAdvance.ToStep(SWIPES), OnboardingFlow.advance(PERMISSIONS, requiredOnly))
    }

    @Test
    fun previous_andPrimaryAction() {
        assertNull(OnboardingFlow.previous(WELCOME))
        assertEquals(WELCOME, OnboardingFlow.previous(PERMISSIONS))
        assertEquals(PERMISSIONS, OnboardingFlow.previous(SWIPES))
        assertEquals(PrimaryAction.GET_STARTED, OnboardingFlow.primaryAction(WELCOME))
        assertEquals(PrimaryAction.NEXT, OnboardingFlow.primaryAction(PERMISSIONS))
        assertEquals(PrimaryAction.FINISH, OnboardingFlow.primaryAction(SWIPES))
    }
}
