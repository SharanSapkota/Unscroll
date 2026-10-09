package com.unscroll.app.data.plus

import android.app.Activity
import com.unscroll.app.domain.plus.LaunchResult
import com.unscroll.app.domain.plus.OfferResult
import com.unscroll.app.domain.plus.PlusOffer
import com.unscroll.app.domain.plus.PurchaseUpdate
import com.unscroll.app.domain.plus.PurchasesResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Google Play Billing, behind an interface so EntitlementRepository can be tested with a fake.
 * Implementations never throw: every failure comes back as "unavailable".
 */
interface BillingGateway {
    /**
     * Debug builds only: the last Billing call's response code name for the paywall, e.g.
     * "queryProductDetails: ITEM_UNAVAILABLE". Always null in release builds.
     */
    val debugStatus: StateFlow<String?>

    /** Play's purchase callbacks (after the purchase flow, or when a pending purchase completes). */
    val purchaseUpdates: Flow<PurchaseUpdate>

    /** The user's active subscriptions. */
    suspend fun queryPurchases(): PurchasesResult

    /** Acknowledges a purchase. Returns whether Play confirmed it. */
    suspend fun acknowledge(purchaseToken: String): Boolean

    /** The monthly base plan with its localized price. */
    suspend fun queryOffer(): OfferResult

    /** Opens Play's purchase sheet for [offer]. */
    suspend fun launchPurchase(activity: Activity, offer: PlusOffer): LaunchResult
}
