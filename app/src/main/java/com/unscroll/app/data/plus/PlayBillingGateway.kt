package com.unscroll.app.data.plus

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClient.ProductType
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.unscroll.app.domain.plus.BillingDiagnostics
import com.unscroll.app.domain.plus.LaunchResult
import com.unscroll.app.domain.plus.OfferResult
import com.unscroll.app.domain.plus.PlusOffer
import com.unscroll.app.domain.plus.PlusProduct
import com.unscroll.app.domain.plus.PlusPurchase
import com.unscroll.app.domain.plus.PurchaseState
import com.unscroll.app.domain.plus.PurchaseUpdate
import com.unscroll.app.domain.plus.PurchasesResult
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [BillingGateway] on the Google Play Billing Library. Billing talks to the Play Store app on the
 * phone; Unscroll itself sends nothing anywhere. No usage data is ever passed to Billing.
 *
 * Never throws: a missing Play Store, a disconnected service or any error is "unavailable", and
 * EntitlementRepository then keeps the last known state.
 */
@Singleton
class PlayBillingGateway @Inject constructor(
    @ApplicationContext private val context: Context,
    @DebugBuild private val debugBuild: Boolean,
) : BillingGateway {

    private val _debugStatus = MutableStateFlow<String?>(null)
    override val debugStatus: StateFlow<String?> = _debugStatus.asStateFlow()

    private val updates = MutableSharedFlow<PurchaseUpdate>(extraBufferCapacity = UPDATE_BUFFER)
    override val purchaseUpdates: Flow<PurchaseUpdate> = updates.asSharedFlow()

    private val listener = PurchasesUpdatedListener { result, _ ->
        // The purchases themselves are re-read with queryPurchases(), which sees all of them.
        val update = when (result.responseCode) {
            BillingResponseCode.OK, BillingResponseCode.ITEM_ALREADY_OWNED -> PurchaseUpdate.Changed
            BillingResponseCode.USER_CANCELED -> PurchaseUpdate.Cancelled
            else -> PurchaseUpdate.Failed
        }
        updates.tryEmit(update)
    }

    private val client: BillingClient by lazy {
        BillingClient.newBuilder(context)
            .setListener(listener)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
    }

    private val connectMutex = Mutex()

    /** The last product details, needed to launch the purchase flow. */
    @Volatile
    private var productDetails: ProductDetails? = null

    override suspend fun queryPurchases(): PurchasesResult = safely<PurchasesResult>(PurchasesResult.Unavailable) {
        if (!connect()) return@safely PurchasesResult.Unavailable
        val params = QueryPurchasesParams.newBuilder().setProductType(ProductType.SUBS).build()
        suspendCancellableCoroutine<PurchasesResult> { cont ->
            val once = Once(cont)
            client.queryPurchasesAsync(params) { result, purchases ->
                once.resume(
                    if (result.responseCode == BillingResponseCode.OK) {
                        PurchasesResult.Ok(purchases.map(::toPlusPurchase))
                    } else {
                        Log.w(TAG, "queryPurchases: ${result.responseCode} ${result.debugMessage}")
                        PurchasesResult.Unavailable
                    },
                )
            }
        }
    }

    override suspend fun acknowledge(purchaseToken: String): Boolean = safely(false) {
        if (!connect()) return@safely false
        val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchaseToken).build()
        suspendCancellableCoroutine<Boolean> { cont ->
            val once = Once(cont)
            client.acknowledgePurchase(params) { result -> once.resume(result.responseCode == BillingResponseCode.OK) }
        }
    }

    override suspend fun queryOffer(): OfferResult = safely<OfferResult>(OfferResult.Unavailable) {
        if (!connect()) return@safely OfferResult.Unavailable
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(PlusProduct.PRODUCT_ID)
            .setProductType(ProductType.SUBS)
            .build()
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(product)).build()
        if (debugBuild) Log.d(TAG, "$STEP_QUERY: productId=${PlusProduct.PRODUCT_ID} productType=${ProductType.SUBS}")
        val details = suspendCancellableCoroutine<ProductDetails?> { cont ->
            val once = Once(cont)
            client.queryProductDetailsAsync(params) { result, detailsResult ->
                if (result.responseCode != BillingResponseCode.OK) {
                    Log.w(TAG, "queryOffer: ${result.responseCode} ${result.debugMessage}")
                }
                if (debugBuild) {
                    val count = detailsResult.productDetailsList.size
                    val unfetched = detailsResult.unfetchedProductList.size
                    debug(
                        STEP_QUERY,
                        result,
                        log = "productId=${PlusProduct.PRODUCT_ID} productDetails=$count unfetched=$unfetched",
                        status = "$count ProductDetails",
                    )
                }
                val ok = result.responseCode == BillingResponseCode.OK
                once.resume(
                    if (ok) detailsResult.productDetailsList.firstOrNull { it.productId == PlusProduct.PRODUCT_ID } else null,
                )
            }
        }
        val offer = details?.let(::baseOffer)
        if (offer == null) {
            if (debugBuild && details != null) {
                Log.d(TAG, "$STEP_QUERY: no offer-free base plan (wanted '${PlusProduct.BASE_PLAN_ID}')")
                _debugStatus.value = "$STEP_QUERY: no base plan '${PlusProduct.BASE_PLAN_ID}'"
            }
            return@safely OfferResult.Unavailable
        }
        productDetails = details
        OfferResult.Available(offer)
    }

    override suspend fun launchPurchase(activity: Activity, offer: PlusOffer): LaunchResult = safely(LaunchResult.UNAVAILABLE) {
        val details = productDetails
        if (details == null || !connect()) return@safely LaunchResult.UNAVAILABLE
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offer.offerToken)
                        .build(),
                ),
            )
            .build()
        val result = withContext(Dispatchers.Main.immediate) { client.launchBillingFlow(activity, params) }
        debug(STEP_LAUNCH, result)
        when (result.responseCode) {
            BillingResponseCode.OK -> LaunchResult.LAUNCHED
            BillingResponseCode.ITEM_ALREADY_OWNED -> LaunchResult.ALREADY_OWNED
            else -> {
                Log.w(TAG, "launchPurchase: ${result.responseCode} ${result.debugMessage}")
                LaunchResult.UNAVAILABLE
            }
        }
    }

    /** Connects if needed. Auto reconnection handles later drops; this covers the first one. */
    private suspend fun connect(): Boolean = connectMutex.withLock {
        if (client.isReady) return@withLock true
        withTimeoutOrNull(CONNECT_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine<Boolean> { cont ->
                val once = Once(cont)
                client.startConnection(
                    object : BillingClientStateListener {
                        override fun onBillingSetupFinished(result: BillingResult) {
                            if (result.responseCode != BillingResponseCode.OK) {
                                Log.w(TAG, "Billing setup: ${result.responseCode} ${result.debugMessage}")
                            }
                            debug(STEP_CONNECT, result)
                            once.resume(result.responseCode == BillingResponseCode.OK)
                        }

                        override fun onBillingServiceDisconnected() {
                            debug(STEP_CONNECT, BillingResponseCode.SERVICE_DISCONNECTED, "onBillingServiceDisconnected")
                            once.resume(false)
                        }
                    },
                )
            }
        } ?: false.also { debugTimeout(STEP_CONNECT) }
    }

    /** Debug builds only: logs the full result and shows its code name on the paywall. */
    private fun debug(step: String, result: BillingResult, log: String? = null, status: String? = null) =
        debug(step, result.responseCode, result.debugMessage, log, status)

    private fun debug(step: String, code: Int, debugMessage: String?, log: String? = null, status: String? = null) {
        if (!debugBuild) return
        Log.d(TAG, BillingDiagnostics.logLine(step, code, debugMessage, log))
        _debugStatus.value = BillingDiagnostics.statusLine(step, code, status)
    }

    private fun debugTimeout(step: String) {
        if (!debugBuild) return
        Log.d(TAG, "$step: no answer within ${CONNECT_TIMEOUT_MILLIS} ms")
        _debugStatus.value = "$step: timed out"
    }

    /** The monthly base plan (never a promotional offer) and its recurring price. */
    private fun baseOffer(details: ProductDetails): PlusOffer? {
        val offers = details.subscriptionOfferDetails.orEmpty()
        val base = offers.firstOrNull { it.basePlanId == PlusProduct.BASE_PLAN_ID && it.offerId == null }
            ?: offers.firstOrNull { it.offerId == null }
            ?: return null
        val price = base.pricingPhases.pricingPhaseList.lastOrNull()?.formattedPrice ?: return null
        return PlusOffer(formattedPrice = price, offerToken = base.offerToken)
    }

    private fun toPlusPurchase(purchase: Purchase) = PlusPurchase(
        token = purchase.purchaseToken,
        products = purchase.products,
        state = when (purchase.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> PurchaseState.PURCHASED
            Purchase.PurchaseState.PENDING -> PurchaseState.PENDING
            else -> PurchaseState.UNSPECIFIED
        },
        acknowledged = purchase.isAcknowledged,
    )

    private suspend fun <T> safely(fallback: T, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Billing call failed", e)
        fallback
    }

    /** Billing may call a listener more than once (e.g. a disconnect after setup); resume only once. */
    private class Once<T>(private val cont: CancellableContinuation<T>) {
        private val done = AtomicBoolean(false)

        fun resume(value: T) {
            if (done.compareAndSet(false, true) && cont.isActive) cont.resume(value)
        }
    }

    private companion object {
        const val TAG = "PlayBilling"
        const val UPDATE_BUFFER = 8
        const val CONNECT_TIMEOUT_MILLIS = 10_000L
        const val STEP_CONNECT = "startConnection"
        const val STEP_QUERY = "queryProductDetailsAsync"
        const val STEP_LAUNCH = "launchBillingFlow"
    }
}
