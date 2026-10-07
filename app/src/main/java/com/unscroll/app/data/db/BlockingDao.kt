package com.unscroll.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockingDao {

    @Query("SELECT * FROM app_limits")
    fun observeLimits(): Flow<List<AppLimitEntity>>

    @Query("SELECT * FROM app_limits WHERE packageName = :packageName")
    suspend fun getLimit(packageName: String): AppLimitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLimit(limit: AppLimitEntity)

    /** Limits whose pending change is due at [now]. */
    @Query("SELECT * FROM app_limits WHERE pendingChangeAppliesAt IS NOT NULL AND pendingChangeAppliesAt <= :now")
    suspend fun getDuePendingChanges(now: Long): List<AppLimitEntity>

    @Insert
    suspend fun insertOverride(override: BlockOverrideEntity): Long

    /** When the latest still-running time extension for the app ends, or null. Swipe extensions don't count. */
    @Query(
        "SELECT MAX(expiresAt) FROM block_overrides " +
            "WHERE packageName = :packageName AND expiresAt > :now AND method != 'SWIPES'",
    )
    suspend fun activeOverrideUntil(packageName: String, now: Long): Long?

    /** "I need access" swipe extensions for the app since [since]. */
    @Query("SELECT COUNT(*) FROM block_overrides WHERE packageName = :packageName AND method = 'SWIPES' AND grantedAt >= :since")
    suspend fun swipeExtensionsSince(packageName: String, since: Long): Int

    @Query("DELETE FROM block_overrides")
    suspend fun deleteAllOverrides()

    @Query("SELECT * FROM block_overrides WHERE grantedAt >= :since ORDER BY grantedAt DESC")
    fun observeOverridesSince(since: Long): Flow<List<BlockOverrideEntity>>
}
