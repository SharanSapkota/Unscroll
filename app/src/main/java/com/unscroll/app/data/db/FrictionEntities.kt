package com.unscroll.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Per-app nudge and friction settings. A row only exists once the user changes a default. The
 * pause-screen columns were dropped in v5 (no pause screen: apps open instantly, the live timer
 * is the stopper).
 */
@Entity(tableName = "app_friction")
data class AppFrictionEntity(
    @PrimaryKey val packageName: String,
    val nudgesEnabled: Boolean,
    /** "5,10,20" */
    val nudgeThresholds: String,
    val breakRemindersEnabled: Boolean,
    val breakIntervalMinutes: Int,
    val limitWarningsEnabled: Boolean,
    val tintEnabled: Boolean,
    /** Added in v4 (M7). Null = no swipe breaks. */
    val swipeBreakAfter: Int? = null,
)

/** A once-a-day nudge that was sent, so it is never sent twice ([kind] "OPENS" or "LIMIT"). */
@Entity(tableName = "nudge_log", primaryKeys = ["packageName", "day", "kind", "value"])
data class NudgeLogEntity(
    val packageName: String,
    /** Local date, "2026-10-06". */
    val day: String,
    val kind: String,
    val value: Int,
    val sentAt: Long,
)
