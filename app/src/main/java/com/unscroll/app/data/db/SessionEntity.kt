package com.unscroll.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.unscroll.app.domain.session.Session

@Entity(
    tableName = "sessions",
    indices = [Index("startTime"), Index("endTime")],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val startTime: Long,
    val endTime: Long? = null,
    @ColumnInfo(defaultValue = "0") val scrollCount: Int = 0,
)

fun SessionEntity.toSession() = Session(
    id = id,
    packageName = packageName,
    startTime = startTime,
    endTime = endTime,
    scrollCount = scrollCount,
)
