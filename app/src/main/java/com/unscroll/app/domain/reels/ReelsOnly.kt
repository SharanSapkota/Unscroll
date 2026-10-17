package com.unscroll.app.domain.reels

import com.unscroll.app.domain.section.ReelsCapableApps
import com.unscroll.app.domain.section.SectionVerdict
import com.unscroll.app.domain.scroll.ScrollConsent
import kotlinx.coroutines.flow.StateFlow

/**
 * The Reels-only timer: for Instagram and TikTok the pill shows only while the user watches Reels
 * or the For You feed, and counts only that time and those swipes. One switch, separate from
 * section blocking ("Block reels only", SectionBlockingConfig), so either can be turned off alone.
 */
object ReelsOnlyPillConfig {
    /** Master switch. False: every app keeps the whole-app pill, nothing reads screens for it. */
    const val ENABLED = true

    /** The apps it applies to. Every other app keeps the pill while it is in the foreground. */
    val PACKAGES: Set<String> = ReelsCapableApps.INSTAGRAM + ReelsCapableApps.TIKTOK

    fun appliesTo(packageName: String): Boolean = ENABLED && packageName in PACKAGES
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

    /** "Show timer only in Reels" is off for the app: whole-app timer, no note. */
    TURNED_OFF,

    /** No identifiers for the installed version. */
    UNSUPPORTED_VERSION,

    /** The user hasn't agreed to screen checks (the updated disclosure). */
    NEEDS_CONSENT,

    /** The accessibility service isn't running. */
    SERVICE_OFF,

    /** The detector answered UNKNOWN for longer than [ReelsHysteresis.UNKNOWN_FALLBACK_MILLIS]. */
    SCREEN_UNREADABLE,
    ;

    /** Shows a note in Settings and App detail (a reason the user may want to know about). */
    val showsNote: Boolean get() = this != ACTIVE && this != NOT_APPLICABLE && this != TURNED_OFF
}

/** Everything that decides [ReelsTimerStatus] for one app. */
data class ReelsInputs(
    val packageName: String,
    /** "Show timer only in Reels" for this app (on by default). */
    val toggleOn: Boolean,
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
            !toggleOn -> ReelsTimerStatus.TURNED_OFF
            !versionSupported -> ReelsTimerStatus.UNSUPPORTED_VERSION
            consent != ScrollConsent.AGREED -> ReelsTimerStatus.NEEDS_CONSENT
            !serviceConnected -> ReelsTimerStatus.SERVICE_OFF
            live == ReelsState.UNKNOWN -> ReelsTimerStatus.SCREEN_UNREADABLE
            else -> ReelsTimerStatus.ACTIVE
        }
    }

    /**
     * Whether the service should read [packageName]'s screen at all: the timer is wanted for it
     * and allowed (switch, toggle, consent, version). A fallen-back app keeps being checked so it
     * can recover.
     */
    fun shouldDetect(inputs: ReelsInputs, enabled: Boolean = ReelsOnlyPillConfig.ENABLED): Boolean =
        availability(inputs.copy(live = null), enabled) == ReelsTimerStatus.ACTIVE

    /**
     * Whether a swipe in an app counts. Apps on the Reels-only timer count only swipes in Reels;
     * every other app (and a fallen-back one) counts every swipe, as before.
     */
    fun countsSwipe(live: ReelsLive?): Boolean = live == null || !live.reelsOnly || live.state == ReelsState.IN_REELS

    /** The pill is shown for an app on the Reels-only timer only while in Reels. */
    fun pillVisible(live: ReelsLive?): Boolean = live == null || !live.reelsOnly || live.state == ReelsState.IN_REELS
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
