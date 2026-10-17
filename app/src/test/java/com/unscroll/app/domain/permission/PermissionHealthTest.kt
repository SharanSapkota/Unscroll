package com.unscroll.app.domain.permission

import com.unscroll.app.domain.scroll.ScrollConsent
import com.unscroll.app.domain.scroll.ScrollCountingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionHealthTest {

    @Test
    fun allGranted_andScrollCountingActive_isAllSet() {
        assertEquals(emptyList<HealthItem>(), PermissionHealth.issues(PermissionState.ALL, ScrollCountingStatus.ACTIVE))
    }

    @Test
    fun swipeCountingNotRunning_isAnIssue_inEveryState() {
        listOf(
            ScrollCountingStatus.OFF,
            ScrollCountingStatus.NEEDS_CONSENT,
            ScrollCountingStatus.NEEDS_ENABLING,
            ScrollCountingStatus.NEEDS_REENABLE,
        ).forEach { status ->
            assertEquals(status.name, listOf(HealthItem.ACCESSIBILITY), PermissionHealth.issues(PermissionState.ALL, status))
        }
    }

    @Test
    fun missingPermissions_areListedInChecklistOrder() {
        val state = PermissionState(setOf(AppPermission.USAGE_ACCESS, AppPermission.OVERLAY))
        assertEquals(
            listOf(HealthItem.NOTIFICATIONS, HealthItem.BATTERY, HealthItem.ACCESSIBILITY),
            PermissionHealth.issues(state, ScrollCountingStatus.OFF),
        )
        assertEquals(
            listOf(HealthItem.USAGE_ACCESS, HealthItem.OVERLAY, HealthItem.NOTIFICATIONS, HealthItem.BATTERY, HealthItem.ACCESSIBILITY),
            PermissionHealth.issues(PermissionState.NONE, ScrollCountingStatus.NEEDS_REENABLE),
        )
    }

    @Test
    fun checklist_alwaysListsAccessibility() {
        val all = listOf(HealthItem.USAGE_ACCESS, HealthItem.OVERLAY, HealthItem.NOTIFICATIONS, HealthItem.BATTERY, HealthItem.ACCESSIBILITY)
        ScrollCountingStatus.entries.forEach { status ->
            assertEquals(all, PermissionHealth.checklist(PermissionState.NONE, status).map { it.first })
        }
    }

    @Test
    fun accessibilityButton_showsTheDisclosureUntilTheUserAgreed() {
        assertTrue(PermissionHealth.accessibilityNeedsDisclosure(ScrollConsent.NOT_ASKED))
        assertTrue(PermissionHealth.accessibilityNeedsDisclosure(ScrollConsent.DECLINED))
        assertFalse(PermissionHealth.accessibilityNeedsDisclosure(ScrollConsent.AGREED))
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
