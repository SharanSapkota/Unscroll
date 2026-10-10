package com.unscroll.app.data.blocking

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.unscroll.app.data.db.UnscrollDatabase
import com.unscroll.app.domain.apps.ExcludedApps
import com.unscroll.app.domain.blocking.AppLimit
import com.unscroll.app.domain.blocking.BlockDecision
import com.unscroll.app.domain.blocking.BlockDuration
import com.unscroll.app.domain.blocking.BlockEvaluator
import com.unscroll.app.domain.blocking.BlockReason
import com.unscroll.app.domain.blocking.BlockSchedule
import com.unscroll.app.domain.blocking.BlockScreenRules
import com.unscroll.app.domain.blocking.LimitSettings
import com.unscroll.app.domain.blocking.QuickBlockTarget
import com.unscroll.app.domain.blocking.TimedBlock
import com.unscroll.app.domain.section.SectionVerdict
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
    fun turningOffBlockEntireApp_unblocksImmediately() = runTest {
        repository.updateLimit(PKG) { it.copy(entireAppBlockedUntil = TimedBlock.FOREVER) }
        assertEquals(BlockDecision.Blocked(BlockReason.BLOCKED_ENTIRE_APP, until = null), decide())

        val stored = repository.updateLimit(PKG) { it.copy(entireAppBlockedUntil = null) }

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
        repository.updateLimit(PKG) { it.copy(entireAppBlockedUntil = TimedBlock.FOREVER) }
        val shown = (decide() as BlockDecision.Blocked).reason

        repository.updateLimit(PKG) { it.copy(entireAppBlockedUntil = null) }

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
        repository.updateLimit(PKG) { it.copy(dailyLimitMinutes = 45, entireAppBlockedUntil = TimedBlock.FOREVER, swipeLimit = 100) }
        repository.updateLimit(PKG) { it.copy(entireAppBlockedUntil = null) }

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

    @Test
    fun quickBlock_onOff_andDurationChange() = runTest {
        repository.setQuickBlockDuration(PKG, QuickBlockTarget.ENTIRE_APP, BlockDuration.MIN_15, now)
        repository.setQuickBlock(PKG, QuickBlockTarget.ENTIRE_APP, on = true, now = now)
        assertEquals(BlockDecision.Blocked(BlockReason.BLOCKED_ENTIRE_APP, until = now + 15 * MINUTE), decide())

        // A new chip while on restarts the block with it.
        repository.setQuickBlockDuration(PKG, QuickBlockTarget.ENTIRE_APP, BlockDuration.UNTIL_OFF, now)
        assertEquals(BlockDecision.Blocked(BlockReason.BLOCKED_ENTIRE_APP, until = null), decide())

        // Off applies at once.
        repository.setQuickBlock(PKG, QuickBlockTarget.ENTIRE_APP, on = false, now = now)
        assertEquals(BlockDecision.Allowed(remainingMillis = null), decide())
        assertEquals(BlockDuration.UNTIL_OFF, repository.getLimit(PKG).settings.lastEntireDuration)
    }

    @Test
    fun timedBlock_survivesAProcessRestart_andEndsOnTime() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.deleteDatabase(RESTART_DB)
        fun open() = Room.databaseBuilder(context, UnscrollDatabase::class.java, RESTART_DB)
            .allowMainThreadQueries()
            .build()
        try {
            // Before "process death": a 30 min block on the app, 1 hour on its reels.
            open().apply {
                val repo = LimitRepository(blockingDao())
                repo.setQuickBlockDuration(PKG, QuickBlockTarget.ENTIRE_APP, BlockDuration.MIN_30, now)
                repo.setQuickBlock(PKG, QuickBlockTarget.ENTIRE_APP, on = true, now = now)
                repo.setQuickBlockDuration(PKG, QuickBlockTarget.REELS, BlockDuration.HOUR_1, now)
                repo.setQuickBlock(PKG, QuickBlockTarget.REELS, on = true, now = now)
                close()
            }
            // A new process with a new database instance and a later clock.
            open().apply {
                val repo = LimitRepository(blockingDao())
                var later = now + 29 * MINUTE
                val restarted = BlockEvaluator(Clock { later }, ExcludedApps.STATIC)
                var settings = repo.getLimit(PKG).settings
                assertEquals(
                    BlockDecision.Blocked(BlockReason.BLOCKED_ENTIRE_APP, until = now + 30 * MINUTE),
                    restarted.evaluate(settings, usedTodayMillis = 0, zone = zone),
                )
                // Exactly at the end: open again, without anything having cleared it.
                later = now + 30 * MINUTE
                assertEquals(BlockDecision.Allowed(null), restarted.evaluate(settings, usedTodayMillis = 0, zone = zone))
                assertTrue(restarted.blocksSection(settings, SectionVerdict.IN_BLOCKED_SECTION))

                // The scheduler's cleanup turns the expired toggle off and points at the next end.
                assertEquals(now + 60 * MINUTE, repo.clearExpiredBlocks(later))
                settings = repo.getLimit(PKG).settings
                assertNull(settings.entireAppBlockedUntil)
                assertEquals(now + 60 * MINUTE, settings.reelsBlockedUntil)
                assertEquals(BlockDuration.MIN_30, settings.lastEntireDuration)

                later = now + 60 * MINUTE
                assertEquals(false, restarted.blocksSection(settings, SectionVerdict.IN_BLOCKED_SECTION))
                assertNull(repo.clearExpiredBlocks(later))
                assertNull(repo.getLimit(PKG).settings.reelsBlockedUntil)
                close()
            }
        } finally {
            context.deleteDatabase(RESTART_DB)
        }
    }

    private companion object {
        const val RESTART_DB = "limit-restart-test.db"
        const val PKG = "com.instagram.android"
        const val MINUTE = 60_000L
    }
}
