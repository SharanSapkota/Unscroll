package com.unscroll.app.domain.tracking

import com.unscroll.app.domain.permission.AppPermission
import com.unscroll.app.domain.permission.PermissionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackingStartRulesTest {

    private val required = PermissionState(setOf(AppPermission.USAGE_ACCESS, AppPermission.OVERLAY))
    private val usageOnly = PermissionState(setOf(AppPermission.USAGE_ACCESS))
    private val overlayOnly = PermissionState(setOf(AppPermission.OVERLAY, AppPermission.NOTIFICATIONS))

    @Test
    fun enabled_neverSetMeansOn_onlyAnExplicitOffIsOff() {
        assertTrue(TrackingStartRules.enabled(stored = null))
        assertFalse(TrackingStartRules.enabled(stored = false))
        assertTrue(TrackingStartRules.enabled(stored = true))
    }

    @Test
    fun status_offWinsOverMissingPermissions() {
        assertEquals(TrackingStatus.OFF, TrackingStartRules.status(enabled = false, permissions = PermissionState.NONE))
        assertEquals(TrackingStatus.OFF, TrackingStartRules.status(enabled = false, permissions = PermissionState.ALL))
    }

    @Test
    fun status_onWithARequiredPermissionMissing_isPaused() {
        assertEquals(TrackingStatus.PAUSED, TrackingStartRules.status(true, PermissionState.NONE))
        assertEquals(TrackingStatus.PAUSED, TrackingStartRules.status(true, usageOnly))
        assertEquals(TrackingStatus.PAUSED, TrackingStartRules.status(true, overlayOnly))
    }

    @Test
    fun status_onWithRequiredPermissions_isActive_optionalOnesDontMatter() {
        assertEquals(TrackingStatus.ACTIVE, TrackingStartRules.status(true, required))
        assertEquals(TrackingStatus.ACTIVE, TrackingStartRules.status(true, PermissionState.ALL))
    }

    @Test
    fun shouldStart_onlyAfterOnboarding_whenOn_withPermissions_andNotRunning() {
        assertTrue(TrackingStartRules.shouldStart(true, true, required, serviceRunning = false))

        assertFalse("onboarding not done", TrackingStartRules.shouldStart(false, true, required, false))
        assertFalse("turned off", TrackingStartRules.shouldStart(true, false, required, false))
        assertFalse("usage access missing", TrackingStartRules.shouldStart(true, true, overlayOnly, false))
        assertFalse("overlay missing", TrackingStartRules.shouldStart(true, true, usageOnly, false))
        assertFalse("already running", TrackingStartRules.shouldStart(true, true, required, true))
    }

    @Test
    fun shouldStart_becomesTrueWhenTheMissingPermissionIsGranted() {
        assertFalse(TrackingStartRules.shouldStart(true, true, usageOnly, false))
        assertTrue(TrackingStartRules.shouldStart(true, true, required, false))
    }

    @Test
    fun permissionToFix_isTheFirstMissingRequiredOne() {
        assertEquals(AppPermission.USAGE_ACCESS, TrackingStartRules.permissionToFix(PermissionState.NONE))
        assertEquals(AppPermission.USAGE_ACCESS, TrackingStartRules.permissionToFix(overlayOnly))
        assertEquals(AppPermission.OVERLAY, TrackingStartRules.permissionToFix(usageOnly))
        assertNull(TrackingStartRules.permissionToFix(required))
    }
}
