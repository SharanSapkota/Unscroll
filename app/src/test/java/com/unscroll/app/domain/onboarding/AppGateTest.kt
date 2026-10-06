package com.unscroll.app.domain.onboarding

import com.unscroll.app.domain.permission.AppPermission
import com.unscroll.app.domain.permission.PermissionState
import org.junit.Assert.assertEquals
import org.junit.Test

class AppGateTest {

    private val requiredOnly =
        PermissionState(setOf(AppPermission.USAGE_ACCESS, AppPermission.OVERLAY))

    @Test
    fun notCompleted_showsOnboarding_evenWithAllPermissions() {
        assertEquals(AppDestination.ONBOARDING, AppGate.resolve(false, PermissionState.ALL))
    }

    @Test
    fun completed_withRequiredPermissions_showsMain() {
        assertEquals(AppDestination.MAIN, AppGate.resolve(true, requiredOnly))
    }

    @Test
    fun completed_butRequiredPermissionRevoked_showsOnboarding() {
        assertEquals(
            AppDestination.ONBOARDING,
            AppGate.resolve(true, PermissionState(setOf(AppPermission.USAGE_ACCESS))),
        )
        assertEquals(
            AppDestination.ONBOARDING,
            AppGate.resolve(
                true,
                PermissionState(
                    setOf(
                        AppPermission.OVERLAY,
                        AppPermission.NOTIFICATIONS,
                        AppPermission.IGNORE_BATTERY_OPTIMIZATIONS,
                    ),
                ),
            ),
        )
    }
}
