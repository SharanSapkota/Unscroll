package com.unscroll.app.service

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.unscroll.app.data.blocking.LimitRepository
import com.unscroll.app.data.friction.FrictionRepository
import com.unscroll.app.data.friction.NudgeKind
import com.unscroll.app.data.friction.QuietHoursPreferences
import com.unscroll.app.domain.blocking.BlockDecision
import com.unscroll.app.domain.blocking.BlockSafety
import com.unscroll.app.domain.friction.BreakReminders
import com.unscroll.app.domain.friction.NudgeRules
import com.unscroll.app.domain.insights.TimeRange
import com.unscroll.app.domain.insights.UsageDataSource
import com.unscroll.app.domain.insights.localDate
import com.unscroll.app.domain.insights.startOfDay
import com.unscroll.app.domain.scroll.SwipeBreakTracker
import com.unscroll.app.domain.session.ActiveSession
import com.unscroll.app.domain.time.Clock
import com.unscroll.app.overlay.OverlayMessages
import com.unscroll.app.overlay.OverlayTimerManager
import com.unscroll.app.overlay.PillMessage
import com.unscroll.app.overlay.PillMessageKind
import com.unscroll.app.overlay.TintOverlay
import com.unscroll.app.ui.scroll.BreakActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Friction and nudges (M6), run by TrackingService next to the overlay and the block enforcer:
 * - open-count notifications (once per threshold per day),
 * - break reminders on the pill (or a heads-up notification without the overlay),
 * - 80 % / 100 % limit warnings,
 * - the experimental gray tint after the limit while an extension runs,
 * - the "take a break" screen after N swipes, when scroll counting is on (M7).
 * Quiet hours silence the nudges, reminders and warnings, never the break screen or blocking.
 */
@Singleton
class FrictionCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val blockEnforcer: BlockEnforcer,
    private val friction: FrictionRepository,
    private val limits: LimitRepository,
    private val usage: UsageDataSource,
    private val quietHours: QuietHoursPreferences,
    private val overlay: OverlayTimerManager,
    private val messages: OverlayMessages,
    private val tint: TintOverlay,
    private val notifier: NudgeNotifier,
    private val clock: Clock,
) {
    val swipeBreaks = SwipeBreakTracker()

    /** Break reminders already shown, per session id. */
    private val breaksShown = mutableMapOf<Long, Int>()
    private var nextMessageId = 1L

    suspend fun run() = coroutineScope {
        try {
            launch { watchOpenCounts() }
            launch { watchSwipeBreaks() }
            watchForegroundSession()
        } finally {
            withContext(Dispatchers.Main.immediate + NonCancellable) {
                tint.hide()
                messages.dismiss()
            }
        }
    }

    /** Removes the tint and any pill message right away. Main thread; used by onDestroy. */
    fun hideNow() {
        tint.hide()
        messages.dismiss()
    }

    // --- Take a break after N swipes (M7) --------------------------------------------------------

    private suspend fun watchSwipeBreaks() {
        // Swipes only arrive while the accessibility service counts them; otherwise this is idle.
        sessionManager.swipes.filterNotNull().collect { swipes ->
            if (swipes.count == 0 || !isSafe(swipes.packageName)) return@collect
            val settings = friction.getSettings(swipes.packageName)
            if (!swipeBreaks.onSwipeCount(swipes.sessionId, swipes.count, settings.swipeBreakAfter)) return@collect
            // The block screen wins.
            if (blockEnforcer.decide(swipes.packageName) is BlockDecision.Blocked) return@collect
            val session = sessionManager.currentSession.value?.takeIf { it.id == swipes.sessionId } ?: return@collect
            showBreakScreen(swipes.packageName, swipes.sessionId, swipes.count, clock.now() - session.startTime)
        }
    }

    /** "Keep scrolling" on the break screen: the next break comes after another N swipes. */
    fun onSwipeBreakKeepScrolling(packageName: String, sessionId: Long, swipes: Int) {
        swipeBreaks.onKeepScrolling(sessionId, swipes)
    }

    private fun showBreakScreen(packageName: String, sessionId: Long, swipes: Int, sessionMillis: Long) {
        // Like the block screen: home first, so the app isn't left running underneath.
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val breakScreen = BreakActivity.intent(context, packageName, sessionId, swipes, sessionMillis)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        try {
            context.startActivities(arrayOf(home, breakScreen))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "Could not open the break screen", e)
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not open the break screen", e)
        }
    }

    // --- Open-count nudges ----------------------------------------------------------------------

    private suspend fun watchOpenCounts() {
        sessionManager.currentSession
            .map { it?.let { session -> session.id to session.packageName } }
            .distinctUntilChanged()
            .collect { opened ->
                val (_, packageName) = opened ?: return@collect
                if (isSafe(packageName)) checkOpenCount(packageName)
            }
    }

    private suspend fun checkOpenCount(packageName: String) {
        val now = clock.now()
        val zone = ZoneId.systemDefault()
        val settings = friction.getSettings(packageName)
        if (!settings.nudgesEnabled || quietHours.current().isQuiet(now, zone)) return
        val today = localDate(now, zone)
        val todayRange = TimeRange(startOfDay(today, zone), startOfDay(today.plusDays(1), zone))
        val opens = usage.appSessionStats(todayRange, now).firstOrNull { it.packageName == packageName }?.opens ?: 0
        val sent = friction.sentNudges(packageName, today, NudgeKind.OPENS)
        val due = NudgeRules.thresholdsToNotify(opens, settings.nudgeThresholds, sent)
        if (due.isEmpty()) return
        // One notification for the highest threshold reached; all reached ones are marked sent.
        friction.recordNudges(packageName, today, NudgeKind.OPENS, due, now)
        notifier.notifyOpens(packageName, due.max())
    }

    // --- Break reminders, limit warnings, tint --------------------------------------------------

    private suspend fun watchForegroundSession() {
        sessionManager.foregroundSession.collectLatest { session ->
            if (session == null || !isSafe(session.packageName)) {
                withContext(Dispatchers.Main.immediate) { tint.hide() }
                messages.dismiss()
                return@collectLatest
            }
            while (true) {
                val nextCheck = checkSession(session)
                delay((nextCheck - clock.now()).coerceIn(MIN_CHECK_MILLIS, MAX_CHECK_MILLIS))
            }
        }
    }

    /** Shows whatever is due for the session in front and returns when to check again. */
    private suspend fun checkSession(session: ActiveSession): Long {
        val now = clock.now()
        val zone = ZoneId.systemDefault()
        val packageName = session.packageName
        val settings = friction.getSettings(packageName)
        val quiet = quietHours.current().isQuiet(now, zone)
        var nextCheck = now + MAX_CHECK_MILLIS

        // Break reminders: every interval of the session, "Keep going" waits for the next one.
        if (settings.breakRemindersEnabled) {
            val interval = settings.breakIntervalMinutes * MINUTE
            val lastShown = breaksShown[session.id] ?: 0
            val due = BreakReminders.dueReminder(session.startTime, interval, now, lastShown)
            if (due != null) {
                breaksShown[session.id] = due
                if (!quiet) {
                    val minutes = ((now - session.startTime) / MINUTE).toInt()
                    if (overlay.canShowMessages()) {
                        messages.post(PillMessage(nextMessageId++, PillMessageKind.BREAK, packageName, minutes))
                    } else {
                        notifier.notifyBreak(packageName, minutes)
                    }
                }
            }
            nextCheck = minOf(nextCheck, BreakReminders.nextReminderAt(session.startTime, interval, breaksShown[session.id] ?: 0))
        }

        // Limit warnings and the tint need a daily limit.
        val limitMinutes = limits.getLimit(packageName, now).settings.dailyLimitMinutes
        if (limitMinutes == null) {
            withContext(Dispatchers.Main.immediate) { tint.hide() }
            return nextCheck
        }
        val today = localDate(now, zone)
        val used = usage.appTotals(TimeRange(startOfDay(today, zone), now), now)[packageName] ?: 0L

        if (settings.limitWarningsEnabled) {
            val sent = friction.sentNudges(packageName, today, NudgeKind.LIMIT)
            val due = NudgeRules.limitLevelsToWarn(used, limitMinutes, sent)
            if (due.isNotEmpty()) {
                friction.recordNudges(packageName, today, NudgeKind.LIMIT, due, now)
                if (!quiet) showLimitWarning(packageName, due.max())
            }
            NudgeRules.nextLimitWarningAt(used, limitMinutes, sent + due, now)?.let { nextCheck = minOf(nextCheck, it) }
        }

        // Tint: limit exceeded and the user chose to continue (an extension is running).
        val overLimit = used >= limitMinutes * MINUTE
        val extended = limits.activeExtensionUntil(packageName, now) != null
        withContext(Dispatchers.Main.immediate) {
            if (settings.tintEnabled && overLimit && extended) tint.show() else tint.hide()
        }
        return nextCheck
    }

    private suspend fun showLimitWarning(packageName: String, percent: Int) {
        if (!overlay.canShowMessages()) {
            notifier.notifyLimit(packageName, percent)
            return
        }
        val kind = if (percent >= NudgeRules.LIMIT_REACHED_PERCENT) {
            PillMessageKind.LIMIT_REACHED
        } else {
            PillMessageKind.LIMIT_WARNING
        }
        val message = PillMessage(nextMessageId++, kind, packageName)
        messages.post(message)
        coroutineScope {
            launch {
                delay(WARNING_VISIBLE_MILLIS)
                messages.dismiss(message.id)
            }
        }
    }

    // --- Safety ---------------------------------------------------------------------------------

    /** Never for non-tracked apps, Unscroll itself, launchers, Settings, the dialer or emergency apps. */
    private fun isSafe(packageName: String): Boolean =
        BlockSafety.canBlock(packageName, context.packageName, homePackages())

    private fun homePackages(): Set<String> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { it.activityInfo?.packageName }
            .toSet()
    }

    private companion object {
        const val TAG = "FrictionCoordinator"
        const val MINUTE = 60_000L
        const val MIN_CHECK_MILLIS = 1_000L
        const val MAX_CHECK_MILLIS = 15_000L
        const val WARNING_VISIBLE_MILLIS = 8_000L
    }
}
