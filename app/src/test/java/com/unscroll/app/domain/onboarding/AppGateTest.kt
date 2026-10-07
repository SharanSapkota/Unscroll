package com.unscroll.app.domain.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class AppGateTest {

    @Test
    fun notCompleted_showsOnboarding() {
        assertEquals(AppDestination.ONBOARDING, AppGate.resolve(false))
    }

    @Test
    fun completed_showsMain_evenIfAPermissionIsRevokedLater() {
        // Revoked permissions pause tracking and show a banner on Home instead.
        assertEquals(AppDestination.MAIN, AppGate.resolve(true))
    }
}
