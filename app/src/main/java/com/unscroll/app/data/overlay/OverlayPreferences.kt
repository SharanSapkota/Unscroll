package com.unscroll.app.data.overlay

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.unscroll.app.domain.overlay.ColorThresholds
import com.unscroll.app.domain.overlay.OverlaySettings
import com.unscroll.app.domain.overlay.PillPosition
import com.unscroll.app.domain.overlay.PillSize
import com.unscroll.app.domain.overlay.ScreenOrientation
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Overlay timer settings and the saved pill position per orientation. */
@Singleton
class OverlayPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val preferences: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val settings: Flow<OverlaySettings> = preferences
        .map { it.toSettings() }
        .distinctUntilChanged()

    suspend fun setEnabled(enabled: Boolean) = edit { it[ENABLED] = enabled }

    suspend fun setShowTodayTotal(show: Boolean) = edit { it[SHOW_TODAY_TOTAL] = show }

    suspend fun setShowSwipes(show: Boolean) = edit { it[SHOW_SWIPES] = show }

    suspend fun setThresholds(thresholds: ColorThresholds) {
        val safe = thresholds.normalized()
        edit {
            it[WARNING_MINUTES] = safe.warningAfterMinutes
            it[DANGER_MINUTES] = safe.dangerAfterMinutes
        }
    }

    suspend fun setSize(size: PillSize) = edit { it[SIZE] = size.name }

    suspend fun setOpacity(opacity: Float) = edit { it[OPACITY] = OverlaySettings.clampOpacity(opacity) }

    suspend fun position(orientation: ScreenOrientation): PillPosition? {
        val prefs = preferences.first()
        val x = prefs[xKey(orientation)] ?: return null
        val y = prefs[yKey(orientation)] ?: return null
        return PillPosition(x, y)
    }

    suspend fun savePosition(orientation: ScreenOrientation, position: PillPosition) = edit {
        it[xKey(orientation)] = position.x
        it[yKey(orientation)] = position.y
    }

    /** Back to the default top-center position in every orientation. */
    suspend fun resetPositions() = edit { prefs ->
        ScreenOrientation.entries.forEach {
            prefs.remove(xKey(it))
            prefs.remove(yKey(it))
        }
    }

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        dataStore.edit { block(it) }
    }

    private fun Preferences.toSettings() = OverlaySettings(
        enabled = this[ENABLED] ?: true,
        showTodayTotal = this[SHOW_TODAY_TOTAL] ?: false,
        thresholds = ColorThresholds(
            warningAfterMinutes = this[WARNING_MINUTES] ?: ColorThresholds.DEFAULT_WARNING_MINUTES,
            dangerAfterMinutes = this[DANGER_MINUTES] ?: ColorThresholds.DEFAULT_DANGER_MINUTES,
        ).normalized(),
        size = this[SIZE]?.let { name -> PillSize.entries.firstOrNull { it.name == name } }
            ?: PillSize.MEDIUM,
        opacity = OverlaySettings.clampOpacity(this[OPACITY] ?: OverlaySettings.DEFAULT_OPACITY),
        showSwipes = this[SHOW_SWIPES] ?: true,
    )

    private companion object {
        val ENABLED = booleanPreferencesKey("overlay_enabled")
        val SHOW_TODAY_TOTAL = booleanPreferencesKey("overlay_show_today_total")
        val WARNING_MINUTES = intPreferencesKey("overlay_warning_minutes")
        val DANGER_MINUTES = intPreferencesKey("overlay_danger_minutes")
        val SIZE = stringPreferencesKey("overlay_size")
        val OPACITY = floatPreferencesKey("overlay_opacity")
        val SHOW_SWIPES = booleanPreferencesKey("overlay_show_swipes")

        fun xKey(orientation: ScreenOrientation) =
            intPreferencesKey("overlay_x_${orientation.name.lowercase()}")

        fun yKey(orientation: ScreenOrientation) =
            intPreferencesKey("overlay_y_${orientation.name.lowercase()}")
    }
}
