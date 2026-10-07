package com.unscroll.app.data.blocking

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.unscroll.app.data.db.SessionEntity
import com.unscroll.app.data.db.UnscrollDatabase
import com.unscroll.app.domain.blocking.AppLimit
import com.unscroll.app.domain.blocking.LimitSettings
import com.unscroll.app.domain.blocking.SwipeLimitScope
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class SwipeLimitRepositoryTest {

    private lateinit var database: UnscrollDatabase
    private lateinit var limits: LimitRepository
    private lateinit var repository: SwipeLimitRepository
    private val zone = ZoneId.of("Europe/Berlin")

    private fun at(day: Int, hour: Int, minute: Int = 0) =
        LocalDateTime.of(2026, 10, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Application>(),
            UnscrollDatabase::class.java,
        ).allowMainThreadQueries().build()
        limits = LimitRepository(database.blockingDao())
        repository = SwipeLimitRepository(database.sessionDao(), limits)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun session(start: Long, end: Long?, swipes: Int, pkg: String = PKG) {
        database.sessionDao().insert(SessionEntity(packageName = pkg, startTime = start, endTime = end, scrollCount = swipes))
    }

    @Test
    fun noSwipeLimit_noStatus() = runTest {
        assertNull(repository.status(PKG, LimitSettings(), at(7, 12), zone))
    }

    @Test
    fun perDay_countsTodaysSessionsOfThatAppOnly() = runTest {
        session(at(6, 22), at(6, 23), swipes = 90) // Yesterday.
        session(at(7, 9), at(7, 9, 20), swipes = 30)
        session(at(7, 11), null, swipes = 25)
        session(at(7, 10), at(7, 10, 5), swipes = 99, pkg = OTHER)

        val status = repository.status(PKG, LimitSettings(swipeLimit = 50), at(7, 11, 10), zone)!!

        assertEquals(55, status.used)
        assertTrue(status.reached)
    }

    @Test
    fun perSession_resetsAfterLeavingForTheGap() = runTest {
        val settings = LimitSettings(swipeLimit = 50, swipeLimitScope = SwipeLimitScope.SESSION, swipeSessionGapMinutes = 30)
        session(at(7, 9), at(7, 9, 10), swipes = 40)
        session(at(7, 9, 20), at(7, 9, 30), swipes = 15) // 10 min break: same swipe session.

        assertEquals(55, repository.status(PKG, settings, at(7, 9, 35), zone)!!.used)

        // Away 45 min, then back: a new swipe session, the count starts again.
        session(at(7, 10, 15), null, swipes = 3)
        val fresh = repository.status(PKG, settings, at(7, 10, 16), zone)!!
        assertEquals(3, fresh.used)
        assertFalse(fresh.reached)
    }

    @Test
    fun extensions_add20SwipesForTheCurrentWindowOnly() = runTest {
        limits.updateLimit(PKG) { LimitSettings(swipeLimit = 50) }
        session(at(7, 9), null, swipes = 60)
        limits.grantSwipeExtension(PKG, at(6, 20)) // Yesterday: doesn't count today.
        limits.grantSwipeExtension(PKG, at(7, 9, 30))

        val status = repository.status(PKG, LimitSettings(swipeLimit = 50), at(7, 9, 31), zone)!!

        assertEquals(70, status.allowance)
        assertEquals(10, status.remaining)
        // A swipe extension is not a time extension.
        assertNull(limits.activeExtensionUntil(PKG, at(7, 9, 30)))
    }

    @Test
    fun swipeLimitSettings_roundTripThroughTheDatabase() = runTest {
        val settings = LimitSettings(
            dailyLimitMinutes = 45,
            swipeLimit = 120,
            swipeLimitScope = SwipeLimitScope.SESSION,
            swipeSessionGapMinutes = 15,
            swipeAccessAllowed = true,
        )
        limits.updateLimit(PKG) { settings }
        assertEquals(AppLimit(PKG, settings), limits.getLimit(PKG))
    }

    private companion object {
        const val PKG = "com.instagram.android"
        const val OTHER = "com.zhiliaoapp.musically"
    }
}
