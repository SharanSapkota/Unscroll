package com.unscroll.app.data.db

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
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
class BlockingDaoTest {

    private lateinit var database: UnscrollDatabase
    private lateinit var dao: BlockingDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            UnscrollDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.blockingDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun limit(
        packageName: String = INSTAGRAM,
        dailyLimitMinutes: Int? = 30,
        pendingJson: String? = null,
        pendingAt: Long? = null,
    ) = AppLimitEntity(
        packageName = packageName,
        dailyLimitMinutes = dailyLimitMinutes,
        blockedAlways = false,
        scheduleEnabled = true,
        scheduleDays = 0b0011111,
        scheduleStartMinute = 22 * 60,
        scheduleEndMinute = 7 * 60,
        pendingChangeJson = pendingJson,
        pendingChangeAppliesAt = pendingAt,
    )

    @Test
    fun upsert_insertsThenReplaces() = runTest {
        assertNull(dao.getLimit(INSTAGRAM))

        dao.upsertLimit(limit(dailyLimitMinutes = 30))
        assertEquals(limit(dailyLimitMinutes = 30), dao.getLimit(INSTAGRAM))

        dao.upsertLimit(limit(dailyLimitMinutes = null))
        assertEquals(limit(dailyLimitMinutes = null), dao.getLimit(INSTAGRAM))
        assertEquals(1, dao.observeLimits().first().size)
    }

    @Test
    fun duePendingChanges_onlyThoseWhoseTimeHasCome() = runTest {
        dao.upsertLimit(limit(INSTAGRAM, pendingJson = "{}", pendingAt = 1_000))
        dao.upsertLimit(limit(TIKTOK, pendingJson = "{}", pendingAt = 5_000))
        dao.upsertLimit(limit(FACEBOOK))

        assertEquals(emptyList<String>(), dao.getDuePendingChanges(999).map { it.packageName })
        assertEquals(listOf(INSTAGRAM), dao.getDuePendingChanges(1_000).map { it.packageName })
        assertEquals(setOf(INSTAGRAM, TIKTOK), dao.getDuePendingChanges(9_000).map { it.packageName }.toSet())
    }

    @Test
    fun overrides_activeUntilIsLatestUnexpired() = runTest {
        assertNull(dao.activeOverrideUntil(INSTAGRAM, now = 0))

        dao.insertOverride(BlockOverrideEntity(packageName = INSTAGRAM, grantedAt = 0, expiresAt = 300, method = "WAIT"))
        dao.insertOverride(BlockOverrideEntity(packageName = INSTAGRAM, grantedAt = 100, expiresAt = 400, method = "PHRASE"))
        dao.insertOverride(BlockOverrideEntity(packageName = TIKTOK, grantedAt = 100, expiresAt = 900, method = "WAIT"))

        assertEquals(400L, dao.activeOverrideUntil(INSTAGRAM, now = 200))
        assertEquals(400L, dao.activeOverrideUntil(INSTAGRAM, now = 350))
        assertNull(dao.activeOverrideUntil(INSTAGRAM, now = 400))
        assertNull(dao.activeOverrideUntil(FACEBOOK, now = 0))
    }

    @Test
    fun deleteAllOverrides_endsTheLog_butKeepsLimits() = runTest {
        dao.upsertLimit(AppLimitEntity(INSTAGRAM, 30, false, false, 127, 0, 0, null, null))
        dao.insertOverride(BlockOverrideEntity(packageName = INSTAGRAM, grantedAt = 100, expiresAt = 400, method = "WAIT"))

        dao.deleteAllOverrides()

        assertEquals(null, dao.activeOverrideUntil(INSTAGRAM, now = 200))
        assertEquals(30, dao.getLimit(INSTAGRAM)?.dailyLimitMinutes)
    }

    @Test
    fun overrides_areLoggedForTheDashboard() = runTest {
        dao.insertOverride(BlockOverrideEntity(packageName = INSTAGRAM, grantedAt = 100, expiresAt = 400, method = "WAIT"))
        dao.insertOverride(BlockOverrideEntity(packageName = TIKTOK, grantedAt = 500, expiresAt = 800, method = "PHRASE"))

        val since = dao.observeOverridesSince(200).first()
        assertEquals(listOf(TIKTOK), since.map { it.packageName })
        assertEquals("PHRASE", since.single().method)
        assertEquals(2, dao.observeOverridesSince(0).first().size)
    }

    private companion object {
        const val INSTAGRAM = "com.instagram.android"
        const val TIKTOK = "com.zhiliaoapp.musically"
        const val FACEBOOK = "com.facebook.katana"
    }
}
