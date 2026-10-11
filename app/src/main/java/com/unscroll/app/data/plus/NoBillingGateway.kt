package com.unscroll.app.data.plus

import android.app.Activity
import com.unscroll.app.domain.plus.LaunchResult
import com.unscroll.app.domain.plus.OfferResult
import com.unscroll.app.domain.plus.PlusOffer
import com.unscroll.app.domain.plus.PurchaseUpdate
import com.unscroll.app.domain.plus.PurchasesResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The [BillingGateway] while payments are off ([PlusConfig.BILLING_ENABLED] false): no BillingClient,
 * no connection, no queries. EntitlementRepository doesn't call it then either; this only makes
 * sure nothing can reach Play Billing by accident.
 */
object NoBillingGateway : BillingGateway {
    override val debugStatus: StateFlow<String?> = MutableStateFlow<String?>(null).asStateFlow()
    override val purchaseUpdates: Flow<PurchaseUpdate> = emptyFlow()
    override suspend fun queryPurchases(): PurchasesResult = PurchasesResult.Unavailable
    override suspend fun acknowledge(purchaseToken: String): Boolean = false
    override suspend fun queryOffer(): OfferResult = OfferResult.Unavailable
    override suspend fun launchPurchase(activity: Activity, offer: PlusOffer): LaunchResult = LaunchResult.UNAVAILABLE
}
