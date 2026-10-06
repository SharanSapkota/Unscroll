package com.unscroll.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v1 → v2 (M5): adds the limits and override tables. Sessions are untouched. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `app_limits` (" +
                "`packageName` TEXT NOT NULL, " +
                "`dailyLimitMinutes` INTEGER, " +
                "`blockedAlways` INTEGER NOT NULL, " +
                "`scheduleEnabled` INTEGER NOT NULL, " +
                "`scheduleDays` INTEGER NOT NULL, " +
                "`scheduleStartMinute` INTEGER NOT NULL, " +
                "`scheduleEndMinute` INTEGER NOT NULL, " +
                "`pendingChangeJson` TEXT, " +
                "`pendingChangeAppliesAt` INTEGER, " +
                "PRIMARY KEY(`packageName`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `block_overrides` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`packageName` TEXT NOT NULL, " +
                "`grantedAt` INTEGER NOT NULL, " +
                "`expiresAt` INTEGER NOT NULL, " +
                "`method` TEXT NOT NULL)",
        )
    }
}

/** v2 → v3 (M6): per-app friction settings, pause outcomes and the once-a-day nudge log. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `app_friction` (" +
                "`packageName` TEXT NOT NULL, " +
                "`pauseEnabled` INTEGER NOT NULL, " +
                "`pauseSeconds` INTEGER NOT NULL, " +
                "`nudgesEnabled` INTEGER NOT NULL, " +
                "`nudgeThresholds` TEXT NOT NULL, " +
                "`breakRemindersEnabled` INTEGER NOT NULL, " +
                "`breakIntervalMinutes` INTEGER NOT NULL, " +
                "`limitWarningsEnabled` INTEGER NOT NULL, " +
                "`tintEnabled` INTEGER NOT NULL, " +
                "PRIMARY KEY(`packageName`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `pause_outcomes` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`packageName` TEXT NOT NULL, " +
                "`shownAt` INTEGER NOT NULL, " +
                "`outcome` TEXT NOT NULL)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_pause_outcomes_shownAt` ON `pause_outcomes` (`shownAt`)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `nudge_log` (" +
                "`packageName` TEXT NOT NULL, " +
                "`day` TEXT NOT NULL, " +
                "`kind` TEXT NOT NULL, " +
                "`value` INTEGER NOT NULL, " +
                "`sentAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`packageName`, `day`, `kind`, `value`))",
        )
    }
}

/** v3 → v4 (M7): per-app "take a break after N swipes". Null keeps it off for existing rows. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `app_friction` ADD COLUMN `swipeBreakAfter` INTEGER")
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
