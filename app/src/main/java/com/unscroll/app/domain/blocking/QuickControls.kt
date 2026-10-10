package com.unscroll.app.domain.blocking

import com.unscroll.app.domain.section.BlockedSection
import com.unscroll.app.domain.section.ReelsCapableApps

/** Whether "Block reels only" can be switched on for an app right now, in the order checked. */
enum class ReelsAvailability {
    /** Plus, consent and the service are there: the toggle works. */
    AVAILABLE,

    /** Works, but the global "Turn off section blocking" is set in Settings (shown as a caption). */
    TURNED_OFF,

    /**
     * No identifiers for the installed version, so the detector could only say UNKNOWN: the
     * toggle is disabled ("Not available for this app version yet") rather than pretending.
     */
    NOT_AVAILABLE,

    /** Section blocking is Unscroll Plus: turning it on opens the paywall. */
    NEEDS_PLUS,

    /** No consent yet: turning it on opens the disclosure, then Accessibility settings. */
    NEEDS_CONSENT,

    /** Consent given but the service is off: turning it on opens Accessibility settings. */
    NEEDS_SERVICE,
}

/** What the reels toggle needs to know about section blocking. */
data class SectionAccess(
    /** The config has identifiers for the installed version of the app. */
    val rulesAvailable: Boolean,
    val isPlus: Boolean,
    val consented: Boolean,
    /** The section-blocking accessibility service is running. */
    val serviceOn: Boolean,
    /** The global kill switch. */
    val turnedOff: Boolean,
)

/** One toggle row: on or off, the live countdown and the selected duration chip. */
data class QuickToggleState(
    val on: Boolean,
    /** Null when off or "until I turn it off". */
    val remainingMillis: Long?,
    val duration: BlockDuration,
)

data class ReelsToggleState(
    /** Reels, For You or Shorts, for the label. */
    val section: BlockedSection,
    val toggle: QuickToggleState,
    /** "Block entire app" is on: shown on and disabled, captioned "Included". */
    val included: Boolean,
    val availability: ReelsAvailability,
)

/** The quick controls of one app: always "Block entire app"; "Block reels only" for Reels-capable apps. */
data class QuickControlsState(
    val entireApp: QuickToggleState,
    /** Null for every app that isn't in [ReelsCapableApps]: no reels toggle at all. */
    val reels: ReelsToggleState?,
)

/** A running quick block, for an app's status line ("Reels blocked · 12 min left"). */
data class QuickStatus(
    val target: QuickBlockTarget,
    /** For [QuickBlockTarget.REELS]: which section, for the wording. */
    val section: BlockedSection?,
    /** Null: until the user turns it off. */
    val remainingMillis: Long?,
)

/** What tapping the reels toggle does. */
enum class ReelsTapAction { TOGGLE, OPEN_PAYWALL, OPEN_DISCLOSURE, OPEN_ACCESSIBILITY, NONE }

/** Pure: builds the quick controls from the stored settings and the clock. */
object QuickControls {

    fun build(packageName: String, settings: LimitSettings, now: Long, access: SectionAccess): QuickControlsState {
        val entire = toggle(settings.entireAppBlockedUntil, settings.lastEntireDuration, now)
        val reels = ReelsCapableApps.sectionFor(packageName)?.let { section ->
            ReelsToggleState(
                section = section,
                toggle = toggle(settings.reelsBlockedUntil, settings.lastReelsDuration, now),
                included = entire.on,
                availability = availability(access),
            )
        }
        return QuickControlsState(entireApp = entire, reels = reels)
    }

    /** The running quick block to show in the app's status line: the entire app first, then reels. */
    fun status(packageName: String, settings: LimitSettings, now: Long): QuickStatus? {
        if (settings.entireAppBlocked(now)) {
            return QuickStatus(QuickBlockTarget.ENTIRE_APP, null, TimedBlock.remainingMillis(settings.entireAppBlockedUntil, now))
        }
        val section = ReelsCapableApps.sectionFor(packageName) ?: return null
        if (!settings.reelsBlocked(now)) return null
        return QuickStatus(QuickBlockTarget.REELS, section, TimedBlock.remainingMillis(settings.reelsBlockedUntil, now))
    }

    fun availability(access: SectionAccess): ReelsAvailability = when {
        !access.rulesAvailable -> ReelsAvailability.NOT_AVAILABLE
        !access.isPlus -> ReelsAvailability.NEEDS_PLUS
        !access.consented -> ReelsAvailability.NEEDS_CONSENT
        !access.serviceOn -> ReelsAvailability.NEEDS_SERVICE
        access.turnedOff -> ReelsAvailability.TURNED_OFF
        else -> ReelsAvailability.AVAILABLE
    }

    /**
     * Turning off always works (the user can always undo a block). Turning on goes through Plus,
     * consent and the service first; with no identifiers, or while "Included", nothing happens.
     */
    fun reelsTap(state: ReelsToggleState, turnOn: Boolean): ReelsTapAction = when {
        state.included -> ReelsTapAction.NONE
        !turnOn -> ReelsTapAction.TOGGLE
        else -> when (state.availability) {
            ReelsAvailability.AVAILABLE, ReelsAvailability.TURNED_OFF -> ReelsTapAction.TOGGLE
            ReelsAvailability.NOT_AVAILABLE -> ReelsTapAction.NONE
            ReelsAvailability.NEEDS_PLUS -> ReelsTapAction.OPEN_PAYWALL
            ReelsAvailability.NEEDS_CONSENT -> ReelsTapAction.OPEN_DISCLOSURE
            ReelsAvailability.NEEDS_SERVICE -> ReelsTapAction.OPEN_ACCESSIBILITY
        }
    }

    /** A reels toggle waiting on the disclosure or Accessibility settings turns on once it works. */
    fun canCompletePendingEnable(availability: ReelsAvailability): Boolean =
        availability == ReelsAvailability.AVAILABLE || availability == ReelsAvailability.TURNED_OFF

    private fun toggle(until: Long?, duration: BlockDuration, now: Long) = QuickToggleState(
        on = TimedBlock.isActive(until, now),
        remainingMillis = TimedBlock.remainingMillis(until, now),
        duration = duration,
    )
}
