package com.unscroll.app.domain.onboarding

import com.unscroll.app.domain.onboarding.OnboardingStep.PERMISSIONS
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
    fun twoPages_welcomeThenPermissions() {
        assertEquals(listOf(WELCOME, PERMISSIONS), OnboardingStep.entries)
        assertEquals(2, OnboardingFlow.stepCount)
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
    fun advance_welcomeGoesToPermissions_permissionsFinishOnlyWhenRequiredGranted() {
        assertEquals(OnboardingAdvance.ToStep(PERMISSIONS), OnboardingFlow.advance(WELCOME, none))
        assertEquals(OnboardingAdvance.ToStep(PERMISSIONS), OnboardingFlow.advance(PERMISSIONS, usageOnly))
        assertEquals(OnboardingAdvance.Finish, OnboardingFlow.advance(PERMISSIONS, requiredOnly))
    }

    @Test
    fun previous_andPrimaryAction() {
        assertNull(OnboardingFlow.previous(WELCOME))
        assertEquals(WELCOME, OnboardingFlow.previous(PERMISSIONS))
        assertEquals(PrimaryAction.GET_STARTED, OnboardingFlow.primaryAction(WELCOME))
        assertEquals(PrimaryAction.FINISH, OnboardingFlow.primaryAction(PERMISSIONS))
    }
}
