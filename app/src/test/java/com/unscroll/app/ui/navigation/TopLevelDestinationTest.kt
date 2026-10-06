package com.unscroll.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelDestinationTest {

    @Test
    fun bottomBar_hasDashboardAppsSettingsInOrder() {
        assertEquals(
            listOf("dashboard", "apps", "settings"),
            TopLevelDestination.entries.map { it.route },
        )
    }
}
