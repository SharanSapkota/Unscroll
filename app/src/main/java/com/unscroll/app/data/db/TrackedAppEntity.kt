package com.unscroll.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * v8: the user's tracked apps, the single source of truth for what Unscroll tracks. Removing an
 * app only sets [removed]; its sessions, limits and settings live in their own tables and stay.
 */
@Entity(tableName = "tracked_apps")
data class TrackedAppEntity(
    @PrimaryKey val packageName: String,
    val cachedLabel: String?,
    val addedAt: Long,
    /** "ACTIVE" or "PAUSED". */
    @ColumnInfo(defaultValue = "'ACTIVE'") val status: String = "ACTIVE",
    @ColumnInfo(defaultValue = "1") val countSwipes: Boolean = true,
    @ColumnInfo(defaultValue = "0") val removed: Boolean = false,
)

@Dao
interface TrackedAppDao {
    /** Every row, removed ones included, in the order they were added. */
    @Query("SELECT * FROM tracked_apps ORDER BY addedAt, packageName")
    fun observeAll(): Flow<List<TrackedAppEntity>>

    @Query("SELECT * FROM tracked_apps WHERE packageName = :packageName")
    suspend fun get(packageName: String): TrackedAppEntity?

    /** Seeding: never overwrites a row, so a removed default stays removed. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(apps: List<TrackedAppEntity>)

    @Upsert
    suspend fun upsert(app: TrackedAppEntity)

    @Query("UPDATE tracked_apps SET removed = :removed WHERE packageName = :packageName")
    suspend fun setRemoved(packageName: String, removed: Boolean)

    @Query("UPDATE tracked_apps SET status = :status WHERE packageName = :packageName")
    suspend fun setStatus(packageName: String, status: String)

    @Query("UPDATE tracked_apps SET countSwipes = :countSwipes WHERE packageName = :packageName")
    suspend fun setCountSwipes(packageName: String, countSwipes: Boolean)

    @Query("UPDATE tracked_apps SET cachedLabel = :label WHERE packageName = :packageName")
    suspend fun setLabel(packageName: String, label: String)
}
