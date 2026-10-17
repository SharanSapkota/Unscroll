package com.unscroll.app.data.scroll

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import com.unscroll.app.data.plus.DebugBuild
import com.unscroll.app.domain.ApplicationScope
import com.unscroll.app.domain.scroll.ScrollConsent
import com.unscroll.app.domain.scroll.ScrollCountingInputs
import com.unscroll.app.domain.scroll.ScrollCountingRules
import com.unscroll.app.domain.scroll.ScrollCountingStatus
import com.unscroll.app.domain.scroll.SwipeDebugFormat
import com.unscroll.app.domain.scroll.SwipeDebugInfo
import com.unscroll.app.domain.time.Clock
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State of the optional scroll-counting feature: the user's consent (DataStore), whether the
 * service is switched on in Accessibility settings (re-checked on resume, see [refresh]) and
 * whether it is connected right now (reported by [ScrollAccessibilityService]).
 */
@Singleton
class ScrollCountingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: ScrollCountingPreferences,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
    @DebugBuild private val debugBuild: Boolean,
) {
    private val _connected = MutableStateFlow(false)

    /** The service is bound and running. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val enabledInSettings = MutableStateFlow(checkEnabledInSettings())

    /** The swipe counter is switched on in Accessibility settings (as of the last [refresh]). */
    val serviceEnabled: StateFlow<Boolean> = enabledInSettings.asStateFlow()

    private val _turnOffRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** The user turned the feature off in the app; the running service switches itself off. */
    val turnOffRequests: SharedFlow<Unit> = _turnOffRequests.asSharedFlow()

    val record: Flow<ScrollCountingRecord> = preferences.record

    /**
     * Set by the running service: performGlobalAction(GLOBAL_ACTION_HOME). The swipe limit's
     * fallback uses it when the cover can't be drawn.
     */
    @Volatile
    var goHomeAction: (() -> Boolean)? = null

    /**
     * Set by the running service: performGlobalAction(GLOBAL_ACTION_BACK). The limit cover's
     * "Take me back to chat" (Instagram/TikTok) uses it.
     */
    @Volatile
    var goBackAction: (() -> Boolean)? = null

    /** Re-evaluated once more when the bind grace period after a process start is over. */
    private val graceTicks: Flow<Unit> = flow {
        emit(Unit)
        val left = ScrollCountingRules.BIND_GRACE_MILLIS - sinceProcessStart()
        if (left > 0) {
            delay(left)
            emit(Unit)
        }
    }

    val status: Flow<ScrollCountingStatus> = combine(
        preferences.record,
        enabledInSettings,
        _connected,
        graceTicks,
    ) { record, enabled, connected, _ ->
        ScrollCountingRules.status(
            ScrollCountingInputs(
                consent = record.consent,
                enabledInSettings = enabled,
                connected = connected,
                everConnected = record.connectedSinceConsent,
                sinceProcessStartMillis = sinceProcessStart(),
            ),
        )
    }.distinctUntilChanged()

    /** Swipes are being counted right now. Kept hot so the swipe limit can read it at once. */
    val isCounting: StateFlow<Boolean> = combine(preferences.record, _connected) { record, connected ->
        ScrollCountingRules.shouldCount(record.consent, connected)
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, false)

    /** First time counting was switched on, or null if it never was (dashboard cards stay hidden). */
    val countingSince: Flow<Long?> = preferences.record.map { it.countingSince }.distinctUntilChanged()

    /** Debug builds: the service reports every event ([reportDebug]); release builds skip it. */
    val debugEnabled: Boolean = debugBuild

    private val _debugInfo = MutableStateFlow(SwipeDebugInfo())

    /** Debug builds only: the last event the service handled (stays empty in release builds). */
    val debugInfo: StateFlow<SwipeDebugInfo> = _debugInfo.asStateFlow()

    /**
     * Debug builds only: records the last event and logs it (tag SwipeDebug): connected, event type,
     * package, outcome and the session's swipe count. Release builds do and log nothing.
     */
    fun reportDebug(info: SwipeDebugInfo) {
        if (!debugEnabled) return
        _debugInfo.value = info
        Log.d(DEBUG_TAG, SwipeDebugFormat.line(_connected.value, info))
    }

    /** Accessibility settings give no callback; call this whenever the app comes back. */
    fun refresh() {
        enabledInSettings.value = checkEnabledInSettings()
    }

    suspend fun setConsent(consent: ScrollConsent) {
        preferences.setConsent(consent, clock.now())
    }

    /** The answer about screen checks (Reels-only timer); "I agree" on the disclosure gives both. */
    suspend fun setReelsConsent(consent: ScrollConsent) {
        preferences.setReelsConsent(consent, clock.now())
    }

    /** "Not now" on Home's "Turn on swipe counting" banner. */
    suspend fun dismissSetupBanner() {
        preferences.dismissSetupBanner(clock.now())
    }

    /** "Turn off": withdraws consent and asks the running service to switch itself off. */
    suspend fun turnOff() {
        preferences.setConsent(ScrollConsent.DECLINED, clock.now())
        preferences.setReelsConsent(ScrollConsent.DECLINED, clock.now())
        _turnOffRequests.tryEmit(Unit)
    }

    fun onServiceConnected() {
        _connected.value = true
        enabledInSettings.value = true
        scope.launch { preferences.onServiceConnected(clock.now()) }
    }

    fun onServiceDisconnected() {
        _connected.value = false
        refresh()
    }

    /**
     * Switched on in Accessibility settings: the secure setting lists our component, or (some OEM
     * builds are late to write the setting) AccessibilityManager reports it enabled. The component
     * is always built from context.packageName ([scrollServiceComponent]).
     */
    private fun checkEnabledInSettings(): Boolean {
        val component = scrollServiceComponent(context)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        )
        return isAccessibilityServiceEnabled(enabledServices, component) || enabledInAccessibilityManager(component)
    }

    private fun enabledInAccessibilityManager(component: ComponentName): Boolean = try {
        context.getSystemService(AccessibilityManager::class.java)
            ?.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .orEmpty()
            .any { info ->
                val service = info.resolveInfo?.serviceInfo
                service != null && service.packageName == component.packageName && service.name == component.className
            }
    } catch (e: RuntimeException) {
        false
    }

    private companion object {
        const val DEBUG_TAG = "SwipeDebug"
    }

    private fun sinceProcessStart(): Long = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
}
