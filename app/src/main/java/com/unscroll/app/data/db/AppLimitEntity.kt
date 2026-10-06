package com.unscroll.app.data.db

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
)

/** One "I need access" extension granted from the block screen. */
@Entity(tableName = "block_overrides")
data class BlockOverrideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val grantedAt: Long,
    val expiresAt: Long,
    /** "PHRASE" or "WAIT": which friction the user passed. */
    val method: String,
)
