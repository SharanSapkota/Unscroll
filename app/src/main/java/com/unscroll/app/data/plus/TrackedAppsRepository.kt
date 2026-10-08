package com.unscroll.app.data.plus

import com.unscroll.app.domain.ApplicationScope
import com.unscroll.app.domain.insights.TimeRange
import com.unscroll.app.domain.insights.UsageDataSource
import com.unscroll.app.domain.plus.FreeTier
import com.unscroll.app.domain.plus.FreeTierRules
import com.unscroll.app.domain.plus.TrackedAppsSource
import com.unscroll.app.domain.plus.TrackedAppsState
import com.unscroll.app.domain.time.Clock
import com.unscroll.app.domain.tracking.TrackedApps
import com.unscroll.app.util.InstalledTrackedApps
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * The active and paused tracked apps. With Plus every tracked app is active. Free users get
 * [FreeTier.FREE_APPS]: the ones they picked, or the most used until they pick. Paused apps keep
 * their history, limits and settings; they simply aren't tracked (SessionManager asks
 * [isActive]), so they get no pill, limits or blocking. Getting Plus resumes them as they were.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class TrackedAppsRepository @Inject constructor(
    entitlement: EntitlementRepository,
    private val preferences: EntitlementPreferences,
    private val installedApps: InstalledTrackedApps,
    private val usage: UsageDataSource,
    private val clock: Clock,
    @ApplicationScope scope: CoroutineScope,
) : TrackedAppsSource {

    /** Bumped on resume, so newly installed apps are noticed. */
    private val refreshes = MutableStateFlow(0)

    private val current = combine(
        entitlement.isPlus,
        preferences.pickedApps,
        entitlement.plusEnded,
        refreshes,
    ) { plus, picked, ended, _ -> Triple(plus, picked, ended) }
        .mapLatest { (plus, picked, ended) -> resolve(plus, picked, ended) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    override val state: Flow<TrackedAppsState> = current.filterNotNull()

    override suspend fun isActive(packageName: String): Boolean = packageName in state.first().active

    fun refresh() {
        refreshes.update { it + 1 }
    }

    /** The user's free apps (pick screen). */
    suspend fun pick(packages: Collection<String>) {
        preferences.savePickedApps(packages.toSet())
    }

    /** Installed tracked apps, most used in the last [RECENT_DAYS] days first. */
    suspend fun mostUsed(): List<String> {
        val now = clock.now()
        return usage.appTotals(TimeRange(now - RECENT_DAYS * DAY_MILLIS, now), now)
            .filterValues { it > 0 }
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
    }

    private suspend fun resolve(plus: Boolean, picked: Set<String>, ended: Boolean): TrackedAppsState {
        val apps = installedApps.packages()
        val mostUsed = if (plus) emptyList() else mostUsed()
        var picks = picked
        if (!plus && ended && apps.none { it in picked } && apps.size > FreeTier.FREE_APPS) {
            // Plus ended before the user ever picked: keep the most used and remember that, so the
            // kept app doesn't change from day to day. They can change it in Settings.
            picks = FreeTierRules.keepOnDowngrade(apps, picked, mostUsed)
            preferences.savePickedApps(picks)
        }
        return FreeTierRules.resolve(plus, apps, TrackedApps.packageNames, picks, mostUsed)
    }

    private companion object {
        const val RECENT_DAYS = 30L
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }
}
