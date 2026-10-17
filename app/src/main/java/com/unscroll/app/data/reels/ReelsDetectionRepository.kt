package com.unscroll.app.data.reels

import com.unscroll.app.data.apps.InstalledApps
import com.unscroll.app.data.overlay.OverlayPreferences
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The Reels-only timer's state per app (Instagram, TikTok): whether it runs or why it falls back
 * ([ReelsTimerStatus]), and the confirmed section from the screen readings that
 * ScrollAccessibilityService reports ([observe], debounced by [ReelsHysteresis]).
 *
 * Only the verdict, the matched rule's identifiers and the version are kept, in memory; nothing
 * about a screen is stored or sent.
 */
@Singleton
class ReelsDetectionRepository @Inject constructor(
    private val overlay: OverlayPreferences,
    private val scrollCounting: ScrollCountingRepository,
    private val installedApps: InstalledApps,
    @ApplicationScope scope: CoroutineScope,
) : ReelsTracking {

    val detectors = SectionDetectors()

    private val versions = MutableStateFlow(readVersions())

    private val lock = Any()
    private val hysteresis = HashMap<String, ReelsHysteresis>()
    private val readings = MutableStateFlow<Map<String, Reading>>(emptyMap())

    /** Everything but the live reading, per Reels-only app. */
    private val baseInputs: Flow<Map<String, ReelsInputs>> = combine(
        overlay.settings,
        scrollCounting.record,
        scrollCounting.connected,
        versions,
    ) { settings, record, connected, versionMap ->
        ReelsOnlyPillConfig.PACKAGES.associateWith { pkg ->
            ReelsInputs(
                packageName = pkg,
                toggleOn = settings.reelsOnlyFor(pkg),
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

    /** Whether the service should read [packageName]'s screen (switch, toggle, consent, version). */
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
        publish(packageName, Reading(machine.confirmed, machine.confirmedSince, state, result.rule))
        recheck
    }

    override fun onAppLeft(packageName: String) = synchronized(lock) {
        if (hysteresis.remove(packageName) != null || packageName in readings.value) {
            readings.value = readings.value - packageName
        }
    }

    /** The service stopped: no readings until it runs again (every app falls back meanwhile). */
    fun onServiceStopped() = synchronized(lock) {
        hysteresis.clear()
        readings.value = emptyMap()
    }

    /** Apps get updated: re-read the installed versions (on resume and when the service connects). */
    fun refresh() {
        versions.value = readVersions()
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
