package com.unscroll.app.data.goals

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GoalPreferencesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun TestScope.createPreferences() = GoalPreferences(
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            tempFolder.root.resolve("test.preferences_pb")
        },
    )

    @Test
    fun offByDefault_setAndClear() = runTest {
        val preferences = createPreferences()
        assertNull(preferences.dailyGoalMinutes.first())

        preferences.setDailyGoal(90)
        assertEquals(90, preferences.dailyGoalMinutes.first())

        preferences.setDailyGoal(null)
        assertNull(preferences.dailyGoalMinutes.first())

        preferences.setDailyGoal(0)
        assertNull(preferences.dailyGoalMinutes.first())
    }
}
