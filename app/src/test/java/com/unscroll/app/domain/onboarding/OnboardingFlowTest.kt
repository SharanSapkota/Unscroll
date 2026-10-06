package com.unscroll.app.domain.onboarding

import com.unscroll.app.domain.onboarding.OnboardingStep.BATTERY
import com.unscroll.app.domain.onboarding.OnboardingStep.NOTIFICATIONS
import com.unscroll.app.domain.onboarding.OnboardingStep.OVERLAY
import com.unscroll.app.domain.onboarding.OnboardingStep.USAGE_ACCESS
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
    private val all = PermissionState.ALL
    private val usageOnly = PermissionState(setOf(AppPermission.USAGE_ACCESS))
    private val requiredOnly =
        PermissionState(setOf(AppPermission.USAGE_ACCESS, AppPermission.OVERLAY))

    @Test
    fun steps_areInExpectedOrder() {
        assertEquals(
            listOf(WELCOME, USAGE_ACCESS, OVERLAY, NOTIFICATIONS, BATTERY),
            OnboardingStep.entries,
        )
        assertEquals(5, OnboardingFlow.stepCount)
    }

    @Test
    fun startStep_firstLaunch_isWelcome_evenIfPermissionsAlreadyGranted() {
        assertEquals(WELCOME, OnboardingFlow.startStep(onboardingCompleted = false, none))
        assertEquals(WELCOME, OnboardingFlow.startStep(onboardingCompleted = false, all))
    }

    @Test
    fun startStep_afterRevokingRequiredPermission_jumpsToIt() {
        assertEquals(USAGE_ACCESS, OnboardingFlow.startStep(onboardingCompleted = true, none))
        assertEquals(OVERLAY, OnboardingFlow.startStep(onboardingCompleted = true, usageOnly))
    }

    @Test
    fun canContinue_requiredStepsBlockUntilGranted() {
        assertFalse(OnboardingFlow.canContinue(USAGE_ACCESS, none))
        assertTrue(OnboardingFlow.canContinue(USAGE_ACCESS, usageOnly))
        assertFalse(OnboardingFlow.canContinue(OVERLAY, usageOnly))
        assertTrue(OnboardingFlow.canContinue(OVERLAY, requiredOnly))
    }

    @Test
    fun canContinue_welcomeAndOptionalStepsNeverBlock() {
        assertTrue(OnboardingFlow.canContinue(WELCOME, none))
        assertTrue(OnboardingFlow.canContinue(NOTIFICATIONS, none))
        assertTrue(OnboardingFlow.canContinue(BATTERY, none))
    }

    @Test
    fun advance_movesToNextStep() {
        assertEquals(OnboardingAdvance.ToStep(USAGE_ACCESS), OnboardingFlow.advance(WELCOME, none))
        assertEquals(OnboardingAdvance.ToStep(OVERLAY), OnboardingFlow.advance(USAGE_ACCESS, usageOnly))
        assertEquals(
            OnboardingAdvance.ToStep(NOTIFICATIONS),
            OnboardingFlow.advance(OVERLAY, requiredOnly),
        )
        assertEquals(
            OnboardingAdvance.ToStep(BATTERY),
            OnboardingFlow.advance(NOTIFICATIONS, requiredOnly),
        )
    }

    @Test
    fun advance_staysOnRequiredStepWhenNotGranted() {
        assertEquals(OnboardingAdvance.ToStep(USAGE_ACCESS), OnboardingFlow.advance(USAGE_ACCESS, none))
        assertEquals(OnboardingAdvance.ToStep(OVERLAY), OnboardingFlow.advance(OVERLAY, usageOnly))
    }

    @Test
    fun advance_fromLastStep_finishesWhenRequiredGranted_evenWithoutOptional() {
        assertEquals(OnboardingAdvance.Finish, OnboardingFlow.advance(BATTERY, requiredOnly))
        assertEquals(OnboardingAdvance.Finish, OnboardingFlow.advance(BATTERY, all))
    }

    @Test
    fun advance_fromLastStep_returnsToRevokedRequiredPermission() {
        assertEquals(OnboardingAdvance.ToStep(OVERLAY), OnboardingFlow.advance(BATTERY, usageOnly))
        assertEquals(OnboardingAdvance.ToStep(USAGE_ACCESS), OnboardingFlow.advance(BATTERY, none))
    }

    @Test
    fun previous_walksBackAndStopsAtWelcome() {
        assertNull(OnboardingFlow.previous(WELCOME))
        assertEquals(WELCOME, OnboardingFlow.previous(USAGE_ACCESS))
        assertEquals(NOTIFICATIONS, OnboardingFlow.previous(BATTERY))
    }

    @Test
    fun primaryAction_labels() {
        assertEquals(PrimaryAction.GET_STARTED, OnboardingFlow.primaryAction(WELCOME, none))
        assertEquals(PrimaryAction.CONTINUE, OnboardingFlow.primaryAction(USAGE_ACCESS, none))
        assertEquals(PrimaryAction.SKIP, OnboardingFlow.primaryAction(NOTIFICATIONS, none))
        assertEquals(PrimaryAction.CONTINUE, OnboardingFlow.primaryAction(NOTIFICATIONS, all))
        assertEquals(PrimaryAction.FINISH, OnboardingFlow.primaryAction(BATTERY, none))
        assertEquals(PrimaryAction.FINISH, OnboardingFlow.primaryAction(BATTERY, all))
    }
}
