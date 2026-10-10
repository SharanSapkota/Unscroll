package com.unscroll.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Limits for one tracked app. A row only exists once the user has configured something. */
@Entity(tableName = "app_limits")
data class AppLimitEntity(
    @PrimaryKey val packageName: String,
    val dailyLimitMinutes: Int?,
    /** "Block completely" before v9; since then a mirror of entireAppBlockedUntil == Long.MAX_VALUE. */
    val blockedAlways: Boolean,
    val scheduleEnabled: Boolean,
    /** Monday is bit 0, Sunday bit 6. */
    val scheduleDays: Int,
    /** Minutes after local midnight. */
    val scheduleStartMinute: Int,
    val scheduleEndMinute: Int,
    /** v6: hard swipe limit, or null for none. */
    val swipeLimit: Int? = null,
    /** v6: "DAY" or "SESSION". */
    @ColumnInfo(defaultValue = "'DAY'") val swipeLimitScope: String = "DAY",
    @ColumnInfo(defaultValue = "30") val swipeSessionGapMinutes: Int = 30,
    @ColumnInfo(defaultValue = "0") val swipeAccessAllowed: Boolean = false,
    /** v9: "Block entire app" until this epoch-millis time (Long.MAX_VALUE: until turned off), or null. */
    val entireAppBlockedUntil: Long? = null,
    /** v9: "Block reels only" until this time, same encoding. */
    val reelsBlockedUntil: Long? = null,
    /** v9: the remembered duration chip, in minutes (0: until turned off). */
    @ColumnInfo(defaultValue = "0") val lastEntireDuration: Int = 0,
    @ColumnInfo(defaultValue = "0") val lastReelsDuration: Int = 0,
)

/** One "I need access" extension granted from the block screen. */
@Entity(tableName = "block_overrides")
data class BlockOverrideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val grantedAt: Long,
    val expiresAt: Long,
    /**
     * "TAP": a time extension from the block screen (expiresAt is when it ends). "PHRASE" and
     * "WAIT" are the same, from before v7, when it took a typed phrase or a 30 s wait.
     * "SWIPES": +20 swipes from the swipe-limit cover (expiresAt equals grantedAt; it lasts until
     * the swipe window ends).
     */
    val method: String,
)
