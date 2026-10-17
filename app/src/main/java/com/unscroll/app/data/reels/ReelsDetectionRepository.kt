package com.unscroll.app.data.reels

import com.unscroll.app.data.apps.InstalledApps
import com.unscroll.app.data.scroll.ScrollCountingRepository
import com.unscroll.app.domain.ApplicationScope
import com.unscroll.app.domain.reels.ReelsTimerStatus
import com.unscroll.app.domain.reels.ReelsHysteresis
import com.unscroll.app.domain.reels.ReelsInputs
import com.unscroll.app.domain.reels.ReelsLive
import com.unscroll.app.domain.reels.ReelsOnlyPillConfig
import com.unscroll.app.domain.reels.ReelsOnlyRules
import com.unscroll.app.domain.reels.ReelsState
import com.unscroll.app.domain.reels.ReelsTracking
import com.unscroll.app.domain.section.AppVersion
import com.unscroll.app.domain.section.SectionDetectors
import com.unscroll.app.domain.section.SectionResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Reels-only timer's state per app (Instagram, TikTok): whether it runs or why it falls back
 * ([ReelsTimerStatus]), and the confirmed section from the screen readings that
 * ScrollAccessibilityService reports ([observe], debounced by [ReelsHysteresis]).
 *
 * Only the verdict, the matched rule's identifiers and the version are kept, in memory; nothing
 * about a screen is stored or sent. [observe] runs on the service's background dispatcher; every
 * method is thread-safe.
 */
@Singleton
class ReelsDetectionRepository @Inject constructor(
    private val scrollCounting: ScrollCountingRepository,
    private val installedApps: InstalledApps,
    @ApplicationScope private val scope: CoroutineScope,
) : ReelsTracking {

    val detectors = SectionDetectors()

    /** Installed versions, read off the main thread (PackageManager); empty until the first read. */
    private val versions = MutableStateFlow<Map<String, AppVersion>>(emptyMap())

    init {
        refresh()
    }

    private val lock = Any()
    private val hysteresis = HashMap<String, ReelsHysteresis>()
    private val readings = MutableStateFlow<Map<String, Reading>>(emptyMap())

    /** When each app was last read successfully, for the watchdog. Guarded by [lock]. */
    private val lastReadingAt = HashMap<String, Long>()

    /** Everything but the live reading, per Reels-capable app. */
    private val baseInputs: Flow<Map<String, ReelsInputs>> = combine(
        scrollCounting.record,
        scrollCounting.connected,
        versions,
    ) { record, connected, versionMap ->
        ReelsOnlyPillConfig.PACKAGES.associateWith { pkg ->
            ReelsInputs(
                packageName = pkg,
                versionSupported = detectors.isAvailable(pkg, versionMap[pkg] ?: AppVersion.UNKNOWN),
                consent = record.reelsConsent,
                serviceConnected = connected,
            )
        }
    }

    private val inputs: StateFlow<Map<String, ReelsInputs>> = baseInputs
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    override val live: StateFlow<Map<String, ReelsLive>> = combine(inputs, readings) { inputMap, readingMap ->
        inputMap.mapValues { (pkg, base) ->
            val reading = readingMap[pkg]
            ReelsLive(
                packageName = pkg,
                availability = ReelsOnlyRules.availability(base.copy(live = reading?.confirmed)),
                state = reading?.confirmed,
                since = reading?.since ?: 0,
                lastReading = reading?.last,
                rule = reading?.rule,
                versionCode = versions.value[pkg]?.versionCode,
            )
        }
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** Per app, for Settings and App detail notes. */
    fun availability(packageName: String): Flow<ReelsTimerStatus> =
        live.map { it[packageName]?.availability ?: ReelsTimerStatus.NOT_APPLICABLE }.distinctUntilChanged()

    /** Whether the service should read [packageName]'s screen (switch, consent, version). */
    fun shouldDetect(packageName: String): Boolean =
        inputs.value[packageName]?.let { ReelsOnlyRules.shouldDetect(it) } ?: false

    /** The packages the service reads screens of, for its event filter. */
    fun detectedPackages(): Set<String> = inputs.value.keys.filterTo(mutableSetOf(), ::shouldDetect)

    fun version(packageName: String): AppVersion = versions.value[packageName] ?: AppVersion.UNKNOWN

    /**
     * One screen reading of [packageName] at [now]. Returns when to read again to finish a pending
     * confirmation or the UNKNOWN timeout, or null.
     */
    fun observe(packageName: String, result: SectionResult, now: Long): Long? = synchronized(lock) {
        val state = ReelsState.of(result.verdict)
        val machine = hysteresis.getOrPut(packageName) { ReelsHysteresis() }
        val recheck = machine.observe(state, now)
        lastReadingAt[packageName] = now
        publish(packageName, Reading(machine.confirmed, machine.confirmedSince, state, result.rule))
        recheck
    }

    override fun onAppLeft(packageName: String) = synchronized(lock) {
        lastReadingAt.remove(packageName)
        if (hysteresis.remove(packageName) != null || packageName in readings.value) {
            readings.value = readings.value - packageName
        }
    }

    /** For the watchdog: when [packageName] was last read, and whether a state is confirmed. */
    fun watchdogInputs(packageName: String): Pair<Long?, Boolean> = synchronized(lock) {
        lastReadingAt[packageName] to (hysteresis[packageName]?.confirmed != null)
    }

    /**
     * The watchdog found no reading for too long: forget the confirmed state, as if the app had
     * just opened. No cover can outlive a detector that stopped reporting (fail open).
     */
    fun resetStale(packageName: String) = onAppLeft(packageName)

    /** The service stopped: no readings until it runs again (every app falls back meanwhile). */
    fun onServiceStopped() = synchronized(lock) {
        hysteresis.clear()
        lastReadingAt.clear()
        readings.value = emptyMap()
    }

    /** Apps get updated: re-read the installed versions (on resume and when the service connects). */
    fun refresh() {
        scope.launch(Dispatchers.IO) { versions.value = readVersions() }
    }

    private fun publish(packageName: String, reading: Reading) {
        readings.value = readings.value + (packageName to reading)
    }

    private fun readVersions(): Map<String, AppVersion> =
        ReelsOnlyPillConfig.PACKAGES.associateWith { installedApps.version(it) }

    private data class Reading(
        val confirmed: ReelsState?,
        val since: Long,
        val last: ReelsState,
        val rule: String,
    )
}
