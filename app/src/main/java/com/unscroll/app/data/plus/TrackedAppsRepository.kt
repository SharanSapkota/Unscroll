package com.unscroll.app.data.plus

import com.unscroll.app.data.apps.InstalledApps
import com.unscroll.app.data.apps.TrackedAppsPreferences
import com.unscroll.app.data.db.TrackedAppDao
import com.unscroll.app.data.db.TrackedAppEntity
import com.unscroll.app.domain.ApplicationScope
import com.unscroll.app.domain.apps.AddResult
import com.unscroll.app.domain.apps.AddRules
import com.unscroll.app.domain.apps.ExcludedApps
import com.unscroll.app.domain.apps.TrackedApp
import com.unscroll.app.domain.apps.TrackedAppStatus
import com.unscroll.app.domain.insights.TimeRange
import com.unscroll.app.domain.insights.UsageDataSource
import com.unscroll.app.domain.plus.FreeTierRules
import com.unscroll.app.domain.plus.TrackedAppsSource
import com.unscroll.app.domain.plus.TrackedAppsState
import com.unscroll.app.domain.time.Clock
import com.unscroll.app.domain.tracking.DefaultTrackedApps
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The single source of truth for which apps Unscroll tracks: the `tracked_apps` table, seeded
 * with Instagram, TikTok (both packages) and Facebook on a fresh install. Any installed app can be
 * added; nothing else in the app keeps its own list.
 *
 * An app is tracked (active) when it is in the list, installed, not paused by the user, not
 * excluded (AppExclusions) and within [EntitlementRepository.maxActiveApps]: with Plus every app,
 * free users [com.unscroll.app.domain.plus.FreeTier.FREE_APPS] (their picks, or the most used
 * until they pick). Everything that isn't tracked gets no session, pill, limits or blocking
 * (SessionManager asks [isActive]). Removing an app only hides it: its history and settings stay
 * in their tables, so adding it again restores them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class TrackedAppsRepository @Inject constructor(
    private val dao: TrackedAppDao,
    private val entitlement: EntitlementRepository,
    private val preferences: EntitlementPreferences,
    private val seed: TrackedAppsPreferences,
    private val installedApps: InstalledApps,
    private val excludedApps: ExcludedApps,
    private val usage: UsageDataSource,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) : TrackedAppsSource {

    /** Bumped on resume, so installs and uninstalls are noticed. */
    private val refreshes = MutableStateFlow(0)

    init {
        // A fresh install, or after "Delete all my data": seed the defaults once.
        scope.launch { seed.seeded.collect { seeded -> if (!seeded) seedDefaults() } }
    }

    /**
     * The user's tracked apps, in the order they were added, with whether each is installed. A
     * removed app, or a default that was never installed on this phone, isn't listed.
     */
    val trackedApps: Flow<List<TrackedApp>> = combine(dao.observeAll(), refreshes) { rows, _ -> rows }
        .mapLatest { rows -> rows.filterNot { it.removed }.map { toTrackedApp(it) }.filter { it.installed || it.label != null } }
        // PackageManager calls stay off the main thread.
        .flowOn(Dispatchers.IO)

    private val current = combine(
        trackedApps,
        entitlement.isPlus,
        preferences.pickedApps,
        entitlement.plusEnded,
        entitlement.maxActiveApps,
    ) { entries, plus, picked, ended, max -> Inputs(entries, plus, picked, ended, max) }
        .mapLatest { resolve(it) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    override val state: Flow<TrackedAppsState> = current.filterNotNull()

    override suspend fun isActive(packageName: String): Boolean = packageName in state.first().active

    override suspend fun countsSwipes(packageName: String): Boolean = state.first().countsSwipes(packageName)

    /** Re-checks installs (on resume) and remembers the names of installed apps. */
    fun refresh() {
        refreshes.update { it + 1 }
        scope.launch { cacheLabels() }
    }

    /**
     * Adds [packageName] (or restores it if it was removed: its history and settings are still
     * there). Excluded apps are refused here, whatever the UI showed. A free user at the limit
     * gets [AddResult.NEEDS_PLUS] and nothing changes.
     */
    suspend fun add(packageName: String, label: String?): AddResult {
        if (excludedApps.isExcluded(packageName)) return AddResult.EXCLUDED
        val state = state.first()
        if (packageName in state.active) return AddResult.ADDED
        val max = entitlement.maxActiveApps.first()
        if (!AddRules.canAdd(state.active.size, max)) return AddResult.NEEDS_PLUS
        val row = dao.get(packageName)
        dao.upsert(
            row?.copy(removed = false, status = TrackedAppStatus.ACTIVE.name, cachedLabel = label ?: row.cachedLabel)
                ?: TrackedAppEntity(packageName = packageName, cachedLabel = label, addedAt = clock.now()),
        )
        // A free user's new app is one of their free picks.
        if (!state.isPlus) preferences.savePickedApps(state.active + packageName)
        return AddResult.ADDED
    }

    /** Stops tracking [packageName]. Its sessions, limits and settings stay for a later re-add. */
    suspend fun remove(packageName: String) {
        dao.setRemoved(packageName, removed = true)
    }

    /** "Track this app" off/on: paused apps stay listed but get no tracking. */
    suspend fun setPaused(packageName: String, paused: Boolean) {
        dao.setStatus(packageName, if (paused) TrackedAppStatus.PAUSED.name else TrackedAppStatus.ACTIVE.name)
    }

    suspend fun setCountSwipes(packageName: String, countSwipes: Boolean) {
        dao.setCountSwipes(packageName, countSwipes)
    }

    /** The user's free apps (pick screen). */
    suspend fun pick(packages: Collection<String>) {
        preferences.savePickedApps(packages.toSet())
    }

    /** Tracked apps, most used in the last [RECENT_DAYS] days first. */
    suspend fun mostUsed(): List<String> {
        val now = clock.now()
        return usage.appTotals(TimeRange(now - RECENT_DAYS * DAY_MILLIS, now), now)
            .filterValues { it > 0 }
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
    }

    private suspend fun resolve(inputs: Inputs): TrackedAppsState {
        val (entries, plus, picked, ended, max) = inputs
        // Only installed, user-active, non-excluded apps can be tracked at all.
        val apps = entries
            .filter { it.installed && it.status == TrackedAppStatus.ACTIVE && !excludedApps.isExcluded(it.packageName) }
            .map { it.packageName }
        val mostUsed = if (plus) emptyList() else mostUsed()
        var picks = picked
        if (!plus && ended && apps.none { it in picked } && apps.size > max) {
            // Plus ended before the user ever picked: keep the most used and remember that, so the
            // kept app doesn't change from day to day. They can change it in Settings.
            picks = FreeTierRules.keepOnDowngrade(apps, picked, mostUsed, max)
            preferences.savePickedApps(picks)
        }
        return FreeTierRules.resolve(plus, apps, apps.toSet(), picks, mostUsed, max).copy(entries = entries)
    }

    private fun toTrackedApp(row: TrackedAppEntity): TrackedApp {
        val installed = installedApps.isInstalled(row.packageName)
        return TrackedApp(
            packageName = row.packageName,
            label = (if (installed) installedApps.label(row.packageName) else null) ?: row.cachedLabel,
            addedAt = row.addedAt,
            status = if (row.status == TrackedAppStatus.PAUSED.name) TrackedAppStatus.PAUSED else TrackedAppStatus.ACTIVE,
            countSwipes = row.countSwipes,
            installed = installed,
        )
    }

    private suspend fun seedDefaults() {
        dao.insertIgnore(
            DefaultTrackedApps.packageNames.mapIndexed { index, packageName ->
                TrackedAppEntity(packageName = packageName, cachedLabel = null, addedAt = index.toLong())
            },
        )
        seed.setSeeded()
        cacheLabels()
    }

    /** Stores the names of installed tracked apps, so they can be shown after an uninstall. */
    private suspend fun cacheLabels() {
        dao.observeAll().first().filterNot { it.removed }.forEach { row ->
            val label = installedApps.label(row.packageName) ?: return@forEach
            if (label != row.cachedLabel) dao.setLabel(row.packageName, label)
        }
    }

    private data class Inputs(
        val entries: List<TrackedApp>,
        val plus: Boolean,
        val picked: Set<String>,
        val ended: Boolean,
        val max: Int,
    )

    private companion object {
        const val RECENT_DAYS = 30L
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }
}
