package com.unscroll.app.domain.plus

import org.junit.Assert.assertEquals
import org.junit.Test

class BillingDiagnosticsTest {

    @Test
    fun codeNames_matchBillingResponseCode() {
        assertEquals("OK", BillingDiagnostics.codeName(0))
        assertEquals("BILLING_UNAVAILABLE", BillingDiagnostics.codeName(3))
        assertEquals("ITEM_UNAVAILABLE", BillingDiagnostics.codeName(4))
        assertEquals("DEVELOPER_ERROR", BillingDiagnostics.codeName(5))
        assertEquals("SERVICE_DISCONNECTED", BillingDiagnostics.codeName(-1))
        assertEquals("NETWORK_ERROR", BillingDiagnostics.codeName(12))
        assertEquals("UNKNOWN_99", BillingDiagnostics.codeName(99))
    }

    @Test
    fun logLine_hasTheCodeItsNameAndTheDebugMessage() {
        assertEquals(
            "startConnection: responseCode=3 (BILLING_UNAVAILABLE) debugMessage=Billing service unavailable on device.",
            BillingDiagnostics.logLine("startConnection", 3, "Billing service unavailable on device."),
        )
        assertEquals(
            "queryProductDetailsAsync: responseCode=0 (OK) debugMessage=<none> productDetails=0",
            BillingDiagnostics.logLine("queryProductDetailsAsync", 0, "", "productDetails=0"),
        )
    }

    @Test
    fun statusLine_isShort() {
        assertEquals("launchBillingFlow: DEVELOPER_ERROR", BillingDiagnostics.statusLine("launchBillingFlow", 5))
        assertEquals(
            "queryProductDetailsAsync: OK · 0 ProductDetails",
            BillingDiagnostics.statusLine("queryProductDetailsAsync", 0, "0 ProductDetails"),
        )
    }
}
