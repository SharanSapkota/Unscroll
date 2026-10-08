package com.unscroll.app.ui.home

import com.unscroll.app.domain.blocking.AppLimit
import com.unscroll.app.domain.insights.PeriodUsage
import com.unscroll.app.domain.insights.UsagePeriod
import com.unscroll.app.domain.scroll.ScrollStats

/** One tracked app on Home. */
data class AppTileState(
    val packageName: String,
    /** Time in the selected period. */
    val millis: Long,
    /** Swipes in the selected period, or null when scroll counting was never on. */
    val swipes: Int?,
    /** Share of today's daily limit used, or null without a limit or outside the Day view. */
    val limitProgress: Float?,
    val blocked: Boolean,
)

/** Builds the Home tiles: every installed tracked app, the most used first. Pure. */
object HomeTiles {

    fun build(
        packages: List<String>,
        usage: PeriodUsage?,
        scroll: ScrollStats?,
        limits: Map<String, AppLimit>,
    ): List<AppTileState> {
        val millisByApp = usage?.apps?.associate { it.packageName to it.usage.totalMillis }.orEmpty()
        val swipesByApp = scroll?.apps?.associate { it.packageName to it.swipes }.orEmpty()
        val isDay = usage?.period == UsagePeriod.TODAY
        return packages
            .mapIndexed { index, packageName ->
                val millis = millisByApp[packageName] ?: 0L
                val settings = limits[packageName]?.settings
                val limitMinutes = settings?.dailyLimitMinutes
                index to AppTileState(
                    packageName = packageName,
                    millis = millis,
                    swipes = scroll?.let { swipesByApp[packageName] ?: 0 },
                    limitProgress = if (isDay && limitMinutes != null && limitMinutes > 0) {
                        millis.toFloat() / (limitMinutes * MINUTE)
                    } else {
                        null
                    },
                    blocked = settings?.blockedAlways == true,
                )
            }
            // Most used first; ties keep the tracked list order.
            .sortedWith(compareByDescending<Pair<Int, AppTileState>> { it.second.millis }.thenBy { it.first })
            .map { it.second }
    }

    private const val MINUTE = 60_000L
}
