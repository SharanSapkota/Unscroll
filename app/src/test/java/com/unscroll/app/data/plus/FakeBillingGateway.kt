package com.unscroll.app.data.plus

import android.app.Activity
import com.unscroll.app.domain.plus.LaunchResult
import com.unscroll.app.domain.plus.OfferResult
import com.unscroll.app.domain.plus.PlusOffer
import com.unscroll.app.domain.plus.PlusProduct
import com.unscroll.app.domain.plus.PlusPurchase
import com.unscroll.app.domain.plus.PurchaseState
import com.unscroll.app.domain.plus.PurchaseUpdate
import com.unscroll.app.domain.plus.PurchasesResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** Play Billing under test control: what it answers, and what it was asked to acknowledge. */
class FakeBillingGateway : BillingGateway {
    /** Null: Billing unavailable. */
    var purchases: List<PlusPurchase>? = emptyList()
    var offer: OfferResult = OfferResult.Available(PlusOffer("€0.67", "offer-token"))
    var launchResult = LaunchResult.LAUNCHED
    var acknowledgeSucceeds = true
    val acknowledged = mutableListOf<String>()
    val updates = MutableSharedFlow<PurchaseUpdate>(extraBufferCapacity = 8)

    override val purchaseUpdates: Flow<PurchaseUpdate> = updates

    override suspend fun queryPurchases(): PurchasesResult =
        purchases?.let { PurchasesResult.Ok(it) } ?: PurchasesResult.Unavailable

    override suspend fun acknowledge(purchaseToken: String): Boolean {
        if (!acknowledgeSucceeds) return false
        acknowledged += purchaseToken
        purchases = purchases?.map { if (it.token == purchaseToken) it.copy(acknowledged = true) else it }
        return true
    }

    override suspend fun queryOffer(): OfferResult = offer

    override suspend fun launchPurchase(activity: Activity, offer: PlusOffer): LaunchResult = launchResult

    companion object {
        fun plus(state: PurchaseState = PurchaseState.PURCHASED, acknowledged: Boolean = true, token: String = "token") =
            PlusPurchase(token, listOf(PlusProduct.PRODUCT_ID), state, acknowledged)
    }
}
