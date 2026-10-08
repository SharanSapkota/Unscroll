package com.unscroll.app.service

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import com.unscroll.app.data.blocking.LimitRepository
import com.unscroll.app.domain.apps.ExcludedApps
import com.unscroll.app.domain.blocking.BlockDecision
import com.unscroll.app.domain.blocking.BlockEvaluator
import com.unscroll.app.domain.insights.TimeRange
import com.unscroll.app.domain.insights.UsageDataSource
import com.unscroll.app.domain.insights.localDate
import com.unscroll.app.domain.insights.startOfDay
import com.unscroll.app.domain.time.Clock
import com.unscroll.app.ui.block.BlockActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine

/**
 * Shows the block screen when the app in front is blocked. Runs inside TrackingService.
 *
 * While a tracked app is in the foreground it re-evaluates right when the daily limit or an
 * extension runs out (and at least every [MAX_CHECK_MILLIS], for schedules), so a user who is
 * already scrolling gets blocked the moment the limit is reached. Limit changes apply at once:
 * every change restarts the evaluation, so an app that was just unblocked is let through.
 */
@Singleton
class BlockEnforcer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val excludedApps: ExcludedApps,
    private val sessionManager: SessionManager,
    private val limits: LimitRepository,
    private val usage: UsageDataSource,
    private val evaluator: BlockEvaluator,
    private val clock: Clock,
) {
    suspend fun run() {
        combine(sessionManager.foregroundSession, limits.observeLimits()) { session, _ -> session }
            .collectLatest { session ->
                val packageName = session?.packageName ?: return@collectLatest
                if (excludedApps.isExcluded(packageName)) {
                    return@collectLatest
                }
                while (true) {
                    when (val decision = decide(packageName)) {
                        is BlockDecision.Blocked -> {
                            showBlockScreen(packageName, decision)
                            // Normally the block screen takes the foreground and this is cancelled.
                            // If Android refused to open it, try again shortly.
                            delay(RETRY_MILLIS)
                        }
                        is BlockDecision.Allowed -> delay(
                            decision.remainingMillis?.coerceIn(MIN_CHECK_MILLIS, MAX_CHECK_MILLIS)
                                ?: MAX_CHECK_MILLIS,
                        )
                    }
                }
            }
    }

    /** Current decision for [packageName], from its stored settings. */
    suspend fun decide(packageName: String): BlockDecision {
        val now = clock.now()
        val limit = limits.getLimit(packageName)
        if (!limit.settings.hasAnyRule) return BlockDecision.Allowed(remainingMillis = null)
        return evaluator.evaluate(
            settings = limit.settings,
            usedTodayMillis = usedToday(packageName, now),
            extensionUntil = limits.activeExtensionUntil(packageName, now),
            now = now,
            packageName = packageName,
        )
    }

    private suspend fun usedToday(packageName: String, now: Long): Long {
        val zone = ZoneId.systemDefault()
        val todayStart = startOfDay(localDate(now, zone), zone)
        return usage.appTotals(TimeRange(todayStart, now), now)[packageName] ?: 0L
    }

    /**
     * Sends the user home first, so the blocked app is stopped rather than left running
     * underneath, then opens the block screen in its own task on top. Also the swipe limit's
     * fallback when the overlay permission is missing.
     */
    fun showBlockScreen(packageName: String, decision: BlockDecision.Blocked) {
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val block = BlockActivity.intent(context, packageName, decision.reason, decision.until)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        try {
            context.startActivities(arrayOf(home, block))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Could not open the block screen", e)
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not open the block screen", e)
        }
    }


    private companion object {
        const val TAG = "BlockEnforcer"
        const val MIN_CHECK_MILLIS = 1_000L
        const val MAX_CHECK_MILLIS = 15_000L
        const val RETRY_MILLIS = 3_000L
    }
}
