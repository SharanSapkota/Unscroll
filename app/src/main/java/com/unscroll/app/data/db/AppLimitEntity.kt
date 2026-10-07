package com.unscroll.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Limits for one tracked app. A row only exists once the user has configured something. */
@Entity(tableName = "app_limits")
data class AppLimitEntity(
    @PrimaryKey val packageName: String,
    val dailyLimitMinutes: Int?,
    val blockedAlways: Boolean,
    val scheduleEnabled: Boolean,
    /** Monday is bit 0, Sunday bit 6. */
    val scheduleDays: Int,
    /** Minutes after local midnight. */
    val scheduleStartMinute: Int,
    val scheduleEndMinute: Int,
    /** A weaker change waiting out its cooldown, encoded by LimitSettingsCodec. */
    val pendingChangeJson: String?,
    val pendingChangeAppliesAt: Long?,
    /** v6: hard swipe limit, or null for none. */
    val swipeLimit: Int? = null,
    /** v6: "DAY" or "SESSION". */
    @ColumnInfo(defaultValue = "'DAY'") val swipeLimitScope: String = "DAY",
    @ColumnInfo(defaultValue = "30") val swipeSessionGapMinutes: Int = 30,
    @ColumnInfo(defaultValue = "0") val swipeAccessAllowed: Boolean = false,
)

/** One "I need access" extension granted from the block screen. */
@Entity(tableName = "block_overrides")
data class BlockOverrideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val grantedAt: Long,
    val expiresAt: Long,
    /**
     * "PHRASE" or "WAIT": a time extension from the block screen (expiresAt is when it ends).
     * "SWIPES": +20 swipes from the swipe-limit cover (expiresAt equals grantedAt; it lasts until
     * the swipe window ends).
     */
    val method: String,
)
