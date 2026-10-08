package com.unscroll.app.data.blocking

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.unscroll.app.data.db.UnscrollDatabase
import com.unscroll.app.domain.apps.ExcludedApps
import com.unscroll.app.domain.blocking.AppLimit
import com.unscroll.app.domain.blocking.BlockDecision
import com.unscroll.app.domain.blocking.BlockEvaluator
import com.unscroll.app.domain.blocking.BlockReason
import com.unscroll.app.domain.blocking.BlockSchedule
import com.unscroll.app.domain.blocking.BlockScreenRules
import com.unscroll.app.domain.blocking.LimitSettings
import com.unscroll.app.domain.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** No cooldown: every change is stored at once, and the next evaluation already sees it. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class LimitRepositoryTest {

    private lateinit var database: UnscrollDatabase
    private lateinit var repository: LimitRepository
    private val zone = ZoneId.of("Europe/Berlin")

    // A Wednesday at 23:00.
    private val now = LocalDateTime.of(2026, 10, 7, 23, 0).atZone(zone).toInstant().toEpochMilli()
    private val evaluator = BlockEvaluator(Clock { now }, ExcludedApps.STATIC)

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            UnscrollDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = LimitRepository(database.blockingDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun decide(usedTodayMillis: Long = 0): BlockDecision = evaluator.evaluate(
        settings = repository.getLimit(PKG).settings,
        usedTodayMillis = usedTodayMillis,
        extensionUntil = repository.activeExtensionUntil(PKG, now),
        now = now,
        zone = zone,
    )

    @Test
    fun unconfiguredApp_hasNoRules() = runTest {
        assertEquals(AppLimit(PKG), repository.getLimit(PKG))
    }

    @Test
    fun turningOffBlockCompletely_unblocksImmediately() = runTest {
        repository.updateLimit(PKG) { it.copy(blockedAlways = true) }
        assertEquals(BlockDecision.Blocked(BlockReason.BLOCKED_ALWAYS, until = null), decide())

        val stored = repository.updateLimit(PKG) { it.copy(blockedAlways = false) }

        assertEquals(AppLimit(PKG, LimitSettings.NONE), stored)
        assertEquals(LimitSettings.NONE, repository.getLimit(PKG).settings)
        assertEquals(BlockDecision.Allowed(remainingMillis = null), decide())
    }

    @Test
    fun raisingALimit_appliesImmediately() = runTest {
        repository.updateLimit(PKG) { it.copy(dailyLimitMinutes = 30) }
        assertTrue(decide(usedTodayMillis = 40 * MINUTE) is BlockDecision.Blocked)

        repository.updateLimit(PKG) { it.copy(dailyLimitMinutes = 60) }

        assertEquals(60, repository.getLimit(PKG).settings.dailyLimitMinutes)
        assertEquals(BlockDecision.Allowed(remainingMillis = 20 * MINUTE), decide(usedTodayMillis = 40 * MINUTE))
    }

    @Test
    fun removingALimit_appliesImmediately() = runTest {
        repository.updateLimit(PKG) { it.copy(dailyLimitMinutes = 30) }
        repository.updateLimit(PKG) { it.copy(dailyLimitMinutes = null) }

        assertEquals(BlockDecision.Allowed(remainingMillis = null), decide(usedTodayMillis = 40 * MINUTE))
    }

    @Test
    fun disablingASchedule_appliesImmediately() = runTest {
        // 22:00 to 07:00 every day, and it's 23:00.
        repository.updateLimit(PKG) { it.copy(schedule = BlockSchedule(enabled = true)) }
        assertTrue(decide() is BlockDecision.Blocked)

        repository.updateLimit(PKG) { it.copy(schedule = it.schedule.copy(enabled = false)) }

        assertEquals(false, repository.getLimit(PKG).settings.schedule.enabled)
        assertEquals(BlockDecision.Allowed(remainingMillis = null), decide())
    }

    @Test
    fun anOpenBlockScreenSeesTheUnblockRightAway() = runTest {
        repository.updateLimit(PKG) { it.copy(blockedAlways = true) }
        val shown = (decide() as BlockDecision.Blocked).reason

        repository.updateLimit(PKG) { it.copy(blockedAlways = false) }

        assertEquals(LimitSettings.NONE, repository.observeLimits().first()[PKG]?.settings)
        assertNull(BlockScreenRules.current(shown, decide(), swipeLimitReached = false))
    }

    @Test
    fun strongerChanges_applyImmediatelyToo() = runTest {
        repository.updateLimit(PKG) { it.copy(dailyLimitMinutes = 30, swipeLimit = 100) }
        repository.updateLimit(PKG) { it.copy(dailyLimitMinutes = 15, swipeLimit = 50) }

        assertEquals(LimitSettings(dailyLimitMinutes = 15, swipeLimit = 50), repository.getLimit(PKG).settings)
    }

    @Test
    fun updateLimit_keepsTheOtherSettings() = runTest {
        repository.updateLimit(PKG) { it.copy(dailyLimitMinutes = 45, blockedAlways = true, swipeLimit = 100) }
        repository.updateLimit(PKG) { it.copy(blockedAlways = false) }

        assertEquals(LimitSettings(dailyLimitMinutes = 45, swipeLimit = 100), repository.getLimit(PKG).settings)
    }

    @Test
    fun extension_oneTap_activeForItsDuration_andLogged() = runTest {
        repository.grantExtension(PKG, now = 1_000, durationMillis = 300_000)

        assertEquals(301_000L, repository.activeExtensionUntil(PKG, now = 2_000))
        assertNull(repository.activeExtensionUntil(PKG, now = 301_000))
        val logged = database.blockingDao().observeOverridesSince(0).first().single()
        assertEquals("TAP", logged.method)
        assertEquals(1_000L, logged.grantedAt)
    }

    @Test
    fun extension_letsTheUserBackInAfterTheDailyLimit() = runTest {
        repository.updateLimit(PKG) { it.copy(dailyLimitMinutes = 30) }
        repository.grantExtension(PKG, now = now, durationMillis = 5 * MINUTE)

        assertEquals(BlockDecision.Allowed(remainingMillis = 5 * MINUTE), decide(usedTodayMillis = 40 * MINUTE))
    }

    private companion object {
        const val PKG = "com.instagram.android"
        const val MINUTE = 60_000L
    }
}
