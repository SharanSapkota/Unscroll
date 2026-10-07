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
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.unscroll.app.domain.overlay.ColorThresholds
import com.unscroll.app.domain.overlay.OverlaySettings
import com.unscroll.app.domain.overlay.PillPosition
import com.unscroll.app.domain.overlay.PillSize
import com.unscroll.app.domain.overlay.ScreenOrientation
import com.unscroll.app.domain.overlay.SwipeColorThresholds
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

    suspend fun setShowSessionTime(show: Boolean) = edit { it[SHOW_SESSION_TIME] = show }

    suspend fun setShowSwipes(show: Boolean) = edit { it[SHOW_SWIPES] = show }

    /** Per app: show the pill for [packageName] or not. */
    suspend fun setPillShownFor(packageName: String, shown: Boolean) = edit {
        it[PILL_HIDDEN_FOR] = (it[PILL_HIDDEN_FOR] ?: emptySet()).toggle(packageName, hidden = !shown)
    }

    /** Per app: show the swipe count on [packageName]'s pill or not. */
    suspend fun setSwipesShownFor(packageName: String, shown: Boolean) = edit {
        it[SWIPES_HIDDEN_FOR] = (it[SWIPES_HIDDEN_FOR] ?: emptySet()).toggle(packageName, hidden = !shown)
    }

    suspend fun setSwipeThresholds(thresholds: SwipeColorThresholds) {
        val safe = thresholds.normalized()
        edit {
            it[SWIPE_WARNING] = safe.warningAfterSwipes
            it[SWIPE_DANGER] = safe.dangerAfterSwipes
        }
    }

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

    private fun Set<String>.toggle(packageName: String, hidden: Boolean): Set<String> =
        if (hidden) this + packageName else this - packageName

    private suspend fun edit(block: (MutablePreferences) -> Unit) {
        dataStore.edit { block(it) }
    }

    private fun Preferences.toSettings() = OverlaySettings(
        enabled = this[ENABLED] ?: true,
        showSessionTime = this[SHOW_SESSION_TIME] ?: false,
        thresholds = ColorThresholds(
            warningAfterMinutes = this[WARNING_MINUTES] ?: ColorThresholds.DEFAULT_WARNING_MINUTES,
            dangerAfterMinutes = this[DANGER_MINUTES] ?: ColorThresholds.DEFAULT_DANGER_MINUTES,
        ).normalized(),
        size = this[SIZE]?.let { name -> PillSize.entries.firstOrNull { it.name == name } }
            ?: PillSize.MEDIUM,
        opacity = OverlaySettings.clampOpacity(this[OPACITY] ?: OverlaySettings.DEFAULT_OPACITY),
        showSwipes = this[SHOW_SWIPES] ?: true,
        swipeThresholds = SwipeColorThresholds(
            warningAfterSwipes = this[SWIPE_WARNING] ?: SwipeColorThresholds.DEFAULT_WARNING_SWIPES,
            dangerAfterSwipes = this[SWIPE_DANGER] ?: SwipeColorThresholds.DEFAULT_DANGER_SWIPES,
        ).normalized(),
        pillHiddenFor = this[PILL_HIDDEN_FOR] ?: emptySet(),
        swipesHiddenFor = this[SWIPES_HIDDEN_FOR] ?: emptySet(),
    )

    private companion object {
        val ENABLED = booleanPreferencesKey("overlay_enabled")
        val SHOW_SESSION_TIME = booleanPreferencesKey("overlay_show_session_time")

        // Daily thresholds. New keys: the old per-session ones meant something else and are
        // removed by RemovedPreferencesMigration, so everyone starts from the daily defaults.
        val WARNING_MINUTES = intPreferencesKey("overlay_daily_warning_minutes")
        val DANGER_MINUTES = intPreferencesKey("overlay_daily_danger_minutes")
        val SIZE = stringPreferencesKey("overlay_size")
        val OPACITY = floatPreferencesKey("overlay_opacity")
        val SHOW_SWIPES = booleanPreferencesKey("overlay_show_swipes")
        val SWIPE_WARNING = intPreferencesKey("overlay_daily_swipe_warning")
        val SWIPE_DANGER = intPreferencesKey("overlay_daily_swipe_danger")
        val PILL_HIDDEN_FOR = stringSetPreferencesKey("overlay_pill_hidden_for")
        val SWIPES_HIDDEN_FOR = stringSetPreferencesKey("overlay_swipes_hidden_for")

        fun xKey(orientation: ScreenOrientation) =
            intPreferencesKey("overlay_x_${orientation.name.lowercase()}")

        fun yKey(orientation: ScreenOrientation) =
            intPreferencesKey("overlay_y_${orientation.name.lowercase()}")
    }
}
