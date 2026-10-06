package com.unscroll.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FrictionDao {

    @Query("SELECT * FROM app_friction")
    fun observeSettings(): Flow<List<AppFrictionEntity>>

    @Query("SELECT * FROM app_friction WHERE packageName = :packageName")
    suspend fun getSettings(packageName: String): AppFrictionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSettings(settings: AppFrictionEntity)

    @Query("DELETE FROM app_friction WHERE packageName = :packageName")
    suspend fun deleteSettings(packageName: String)

    @Insert
    suspend fun insertPauseOutcome(outcome: PauseOutcomeEntity): Long

    /** Pause screens per app since [since], and how many of them the user walked away from. */
    @Query(
        """
        SELECT packageName,
            COUNT(*) AS shown,
            SUM(CASE WHEN outcome = 'ABANDONED' THEN 1 ELSE 0 END) AS abandoned
        FROM pause_outcomes
        WHERE shownAt >= :since
        GROUP BY packageName
        ORDER BY abandoned DESC, packageName
        """,
    )
    fun observePauseStatsSince(since: Long): Flow<List<PauseStatRow>>

    @Query("DELETE FROM pause_outcomes")
    suspend fun deleteAllPauseOutcomes()

    @Query("DELETE FROM nudge_log")
    suspend fun deleteAllNudges()

    @Query("SELECT value FROM nudge_log WHERE packageName = :packageName AND day = :day AND kind = :kind")
    suspend fun sentNudges(packageName: String, day: String, kind: String): List<Int>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNudges(nudges: List<NudgeLogEntity>)
}
