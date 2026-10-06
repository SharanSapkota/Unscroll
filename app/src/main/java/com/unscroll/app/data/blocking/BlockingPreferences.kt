package com.unscroll.app.data.blocking

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.unscroll.app.domain.blocking.BlockingSettings
import com.unscroll.app.domain.blocking.FrictionMode
import com.unscroll.app.domain.blocking.FrictionPolicy
import com.unscroll.app.domain.blocking.PendingFriction
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Friction settings (wait vs typed phrase, cooldown length) in the preferences DataStore. */
@Singleton
class BlockingPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    /** Stored settings; a due pending change is applied by [current]. */
    val settings: Flow<BlockingSettings> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.toSettings() }
        .distinctUntilChanged()

    /** Settings with a due pending change applied (and saved). */
    suspend fun current(now: Long): BlockingSettings {
        val stored = settings.first()
        val resolved = FrictionPolicy.resolve(stored, now)
        if (resolved != stored) save(resolved)
        return resolved
    }

    suspend fun request(mode: FrictionMode, cooldownMinutes: Int, now: Long) {
        save(FrictionPolicy.request(current(now), mode, cooldownMinutes.coerceIn(1, MAX_COOLDOWN), now))
    }

    suspend fun cancelPending(now: Long) {
        save(current(now).copy(pending = null))
    }

    private suspend fun save(settings: BlockingSettings) {
        dataStore.edit { prefs ->
            prefs[FRICTION_MODE] = settings.frictionMode.name
            prefs[COOLDOWN_MINUTES] = settings.cooldownMinutes
            prefs.writePending(settings.pending)
        }
    }

    private fun MutablePreferences.writePending(pending: PendingFriction?) {
        if (pending == null) {
            remove(PENDING_MODE)
            remove(PENDING_COOLDOWN)
            remove(PENDING_APPLIES_AT)
        } else {
            this[PENDING_MODE] = pending.frictionMode.name
            this[PENDING_COOLDOWN] = pending.cooldownMinutes
            this[PENDING_APPLIES_AT] = pending.appliesAt
        }
    }

    private fun Preferences.toSettings(): BlockingSettings {
        val pendingMode = this[PENDING_MODE]?.let(::modeOrNull)
        val pendingCooldown = this[PENDING_COOLDOWN]
        val pendingAt = this[PENDING_APPLIES_AT]
        return BlockingSettings(
            frictionMode = this[FRICTION_MODE]?.let(::modeOrNull) ?: FrictionMode.WAIT,
            cooldownMinutes = (this[COOLDOWN_MINUTES] ?: BlockingSettings.DEFAULT_COOLDOWN_MINUTES)
                .coerceIn(1, MAX_COOLDOWN),
            pending = if (pendingMode != null && pendingCooldown != null && pendingAt != null) {
                PendingFriction(pendingMode, pendingCooldown, pendingAt)
            } else {
                null
            },
        )
    }

    private fun modeOrNull(name: String): FrictionMode? = FrictionMode.entries.firstOrNull { it.name == name }

    private companion object {
        const val MAX_COOLDOWN = 24 * 60
        val FRICTION_MODE = stringPreferencesKey("blocking_friction_mode")
        val COOLDOWN_MINUTES = intPreferencesKey("blocking_cooldown_minutes")
        val PENDING_MODE = stringPreferencesKey("blocking_pending_friction_mode")
        val PENDING_COOLDOWN = intPreferencesKey("blocking_pending_cooldown_minutes")
        val PENDING_APPLIES_AT = longPreferencesKey("blocking_pending_applies_at")
    }
}
