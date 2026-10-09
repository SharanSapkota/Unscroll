package com.unscroll.app.data.plus

import android.app.Activity
import com.unscroll.app.domain.ApplicationScope
import com.unscroll.app.domain.plus.EntitlementRules
import com.unscroll.app.domain.plus.FreeTier
import com.unscroll.app.domain.plus.LaunchResult
import com.unscroll.app.domain.plus.OfferResult
import com.unscroll.app.domain.plus.PlusCheck
import com.unscroll.app.domain.plus.PlusOffer
import com.unscroll.app.domain.plus.PurchaseUpdate
import com.unscroll.app.domain.plus.PurchasesResult
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Whether the user has Unscroll Plus. Play Billing is the source of truth: it is asked when the
 * app starts, on every resume, and whenever Play reports a purchase change. The last answer is
 * cached on the phone, so Plus keeps working offline or when Billing is unavailable (the cache is
 * only replaced by a real answer). Nothing here ever throws or blocks the app.
 *
 * Purchases are acknowledged as soon as they are seen (Play refunds unacknowledged ones after 3
 * days); a failed acknowledgement is retried on the next check. When Plus ends, the "Plus ended"
 * notice is set; nothing is deleted, the extra apps are only paused (TrackedAppsRepository).
 */
@Singleton
class EntitlementRepository @Inject constructor(
    private val billing: BillingGateway,
    private val preferences: EntitlementPreferences,
    @ApplicationScope private val scope: CoroutineScope,
    @DebugBuild private val debugBuild: Boolean,
) {
    /** Plus on or off. Debug builds can force it; release builds always follow Billing. */
    val isPlus: Flow<Boolean> = combine(preferences.cachedPlus, preferences.debugOverride) { cached, override -> effective(cached, override) }
        .distinctUntilChanged()

    /**
     * How many apps can be active at once: the single provider of the free-tier limit
     * ([FreeTier.maxActiveApps]), used when tracking, picking and adding apps.
     */
    val maxActiveApps: Flow<Int> = isPlus.map { FreeTier.maxActiveApps(it) }

    /** Plus ended on this phone and the user hasn't dismissed the notice yet. */
    val plusEnded: Flow<Boolean> = preferences.plusEnded

    /** The forced state in debug builds (null: follow Billing). Always null in release. */
    val debugOverride: Flow<Boolean?> = if (debugBuild) preferences.debugOverride else flowOf(null)

    private val _pending = MutableStateFlow(false)

    /** A purchase is waiting for payment (e.g. cash at a store). Not Plus yet. */
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    /** Debug builds only: the last Billing response code name, for the paywall. Null in release. */
    val billingDebugStatus: StateFlow<String?> = billing.debugStatus

    /** Purchase flows that failed (not cancelled), for the paywall's message. */
    val purchaseFailures: Flow<PurchaseUpdate> = billing.purchaseUpdates.filter { it == PurchaseUpdate.Failed }

    private val mutex = Mutex()
    private val started = AtomicBoolean(false)

    /** Starts listening to Play's purchase updates and checks once. Safe to call more than once. */
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            billing.purchaseUpdates.collect { if (it == PurchaseUpdate.Changed) check() }
        }
        refresh()
    }

    /** Asks Billing again in the background (app start, resume). */
    fun refresh() {
        scope.launch { check() }
    }

    /** Asks Billing and stores the answer. If Billing is unavailable, the last state stays. */
    suspend fun check(): PlusCheck = mutex.withLock {
        when (val result = billing.queryPurchases()) {
            PurchasesResult.Unavailable -> PlusCheck.UNAVAILABLE
            is PurchasesResult.Ok -> {
                EntitlementRules.toAcknowledge(result.purchases).forEach { billing.acknowledge(it) }
                val plus = EntitlementRules.hasPlus(result.purchases)
                val pending = EntitlementRules.isPending(result.purchases)
                _pending.value = pending
                val override = preferences.debugOverride.first()
                preferences.saveCachedPlus(plus, ended = endedNotice(effective(plus, override)))
                when {
                    plus -> PlusCheck.PLUS
                    pending -> PlusCheck.PENDING
                    else -> PlusCheck.FREE
                }
            }
        }
    }

    /** "Restore purchases": Billing knows the subscriptions of the Google account on this phone. */
    suspend fun restore(): PlusCheck = check()

    suspend fun offer(): OfferResult = billing.queryOffer()

    suspend fun purchase(activity: Activity, offer: PlusOffer): LaunchResult {
        val result = billing.launchPurchase(activity, offer)
        if (result == LaunchResult.ALREADY_OWNED) check()
        return result
    }

    suspend fun dismissPlusEnded() {
        preferences.setPlusEnded(false)
    }

    /** Debug builds only: force Plus on (true), off (false), or follow Billing (null). */
    suspend fun setDebugOverride(plus: Boolean?) {
        if (!debugBuild) return
        mutex.withLock {
            val cached = preferences.cachedPlus.first()
            preferences.setDebugOverride(plus, ended = endedNotice(effective(cached, plus)))
        }
    }

    /** Plus as the app sees it: the debug override (debug builds only), else Billing's last answer. */
    private fun effective(cached: Boolean?, override: Boolean?): Boolean =
        (if (debugBuild) override else null) ?: (cached ?: false)

    /** The notice after a change to [after]: set when Plus just ended, cleared with Plus, else unchanged. */
    private suspend fun endedNotice(after: Boolean): Boolean? = when {
        EntitlementRules.ended(isPlus.first(), after) -> true
        after -> false
        else -> null
    }
}
