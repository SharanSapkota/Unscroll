package com.unscroll.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class TopLevelDestinationTest {

    @Test
    fun bottomBar_hasHomeAppsSettingsInOrder() {
        assertEquals(
            listOf("home", "apps", "settings"),
            TopLevelDestination.entries.map { it.route },
        )
    }

    @Test
    fun appDetailRoute_carriesThePackageName() {
        assertEquals("app/com.instagram.android", AppDetailRoute.of("com.instagram.android"))
    }
}
