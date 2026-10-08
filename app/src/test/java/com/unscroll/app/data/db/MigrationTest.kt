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
import org.junit.Assert.assertFalse
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

    private fun createVersion1() = createDatabase(version = 1)

    /** Builds the schema of [version] by hand: v1 tables, plus each migration up to it. */
    private fun createDatabase(version: Int) {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(DB_NAME)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE TABLE IF NOT EXISTS `sessions` (" +
                                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                                "`packageName` TEXT NOT NULL, `startTime` INTEGER NOT NULL, " +
                                "`endTime` INTEGER, `scrollCount` INTEGER NOT NULL DEFAULT 0)",
                        )
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_startTime` ON `sessions` (`startTime`)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sessions_endTime` ON `sessions` (`endTime`)")
                        if (version >= 2) MIGRATION_1_2.migrate(db)
                        if (version >= 3) MIGRATION_2_3.migrate(db)
                        if (version >= 4) MIGRATION_3_4.migrate(db)
                        if (version >= 5) MIGRATION_4_5.migrate(db)
                        if (version >= 6) MIGRATION_5_6.migrate(db)
                        if (version >= 7) MIGRATION_6_7.migrate(db)
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

            val limit = AppLimitEntity("com.instagram.android", 30, false, false, 127, 1320, 420)
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

    @Test
    fun migrate2To3_keepsLimitsAndSessions_andAddsFrictionTables() = runTest {
        createDatabase(version = 2)
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(DB_NAME)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(2) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            )
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config).apply {
            writableDatabase.execSQL(
                "INSERT INTO app_limits VALUES ('com.instagram.android', 45, 0, 1, 31, 1320, 420, NULL, NULL)",
            )
            close()
        }

        val database = Room.databaseBuilder(context, UnscrollDatabase::class.java, DB_NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(2, database.sessionDao().observeRecent(10).first().size)
            assertEquals(45, database.blockingDao().getLimit("com.instagram.android")?.dailyLimitMinutes)

            val friction = database.frictionDao()
            friction.upsertSettings(
                AppFrictionEntity(
                    packageName = "com.instagram.android",
                    nudgesEnabled = true,
                    nudgeThresholds = "5,10,20",
                    breakRemindersEnabled = true,
                    breakIntervalMinutes = 15,
                    limitWarningsEnabled = true,
                    tintEnabled = false,
                ),
            )
            assertEquals("5,10,20", friction.getSettings("com.instagram.android")?.nudgeThresholds)
            // Added in v3, dropped again in v5.
            assertFalse(database.hasTable("pause_outcomes"))
            friction.insertNudges(listOf(NudgeLogEntity("com.instagram.android", "2026-10-06", "OPENS", 5, 1)))
            assertEquals(listOf(5), friction.sentNudges("com.instagram.android", "2026-10-06", "OPENS"))
        } finally {
            database.close()
        }
    }

    @Test
    fun migrate3To4To5_keepsFrictionSettings_addsSwipeBreakOff_andDropsPauseColumns() = runTest {
        createDatabase(version = 3)
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(DB_NAME)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(3) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            )
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config).apply {
            writableDatabase.execSQL(
                "INSERT INTO app_friction VALUES ('com.instagram.android', 1, 20, 1, '5,10', 0, 15, 1, 0)",
            )
            close()
        }

        val database = Room.databaseBuilder(context, UnscrollDatabase::class.java, DB_NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val sessions = database.sessionDao().observeRecent(10).first()
            assertEquals(listOf(3, 0), sessions.map { it.scrollCount })

            val friction = database.frictionDao()
            val migrated = friction.getSettings("com.instagram.android")
            assertEquals(
                AppFrictionEntity(
                    packageName = "com.instagram.android",
                    nudgesEnabled = true,
                    nudgeThresholds = "5,10",
                    breakRemindersEnabled = false,
                    breakIntervalMinutes = 15,
                    limitWarningsEnabled = true,
                    tintEnabled = false,
                    swipeBreakAfter = null,
                ),
                migrated,
            )

            friction.upsertSettings(migrated!!.copy(swipeBreakAfter = 50))
            assertEquals(50, friction.getSettings("com.instagram.android")?.swipeBreakAfter)
        } finally {
            database.close()
        }
    }

    @Test
    fun migrate4To5_dropsPauseOutcomes_andPauseSettings_keepingEverythingElse() = runTest {
        createDatabase(version = 4)
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(DB_NAME)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(4) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            )
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config).apply {
            writableDatabase.apply {
                // v4 app_friction: packageName, pauseEnabled, pauseSeconds, nudgesEnabled, nudgeThresholds,
                // breakRemindersEnabled, breakIntervalMinutes, limitWarningsEnabled, tintEnabled, swipeBreakAfter.
                execSQL("INSERT INTO app_friction VALUES ('com.instagram.android', 0, 25, 0, '3,30', 1, 45, 0, 1, 100)")
                execSQL("INSERT INTO app_friction VALUES ('com.facebook.katana', 1, 10, 1, '5,10,20', 1, 15, 1, 0, NULL)")
                execSQL("INSERT INTO pause_outcomes (packageName, shownAt, outcome) VALUES ('com.instagram.android', 1, 'ABANDONED')")
                execSQL("INSERT INTO app_limits VALUES ('com.instagram.android', 30, 1, 0, 127, 1320, 420, NULL, NULL)")
                execSQL("INSERT INTO nudge_log VALUES ('com.instagram.android', '2026-10-06', 'OPENS', 5, 1)")
            }
            close()
        }

        val database = Room.databaseBuilder(context, UnscrollDatabase::class.java, DB_NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            assertFalse(database.hasTable("pause_outcomes"))
            val friction = database.frictionDao()
            assertEquals(
                AppFrictionEntity(
                    packageName = "com.instagram.android",
                    nudgesEnabled = false,
                    nudgeThresholds = "3,30",
                    breakRemindersEnabled = true,
                    breakIntervalMinutes = 45,
                    limitWarningsEnabled = false,
                    tintEnabled = true,
                    swipeBreakAfter = 100,
                ),
                friction.getSettings("com.instagram.android"),
            )
            assertEquals(2, friction.observeSettings().first().size)
            assertEquals(listOf(5), friction.sentNudges("com.instagram.android", "2026-10-06", "OPENS"))
            assertEquals(30, database.blockingDao().getLimit("com.instagram.android")?.dailyLimitMinutes)
            assertEquals(listOf(3, 0), database.sessionDao().observeRecent(10).first().map { it.scrollCount })
        } finally {
            database.close()
        }
    }

    @Test
    fun migrate5To6_keepsLimits_andAddsSwipeLimitOff() = runTest {
        createDatabase(version = 5)
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(DB_NAME)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(5) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            )
            .build()
        FrameworkSQLiteOpenHelperFactory().create(config).apply {
            writableDatabase.execSQL(
                "INSERT INTO app_limits VALUES ('com.instagram.android', 45, 1, 0, 127, 1320, 420, NULL, NULL)",
            )
            close()
        }

        val database = Room.databaseBuilder(context, UnscrollDatabase::class.java, DB_NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val limit = database.blockingDao().getLimit("com.instagram.android")!!
            assertEquals(45, limit.dailyLimitMinutes)
            assertEquals(true, limit.blockedAlways)
            assertEquals(null, limit.swipeLimit)
            assertEquals("DAY", limit.swipeLimitScope)
            assertEquals(30, limit.swipeSessionGapMinutes)
            assertEquals(false, limit.swipeAccessAllowed)
            assertEquals(listOf(3, 0), database.sessionDao().observeRecent(10).first().map { it.scrollCount })

            database.blockingDao().upsertLimit(limit.copy(swipeLimit = 100))
            assertEquals(100, database.blockingDao().getLimit("com.instagram.android")?.swipeLimit)
        } finally {
            database.close()
        }
    }

    @Test
    fun migrate6To7_appliesPendingChangesNow_andDropsThePendingColumns() = runTest {
        createDatabase(version = 6)
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(DB_NAME)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(6) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                },
            )
            .build()
        // Instagram: blocked, with "Block completely" off still waiting out its cooldown (far in
        // the future) and a raised swipe limit. TikTok: a corrupt pending value. Facebook: none.
        val pendingOff = "{\"dailyLimitMinutes\":60,\"blockedAlways\":false,\"scheduleEnabled\":false," +
            "\"scheduleDays\":127,\"scheduleStartMinute\":1320,\"scheduleEndMinute\":420," +
            "\"swipeLimit\":200,\"swipeLimitPerSession\":false,\"swipeSessionGapMinutes\":30," +
            "\"swipeAccessAllowed\":false}"
        val columns = "(packageName, dailyLimitMinutes, blockedAlways, scheduleEnabled, scheduleDays, " +
            "scheduleStartMinute, scheduleEndMinute, pendingChangeJson, pendingChangeAppliesAt, swipeLimit, " +
            "swipeLimitScope, swipeSessionGapMinutes, swipeAccessAllowed)"
        FrameworkSQLiteOpenHelperFactory().create(config).apply {
            writableDatabase.execSQL(
                "INSERT INTO app_limits $columns VALUES " +
                    "('com.instagram.android', 30, 1, 1, 31, 1320, 420, ?, 9999999999999, 100, 'DAY', 30, 0)",
                arrayOf<Any?>(pendingOff),
            )
            writableDatabase.execSQL(
                "INSERT INTO app_limits $columns VALUES " +
                    "('com.zhiliaoapp.musically', 15, 1, 0, 127, 1320, 420, 'not json', 5000, NULL, 'SESSION', 15, 1)",
            )
            writableDatabase.execSQL(
                "INSERT INTO app_limits $columns VALUES " +
                    "('com.facebook.katana', 45, 0, 1, 1, 600, 720, NULL, NULL, 50, 'DAY', 30, 1)",
            )
            writableDatabase.execSQL(
                "INSERT INTO block_overrides (packageName, grantedAt, expiresAt, method) " +
                    "VALUES ('com.instagram.android', 1, 2, 'PHRASE')",
            )
            close()
        }

        val database = Room.databaseBuilder(context, UnscrollDatabase::class.java, DB_NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = database.blockingDao()
            assertEquals(
                AppLimitEntity(
                    packageName = "com.instagram.android",
                    dailyLimitMinutes = 60,
                    blockedAlways = false,
                    scheduleEnabled = false,
                    scheduleDays = 127,
                    scheduleStartMinute = 1320,
                    scheduleEndMinute = 420,
                    swipeLimit = 200,
                    swipeLimitScope = "DAY",
                    swipeSessionGapMinutes = 30,
                    swipeAccessAllowed = false,
                ),
                dao.getLimit("com.instagram.android"),
            )
            assertEquals(
                AppLimitEntity(
                    packageName = "com.zhiliaoapp.musically",
                    dailyLimitMinutes = 15,
                    blockedAlways = true,
                    scheduleEnabled = false,
                    scheduleDays = 127,
                    scheduleStartMinute = 1320,
                    scheduleEndMinute = 420,
                    swipeLimit = null,
                    swipeLimitScope = "SESSION",
                    swipeSessionGapMinutes = 15,
                    swipeAccessAllowed = true,
                ),
                dao.getLimit("com.zhiliaoapp.musically"),
            )
            assertEquals(
                AppLimitEntity(
                    packageName = "com.facebook.katana",
                    dailyLimitMinutes = 45,
                    blockedAlways = false,
                    scheduleEnabled = true,
                    scheduleDays = 1,
                    scheduleStartMinute = 600,
                    scheduleEndMinute = 720,
                    swipeLimit = 50,
                    swipeLimitScope = "DAY",
                    swipeSessionGapMinutes = 30,
                    swipeAccessAllowed = true,
                ),
                dao.getLimit("com.facebook.katana"),
            )
            val limitColumns = database.openHelper.readableDatabase
                .query("PRAGMA table_info(`app_limits`)")
                .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(1)) } }
            assertFalse("pendingChangeJson" in limitColumns)
            assertFalse("pendingChangeAppliesAt" in limitColumns)
            // Sessions and the extension log are untouched.
            assertEquals(2, database.sessionDao().observeRecent(10).first().size)
            assertEquals(listOf("PHRASE"), dao.observeOverridesSince(0).first().map { it.method })
        } finally {
            database.close()
        }
    }

    @Test
    fun migrate7To8_addsTrackedApps_withTheDefaults_keepingSessions() = runTest {
        createDatabase(version = 7)
        val database = Room.databaseBuilder(context, UnscrollDatabase::class.java, DB_NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = database.trackedAppDao()
            assertEquals(
                listOf(
                    "com.instagram.android",
                    "com.zhiliaoapp.musically",
                    "com.ss.android.ugc.trill",
                    "com.facebook.katana",
                ),
                dao.observeAll().first().map { it.packageName },
            )
            assertEquals(
                TrackedAppEntity("com.instagram.android", cachedLabel = null, addedAt = 0, status = "ACTIVE", countSwipes = true, removed = false),
                dao.get("com.instagram.android"),
            )
            assertEquals(2, database.sessionDao().observeRecent(10).first().size)

            // Any package can be added, and removing only hides it.
            dao.upsert(TrackedAppEntity("com.example.video", cachedLabel = "Video", addedAt = 100))
            dao.setRemoved("com.example.video", removed = true)
            assertEquals(true, dao.get("com.example.video")?.removed)
        } finally {
            database.close()
        }
    }

    private fun UnscrollDatabase.hasTable(name: String): Boolean =
        openHelper.readableDatabase
            .query("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(name))
            .use { it.count > 0 }

    private companion object {
        const val DB_NAME = "migration-test.db"
    }
}
