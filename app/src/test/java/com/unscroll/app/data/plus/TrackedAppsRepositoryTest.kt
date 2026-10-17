package com.unscroll.app.data.plus

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.unscroll.app.data.apps.InstalledApps
import com.unscroll.app.data.apps.TrackedAppsPreferences
import com.unscroll.app.data.blocking.LimitRepository
import com.unscroll.app.data.db.AppLimitEntity
import com.unscroll.app.data.db.SessionEntity
import com.unscroll.app.data.db.TrackedAppEntity
import com.unscroll.app.data.db.UnscrollDatabase
import com.unscroll.app.data.plus.FakeBillingGateway.Companion.plus
import com.unscroll.app.domain.apps.AddResult
import com.unscroll.app.domain.apps.ExcludedApps
import com.unscroll.app.domain.apps.LaunchableApp
import com.unscroll.app.domain.plus.AppTrackingStatus
import com.unscroll.app.domain.session.Session
import com.unscroll.app.domain.time.Clock
import com.unscroll.app.domain.tracking.DefaultTrackedApps.FACEBOOK
import com.unscroll.app.domain.tracking.DefaultTrackedApps.INSTAGRAM
import com.unscroll.app.domain.tracking.DefaultTrackedApps.TIKTOK
import com.unscroll.app.domain.tracking.DefaultTrackedApps.TIKTOK_ASIA
import com.unscroll.app.testing.BillingSwitch
import com.unscroll.app.testing.FakeUsageDataSource
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Installed apps under test control. Thread-safe: the repository reads it on Dispatchers.IO. */
private class FakeInstalledApps(
    val installed: MutableSet<String> = ConcurrentHashMap.newKeySet<String>().apply { addAll(listOf(INSTAGRAM, TIKTOK, FACEBOOK)) },
) : InstalledApps {
    override fun isInstalled(packageName: String): Boolean = packageName in installed

    override fun label(packageName: String): String? = if (packageName in installed) "label:$packageName" else null

    override suspend fun launchable(): List<LaunchableApp> = installed.map { LaunchableApp(it, "label:$it") }
}

/**
 * The tracked list end to end: Room (`tracked_apps`), Billing → EntitlementRepository for the
 * free-tier limit, and fake installed apps.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class TrackedAppsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var database: UnscrollDatabase
    private val billing = FakeBillingGateway()
    private val usage = FakeUsageDataSource()
    private val installed = FakeInstalledApps()
    private lateinit var preferences: EntitlementPreferences
    private lateinit var entitlement: EntitlementRepository

    @Before
    fun setUp() {
        database = newDatabase()
    }

    private fun newDatabase(): UnscrollDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Application>(),
        UnscrollDatabase::class.java,
    ).build()

    @After
    fun tearDown() {
        database.close()
    }

    private var dataStore: DataStore<Preferences>? = null
    private var dataStoreName = "plus"

    /**
     * Payments on unless a test says otherwise (the free-tier tests need Billing). A second call in
     * the same test reuses the database and DataStore, like the app after an update.
     */
    private fun TestScope.repository(billingEnabled: Boolean = BillingSwitch.ON): TrackedAppsRepository {
        val dataStore = dataStore ?: PreferenceDataStoreFactory.create(scope = backgroundScope) {
            tempFolder.root.resolve("$dataStoreName.preferences_pb")
        }.also { dataStore = it }
        preferences = EntitlementPreferences(dataStore)
        entitlement = EntitlementRepository(billing, preferences, backgroundScope, debugBuild = false, billingEnabled = billingEnabled)
        return TrackedAppsRepository(
            dao = database.trackedAppDao(),
            entitlement = entitlement,
            preferences = preferences,
            seed = TrackedAppsPreferences(dataStore),
            installedApps = installed,
            excludedApps = ExcludedApps.STATIC,
            usage = usage,
            clock = Clock { NOW },
            scope = backgroundScope,
            limits = LimitRepository(database.blockingDao()),
        )
    }

    private suspend fun becomePlus() {
        billing.purchases = listOf(plus())
        entitlement.check()
    }

    private fun used(packageName: String, minutes: Long) {
        usage.sessions += Session(usage.sessions.size + 1L, packageName, NOW - HOUR, NOW - HOUR + minutes * 60_000, 0)
    }

    @Test
    fun freshInstall_seedsTheDefaults_showsTheInstalledOnes_andNeedsAPick() = runTest {
        used(TIKTOK, 50)
        used(INSTAGRAM, 10)
        val repository = repository()

        val state = repository.state.first { it.entries.size == 3 }
        // TikTok's second package is seeded too, but hidden until it is ever installed.
        assertEquals(listOf(INSTAGRAM, TIKTOK, FACEBOOK), state.entries.map { it.packageName })
        assertEquals(4, database.trackedAppDao().observeAll().first().size)
        assertNotNull(database.trackedAppDao().get(TIKTOK_ASIA))
        assertTrue(state.needsPick)
        assertEquals(setOf(TIKTOK), state.active)
        assertTrue(repository.isActive(TIKTOK))
        assertFalse(repository.isActive(INSTAGRAM))
    }

    @Test
    fun limitDefaults_newlyTrackedApps_getA30MinuteTimeLimit_andNoSwipeLimit() = runTest {
        installed.installed += VIDEO
        val repository = repository()
        becomePlus()
        repository.state.first { it.isPlus && it.entries.size == 3 }
        val dao = database.blockingDao()
        dao.observeLimits().first { rows -> rows.size == DEFAULTS_SEEDED }
        listOf(INSTAGRAM, TIKTOK, TIKTOK_ASIA, FACEBOOK).forEach {
            assertEquals(it, 30, dao.getLimit(it)?.dailyLimitMinutes)
            assertNull(it, dao.getLimit(it)?.swipeLimit)
        }

        assertEquals(AddResult.ADDED, repository.add(VIDEO, "Video"))
        assertEquals(30, dao.getLimit(VIDEO)?.dailyLimitMinutes)
        assertNull(dao.getLimit(VIDEO)?.swipeLimit)
    }

    @Test
    fun limitDefaults_neverChangeAnExistingUsersApps() = runTest {
        // Tracked before the default existed: a row in the list, no stored limits ("no limit").
        database.trackedAppDao().insertIgnore(listOf(TrackedAppEntity(packageName = INSTAGRAM, cachedLabel = null, addedAt = 0)))
        // And an app whose limit the user switched off.
        database.blockingDao().upsertLimit(
            AppLimitEntity(TIKTOK, dailyLimitMinutes = null, blockedAlways = false, scheduleEnabled = false, scheduleDays = 0, scheduleStartMinute = 0, scheduleEndMinute = 0),
        )
        val repository = repository()
        repository.state.first { it.entries.size == 3 }
        database.blockingDao().observeLimits().first { rows -> rows.any { it.packageName == FACEBOOK } }

        assertNull(database.blockingDao().getLimit(INSTAGRAM))
        assertNull(database.blockingDao().getLimit(TIKTOK)?.dailyLimitMinutes)
        assertEquals(30, database.blockingDao().getLimit(FACEBOOK)?.dailyLimitMinutes)
    }

    @Test
    fun pick_tracksOnlyThePick_andShowsTheRestAsLocked() = runTest {
        val repository = repository()
        repository.state.first { it.entries.size == 3 }
        repository.pick(listOf(FACEBOOK))

        val state = repository.state.first { !it.needsPick }
        assertEquals(setOf(FACEBOOK), state.active)
        assertEquals(AppTrackingStatus.LOCKED, state.statusOf(INSTAGRAM))
        assertEquals(AppTrackingStatus.ACTIVE, state.statusOf(FACEBOOK))
    }

    @Test
    fun plus_anyAddedApp_isTracked_withSwipesCounted() = runTest {
        installed.installed += VIDEO
        val repository = repository()
        becomePlus()
        repository.state.first { it.isPlus && it.entries.size == 3 }

        assertEquals(AddResult.ADDED, repository.add(VIDEO, "Video"))
        val state = repository.state.first { VIDEO in it.active }
        assertEquals(setOf(INSTAGRAM, TIKTOK, FACEBOOK, VIDEO), state.active)
        assertTrue(repository.countsSwipes(VIDEO))
        assertEquals(VIDEO, repository.trackedApps.first().last().packageName)
    }

    @Test
    fun free_atTheLimit_addingNeedsPlus_andNothingChanges() = runTest {
        installed.installed += VIDEO
        val repository = repository()
        repository.state.first { it.entries.size == 3 }
        repository.pick(listOf(INSTAGRAM))
        repository.state.first { it.active == setOf(INSTAGRAM) }

        assertEquals(AddResult.NEEDS_PLUS, repository.add(VIDEO, "Video"))
        assertNull(database.trackedAppDao().get(VIDEO))
        assertFalse(repository.isActive(VIDEO))
    }

    @Test
    fun free_withRoomUnderTheLimit_addsTheAppAsTheFreeApp() = runTest {
        installed.installed.clear()
        installed.installed += VIDEO
        val repository = repository()
        repository.state.first { it.entries.isEmpty() }

        assertEquals(AddResult.ADDED, repository.add(VIDEO, "Video"))
        assertEquals(setOf(VIDEO), repository.state.first { it.active.isNotEmpty() }.active)
        assertEquals(setOf(VIDEO), preferences.pickedApps.first())
    }

    @Test
    fun excludedApps_areRefused_byTheRepository() = runTest {
        val repository = repository()
        becomePlus()
        repository.state.first { it.isPlus }

        assertEquals(AddResult.EXCLUDED, repository.add("com.android.settings", "Settings"))
        assertEquals(AddResult.EXCLUDED, repository.add("com.sharansapkota.unscroll", "Unscroll"))
        assertNull(database.trackedAppDao().get("com.android.settings"))
    }

    @Test
    fun remove_thenReAdd_restoresHistoryAndSettings() = runTest {
        installed.installed += VIDEO
        val repository = repository()
        becomePlus()
        repository.state.first { it.isPlus }
        repository.add(VIDEO, "Video")
        repository.state.first { VIDEO in it.active }
        database.sessionDao().insert(SessionEntity(packageName = VIDEO, startTime = 1_000, endTime = 61_000))
        database.blockingDao().upsertLimit(
            AppLimitEntity(VIDEO, dailyLimitMinutes = 30, blockedAlways = false, scheduleEnabled = false, scheduleDays = 0, scheduleStartMinute = 0, scheduleEndMinute = 0),
        )
        val addedAt = database.trackedAppDao().get(VIDEO)?.addedAt

        repository.remove(VIDEO)
        val removed = repository.state.first { VIDEO !in it.active }
        assertTrue(removed.entries.none { it.packageName == VIDEO })
        // Nothing was deleted.
        assertEquals(listOf(VIDEO), database.sessionDao().getAll().map { it.packageName })
        assertEquals(30, database.blockingDao().getLimit(VIDEO)?.dailyLimitMinutes)

        assertEquals(AddResult.ADDED, repository.add(VIDEO, "Video"))
        repository.state.first { VIDEO in it.active }
        assertEquals(30, database.blockingDao().getLimit(VIDEO)?.dailyLimitMinutes)
        assertEquals(1, database.sessionDao().getAll().size)
        assertEquals(addedAt, database.trackedAppDao().get(VIDEO)?.addedAt)
    }

    @Test
    fun pause_andResume() = runTest {
        val repository = repository()
        becomePlus()
        repository.state.first { it.isPlus && INSTAGRAM in it.active }

        repository.setPaused(INSTAGRAM, paused = true)
        val paused = repository.state.first { INSTAGRAM !in it.active }
        assertEquals(AppTrackingStatus.PAUSED, paused.statusOf(INSTAGRAM))

        repository.setPaused(INSTAGRAM, paused = false)
        assertEquals(AppTrackingStatus.ACTIVE, repository.state.first { INSTAGRAM in it.active }.statusOf(INSTAGRAM))
    }

    @Test
    fun uninstalledApp_showsNotInstalled_isNotTracked_andComesBackOnReinstall() = runTest {
        val repository = repository()
        becomePlus()
        repository.state.first { it.isPlus && FACEBOOK in it.active }
        // The name was remembered while it was installed.
        database.trackedAppDao().observeAll().first { rows -> rows.any { it.packageName == FACEBOOK && it.cachedLabel != null } }

        installed.installed -= FACEBOOK
        repository.refresh()
        // Wait for the settled state: an earlier emission can still carry the rows from before the
        // name was cached, where the uninstalled app isn't listed at all.
        val state = repository.state.first { it.statusOf(FACEBOOK) == AppTrackingStatus.NOT_INSTALLED }
        assertFalse(FACEBOOK in state.active)
        assertEquals("label:$FACEBOOK", state.entries.first { it.packageName == FACEBOOK }.label)

        installed.installed += FACEBOOK
        repository.refresh()
        repository.state.first { FACEBOOK in it.active }
    }

    @Test
    fun countSwipesOff_stopsCountingForThatAppOnly() = runTest {
        val repository = repository()
        becomePlus()
        repository.state.first { it.isPlus && INSTAGRAM in it.active }

        repository.setCountSwipes(INSTAGRAM, countSwipes = false)
        repository.state.first { !it.countsSwipes(INSTAGRAM) }
        assertTrue(repository.isActive(INSTAGRAM))
        assertTrue(repository.countsSwipes(TIKTOK))
    }

    @Test
    fun upgrade_resumesEveryApp_andDowngrade_keepsThePick() = runTest {
        val repository = repository()
        repository.state.first { it.entries.size == 3 }
        repository.pick(listOf(INSTAGRAM))
        repository.state.first { !it.needsPick }

        becomePlus()
        assertEquals(setOf(INSTAGRAM, TIKTOK, FACEBOOK), repository.state.first { it.isPlus }.active)

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
        becomePlus()
        repository.state.first { it.isPlus && it.entries.size == 3 }

        billing.purchases = emptyList()
        entitlement.check()
        val free = repository.state.first { !it.isPlus && !it.needsPick }
        assertEquals(setOf(FACEBOOK), free.active)
        assertEquals(setOf(FACEBOOK), preferences.pickedApps.first())
    }

    // Payments off (PlusConfig.BILLING_ENABLED false).

    @Test
    fun billingOff_freshInstall_tracksEveryApp_withoutAPick() = runTest {
        used(TIKTOK, 50)
        val repository = repository(billingEnabled = BillingSwitch.OFF)

        val state = repository.state.first { it.entries.size == 3 }
        assertEquals(setOf(INSTAGRAM, TIKTOK, FACEBOOK), state.active)
        assertFalse(state.needsPick)
        assertTrue(state.isPlus)
        assertEquals(0, billing.calls)
    }

    @Test
    fun billingOff_addingApps_neverNeedsPlus() = runTest {
        installed.installed += setOf(VIDEO, "com.example.a", "com.example.b")
        val repository = repository(billingEnabled = BillingSwitch.OFF)
        repository.state.first { it.entries.size == 3 }

        listOf(VIDEO, "com.example.a", "com.example.b").forEach {
            assertEquals(AddResult.ADDED, repository.add(it, it))
        }
        assertEquals(6, repository.state.first { it.active.size == 6 }.active.size)
        // No free picks are written while everything is unlocked.
        assertEquals(emptySet<String>(), preferences.pickedApps.first())
    }

    @Test
    fun billingOff_reactivatesAppsPausedByTheFreeTier_withHistoryAndSettingsIntact() = runTest {
        // Before: a free user whose Plus ended, Facebook picked, the other two locked by the free tier.
        val before = repository()
        before.state.first { it.entries.size == 3 }
        before.pick(listOf(FACEBOOK))
        // The pick must be in the state before the notice: with the notice and no pick, the
        // downgrade would keep the most used app instead (as when Plus ends without a pick).
        before.state.first { it.active == setOf(FACEBOOK) }
        preferences.setPlusEnded(true)
        entitlement.plusEnded.first { it }
        assertEquals(AppTrackingStatus.LOCKED, before.state.first { it.active == setOf(FACEBOOK) }.statusOf(INSTAGRAM))
        database.sessionDao().insert(SessionEntity(packageName = INSTAGRAM, startTime = 1_000, endTime = 61_000))
        database.blockingDao().upsertLimit(
            AppLimitEntity(INSTAGRAM, dailyLimitMinutes = 30, blockedAlways = false, scheduleEnabled = false, scheduleDays = 0, scheduleStartMinute = 0, scheduleEndMinute = 0),
        )

        // After the update with payments off: every app is active again, nothing deleted.
        val after = repository(billingEnabled = BillingSwitch.OFF)
        val state = after.state.first { it.active.size == 3 }
        assertEquals(setOf(INSTAGRAM, TIKTOK, FACEBOOK), state.active)
        assertEquals(AppTrackingStatus.ACTIVE, state.statusOf(INSTAGRAM))
        assertTrue(after.isActive(INSTAGRAM))
        assertFalse(entitlement.plusEnded.first())
        assertEquals(listOf(INSTAGRAM), database.sessionDao().getAll().map { it.packageName })
        assertEquals(30, database.blockingDao().getLimit(INSTAGRAM)?.dailyLimitMinutes)
        // No row was paused, removed or deleted (the four seeded defaults include TikTok's second package).
        val rows = database.trackedAppDao().observeAll().first()
        assertEquals(4, rows.size)
        assertTrue(rows.all { !it.removed && it.status == "ACTIVE" })
        // The stored pick stays for when payments come back.
        assertEquals(setOf(FACEBOOK), preferences.pickedApps.first())
    }

    @Test
    fun billingOff_keepsTheUsersOwnPause() = runTest {
        val repository = repository(billingEnabled = BillingSwitch.OFF)
        repository.state.first { INSTAGRAM in it.active }

        repository.setPaused(INSTAGRAM, paused = true)
        assertEquals(AppTrackingStatus.PAUSED, repository.state.first { INSTAGRAM !in it.active }.statusOf(INSTAGRAM))
    }

    @Test
    fun bothStates_withPlus_theTrackedListWorksTheSame() {
        BillingSwitch.BOTH.forEach { enabled ->
            // A fresh install for each state.
            database.close()
            database = newDatabase()
            dataStore = null
            dataStoreName = "plus-$enabled"
            installed.installed += VIDEO
            billing.purchases = listOf(plus())
            runTest {
                val repository = repository(billingEnabled = enabled)
                entitlement.check()
                val state = repository.state.first { it.isPlus && it.entries.size == 3 }
                assertEquals("billingEnabled=$enabled", setOf(INSTAGRAM, TIKTOK, FACEBOOK), state.active)
                assertFalse(state.needsPick)

                assertEquals(AddResult.ADDED, repository.add(VIDEO, "Video"))
                repository.state.first { VIDEO in it.active }
                repository.setCountSwipes(VIDEO, countSwipes = false)
                repository.state.first { !it.countsSwipes(VIDEO) }
                repository.setPaused(VIDEO, paused = true)
                assertEquals(AppTrackingStatus.PAUSED, repository.state.first { VIDEO !in it.active }.statusOf(VIDEO))
                repository.remove(VIDEO)
                repository.state.first { latest -> latest.entries.none { it.packageName == VIDEO } }
            }
            installed.installed -= VIDEO
        }
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val HOUR = 3_600_000L
        const val VIDEO = "com.example.video"

        /** Instagram, both TikTok packages and Facebook. */
        const val DEFAULTS_SEEDED = 4
    }
}
