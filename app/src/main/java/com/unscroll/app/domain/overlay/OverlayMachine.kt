package com.unscroll.app.domain.overlay

import com.unscroll.app.domain.reels.SectionGate
import com.unscroll.app.domain.reels.TimerVisibility

/** Why a cover is up. The cover says it ("Time limit reached", "Swipe limit reached"). */
enum class CoverReason { TIME_LIMIT, SWIPE_LIMIT, SCHEDULE }

/** How the pill looks while its window is attached. */
enum class PillMode {
    /** Normal: ticking, touchable, full opacity setting. */
    ACTIVE,

    /** Reels-capable app off Reels with "Always while in the app": ~60 % opacity, pause mark, frozen numbers. */
    DIMMED,

    /** Reels-capable app off Reels with "Only on Reels": attached but invisible and untouchable. */
    PARKED,
}

/** Everything the overlay decides from, at one moment. Produced by OverlayCoordinator. */
data class OverlaySignals(
    /** The tracked app in the foreground (SessionManager.foregroundSession), or null. */
    val foreground: String?,
    val screenOn: Boolean = true,
    /** TrackingService's overlay loop is running. */
    val trackingOn: Boolean = true,
    /** "Display over other apps" is granted (re-checked, there is no callback for a revocation). */
    val canDrawOverlays: Boolean = true,
    /** The pill is switched on globally and for [foreground]. */
    val pillEnabled: Boolean = true,
    /** Where [foreground] stands for Reels gating. */
    val gate: SectionGate = SectionGate.WHOLE_APP,
    /** "Timer visibility" for [foreground] (Reels-capable apps only). */
    val visibility: TimerVisibility = TimerVisibility.ALWAYS,
    /** A limit reached for [foreground] (first reached wins, LimitCoverRules), or null. */
    val coverRequest: CoverReason? = null,
    /** "Take me back to chat" was just tapped: no cover until the snooze ends. */
    val coverSnoozed: Boolean = false,
)

/** The overlay as a whole: the pill window, the cover window, or neither. */
sealed interface OverlayState {
    data object Hidden : OverlayState

    data class PillActive(val packageName: String) : OverlayState

    data class PillDimmed(val packageName: String) : OverlayState

    /** "Only on Reels" off Reels: the pill window stays attached, invisible. */
    data class PillParked(val packageName: String) : OverlayState

    /** The cover is up; [pill] is the pill's mode underneath (null: no pill window). */
    data class CoverShowing(val packageName: String, val reason: CoverReason, val pill: PillMode?) : OverlayState
}

/** One WindowManager change. OverlayCoordinator carries them out in order, on the main thread. */
sealed interface WindowOp {
    data class AddPill(val packageName: String, val mode: PillMode) : WindowOp

    /** Same window, another app (a direct switch between two tracked apps). */
    data class RetargetPill(val packageName: String, val mode: PillMode) : WindowOp

    /** Same window, same app: only its look changes (tab change). Never a remove and re-add. */
    data class SetPillMode(val mode: PillMode) : WindowOp

    data object RemovePill : WindowOp

    data class AddCover(val packageName: String, val reason: CoverReason) : WindowOp

    /** The cover stays; what it says changes (another reason, or another app). */
    data class UpdateCover(val packageName: String, val reason: CoverReason) : WindowOp

    data object RemoveCover : WindowOp
}

data class OverlayTransition(val from: OverlayState, val to: OverlayState, val ops: List<WindowOp>)

/**
 * The overlay's single state machine (pure; OverlayCoordinator owns the windows and feeds it).
 *
 * The target state is a function of [OverlaySignals]:
 * - No foreground app, screen off, tracking stopped, or no overlay permission: [OverlayState.Hidden]
 *   (both windows removed). These are the only reasons a window is ever removed.
 * - A cover request, allowed by the gate (every app but a Reels-capable one off Reels or with
 *   detection unavailable, which fails open) and not snoozed: [OverlayState.CoverShowing].
 * - Otherwise the pill, if it is enabled: ACTIVE, or for a Reels-capable app off Reels DIMMED
 *   ("Always while in the app") or PARKED ("Only on Reels"). Disabled: Hidden.
 *
 * Transition table (from → to: window ops). Every pair not listed is a no-op.
 *
 * | From              | To                     | Ops                                              |
 * |-------------------|------------------------|--------------------------------------------------|
 * | Hidden            | PillActive/Dimmed/Parked | AddPill(mode)                                  |
 * | Hidden            | CoverShowing           | AddPill(mode) if a pill, AddCover                |
 * | PillX(app)        | PillY(app)             | SetPillMode(Y) (tab change: never re-added)      |
 * | PillX(app A)      | PillY(app B)           | RetargetPill(B, Y) (direct app switch)           |
 * | PillX             | CoverShowing           | SetPillMode/Retarget if needed, AddCover         |
 * | PillX             | Hidden                 | RemovePill                                       |
 * | CoverShowing      | PillX (same app)       | RemoveCover, SetPillMode(X) if changed           |
 * | CoverShowing      | CoverShowing (other)   | UpdateCover, pill ops if needed                  |
 * | CoverShowing      | Hidden                 | RemoveCover, RemovePill if a pill                |
 *
 * Ops are ordered: RemoveCover, then pill ops, then AddCover/UpdateCover, so the cover is always
 * added last and sits on top. Applying the same signals twice gives no ops (idempotent).
 */
object OverlayMachine {

    fun target(signals: OverlaySignals): OverlayState {
        val app = signals.foreground
        if (app == null || !signals.screenOn || !signals.trackingOn || !signals.canDrawOverlays) return OverlayState.Hidden
        val pill = if (signals.pillEnabled) pillMode(signals.gate, signals.visibility) else null
        val cover = signals.coverRequest?.takeIf { signals.gate.allowsInterruptions && !signals.coverSnoozed }
        return when {
            cover != null -> OverlayState.CoverShowing(app, cover, pill)
            pill == null -> OverlayState.Hidden
            pill == PillMode.ACTIVE -> OverlayState.PillActive(app)
            pill == PillMode.DIMMED -> OverlayState.PillDimmed(app)
            else -> OverlayState.PillParked(app)
        }
    }

    fun pillMode(gate: SectionGate, visibility: TimerVisibility): PillMode = when (gate) {
        SectionGate.WHOLE_APP, SectionGate.IN_REELS, SectionGate.DETECTION_UNAVAILABLE -> PillMode.ACTIVE
        SectionGate.OFF_REELS -> if (visibility == TimerVisibility.ALWAYS) PillMode.DIMMED else PillMode.PARKED
    }

    /** The move from [from] to the state [signals] call for, with the window ops it takes. */
    fun transition(from: OverlayState, signals: OverlaySignals): OverlayTransition {
        val to = target(signals)
        return OverlayTransition(from, to, ops(from, to))
    }

    fun ops(from: OverlayState, to: OverlayState): List<WindowOp> {
        if (from == to) return emptyList()
        val ops = ArrayList<WindowOp>(3)
        val oldCover = from.cover()
        val newCover = to.cover()
        if (oldCover != null && newCover == null) ops += WindowOp.RemoveCover

        val oldPill = from.pill()
        val newPill = to.pill()
        when {
            oldPill != null && newPill == null -> ops += WindowOp.RemovePill
            oldPill == null && newPill != null -> ops += WindowOp.AddPill(newPill.first, newPill.second)
            oldPill != null && newPill != null && oldPill.first != newPill.first ->
                ops += WindowOp.RetargetPill(newPill.first, newPill.second)
            oldPill != null && newPill != null && oldPill.second != newPill.second ->
                ops += WindowOp.SetPillMode(newPill.second)
        }

        if (newCover != null) {
            when {
                oldCover == null -> ops += WindowOp.AddCover(newCover.first, newCover.second)
                oldCover != newCover -> ops += WindowOp.UpdateCover(newCover.first, newCover.second)
            }
        }
        return ops
    }

    /** The pill window's app and mode in [this] state, or null without a pill window. */
    fun OverlayState.pill(): Pair<String, PillMode>? = when (this) {
        OverlayState.Hidden -> null
        is OverlayState.PillActive -> packageName to PillMode.ACTIVE
        is OverlayState.PillDimmed -> packageName to PillMode.DIMMED
        is OverlayState.PillParked -> packageName to PillMode.PARKED
        is OverlayState.CoverShowing -> pill?.let { packageName to it }
    }

    private fun OverlayState.cover(): Pair<String, CoverReason>? =
        (this as? OverlayState.CoverShowing)?.let { it.packageName to it.reason }
}
