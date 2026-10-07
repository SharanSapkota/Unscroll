package com.unscroll.app.domain.permission

import com.unscroll.app.domain.scroll.ScrollCountingStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class PermissionHealthTest {

    @Test
    fun allGranted_andScrollCountingOffOrActive_isAllSet() {
        assertEquals(emptyList<HealthItem>(), PermissionHealth.issues(PermissionState.ALL, ScrollCountingStatus.OFF))
        assertEquals(emptyList<HealthItem>(), PermissionHealth.issues(PermissionState.ALL, ScrollCountingStatus.ACTIVE))
        assertEquals(
            emptyList<HealthItem>(),
            PermissionHealth.issues(PermissionState.ALL, ScrollCountingStatus.NEEDS_CONSENT),
        )
    }

    @Test
    fun missingPermissions_areListedInChecklistOrder() {
        val state = PermissionState(setOf(AppPermission.USAGE_ACCESS, AppPermission.OVERLAY))
        assertEquals(
            listOf(HealthItem.NOTIFICATIONS, HealthItem.BATTERY),
            PermissionHealth.issues(state, ScrollCountingStatus.OFF),
        )
    }

    @Test
    fun scrollCountingAgreedButNotRunning_isAnIssue() {
        assertEquals(
            listOf(HealthItem.ACCESSIBILITY),
            PermissionHealth.issues(PermissionState.ALL, ScrollCountingStatus.NEEDS_ENABLING),
        )
        assertEquals(
            listOf(HealthItem.USAGE_ACCESS, HealthItem.OVERLAY, HealthItem.NOTIFICATIONS, HealthItem.BATTERY, HealthItem.ACCESSIBILITY),
            PermissionHealth.issues(PermissionState.NONE, ScrollCountingStatus.NEEDS_REENABLE),
        )
    }

    @Test
    fun checklist_listsAccessibilityOnlyWhenScrollCountingIsOn() {
        fun items(scroll: ScrollCountingStatus) = PermissionHealth.checklist(PermissionState.NONE, scroll).map { it.first }
        val base = listOf(HealthItem.USAGE_ACCESS, HealthItem.OVERLAY, HealthItem.NOTIFICATIONS, HealthItem.BATTERY)

        assertEquals(base, items(ScrollCountingStatus.OFF))
        assertEquals(base, items(ScrollCountingStatus.NEEDS_CONSENT))
        assertEquals(base + HealthItem.ACCESSIBILITY, items(ScrollCountingStatus.ACTIVE))
        assertEquals(base + HealthItem.ACCESSIBILITY, items(ScrollCountingStatus.NEEDS_ENABLING))
        assertEquals(base + HealthItem.ACCESSIBILITY, items(ScrollCountingStatus.NEEDS_REENABLE))
    }

    @Test
    fun checklist_marksGrantedLinesAsDone() {
        val state = PermissionState(setOf(AppPermission.USAGE_ACCESS, AppPermission.NOTIFICATIONS))
        assertEquals(
            listOf(
                HealthItem.USAGE_ACCESS to true,
                HealthItem.OVERLAY to false,
                HealthItem.NOTIFICATIONS to true,
                HealthItem.BATTERY to false,
                HealthItem.ACCESSIBILITY to true,
            ),
            PermissionHealth.checklist(state, ScrollCountingStatus.ACTIVE),
        )
    }
}
