package com.unscroll.app.data.db

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** In-memory Room tests, run on the JVM with Robolectric. */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class SessionDaoTest {

    private lateinit var database: UnscrollDatabase
    private lateinit var dao: SessionDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            UnscrollDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.sessionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insert_storesOpenSessionWithZeroScrolls() = runTest {
        val id = dao.insert(SessionEntity(packageName = "com.instagram.android", startTime = 1_000))

        assertEquals(
            listOf(SessionEntity(id, "com.instagram.android", 1_000, endTime = null, scrollCount = 0)),
            dao.getOpen(),
        )
    }

    @Test
    fun close_setsEndTime() = runTest {
        val id = dao.insert(SessionEntity(packageName = "com.instagram.android", startTime = 1_000))

        dao.close(id, endTime = 5_000)

        assertEquals(emptyList<SessionEntity>(), dao.getOpen())
        assertEquals(5_000L, dao.observeRecent(10).first().single().endTime)
    }

    @Test
    fun closeAllOpen_closesOnlyOpenSessions_neverBeforeTheirStart() = runTest {
        val closedId = dao.insert(SessionEntity(packageName = "a", startTime = 1_000, endTime = 2_000))
        val oldOpenId = dao.insert(SessionEntity(packageName = "b", startTime = 3_000))
        val newOpenId = dao.insert(SessionEntity(packageName = "c", startTime = 9_000))

        val closed = dao.closeAllOpen(endTime = 6_000)

        assertEquals(2, closed)
        val endTimes = dao.observeRecent(10).first().associate { it.id to it.endTime }
        assertEquals(2_000L, endTimes[closedId])
        assertEquals(6_000L, endTimes[oldOpenId])
        // The heartbeat is older than this session, so it ends where it started.
        assertEquals(9_000L, endTimes[newOpenId])
    }

    @Test
    fun observeRecent_isNewestFirstAndLimited() = runTest {
        repeat(5) { index ->
            dao.insert(
                SessionEntity(
                    packageName = "app$index",
                    startTime = index * 1_000L,
                    endTime = index * 1_000L + 500,
                ),
            )
        }

        assertEquals(
            listOf("app4", "app3", "app2"),
            dao.observeRecent(3).first().map { it.packageName },
        )
    }

    @Test
    fun observeRecent_emitsWhenSessionsChange() = runTest {
        dao.observeRecent(10).test {
            assertEquals(emptyList<SessionEntity>(), awaitItem())

            val id = dao.insert(SessionEntity(packageName = "com.facebook.katana", startTime = 1_000))
            awaitUntil { sessions -> sessions.map { it.endTime } == listOf(null) }

            dao.close(id, 2_000)
            awaitUntil { sessions -> sessions.map { it.endTime } == listOf(2_000L) }

            cancelAndIgnoreRemainingEvents()
        }
    }

    /** Room may re-emit unchanged results, so skip items until the expected one arrives. */
    private suspend fun <T> ReceiveTurbine<T>.awaitUntil(predicate: (T) -> Boolean) {
        while (!predicate(awaitItem())) {
            // Keep waiting. awaitItem() fails the test after Turbine's timeout.
        }
    }
}
