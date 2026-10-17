package com.unscroll.app.data.blocking

import com.unscroll.app.data.db.AppLimitEntity
import com.unscroll.app.data.db.BlockOverrideEntity
import com.unscroll.app.data.db.BlockingDao
import com.unscroll.app.domain.blocking.AppLimit
import com.unscroll.app.domain.blocking.BlockDuration
import com.unscroll.app.domain.blocking.BlockSchedule
import com.unscroll.app.domain.blocking.LimitDefaults
import com.unscroll.app.domain.blocking.QuickBlockRules
import com.unscroll.app.domain.blocking.QuickBlockTarget
import com.unscroll.app.domain.blocking.TimedBlock
import com.unscroll.app.domain.blocking.LimitSettings
import com.unscroll.app.domain.blocking.SwipeLimitScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Per-app limits and block-screen extensions. Every change is written straight to the stored
 * settings and applies at once; there is no cooldown.
 */
@Singleton
class LimitRepository @Inject constructor(
    private val dao: BlockingDao,
) {
    private val _extensionChanges = MutableStateFlow(0)

    /** Bumped on every "I need access" extension, so the enforcers re-check at once. */
    val extensionChanges: StateFlow<Int> = _extensionChanges.asStateFlow()

    fun observeLimits(): Flow<Map<String, AppLimit>> =
        dao.observeLimits().map { rows -> rows.associate { it.packageName to it.toDomain() } }

    suspend fun getLimit(packageName: String): AppLimit =
        dao.getLimit(packageName)?.toDomain() ?: AppLimit(packageName)

    /**
     * Stores [LimitDefaults.NEW_APP] (time limit on at 30 minutes) for [packageName] unless it
     * already has stored limits: a newly tracked app gets the defaults, an existing user's saved
     * values (even "no limit") are never touched.
     */
    suspend fun insertDefaultsIfAbsent(packageName: String) {
        dao.insertLimitIfAbsent(AppLimit(packageName, LimitDefaults.NEW_APP).toEntity())
    }

    /** Applies [edit] to the app's settings and saves the result right away. Returns it. */
    suspend fun updateLimit(packageName: String, edit: (LimitSettings) -> LimitSettings): AppLimit {
        val current = getLimit(packageName)
        val updated = current.copy(settings = edit(current.settings))
        dao.upsertLimit(updated.toEntity())
        return updated
    }

    /** A quick toggle on or off ("Block entire app", "Block reels only"), for its remembered duration. */
    suspend fun setQuickBlock(packageName: String, target: QuickBlockTarget, on: Boolean, now: Long): AppLimit =
        updateLimit(packageName) { QuickBlockRules.setOn(it, target, on, now) }

    /** A duration chip: remembered, and restarts the block if it is on. */
    suspend fun setQuickBlockDuration(
        packageName: String,
        target: QuickBlockTarget,
        duration: BlockDuration,
        now: Long,
    ): AppLimit = updateLimit(packageName) { QuickBlockRules.setDuration(it, target, duration, now) }

    /**
     * Turns off every quick block that ran out (the toggle turns itself off) and returns when the
     * next one ends, or null. Blocks already end on time without this (the evaluator compares
     * with the clock); it only keeps the stored state and the UI tidy.
     */
    suspend fun clearExpiredBlocks(now: Long): Long? {
        var next: Long? = null
        dao.getAllLimits().forEach { entity ->
            val limit = entity.toDomain()
            val cleared = QuickBlockRules.clearExpired(limit.settings, now)
            if (cleared != limit.settings) dao.upsertLimit(limit.copy(settings = cleared).toEntity())
            QuickBlockRules.nextExpiry(cleared, now)?.let { next = minOf(next ?: it, it) }
        }
        return next
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
        _extensionChanges.update { it + 1 }
    }

    suspend fun activeExtensionUntil(packageName: String, now: Long): Long? =
        dao.activeOverrideUntil(packageName, now)

    /** "I need access" on the swipe-limit cover: logged like time extensions, worth 20 swipes. */
    suspend fun grantSwipeExtension(packageName: String, now: Long) {
        dao.insertOverride(
            BlockOverrideEntity(packageName = packageName, grantedAt = now, expiresAt = now, method = METHOD_SWIPES),
        )
        _extensionChanges.update { it + 1 }
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
        entireAppBlockedUntil = entireAppBlockedUntil,
        reelsBlockedUntil = reelsBlockedUntil,
        lastEntireDuration = BlockDuration.fromMinutes(lastEntireDuration),
        lastReelsDuration = BlockDuration.fromMinutes(lastReelsDuration),
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
    blockedAlways = settings.entireAppBlockedUntil == TimedBlock.FOREVER,
    scheduleEnabled = settings.schedule.enabled,
    scheduleDays = settings.schedule.daysBitmask,
    scheduleStartMinute = settings.schedule.startMinute,
    scheduleEndMinute = settings.schedule.endMinute,
    swipeLimit = settings.swipeLimit,
    swipeLimitScope = settings.swipeLimitScope.name,
    swipeSessionGapMinutes = settings.swipeSessionGapMinutes,
    swipeAccessAllowed = settings.swipeAccessAllowed,
    entireAppBlockedUntil = settings.entireAppBlockedUntil,
    reelsBlockedUntil = settings.reelsBlockedUntil,
    lastEntireDuration = settings.lastEntireDuration.minutes,
    lastReelsDuration = settings.lastReelsDuration.minutes,
)
