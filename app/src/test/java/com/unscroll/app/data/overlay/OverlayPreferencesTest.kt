package com.unscroll.app.data.overlay

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.unscroll.app.domain.overlay.ColorThresholds
import com.unscroll.app.domain.overlay.OverlaySettings
import com.unscroll.app.domain.overlay.PillPosition
import com.unscroll.app.domain.overlay.PillSize
import com.unscroll.app.domain.overlay.ScreenOrientation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OverlayPreferencesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun TestScope.createPreferences() = OverlayPreferences(
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            tempFolder.root.resolve("test.preferences_pb")
        },
    )

    @Test
    fun defaults() = runTest {
        val settings = createPreferences().settings.first()
        assertEquals(OverlaySettings(), settings)
        // The timer is on by default (it still needs the overlay permission to show).
        assertTrue(settings.enabled)
    }

    @Test
    fun settings_areSavedAndSanitized() = runTest {
        val preferences = createPreferences()

        preferences.setEnabled(false)
        preferences.setShowTodayTotal(true)
        preferences.setThresholds(ColorThresholds(30, 5))
        preferences.setSize(PillSize.SMALL)
        preferences.setOpacity(0.1f)

        assertEquals(
            OverlaySettings(
                enabled = false,
                showTodayTotal = true,
                thresholds = ColorThresholds(30, 31),
                size = PillSize.SMALL,
                opacity = OverlaySettings.MIN_OPACITY,
            ),
            preferences.settings.first(),
        )
    }

    @Test
    fun position_isSavedPerOrientation_andReset() = runTest {
        val preferences = createPreferences()
        assertNull(preferences.position(ScreenOrientation.PORTRAIT))

        preferences.savePosition(ScreenOrientation.PORTRAIT, PillPosition(10, 200))
        preferences.savePosition(ScreenOrientation.LANDSCAPE, PillPosition(900, 40))

        assertEquals(PillPosition(10, 200), preferences.position(ScreenOrientation.PORTRAIT))
        assertEquals(PillPosition(900, 40), preferences.position(ScreenOrientation.LANDSCAPE))

        preferences.resetPositions()

        assertNull(preferences.position(ScreenOrientation.PORTRAIT))
        assertNull(preferences.position(ScreenOrientation.LANDSCAPE))
    }

    @Test
    fun perAppPillAndSwipeSwitches_areSavedAndUndone() = runTest {
        val preferences = createPreferences()
        preferences.setPillShownFor("com.instagram.android", shown = false)
        preferences.setSwipesShownFor("com.zhiliaoapp.musically", shown = false)

        val hidden = preferences.settings.first()
        assertEquals(setOf("com.instagram.android"), hidden.pillHiddenFor)
        assertEquals(setOf("com.zhiliaoapp.musically"), hidden.swipesHiddenFor)

        preferences.setPillShownFor("com.instagram.android", shown = true)
        preferences.setSwipesShownFor("com.zhiliaoapp.musically", shown = true)
        assertEquals(OverlaySettings(), preferences.settings.first())
    }
}
