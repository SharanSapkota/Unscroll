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

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
