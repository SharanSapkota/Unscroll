package com.unscroll.app.data.db

import android.app.Application
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Builds a version 1 database exactly as M2-M4 created it, with data, then opens it with the
 * current Room database. Room validates every table against the entities after migrating, so a
 * migration that doesn't match the entities fails here, and the session data must survive.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class MigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @After
    fun tearDown() {
        context.deleteDatabase(DB_NAME)
    }

    private fun createVersion1() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(DB_NAME)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `sessions` (" +
                                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`packageName` TEXT NOT NULL, `startTime` INTEGER NOT NULL, " +
                                "`endTime` INTEGER, `scrollCount` INTEGER NOT NULL DEFAULT 0)",
                        )
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_startTime` ON `sessions` (`startTime`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_endTime` ON `sessions` (`endTime`)")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            )
            .build()
        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        helper.writableDatabase.apply {
            execSQL(
                "INSERT INTO sessions (packageName, startTime, endTime, scrollCount) " +
                    "VALUES ('com.instagram.android', 1000, 61000, 0)",
            )
            execSQL(
                "INSERT INTO sessions (packageName, startTime, endTime, scrollCount) " +
                    "VALUES ('com.facebook.katana', 70000, NULL, 3)",
            )
        }
        helper.close()
    }

    @Test
    fun migrate1To2_keepsSessions_andAddsLimitAndOverrideTables() = runTest {
        createVersion1()

        val database = Room.databaseBuilder(context, UnscrollDatabase::class.java, DB_NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val sessions = database.sessionDao().observeRecent(10).first()
            assertEquals(
                listOf(
                    SessionEntity(2, "com.facebook.katana", 70_000, null, 3),
                    SessionEntity(1, "com.instagram.android", 1_000, 61_000, 0),
                ),
                sessions,
            )

            val limit = AppLimitEntity("com.instagram.android", 30, false, false, 127, 1320, 420, null, null)
            database.blockingDao().upsertLimit(limit)
            assertEquals(limit, database.blockingDao().getLimit("com.instagram.android"))

            database.blockingDao().insertOverride(
                BlockOverrideEntity(packageName = "com.instagram.android", grantedAt = 1, expiresAt = 2, method = "WAIT"),
            )
            assertEquals(1, database.blockingDao().observeOverridesSince(0).first().size)
        } finally {
            database.close()
        }
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
    }
}
