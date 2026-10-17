package com.unscroll.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * v10: the part of a session spent in one section of an app (Reels-only timer, Instagram and
 * TikTok). Only written while the timer runs for the app; [section] is "REELS" or "OTHER". The
 * session itself (total time in the app) is unchanged, so every existing total stays as it was.
 * Only SessionManager writes these, like sessions.
 */
@Entity(
    tableName = "section_segments",
    indices = [Index("sessionId"), Index(value = ["packageName", "startTime"])],
)
data class SectionSegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val packageName: String,
    val startTime: Long,
    val endTime: Long? = null,
    val section: String,
    @ColumnInfo(defaultValue = "0") val swipes: Int = 0,
)

/** One app's time and swipes in a section within a range. */
data class SectionTotalRow(
    val packageName: String,
    val totalMillis: Long,
    val swipes: Int,
)

@Dao
interface SectionSegmentDao {

    @Insert
    suspend fun insert(segment: SectionSegmentEntity): Long

    @Query("UPDATE section_segments SET endTime = MAX(startTime, :endTime) WHERE id = :id")
    suspend fun close(id: Long, endTime: Long)

    @Query("UPDATE section_segments SET swipes = :swipes WHERE id = :id")
    suspend fun updateSwipes(id: Long, swipes: Int)

    /** Closes segments left open by a killed process at [endTime], never before their start. */
    @Query("UPDATE section_segments SET endTime = MAX(startTime, :endTime) WHERE endTime IS NULL")
    suspend fun closeAllOpen(endTime: Long): Int

    /**
     * Time per app in [section] inside [rangeStart, rangeEnd), clipped like sessions (a segment
     * that crosses midnight counts its part on each side; open ones run until [now]), and the
     * swipes of the segments that started in the range.
     */
    @Query(
        """
        SELECT packageName,
            SUM(MAX(0, MIN(COALESCE(endTime, :now), :rangeEnd) - MAX(startTime, :rangeStart))) AS totalMillis,
            SUM(CASE WHEN startTime >= :rangeStart THEN swipes ELSE 0 END) AS swipes
        FROM section_segments
        WHERE section = :section AND startTime < :rangeEnd AND COALESCE(endTime, :now) > :rangeStart
        GROUP BY packageName
        """,
    )
    suspend fun totals(section: String, rangeStart: Long, rangeEnd: Long, now: Long): List<SectionTotalRow>

    @Query("SELECT * FROM section_segments ORDER BY startTime, id")
    suspend fun getAll(): List<SectionSegmentEntity>

    @Query("DELETE FROM section_segments")
    suspend fun deleteAll(): Int
}
