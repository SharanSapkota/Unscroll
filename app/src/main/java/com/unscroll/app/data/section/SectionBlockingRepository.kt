package com.unscroll.app.data.section

import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import com.unscroll.app.data.plus.DebugBuild
import com.unscroll.app.data.plus.EntitlementRepository
import com.unscroll.app.domain.ApplicationScope
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
import kotlinx.coroutines.flow.flow
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

    suspend fun setBlocked(packageName: String, blocked: Boolean) = preferences.setBlocked(packageName, blocked)

    suspend fun setMode(packageName: String, mode: SectionBlockMode) = preferences.setMode(packageName, mode)

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
