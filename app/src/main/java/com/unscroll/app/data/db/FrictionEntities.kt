package com.unscroll.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Per-app pause screen and nudge settings. A row only exists once the user changes a default. */
@Entity(tableName = "app_friction")
data class AppFrictionEntity(
    @PrimaryKey val packageName: String,
    val pauseEnabled: Boolean,
    val pauseSeconds: Int,
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

/** One pause screen and what the user did: "CONTINUED" or "ABANDONED". */
@Entity(tableName = "pause_outcomes", indices = [Index("shownAt")])
data class PauseOutcomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val shownAt: Long,
    val outcome: String,
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

/** Result row: pause screens shown and skipped for one app. */
data class PauseStatRow(
    val packageName: String,
    val shown: Int,
    val abandoned: Int,
)
