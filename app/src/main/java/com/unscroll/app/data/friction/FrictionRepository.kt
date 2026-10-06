package com.unscroll.app.data.friction

import com.unscroll.app.data.db.AppFrictionEntity
import com.unscroll.app.data.db.FrictionDao
import com.unscroll.app.data.db.NudgeLogEntity
import com.unscroll.app.data.db.PauseOutcomeEntity
import com.unscroll.app.domain.friction.FrictionSettings
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class PauseOutcome { CONTINUED, ABANDONED }

enum class NudgeKind { OPENS, LIMIT }

data class PauseStat(val packageName: String, val shown: Int, val abandoned: Int)

/** Per-app friction settings, the pause-outcome log and the once-a-day nudge log. */
@Singleton
class FrictionRepository @Inject constructor(
    private val dao: FrictionDao,
) {
    /** Only apps the user changed; everything else uses [FrictionSettings.DEFAULT]. */
    fun observeSettings(): Flow<Map<String, FrictionSettings>> =
        dao.observeSettings().map { rows -> rows.associate { it.packageName to it.toDomain() } }

    suspend fun getSettings(packageName: String): FrictionSettings =
        dao.getSettings(packageName)?.toDomain() ?: FrictionSettings.DEFAULT

    suspend fun saveSettings(packageName: String, settings: FrictionSettings) {
        dao.upsertSettings(settings.normalized().toEntity(packageName))
    }

    suspend fun resetToDefaults(packageName: String) = dao.deleteSettings(packageName)

    suspend fun logPauseOutcome(packageName: String, shownAt: Long, outcome: PauseOutcome) {
        dao.insertPauseOutcome(PauseOutcomeEntity(packageName = packageName, shownAt = shownAt, outcome = outcome.name))
    }

    fun observePauseStatsSince(since: Long): Flow<List<PauseStat>> =
        dao.observePauseStatsSince(since).map { rows ->
            rows.map { PauseStat(it.packageName, it.shown, it.abandoned) }
        }

    suspend fun sentNudges(packageName: String, day: LocalDate, kind: NudgeKind): Set<Int> =
        dao.sentNudges(packageName, day.toString(), kind.name).toSet()

    suspend fun recordNudges(packageName: String, day: LocalDate, kind: NudgeKind, values: List<Int>, now: Long) {
        dao.insertNudges(values.map { NudgeLogEntity(packageName, day.toString(), kind.name, it, now) })
    }
}

private fun AppFrictionEntity.toDomain() = FrictionSettings(
    pauseEnabled = pauseEnabled,
    pauseSeconds = pauseSeconds,
    nudgesEnabled = nudgesEnabled,
    nudgeThresholds = FrictionSettings.decodeThresholds(nudgeThresholds),
    breakRemindersEnabled = breakRemindersEnabled,
    breakIntervalMinutes = breakIntervalMinutes,
    limitWarningsEnabled = limitWarningsEnabled,
    tintEnabled = tintEnabled,
    swipeBreakAfter = swipeBreakAfter,
).normalized()

private fun FrictionSettings.toEntity(packageName: String) = AppFrictionEntity(
    packageName = packageName,
    pauseEnabled = pauseEnabled,
    pauseSeconds = pauseSeconds,
    nudgesEnabled = nudgesEnabled,
    nudgeThresholds = FrictionSettings.encodeThresholds(nudgeThresholds),
    breakRemindersEnabled = breakRemindersEnabled,
    breakIntervalMinutes = breakIntervalMinutes,
    limitWarningsEnabled = limitWarningsEnabled,
    tintEnabled = tintEnabled,
    swipeBreakAfter = swipeBreakAfter,
)
