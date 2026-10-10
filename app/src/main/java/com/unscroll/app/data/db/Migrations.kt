package com.unscroll.app.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.unscroll.app.domain.blocking.TimedBlock
import com.unscroll.app.domain.tracking.DefaultTrackedApps

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

/**
 * v4 → v5: the pause screen is gone (apps open instantly; the live timer is the stopper). Drops
 * `pause_outcomes` and the pause columns of `app_friction`. SQLite on API 26 has no DROP COLUMN,
 * so `app_friction` is rebuilt, keeping every other setting. Nothing else is touched.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `pause_outcomes`")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `app_friction_new` (" +
                "`packageName` TEXT NOT NULL, " +
                "`nudgesEnabled` INTEGER NOT NULL, " +
                "`nudgeThresholds` TEXT NOT NULL, " +
                "`breakRemindersEnabled` INTEGER NOT NULL, " +
                "`breakIntervalMinutes` INTEGER NOT NULL, " +
                "`limitWarningsEnabled` INTEGER NOT NULL, " +
                "`tintEnabled` INTEGER NOT NULL, " +
                "`swipeBreakAfter` INTEGER, " +
                "PRIMARY KEY(`packageName`))",
        )
        db.execSQL(
            "INSERT INTO `app_friction_new` (packageName, nudgesEnabled, nudgeThresholds, " +
                "breakRemindersEnabled, breakIntervalMinutes, limitWarningsEnabled, tintEnabled, swipeBreakAfter) " +
                "SELECT packageName, nudgesEnabled, nudgeThresholds, breakRemindersEnabled, " +
                "breakIntervalMinutes, limitWarningsEnabled, tintEnabled, swipeBreakAfter FROM `app_friction`",
        )
        db.execSQL("DROP TABLE `app_friction`")
        db.execSQL("ALTER TABLE `app_friction_new` RENAME TO `app_friction`")
    }
}

/** v5 → v6: the hard swipe limit on `app_limits`. Existing rows get no swipe limit. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `app_limits` ADD COLUMN `swipeLimit` INTEGER")
        db.execSQL("ALTER TABLE `app_limits` ADD COLUMN `swipeLimitScope` TEXT NOT NULL DEFAULT 'DAY'")
        db.execSQL("ALTER TABLE `app_limits` ADD COLUMN `swipeSessionGapMinutes` INTEGER NOT NULL DEFAULT 30")
        db.execSQL("ALTER TABLE `app_limits` ADD COLUMN `swipeAccessAllowed` INTEGER NOT NULL DEFAULT 0")
    }
}


/**
 * v6 → v7: no more cooldown. Any pending change in `app_limits` is applied now (whether or not
 * its cooldown was over), then `pendingChangeJson` and `pendingChangeAppliesAt` are dropped. SQLite
 * on API 26 has no DROP COLUMN, so the table is rebuilt, keeping every row and setting.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val pending = db.query("SELECT packageName, pendingChangeJson FROM app_limits WHERE pendingChangeJson IS NOT NULL")
            .use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        val settings = LegacyPendingChange.decode(cursor.getString(1)) ?: continue
                        add(cursor.getString(0) to settings)
                    }
                }
            }
        pending.forEach { (packageName, settings) ->
            db.execSQL(
                "UPDATE app_limits SET dailyLimitMinutes = ?, blockedAlways = ?, scheduleEnabled = ?, " +
                    "scheduleDays = ?, scheduleStartMinute = ?, scheduleEndMinute = ?, swipeLimit = ?, " +
                    "swipeLimitScope = ?, swipeSessionGapMinutes = ?, swipeAccessAllowed = ? WHERE packageName = ?",
                arrayOf<Any?>(
                    settings.dailyLimitMinutes,
                    if (settings.entireAppBlockedUntil == TimedBlock.FOREVER) 1 else 0,
                    if (settings.schedule.enabled) 1 else 0,
                    settings.schedule.daysBitmask,
                    settings.schedule.startMinute,
                    settings.schedule.endMinute,
                    settings.swipeLimit,
                    settings.swipeLimitScope.name,
                    settings.swipeSessionGapMinutes,
                    if (settings.swipeAccessAllowed) 1 else 0,
                    packageName,
                ),
            )
        }
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `app_limits_new` (" +
                "`packageName` TEXT NOT NULL, " +
                "`dailyLimitMinutes` INTEGER, " +
                "`blockedAlways` INTEGER NOT NULL, " +
                "`scheduleEnabled` INTEGER NOT NULL, " +
                "`scheduleDays` INTEGER NOT NULL, " +
                "`scheduleStartMinute` INTEGER NOT NULL, " +
                "`scheduleEndMinute` INTEGER NOT NULL, " +
                "`swipeLimit` INTEGER, " +
                "`swipeLimitScope` TEXT NOT NULL DEFAULT 'DAY', " +
                "`swipeSessionGapMinutes` INTEGER NOT NULL DEFAULT 30, " +
                "`swipeAccessAllowed` INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(`packageName`))",
        )
        db.execSQL(
            "INSERT INTO `app_limits_new` (packageName, dailyLimitMinutes, blockedAlways, scheduleEnabled, " +
                "scheduleDays, scheduleStartMinute, scheduleEndMinute, swipeLimit, swipeLimitScope, " +
                "swipeSessionGapMinutes, swipeAccessAllowed) " +
                "SELECT packageName, dailyLimitMinutes, blockedAlways, scheduleEnabled, scheduleDays, " +
                "scheduleStartMinute, scheduleEndMinute, swipeLimit, swipeLimitScope, swipeSessionGapMinutes, " +
                "swipeAccessAllowed FROM `app_limits`",
        )
        db.execSQL("DROP TABLE `app_limits`")
        db.execSQL("ALTER TABLE `app_limits_new` RENAME TO `app_limits`")
    }
}

/**
 * v7 → v8: the user's tracked apps move into Room (`tracked_apps`), so any installed app can be
 * added. Existing users get the apps Unscroll tracked until now (Instagram, both TikTok packages,
 * Facebook), in the same order, all active with swipe counting on. Sessions, limits and settings
 * are untouched: they were already keyed by package name.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `tracked_apps` (" +
                "`packageName` TEXT NOT NULL, " +
                "`cachedLabel` TEXT, " +
                "`addedAt` INTEGER NOT NULL, " +
                "`status` TEXT NOT NULL DEFAULT 'ACTIVE', " +
                "`countSwipes` INTEGER NOT NULL DEFAULT 1, " +
                "`removed` INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(`packageName`))",
        )
        DefaultTrackedApps.packageNames.forEachIndexed { index, packageName ->
            db.execSQL(
                "INSERT OR IGNORE INTO `tracked_apps` (packageName, cachedLabel, addedAt, status, countSwipes, removed) " +
                    "VALUES (?, NULL, ?, 'ACTIVE', 1, 0)",
                arrayOf<Any>(packageName, index.toLong()),
            )
        }
    }
}

/**
 * v8 → v9: the quick toggles. `app_limits` gets `entireAppBlockedUntil` and `reelsBlockedUntil`
 * (epoch millis; Long.MAX_VALUE = "until I turn it off"; null = off) and the remembered duration
 * chip of each (`lastEntireDuration`, `lastReelsDuration`, minutes, 0 = "until I turn it off").
 * "Block completely" (`blockedAlways`) becomes "Block entire app" until turned off, so every
 * current block stays. `blockedAlways` stays as a mirror of that (SQLite on API 26 can't drop it
 * without a rebuild). Nothing else is touched.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `app_limits` ADD COLUMN `entireAppBlockedUntil` INTEGER")
        db.execSQL("ALTER TABLE `app_limits` ADD COLUMN `reelsBlockedUntil` INTEGER")
        db.execSQL("ALTER TABLE `app_limits` ADD COLUMN `lastEntireDuration` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `app_limits` ADD COLUMN `lastReelsDuration` INTEGER NOT NULL DEFAULT 0")
        db.execSQL(
            "UPDATE `app_limits` SET `entireAppBlockedUntil` = ? WHERE `blockedAlways` = 1",
            arrayOf<Any>(TimedBlock.FOREVER),
        )
    }
}

val ALL_MIGRATIONS = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8,
    MIGRATION_8_9,
)
