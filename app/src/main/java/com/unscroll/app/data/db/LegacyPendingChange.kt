package com.unscroll.app.data.db

import com.unscroll.app.domain.blocking.BlockSchedule
import com.unscroll.app.domain.blocking.LimitSettings
import com.unscroll.app.domain.blocking.SwipeLimitRules
import com.unscroll.app.domain.blocking.SwipeLimitScope

/**
 * Reads the `pendingChangeJson` column of `app_limits` before v7, which held a loosening change
 * waiting out its cooldown. Only [MIGRATION_6_7] uses it, to apply such a change before the column
 * is dropped. JSON written before the swipe-limit fields decodes with their defaults.
 */
internal object LegacyPendingChange {

    /** Returns null for anything it can't read, so a corrupt value never breaks the migration. */
    fun decode(json: String?): LimitSettings? {
        if (json.isNullOrBlank()) return null
        val values = FIELD.findAll(json).associate { it.groupValues[1] to it.groupValues[2] }
        return try {
            LimitSettings(
                dailyLimitMinutes = values["dailyLimitMinutes"]?.takeIf { it != "null" }?.toInt(),
                blockedAlways = values.getValue("blockedAlways").toBooleanStrict(),
                schedule = BlockSchedule(
                    enabled = values.getValue("scheduleEnabled").toBooleanStrict(),
                    days = BlockSchedule.daysFromBitmask(values.getValue("scheduleDays").toInt()),
                    startMinute = values.getValue("scheduleStartMinute").toInt(),
                    endMinute = values.getValue("scheduleEndMinute").toInt(),
                ),
                swipeLimit = values["swipeLimit"]?.takeIf { it != "null" }?.toInt(),
                swipeLimitScope = if (values["swipeLimitPerSession"]?.toBooleanStrict() == true) {
                    SwipeLimitScope.SESSION
                } else {
                    SwipeLimitScope.DAY
                },
                swipeSessionGapMinutes = values["swipeSessionGapMinutes"]?.toInt()
                    ?: SwipeLimitRules.DEFAULT_SESSION_GAP_MINUTES,
                swipeAccessAllowed = values["swipeAccessAllowed"]?.toBooleanStrict() ?: false,
            )
        } catch (e: NoSuchElementException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private val FIELD = Regex("\"(\\w+)\"\\s*:\\s*(null|true|false|-?\\d+)")
}
