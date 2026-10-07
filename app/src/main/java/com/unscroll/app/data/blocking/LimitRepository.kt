package com.unscroll.app.data.blocking

import com.unscroll.app.data.db.AppLimitEntity
import com.unscroll.app.data.db.BlockOverrideEntity
import com.unscroll.app.data.db.BlockingDao
import com.unscroll.app.domain.blocking.AppLimit
import com.unscroll.app.domain.blocking.BlockSchedule
import com.unscroll.app.domain.blocking.LimitSettings
import com.unscroll.app.domain.blocking.SwipeLimitScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Per-app limits and block-screen extensions. Every change is written straight to the stored
 * settings and applies at once; there is no cooldown.
 */
@Singleton
class LimitRepository @Inject constructor(
    private val dao: BlockingDao,
) {
    fun observeLimits(): Flow<Map<String, AppLimit>> =
        dao.observeLimits().map { rows -> rows.associate { it.packageName to it.toDomain() } }

    suspend fun getLimit(packageName: String): AppLimit =
        dao.getLimit(packageName)?.toDomain() ?: AppLimit(packageName)

    /** Applies [edit] to the app's settings and saves the result right away. Returns it. */
    suspend fun updateLimit(packageName: String, edit: (LimitSettings) -> LimitSettings): AppLimit {
        val current = getLimit(packageName)
        val updated = current.copy(settings = edit(current.settings))
        dao.upsertLimit(updated.toEntity())
        return updated
    }

    /** "I need access" on the block screen: one tap, logged. */
    suspend fun grantExtension(packageName: String, now: Long, durationMillis: Long) {
        dao.insertOverride(
            BlockOverrideEntity(
                packageName = packageName,
                grantedAt = now,
                expiresAt = now + durationMillis,
                method = METHOD_TAP,
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
        const val METHOD_TAP = "TAP"
        const val METHOD_SWIPES = "SWIPES"
    }
}

internal fun AppLimitEntity.toDomain() = AppLimit(
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
)

internal fun AppLimit.toEntity() = AppLimitEntity(
    packageName = packageName,
    dailyLimitMinutes = settings.dailyLimitMinutes,
    blockedAlways = settings.blockedAlways,
    scheduleEnabled = settings.schedule.enabled,
    scheduleDays = settings.schedule.daysBitmask,
    scheduleStartMinute = settings.schedule.startMinute,
    scheduleEndMinute = settings.schedule.endMinute,
    swipeLimit = settings.swipeLimit,
    swipeLimitScope = settings.swipeLimitScope.name,
    swipeSessionGapMinutes = settings.swipeSessionGapMinutes,
    swipeAccessAllowed = settings.swipeAccessAllowed,
)
