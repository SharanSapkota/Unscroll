package com.unscroll.app.domain.permission

import com.unscroll.app.domain.permission.AppPermission.IGNORE_BATTERY_OPTIMIZATIONS
import com.unscroll.app.domain.permission.AppPermission.NOTIFICATIONS
import com.unscroll.app.domain.permission.AppPermission.OVERLAY
import com.unscroll.app.domain.permission.AppPermission.USAGE_ACCESS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionStateTest {

    @Test
    fun usageAccessAndOverlay_areRequired_notificationsAndBatteryAreOptional() {
        assertEquals(
            setOf(USAGE_ACCESS, OVERLAY),
            AppPermission.entries.filter { it.required }.toSet(),
        )
    }

    @Test
    fun requiredGranted_falseWhenNothingGranted() {
        assertFalse(PermissionState.NONE.requiredGranted)
    }

    @Test
    fun requiredGranted_falseWhenOnlyOneRequiredGranted() {
        assertFalse(PermissionState(setOf(USAGE_ACCESS, NOTIFICATIONS)).requiredGranted)
        assertFalse(PermissionState(setOf(OVERLAY, IGNORE_BATTERY_OPTIMIZATIONS)).requiredGranted)
    }

    @Test
    fun requiredGranted_trueWithoutOptionalPermissions() {
        assertTrue(PermissionState(setOf(USAGE_ACCESS, OVERLAY)).requiredGranted)
    }

    @Test
    fun firstMissingRequired_followsOnboardingOrder() {
        assertEquals(USAGE_ACCESS, PermissionState.NONE.firstMissingRequired)
        assertEquals(OVERLAY, PermissionState(setOf(USAGE_ACCESS)).firstMissingRequired)
        assertEquals(USAGE_ACCESS, PermissionState(setOf(OVERLAY)).firstMissingRequired)
        assertNull(PermissionState(setOf(USAGE_ACCESS, OVERLAY)).firstMissingRequired)
    }
}
