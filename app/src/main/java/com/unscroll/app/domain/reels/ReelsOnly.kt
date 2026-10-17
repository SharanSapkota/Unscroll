package com.unscroll.app.domain.reels

import com.unscroll.app.domain.blocking.LimitSettings
import com.unscroll.app.domain.section.ReelsCapableApps
import com.unscroll.app.domain.section.SectionVerdict
import com.unscroll.app.domain.scroll.ScrollConsent
import kotlinx.coroutines.flow.StateFlow

/**
 * The Reels-only timer and Reels gating: for Instagram and TikTok ([ReelsCapableApps]) the pill
 * counts only the time and swipes in Reels / For You, and covers, nudges and limit blocks only
 * ever appear there, never in chat or anywhere else in the app. One switch, separate from section
 * blocking ("Block reels only", SectionBlockingConfig), so either can be turned off alone.
 */
object ReelsOnlyPillConfig {
    /**
     * Master switch. False: nothing reads screens, and Instagram and TikTok are treated like every
     * other app (whole-app pill, whole-app limits).
     */
    const val ENABLED = true

    /** The apps it applies to: exactly the Reels-capable apps. */
    val PACKAGES: Set<String> = ReelsCapableApps.packages

    fun appliesTo(packageName: String): Boolean = ENABLED && packageName in PACKAGES
}

/**
 * "Timer visibility" (App detail, Instagram and TikTok only). In both modes the pill counts only
 * Reels time and swipes; they differ off Reels.
 */
enum class TimerVisibility {
    /** Default: the pill stays on every tab, dimmed and paused off Reels. */
    ALWAYS,

    /** The pill is invisible off Reels (its window stays, so it never flickers). */
    ONLY_REELS,
}

/** Where the app on screen stands for Reels gating; decides the pill's mode and whether covers may show. */
enum class SectionGate {
    /** Not a Reels-capable app (or the switch is off): whole-app pill, limits and covers as always. */
    WHOLE_APP,

    /** Reels / For You confirmed on screen: covers, nudges and limit blocks may show. */
    IN_REELS,

    /** Any other screen of the app (chat, home, search, profile, stories) or no reading yet. */
    OFF_REELS,

    /**
     * Detection can't be trusted (accessibility off, no consent, unsupported version, unreadable
     * screen). Fails open: the whole-app pill, and never a cover, nudge or block.
     */
    DETECTION_UNAVAILABLE,
    ;

    /** Covers, pill messages, the tint and the break screen may appear. */
    val allowsInterruptions: Boolean get() = this == WHOLE_APP || this == IN_REELS

    companion object {
        fun of(packageName: String, live: ReelsLive?, enabled: Boolean = ReelsOnlyPillConfig.ENABLED): SectionGate = when {
            !enabled || packageName !in ReelsOnlyPillConfig.PACKAGES -> WHOLE_APP
            live == null || live.packageName != packageName || !live.reelsOnly -> DETECTION_UNAVAILABLE
            live.state == ReelsState.IN_REELS -> IN_REELS
            else -> OFF_REELS
        }
    }
}

/** What the screen detector says about the app on screen. */
enum class ReelsState {
    IN_REELS,
    OTHER,

    /** No rules for the version, unreadable tree or an unrecognized screen. */
    UNKNOWN,
    ;

    companion object {
        fun of(verdict: SectionVerdict): ReelsState = when (verdict) {
            SectionVerdict.IN_BLOCKED_SECTION -> IN_REELS
            SectionVerdict.IN_ALLOWED_SECTION -> OTHER
            SectionVerdict.UNKNOWN -> UNKNOWN
        }
    }
}

/** Why an app uses the Reels-only timer, or why it falls back to the whole-app timer. */
enum class ReelsTimerStatus {
    /** Reels-only timer running. */
    ACTIVE,

    /** Not Instagram or TikTok, or the master switch is off: the usual whole-app timer, no note. */
    NOT_APPLICABLE,

    /** No identifiers for the installed version. */
    UNSUPPORTED_VERSION,

    /** The user hasn't agreed to screen checks (the updated disclosure). */
    NEEDS_CONSENT,

    /** The accessibility service isn't running. */
    SERVICE_OFF,

    /** The detector answered UNKNOWN for longer than [ReelsHysteresis.UNKNOWN_FALLBACK_MILLIS]. */
    SCREEN_UNREADABLE,
    ;

    /**
     * Shows a note in Settings and App detail: detection is unavailable, so the pill counts the
     * whole app and limits won't block (fail open).
     */
    val showsNote: Boolean get() = this != ACTIVE && this != NOT_APPLICABLE
}

/** Everything that decides [ReelsTimerStatus] for one app. */
data class ReelsInputs(
    val packageName: String,
    /** Identifiers exist for the installed version. */
    val versionSupported: Boolean,
    /** The answer on the screen-check disclosure. */
    val consent: ScrollConsent,
    /** The swipe counter's accessibility service is running. */
    val serviceConnected: Boolean,
    /** The confirmed live state, or null before the first reading. */
    val live: ReelsState? = null,
)

object ReelsOnlyRules {

    fun availability(inputs: ReelsInputs, enabled: Boolean = ReelsOnlyPillConfig.ENABLED): ReelsTimerStatus = with(inputs) {
        when {
            !enabled || packageName !in ReelsOnlyPillConfig.PACKAGES -> ReelsTimerStatus.NOT_APPLICABLE
            !versionSupported -> ReelsTimerStatus.UNSUPPORTED_VERSION
            consent != ScrollConsent.AGREED -> ReelsTimerStatus.NEEDS_CONSENT
            !serviceConnected -> ReelsTimerStatus.SERVICE_OFF
            live == ReelsState.UNKNOWN -> ReelsTimerStatus.SCREEN_UNREADABLE
            else -> ReelsTimerStatus.ACTIVE
        }
    }

    /**
     * Whether the service should read [packageName]'s screen at all: allowed (switch, consent,
     * version). A fallen-back app keeps being checked so it can recover.
     */
    fun shouldDetect(inputs: ReelsInputs, enabled: Boolean = ReelsOnlyPillConfig.ENABLED): Boolean =
        availability(inputs.copy(live = null), enabled) == ReelsTimerStatus.ACTIVE

    /**
     * Whether a swipe in an app counts. Apps on the Reels-only timer count only swipes in Reels;
     * every other app (and a fallen-back one) counts every swipe, as before.
     */
    fun countsSwipe(live: ReelsLive?): Boolean = live == null || !live.reelsOnly || live.state == ReelsState.IN_REELS
}

/**
 * Live Reels state of one app, published by the detection repository. [reelsOnly] is true while
 * the Reels-only timer runs for it ([ReelsTimerStatus.ACTIVE]); then [state] is the confirmed
 * state (null before the first reading), and [since] is when that state really began (the first
 * reading of it, before the confirmation delay), used as the segment boundary.
 */
data class ReelsLive(
    val packageName: String,
    val availability: ReelsTimerStatus,
    val state: ReelsState? = null,
    val since: Long = 0,
    /** Debug only: the last raw reading, the rule that produced it, and the installed version code. */
    val lastReading: ReelsState? = null,
    val rule: String? = null,
    val versionCode: Long? = null,
) {
    val reelsOnly: Boolean get() = availability == ReelsTimerStatus.ACTIVE
}

/** The live Reels state for SessionManager and the pill. Implemented by the detection repository. */
interface ReelsTracking {
    /** Per app on the Reels-only list; apps not in the map use the whole-app behavior. */
    val live: StateFlow<Map<String, ReelsLive>>

    /** The app left the screen: its next visit starts unconfirmed (no stale state on return). */
    fun onAppLeft(packageName: String)
}

/** Debug builds only: one line per app for Settings › Debug. Identifiers and verdicts, never content. */
object ReelsDebugFormat {
    /** "com.instagram.android · ACTIVE · IN_REELS (now IN_REELS) · selected clips_tab + clips_viewer_container · v=123". */
    fun line(live: ReelsLive): String = buildList {
        add(live.packageName)
        add(live.availability.name)
        add(live.state?.name ?: "no reading")
        live.lastReading?.takeIf { it != live.state }?.let { add("reading $it") }
        live.rule?.let { add(it) }
        add("v=${live.versionCode ?: "?"}")
    }.joinToString(" · ")
}

/**
 * Which of an app's limits block the whole app. For Instagram and TikTok only "Block entire app"
 * does (the block screen); the time limit and the schedule cover Reels only (OverlayCoordinator),
 * so chat always works. Every other app: all of them, as before.
 */
object ReelsLimitScope {
    fun wholeAppPart(packageName: String, settings: LimitSettings, enabled: Boolean = ReelsOnlyPillConfig.ENABLED): LimitSettings =
        if (enabled && packageName in ReelsOnlyPillConfig.PACKAGES) {
            settings.copy(dailyLimitMinutes = null, schedule = settings.schedule.copy(enabled = false))
        } else {
            settings
        }

    /** True when [packageName]'s time limit and schedule apply to Reels only. */
    fun reelsOnly(packageName: String, enabled: Boolean = ReelsOnlyPillConfig.ENABLED): Boolean =
        enabled && packageName in ReelsOnlyPillConfig.PACKAGES
}
