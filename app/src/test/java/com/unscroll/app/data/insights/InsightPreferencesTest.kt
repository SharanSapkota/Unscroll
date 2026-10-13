package com.unscroll.app.data.insights

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.unscroll.app.domain.fox.FoxMood
import com.unscroll.app.domain.insights.Insight
import com.unscroll.app.domain.insights.InsightCategory
import com.unscroll.app.domain.insights.InsightHistory
import com.unscroll.app.domain.insights.InsightTone
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class InsightPreferencesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun TestScope.createPreferences() = InsightPreferences(
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            tempFolder.root.resolve("test.preferences_pb")
        },
    )

    private fun insight(id: String, category: InsightCategory) =
        Insight(id, category, InsightTone.HONEST, "text", null, FoxMood.HAPPY)

    @Test
    fun defaults_onHonestNoAgeNoNotification() = runTest {
        assertEquals(InsightSettings(true, InsightTone.HONEST, null, false), createPreferences().settings.first())
    }

    @Test
    fun settings_saved() = runTest {
        val preferences = createPreferences()
        preferences.setEnabled(false)
        preferences.setTone(InsightTone.BRUTAL)
        preferences.setAge(34)
        preferences.setWeeklyNotification(true)
        assertEquals(InsightSettings(false, InsightTone.BRUTAL, 34, true), preferences.settings.first())
        preferences.setAge(null)
        assertNull(preferences.settings.first().age)
        preferences.setAge(5)
        assertNull(preferences.settings.first().age)
    }

    @Test
    fun rotation_keepsTheLast30Ids_theCategory_andTheDailyCount() = runTest {
        val preferences = createPreferences()
        repeat(35) { preferences.recordShown(insight("T%02d".format(it), InsightCategory.THUMB), day = 100, now = 1_000) }
        preferences.recordShown(insight("E01", InsightCategory.COULD_HAVE), day = 101, now = 2_000)
        val state = preferences.rotation.first()
        assertEquals(InsightHistory.NO_REPEAT_WITHIN, state.history.recentIds.size)
        assertEquals("E01", state.history.recentIds.last())
        assertEquals("T06", state.history.recentIds.first())
        assertEquals(InsightCategory.COULD_HAVE, state.history.lastCategory)
        assertEquals("E01", state.currentId)
        assertEquals(1, state.shownOn(101))
        assertEquals(0, state.shownOn(102))
    }

    @Test
    fun weekly_recordsTheLifeProjectionTime() = runTest {
        val preferences = createPreferences()
        preferences.recordWeekly(insight("L01", InsightCategory.LIFE), day = 7, now = 5_000)
        preferences.recordNotified(day = 7)
        val state = preferences.rotation.first()
        assertEquals(5_000L, state.history.lastLifeAt)
        assertEquals("L01", state.weeklyId)
        assertEquals(7L, state.weeklyDay)
        assertEquals(7L, state.notifiedDay)
    }
}
