package com.unscroll.app.service

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.unscroll.app.data.blocking.LimitRepository
import com.unscroll.app.data.reels.ReelsUsageRepository
import com.unscroll.app.domain.apps.ExcludedApps
import com.unscroll.app.domain.blocking.BlockDecision
import com.unscroll.app.domain.blocking.BlockEvaluator
import com.unscroll.app.domain.blocking.BlockReason
import com.unscroll.app.domain.blocking.CoverRequests
import com.unscroll.app.domain.insights.TimeRange
import com.unscroll.app.domain.insights.UsageDataSource
import com.unscroll.app.domain.insights.localDate
import com.unscroll.app.domain.insights.startOfDay
import com.unscroll.app.domain.overlay.CoverReason
import com.unscroll.app.domain.reels.ReelsLimitScope
import com.unscroll.app.domain.time.Clock
import com.unscroll.app.ui.block.BlockActivity
import com.unscroll.app.util.AppLog
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

/**
 * Enforces the time-based rules (daily time limit, schedule, "Block entire app"). Runs inside
 * TrackingService.
 *
 * While a tracked app is in the foreground it re-evaluates right when the daily limit or an
 * extension runs out (and at least every [MAX_CHECK_MILLIS], for schedules), so a user who is
 * already scrolling gets blocked the moment the limit is reached. Limit changes apply at once:
 * every change (and every "I need access" extension) restarts the evaluation.
 *
 * - Every app but Instagram/TikTok, as before: the block screen ([BlockActivity]) on top of Home.
 * - Instagram/TikTok ([ReelsLimitScope]): the time limit counts Reels time only, and it and the
 *   schedule only ask for the cover ([CoverRequests]), which OverlayCoordinator shows over Reels
 *   only; chat and every other screen stay usable. "Block entire app" still opens the block screen.
 */
@Singleton
class BlockEnforcer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val excludedApps: ExcludedApps,
    private val sessionManager: SessionManager,
    private val limits: LimitRepository,
    private val usage: UsageDataSource,
    private val reelsUsage: ReelsUsageRepository,
    private val evaluator: BlockEvaluator,
    private val coverRequests: CoverRequests,
    private val clock: Clock,
) {
    suspend fun run() {
        var requested: String? = null
        try {
            combine(sessionManager.foregroundSession, limits.observeLimits(), limits.extensionChanges) { session, _, _ -> session }
                .collectLatest { session ->
                    val packageName = session?.packageName
                    // Left the app (or it changed): its time-limit and schedule requests go.
                    requested?.takeIf { it != packageName }?.let { clearRequests(it) }
                    requested = null
                    if (packageName == null || excludedApps.isExcluded(packageName)) return@collectLatest
                    if (ReelsLimitScope.reelsOnly(packageName)) requested = packageName
                    while (true) delay(enforce(packageName))
                }
        } finally {
            withContext(NonCancellable) { requested?.let { clearRequests(it) } }
        }
    }

    /** Applies the current decision for [packageName]; returns when to check again. */
    private suspend fun enforce(packageName: String): Long {
        val decision = decide(packageName)
        if (ReelsLimitScope.reelsOnly(packageName)) {
            val reason = (decision as? BlockDecision.Blocked)?.reason
            if (reason == BlockReason.BLOCKED_ENTIRE_APP) {
                clearRequests(packageName)
                showBlockScreen(packageName, decision)
                return RETRY_MILLIS
            }
            coverRequests.set(packageName, CoverReason.TIME_LIMIT, holds = reason == BlockReason.DAILY_LIMIT_REACHED)
            coverRequests.set(packageName, CoverReason.SCHEDULE, holds = reason == BlockReason.INSIDE_SCHEDULE)
            return nextCheck(decision)
        }
        if (decision is BlockDecision.Blocked) {
            showBlockScreen(packageName, decision)
            // Normally the block screen takes the foreground and this is cancelled. If Android
            // refused to open it, try again shortly.
            return RETRY_MILLIS
        }
        return nextCheck(decision)
    }

    private fun nextCheck(decision: BlockDecision): Long = when (decision) {
        is BlockDecision.Allowed -> decision.remainingMillis?.coerceIn(MIN_CHECK_MILLIS, MAX_CHECK_MILLIS) ?: MAX_CHECK_MILLIS
        is BlockDecision.Blocked -> decision.until?.let { (it - clock.now()).coerceIn(MIN_CHECK_MILLIS, MAX_CHECK_MILLIS) } ?: MAX_CHECK_MILLIS
    }

    private fun clearRequests(packageName: String) {
        coverRequests.set(packageName, CoverReason.TIME_LIMIT, holds = false)
        coverRequests.set(packageName, CoverReason.SCHEDULE, holds = false)
    }

    /** Current decision for [packageName], from its stored settings (Reels time for Instagram/TikTok). */
    suspend fun decide(packageName: String): BlockDecision {
        val now = clock.now()
        val limit = limits.getLimit(packageName)
        if (!limit.settings.hasAnyRule) return BlockDecision.Allowed(remainingMillis = null)
        return evaluator.evaluate(
            settings = limit.settings,
            usedTodayMillis = usedTodayMillis(packageName, now),
            extensionUntil = limits.activeExtensionUntil(packageName, now),
            now = now,
            packageName = packageName,
        )
    }

    /**
     * Today's time that counts toward [packageName]'s time limit: in Reels / For You for
     * Instagram and TikTok, in the whole app for every other app.
     */
    suspend fun usedTodayMillis(packageName: String, now: Long): Long {
        val zone = ZoneId.systemDefault()
        if (ReelsLimitScope.reelsOnly(packageName)) return reelsUsage.today(now, zone)[packageName]?.millis ?: 0L
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
            AppLog.w(TAG, "Could not open the block screen", e)
        } catch (e: SecurityException) {
            AppLog.w(TAG, "Could not open the block screen", e)
        } catch (e: IllegalStateException) {
            AppLog.w(TAG, "Could not open the block screen", e)
        }
    }

    private companion object {
        const val TAG = "BlockEnforcer"
        const val MIN_CHECK_MILLIS = 1_000L
        const val MAX_CHECK_MILLIS = 15_000L
        const val RETRY_MILLIS = 3_000L
    }
}
