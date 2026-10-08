package com.unscroll.app.domain.plus

/** A purchase as Play Billing reports it, reduced to what the entitlement needs. */
data class PlusPurchase(
    val token: String,
    val products: List<String>,
    val state: PurchaseState,
    val acknowledged: Boolean,
)

enum class PurchaseState { PURCHASED, PENDING, UNSPECIFIED }

/** Result of asking Play Billing which subscriptions the user owns. */
sealed interface PurchasesResult {
    /** Billing answered. Play returns active subscriptions only (grace period included). */
    data class Ok(val purchases: List<PlusPurchase>) : PurchasesResult

    /** Play Billing couldn't answer (no Play Store, offline, service error). */
    data object Unavailable : PurchasesResult
}

/** The paywall's offer: the base plan with the price Play formatted for the user's currency. */
data class PlusOffer(val formattedPrice: String, val offerToken: String)

sealed interface OfferResult {
    data class Available(val offer: PlusOffer) : OfferResult

    /** Billing unavailable, or the product isn't set up (or not available in this country). */
    data object Unavailable : OfferResult
}

/** What Play reports after a purchase flow, or when a pending purchase completes. */
sealed interface PurchaseUpdate {
    data object Changed : PurchaseUpdate
    data object Cancelled : PurchaseUpdate
    data object Failed : PurchaseUpdate
}

enum class LaunchResult { LAUNCHED, ALREADY_OWNED, UNAVAILABLE }

/**
 * Pure rules on Play's purchase list.
 *
 * - Plus while a purchase of [PlusProduct.PRODUCT_ID] is PURCHASED. Play keeps returning it while
 *   the subscription is active, after the user cancels until the paid period ends, and during a
 *   grace period. Expired subscriptions and those on account hold aren't returned: free again.
 * - PENDING (e.g. cash payment not made yet) is not Plus.
 * - Purchased but unacknowledged purchases must be acknowledged within 3 days, or Play refunds them.
 */
object EntitlementRules {
    fun hasPlus(purchases: List<PlusPurchase>): Boolean =
        purchases.any { it.isPlus() && it.state == PurchaseState.PURCHASED }

    fun isPending(purchases: List<PlusPurchase>): Boolean =
        !hasPlus(purchases) && purchases.any { it.isPlus() && it.state == PurchaseState.PENDING }

    fun toAcknowledge(purchases: List<PlusPurchase>): List<String> = purchases
        .filter { it.isPlus() && it.state == PurchaseState.PURCHASED && !it.acknowledged }
        .map { it.token }

    /** True when Plus just ended: it was on and now isn't. Shows the calm "Plus ended" banner. */
    fun ended(before: Boolean?, after: Boolean): Boolean = before == true && !after

    private fun PlusPurchase.isPlus(): Boolean = PlusProduct.PRODUCT_ID in products
}

/** The outcome of asking Billing again (on resume, or "Restore purchases"). */
enum class PlusCheck { PLUS, PENDING, FREE, UNAVAILABLE }
