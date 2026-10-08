package com.unscroll.app.domain.plus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementRulesTest {

    private fun purchase(
        state: PurchaseState,
        acknowledged: Boolean = true,
        product: String = PlusProduct.PRODUCT_ID,
        token: String = "t",
    ) = PlusPurchase(token, listOf(product), state, acknowledged)

    @Test
    fun purchased_isPlus_pendingIsNot() {
        assertTrue(EntitlementRules.hasPlus(listOf(purchase(PurchaseState.PURCHASED))))
        assertFalse(EntitlementRules.hasPlus(listOf(purchase(PurchaseState.PENDING))))
        assertTrue(EntitlementRules.isPending(listOf(purchase(PurchaseState.PENDING))))
        assertFalse(EntitlementRules.hasPlus(emptyList()))
    }

    @Test
    fun otherProducts_dontCount() {
        assertFalse(EntitlementRules.hasPlus(listOf(purchase(PurchaseState.PURCHASED, product = "other"))))
    }

    @Test
    fun onlyUnacknowledgedPlusPurchases_areAcknowledged() {
        val purchases = listOf(
            purchase(PurchaseState.PURCHASED, acknowledged = false, token = "a"),
            purchase(PurchaseState.PURCHASED, acknowledged = true, token = "b"),
            purchase(PurchaseState.PENDING, acknowledged = false, token = "c"),
            purchase(PurchaseState.PURCHASED, acknowledged = false, product = "other", token = "d"),
        )
        assertEquals(listOf("a"), EntitlementRules.toAcknowledge(purchases))
    }

    @Test
    fun ended_onlyWhenPlusWasOn() {
        assertTrue(EntitlementRules.ended(before = true, after = false))
        assertFalse(EntitlementRules.ended(before = null, after = false))
        assertFalse(EntitlementRules.ended(before = false, after = false))
        assertFalse(EntitlementRules.ended(before = true, after = true))
    }

    @Test
    fun manageUrl_pointsAtTheSubscription() {
        assertEquals(
            "https://play.google.com/store/account/subscriptions?sku=unscroll_plus_monthly&package=com.unscroll.app",
            PlusProduct.manageUrl("com.unscroll.app"),
        )
    }
}
