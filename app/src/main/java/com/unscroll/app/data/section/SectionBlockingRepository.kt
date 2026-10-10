package com.unscroll.app.data.section

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import com.unscroll.app.data.blocking.LimitRepository
import com.unscroll.app.data.plus.DebugBuild
import com.unscroll.app.data.plus.EntitlementRepository
import com.unscroll.app.domain.ApplicationScope
import com.unscroll.app.domain.blocking.TimedBlock
import com.unscroll.app.domain.scroll.ScrollConsent
import com.unscroll.app.domain.scroll.ScrollCountingInputs
import com.unscroll.app.domain.scroll.ScrollCountingRules
import com.unscroll.app.domain.scroll.ScrollCountingStatus
import com.unscroll.app.domain.section.SectionBlockMode
import com.unscroll.app.domain.section.SectionBlockingRules
import com.unscroll.app.domain.section.SectionBlockingSettings
import com.unscroll.app.domain.section.SectionDetectors
import com.unscroll.app.domain.time.Clock
import com.unscroll.app.service.SectionBlockingService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State of the optional section blocking: the user's settings (DataStore), Plus, whether its
 * accessibility service is switched on in system settings (re-checked on resume, see [refresh])
 * and whether it is connected. Uses the same status rules as scroll counting
 * ([ScrollCountingRules]); the two services are separate and set up separately.
 */
@Singleton
class SectionBlockingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: SectionBlockingPreferences,
    private val limits: LimitRepository,
    private val entitlement: EntitlementRepository,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
    @DebugBuild val inspectorAllowed: Boolean,
) {
    /** Every app section blocking has an entry for. */
    val detectors = SectionDetectors()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val enabledInSettings = MutableStateFlow(checkEnabledInSettings())

    private val _turnOffRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Consent was withdrawn in the app; the running service switches itself off. */
    val turnOffRequests: SharedFlow<Unit> = _turnOffRequests.asSharedFlow()

    val settings: StateFlow<SectionBlockingSettings> = preferences.settings
        .stateIn(scope, SharingStarted.Eagerly, SectionBlockingSettings())

    val isPlus: StateFlow<Boolean> = entitlement.isPlus.stateIn(scope, SharingStarted.Eagerly, false)

    /**
     * Apps whose reels toggle is on (checked against the clock when the stored limits change; a
     * block that ran out since is turned off by BlockExpiryScheduler, and the service checks the
     * clock again before covering anything).
     */
    val reelsBlockedApps: Flow<Set<String>> = limits.observeLimits()
        .map { stored -> stored.filterValues { it.settings.reelsBlocked(clock.now()) }.keys }
        .distinctUntilChanged()

    /** Consent given, not turned off, and Plus. */
    val isActive: Flow<Boolean> = combine(settings, isPlus) { s, plus -> SectionBlockingRules.isActive(s, plus) }
        .distinctUntilChanged()

    private val graceTicks: Flow<Unit> = flow {
        emit(Unit)
        val left = ScrollCountingRules.BIND_GRACE_MILLIS - sinceProcessStart()
        if (left > 0) {
            delay(left)
            emit(Unit)
        }
    }

    /** OFF / NEEDS_CONSENT / NEEDS_ENABLING / ACTIVE / NEEDS_REENABLE, as for scroll counting. */
    val status: Flow<ScrollCountingStatus> = combine(
        settings,
        preferences.connectedSinceConsent,
        enabledInSettings,
        _connected,
        graceTicks,
    ) { s, everConnected, enabled, connected, _ ->
        ScrollCountingRules.status(
            ScrollCountingInputs(
                consent = s.consent,
                enabledInSettings = enabled,
                connected = connected,
                everConnected = everConnected,
                sinceProcessStartMillis = sinceProcessStart(),
            ),
        )
    }.distinctUntilChanged()

    /** Accessibility settings give no callback; call this whenever the app comes back. */
    fun refresh() {
        enabledInSettings.value = checkEnabledInSettings()
    }

    suspend fun setConsent(consent: ScrollConsent) = preferences.setConsent(consent, clock.now())

    /** Withdraws consent and asks the running service to switch itself off. */
    suspend fun withdraw() {
        preferences.setConsent(ScrollConsent.DECLINED, clock.now())
        _turnOffRequests.tryEmit(Unit)
    }

    /** The global kill switch: "Turn off section blocking" (consent and service stay). */
    suspend fun setTurnedOff(turnedOff: Boolean) = preferences.setTurnedOff(turnedOff)

    suspend fun setMode(packageName: String, mode: SectionBlockMode) = preferences.setMode(packageName, mode)

    /**
     * Before the quick toggles, "Block Reels" was a per-app switch in DataStore. Moves any such
     * app to the reels toggle, on until the user turns it off, then forgets the old set. Runs once
     * per process start; does nothing once the set is empty.
     */
    fun migrateLegacyBlockedApps() {
        scope.launch {
            val legacy = preferences.settings.first().legacyBlockedApps
            if (legacy.isEmpty()) return@launch
            legacy.forEach { packageName ->
                limits.updateLimit(packageName) {
                    if (it.reelsBlockedUntil == null) it.copy(reelsBlockedUntil = TimedBlock.FOREVER) else it
                }
            }
            preferences.clearLegacyBlockedApps()
        }
    }

    /** Debug builds only; release builds ignore it. */
    suspend fun setInspector(on: Boolean) {
        if (inspectorAllowed) preferences.setInspector(on)
    }

    fun onServiceConnected() {
        _connected.value = true
        enabledInSettings.value = true
        scope.launch { preferences.onServiceConnected() }
    }

    fun onServiceDisconnected() {
        _connected.value = false
        refresh()
    }

    private fun checkEnabledInSettings(): Boolean = ScrollCountingRules.isServiceEnabled(
        enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ),
        packageName = context.packageName,
        className = SectionBlockingService::class.java.name,
    )

    private fun sinceProcessStart(): Long = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
}
