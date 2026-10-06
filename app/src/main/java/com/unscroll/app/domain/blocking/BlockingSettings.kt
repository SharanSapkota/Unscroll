package com.unscroll.app.domain.blocking

/** How hard it is to weaken a block. */
data class BlockingSettings(
    val frictionMode: FrictionMode = FrictionMode.WAIT,
    val cooldownMinutes: Int = DEFAULT_COOLDOWN_MINUTES,
    /** A weaker friction setting waiting out the current cooldown. */
    val pending: PendingFriction? = null,
) {
    val cooldownMillis: Long get() = cooldownMinutes * MINUTE

    companion object {
        const val DEFAULT_COOLDOWN_MINUTES = 10
        val COOLDOWN_PRESETS = listOf(5, 10, 30, 60)

        /** "I need access" on the block screen. */
        const val EXTENSION_MILLIS = 5 * 60_000L

        /** How long "Wait" mode makes the user wait on the block screen. */
        const val BLOCK_SCREEN_WAIT_SECONDS = 30

        private const val MINUTE = 60_000L
    }
}

data class PendingFriction(
    val frictionMode: FrictionMode,
    val cooldownMinutes: Int,
    val appliesAt: Long,
)

/**
 * The friction settings get the same treatment as limits, otherwise they would be a loophole:
 * shortening the cooldown or switching from waiting to a typed phrase waits out the current
 * cooldown first. Making friction stronger applies at once.
 */
object FrictionPolicy {

    fun isWeaker(current: BlockingSettings, mode: FrictionMode, cooldownMinutes: Int): Boolean =
        (current.frictionMode == FrictionMode.WAIT && mode == FrictionMode.TYPE_PHRASE) ||
            cooldownMinutes < current.cooldownMinutes

    fun request(current: BlockingSettings, mode: FrictionMode, cooldownMinutes: Int, now: Long): BlockingSettings =
        if (isWeaker(current, mode, cooldownMinutes)) {
            current.copy(pending = PendingFriction(mode, cooldownMinutes, now + current.cooldownMillis))
        } else {
            BlockingSettings(mode, cooldownMinutes, pending = null)
        }

    fun resolve(current: BlockingSettings, now: Long): BlockingSettings {
        val pending = current.pending ?: return current
        return if (now >= pending.appliesAt) {
            BlockingSettings(pending.frictionMode, pending.cooldownMinutes, pending = null)
        } else {
            current
        }
    }
}
