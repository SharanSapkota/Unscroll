package com.unscroll.app.overlay

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class PillMessageKind {
    /** "15 min on Instagram. Time for a break?" with Keep going / Leave. */
    BREAK,

    /** "80% of today's Instagram limit used." */
    LIMIT_WARNING,

    /** "You've reached today's Instagram limit." */
    LIMIT_REACHED,
}

enum class PillAction { KEEP_GOING, LEAVE, DISMISS }

/** A short message the pill expands to show. [minutes] is the session length for breaks. */
data class PillMessage(
    val id: Long,
    val kind: PillMessageKind,
    val packageName: String,
    val minutes: Int = 0,
)

/** The message currently shown on the pill, posted by FrictionCoordinator. */
@Singleton
class OverlayMessages @Inject constructor() {
    private val _message = MutableStateFlow<PillMessage?>(null)
    val message: StateFlow<PillMessage?> = _message.asStateFlow()

    fun post(message: PillMessage) {
        _message.value = message
    }

    /** Clears [id] if it is still the one shown, or any message if [id] is null. */
    fun dismiss(id: Long? = null) {
        _message.update { current -> if (id == null || current?.id == id) null else current }
    }
}
