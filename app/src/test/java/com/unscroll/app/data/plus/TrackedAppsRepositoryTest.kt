package com.unscroll.app.data.plus

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.unscroll.app.data.plus.FakeBillingGateway.Companion.plus
import com.unscroll.app.domain.session.Session
import com.unscroll.app.domain.time.Clock
import com.unscroll.app.domain.tracking.TrackedApps
import com.unscroll.app.domain.tracking.TrackedApps.FACEBOOK
import com.unscroll.app.domain.tracking.TrackedApps.INSTAGRAM
import com.unscroll.app.domain.tracking.TrackedApps.TIKTOK
import com.unscroll.app.testing.FakeUsageDataSource
import com.unscroll.app.util.InstalledTrackedApps
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The free tier end to end: Billing → EntitlementRepository → active apps. No tracked app is
 * installed under Robolectric, so InstalledTrackedApps lists all of them.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class TrackedAppsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val billing = FakeBillingGateway()
    private val usage = FakeUsageDataSource()
    private lateinit var preferences: EntitlementPreferences
    private lateinit var entitlement: EntitlementRepository

    private fun TestScope.repository(): TrackedAppsRepository {
        preferences = EntitlementPreferences(
            PreferenceDataStoreFactory.create(scope = backgroundScope) {
                tempFolder.root.resolve("plus.preferences_pb")
            },
        )
        entitlement = EntitlementRepository(billing, preferences, backgroundScope, debugBuild = false)
        return TrackedAppsRepository(
            entitlement = entitlement,
            preferences = preferences,
            installedApps = InstalledTrackedApps(ApplicationProvider.getApplicationContext()),
            usage = usage,
            clock = Clock { NOW },
            scope = backgroundScope,
        )
    }

    private fun used(packageName: String, minutes: Long) {
        usage.sessions += Session(usage.sessions.size + 1L, packageName, NOW - HOUR, NOW - HOUR + minutes * 60_000, 0)
    }

    @Test
    fun firstLaunch_free_needsPick_andTracksOnlyTheMostUsedMeanwhile() = runTest {
        used(TIKTOK, 50)
        used(INSTAGRAM, 10)
        val repository = repository()

        val state = repository.state.first()
        assertTrue(state.needsPick)
        assertEquals(setOf(TIKTOK), state.active)
        assertTrue(repository.isActive(TIKTOK))
        assertFalse(repository.isActive(INSTAGRAM))
    }

    @Test
    fun pick_tracksOnlyThePick_andPausesTheRest() = runTest {
        used(TIKTOK, 50)
        val repository = repository()
        repository.pick(listOf(FACEBOOK))

        val state = repository.state.first { !it.needsPick }
        assertEquals(setOf(FACEBOOK), state.active)
        assertEquals(TrackedApps.packageNames - FACEBOOK, state.paused.toSet())
    }

    @Test
    fun upgrade_resumesEveryApp_andDowngrade_keepsThePick() = runTest {
        val repository = repository()
        repository.pick(listOf(INSTAGRAM))
        repository.state.first { !it.needsPick }

        billing.purchases = listOf(plus())
        entitlement.check()
        val plusState = repository.state.first { it.isPlus }
        assertEquals(TrackedApps.packageNames, plusState.active)
        assertEquals(emptyList<String>(), plusState.paused)

        billing.purchases = emptyList()
        entitlement.check()
        val free = repository.state.first { !it.isPlus }
        assertEquals(setOf(INSTAGRAM), free.active)
        assertFalse(free.needsPick)
        assertTrue(entitlement.plusEnded.first())
    }

    @Test
    fun downgrade_withoutAPick_keepsTheMostUsed_andRemembersIt() = runTest {
        used(FACEBOOK, 30)
        val repository = repository()
        billing.purchases = listOf(plus())
        entitlement.check()
        repository.state.first { it.isPlus }

        billing.purchases = emptyList()
        entitlement.check()
        val free = repository.state.first { !it.isPlus && !it.needsPick }
        assertEquals(setOf(FACEBOOK), free.active)
        assertEquals(setOf(FACEBOOK), preferences.pickedApps.first())

        // Usage changes later don't swap the kept app.
        used(TIKTOK, 300)
        repository.refresh()
        assertEquals(setOf(FACEBOOK), repository.state.first().active)
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val HOUR = 3_600_000L
    }
}
