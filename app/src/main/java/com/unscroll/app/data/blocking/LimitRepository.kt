package com.unscroll.app.data.blocking

import com.unscroll.app.data.db.AppLimitEntity
import com.unscroll.app.data.db.BlockOverrideEntity
import com.unscroll.app.data.db.BlockingDao
import com.unscroll.app.domain.blocking.AppLimit
import com.unscroll.app.domain.blocking.BlockSchedule
import com.unscroll.app.domain.blocking.FrictionMode
import com.unscroll.app.domain.blocking.LimitChangePolicy
import com.unscroll.app.domain.blocking.LimitSettings
import com.unscroll.app.domain.blocking.LimitSettingsCodec
import com.unscroll.app.domain.blocking.PendingChange
import com.unscroll.app.domain.blocking.SwipeLimitScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Per-app limits and block-screen extensions.
 *
 * Pending changes are applied "on read": [getLimit] and [applyDueChanges] write a change through
 * as soon as its cooldown is over, and both the service and the Apps screen call them, so a
 * change takes effect on time even if the app was closed in between.
 */
@Singleton
class LimitRepository @Inject constructor(
    private val dao: BlockingDao,
) {
    /** Raw stored limits; callers resolve pending changes against their own clock. */
    fun observeLimits(): Flow<Map<String, AppLimit>> =
        dao.observeLimits().map { rows -> rows.associate { it.packageName to it.toDomain() } }

    /** The app's limit with any due pending change applied (and saved). */
    suspend fun getLimit(packageName: String, now: Long): AppLimit {
        val stored = dao.getLimit(packageName)?.toDomain() ?: return AppLimit(packageName)
        val resolved = LimitChangePolicy.resolve(stored, now)
        if (resolved != stored) dao.upsertLimit(resolved.toEntity())
        return resolved
    }

    suspend fun applyDueChanges(now: Long) {
        dao.getDuePendingChanges(now).forEach { row ->
            dao.upsertLimit(LimitChangePolicy.resolve(row.toDomain(), now).toEntity())
        }
    }

    /** Stronger changes apply now; weaker ones wait [delayMillis]. Returns the stored result. */
    suspend fun requestChange(
        packageName: String,
        requested: LimitSettings,
        now: Long,
        delayMillis: Long,
    ): AppLimit {
        val updated = LimitChangePolicy.request(getLimit(packageName, now), requested, now, delayMillis)
        dao.upsertLimit(updated.toEntity())
        return updated
    }

    /**
     * An edit from the Apps screen, applied to what the screen shows (the pending target, if any).
     * See [LimitChangePolicy.edit]. Returns the stored result.
     */
    suspend fun editLimit(
        packageName: String,
        edit: (LimitSettings) -> LimitSettings,
        now: Long,
        delayMillis: Long,
    ): AppLimit {
        val updated = LimitChangePolicy.edit(getLimit(packageName, now), edit, now, delayMillis)
        dao.upsertLimit(updated.toEntity())
        return updated
    }

    suspend fun cancelPendingChange(packageName: String, now: Long) {
        dao.upsertLimit(LimitChangePolicy.cancel(getLimit(packageName, now)).toEntity())
    }

    /** After the user typed the unlock phrase. */
    suspend fun applyPendingChangeNow(packageName: String, now: Long) {
        dao.upsertLimit(LimitChangePolicy.applyNow(getLimit(packageName, now)).toEntity())
    }

    suspend fun grantExtension(packageName: String, now: Long, durationMillis: Long, method: FrictionMode) {
        dao.insertOverride(
            BlockOverrideEntity(
                packageName = packageName,
                grantedAt = now,
                expiresAt = now + durationMillis,
                method = if (method == FrictionMode.TYPE_PHRASE) METHOD_PHRASE else METHOD_WAIT,
            ),
        )
    }

    suspend fun activeExtensionUntil(packageName: String, now: Long): Long? =
        dao.activeOverrideUntil(packageName, now)

    /** "I need access" on the swipe-limit cover: logged like time extensions, worth 20 swipes. */
    suspend fun grantSwipeExtension(packageName: String, now: Long) {
        dao.insertOverride(
            BlockOverrideEntity(packageName = packageName, grantedAt = now, expiresAt = now, method = METHOD_SWIPES),
        )
    }

    suspend fun swipeExtensionsSince(packageName: String, since: Long): Int =
        dao.swipeExtensionsSince(packageName, since)

    private companion object {
        const val METHOD_PHRASE = "PHRASE"
        const val METHOD_WAIT = "WAIT"
        const val METHOD_SWIPES = "SWIPES"
    }
}

internal fun AppLimitEntity.toDomain(): AppLimit {
    val pendingSettings = LimitSettingsCodec.decode(pendingChangeJson)
    return AppLimit(
        packageName = packageName,
        settings = LimitSettings(
            dailyLimitMinutes = dailyLimitMinutes,
            blockedAlways = blockedAlways,
            schedule = BlockSchedule(
                enabled = scheduleEnabled,
                days = BlockSchedule.daysFromBitmask(scheduleDays),
                startMinute = scheduleStartMinute,
                endMinute = scheduleEndMinute,
            ),
            swipeLimit = swipeLimit,
            swipeLimitScope = SwipeLimitScope.entries.firstOrNull { it.name == swipeLimitScope } ?: SwipeLimitScope.DAY,
            swipeSessionGapMinutes = swipeSessionGapMinutes,
            swipeAccessAllowed = swipeAccessAllowed,
        ),
        pending = if (pendingSettings != null && pendingChangeAppliesAt != null) {
            PendingChange(pendingSettings, pendingChangeAppliesAt)
        } else {
            null
        },
    )
}

internal fun AppLimit.toEntity() = AppLimitEntity(
    packageName = packageName,
    dailyLimitMinutes = settings.dailyLimitMinutes,
    blockedAlways = settings.blockedAlways,
    scheduleEnabled = settings.schedule.enabled,
    scheduleDays = settings.schedule.daysBitmask,
    scheduleStartMinute = settings.schedule.startMinute,
    scheduleEndMinute = settings.schedule.endMinute,
    pendingChangeJson = pending?.let { LimitSettingsCodec.encode(it.settings) },
    pendingChangeAppliesAt = pending?.appliesAt,
    swipeLimit = settings.swipeLimit,
    swipeLimitScope = settings.swipeLimitScope.name,
    swipeSessionGapMinutes = settings.swipeSessionGapMinutes,
    swipeAccessAllowed = settings.swipeAccessAllowed,
)
