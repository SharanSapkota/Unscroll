package com.unscroll.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Query("UPDATE sessions SET endTime = :endTime WHERE id = :id")
    suspend fun close(id: Long, endTime: Long)

    /** Closes all open sessions at [endTime], but never before a session's own start. */
    @Query("UPDATE sessions SET endTime = MAX(startTime, :endTime) WHERE endTime IS NULL")
    suspend fun closeAllOpen(endTime: Long): Int

    @Query("SELECT * FROM sessions WHERE endTime IS NULL ORDER BY startTime")
    suspend fun getOpen(): List<SessionEntity>

    @Query("SELECT * FROM sessions ORDER BY startTime DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SessionEntity>>
}
