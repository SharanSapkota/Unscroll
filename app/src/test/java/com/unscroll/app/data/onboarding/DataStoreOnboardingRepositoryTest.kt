package com.unscroll.app.data.onboarding

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DataStoreOnboardingRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun TestScope.createRepository() = DataStoreOnboardingRepository(
        PreferenceDataStoreFactory.create(scope = backgroundScope) {
            tempFolder.root.resolve("test.preferences_pb")
        },
    )

    @Test
    fun defaultsToNotCompleted() = runTest {
        assertFalse(createRepository().onboardingCompleted.first())
    }

    @Test
    fun setOnboardingCompleted_emitsNewValue() = runTest {
        val repository = createRepository()

        repository.onboardingCompleted.test {
            assertEquals(false, awaitItem())
            repository.setOnboardingCompleted(true)
            assertEquals(true, awaitItem())
            repository.setOnboardingCompleted(false)
            assertEquals(false, awaitItem())
        }
    }
}
