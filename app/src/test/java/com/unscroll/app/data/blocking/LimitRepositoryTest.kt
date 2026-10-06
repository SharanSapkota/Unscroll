package com.unscroll.app.data.blocking

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.unscroll.app.data.db.UnscrollDatabase
import com.unscroll.app.domain.blocking.AppLimit
import com.unscroll.app.domain.blocking.FrictionMode
import com.unscroll.app.domain.blocking.LimitSettings
import com.unscroll.app.domain.blocking.PendingChange
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class LimitRepositoryTest {

    private lateinit var database: UnscrollDatabase
    private lateinit var repository: LimitRepository
    private val delay = 10 * 60_000L

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

    @Test
    fun unconfiguredApp_hasNoRules() = runTest {
        assertEquals(AppLimit(PKG), repository.getLimit(PKG, now = 0))
    }

    @Test
    fun strongerChange_isStoredImmediately() = runTest {
        repository.requestChange(PKG, LimitSettings(dailyLimitMinutes = 30), now = 0, delayMillis = delay)
        assertEquals(AppLimit(PKG, LimitSettings(dailyLimitMinutes = 30)), repository.getLimit(PKG, now = 1))
    }

    @Test
    fun weakerChange_waits_thenAppliesOnRead() = runTest {
        repository.requestChange(PKG, LimitSettings(dailyLimitMinutes = 30), now = 0, delayMillis = delay)
        repository.requestChange(PKG, LimitSettings(dailyLimitMinutes = null), now = 1_000, delayMillis = delay)

        val waiting = repository.getLimit(PKG, now = 1_000 + delay - 1)
        assertEquals(LimitSettings(dailyLimitMinutes = 30), waiting.settings)
        assertEquals(PendingChange(LimitSettings.NONE, appliesAt = 1_000 + delay), waiting.pending)

        val applied = repository.getLimit(PKG, now = 1_000 + delay)
        assertEquals(AppLimit(PKG, LimitSettings.NONE, pending = null), applied)
    }

    @Test
    fun applyDueChanges_writesThroughForAllApps() = runTest {
        repository.requestChange(PKG, LimitSettings(blockedAlways = true), now = 0, delayMillis = delay)
        repository.requestChange(PKG, LimitSettings.NONE, now = 0, delayMillis = delay)

        repository.applyDueChanges(now = delay)

        assertNull(database.blockingDao().getLimit(PKG)?.pendingChangeJson)
        assertEquals(false, database.blockingDao().getLimit(PKG)?.blockedAlways)
    }

    @Test
    fun cancelAndApplyNow() = runTest {
        repository.requestChange(PKG, LimitSettings(blockedAlways = true), now = 0, delayMillis = delay)
        repository.requestChange(PKG, LimitSettings.NONE, now = 0, delayMillis = delay)
        repository.cancelPendingChange(PKG, now = 1)
        assertEquals(AppLimit(PKG, LimitSettings(blockedAlways = true)), repository.getLimit(PKG, now = delay * 2))

        repository.requestChange(PKG, LimitSettings.NONE, now = 0, delayMillis = delay)
        repository.applyPendingChangeNow(PKG, now = 1)
        assertEquals(AppLimit(PKG, LimitSettings.NONE), repository.getLimit(PKG, now = 2))
    }

    @Test
    fun extension_activeForItsDuration() = runTest {
        repository.grantExtension(PKG, now = 1_000, durationMillis = 300_000, method = FrictionMode.TYPE_PHRASE)
        assertEquals(301_000L, repository.activeExtensionUntil(PKG, now = 2_000))
        assertNull(repository.activeExtensionUntil(PKG, now = 301_000))
    }

    private companion object {
        const val PKG = "com.instagram.android"
    }
}
