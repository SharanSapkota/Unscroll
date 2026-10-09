package com.unscroll.app.domain.plus

/**
 * Debug builds only: readable Play Billing results for logcat and the paywall. Pure, so the
 * names can be tested without the Billing library.
 */
object BillingDiagnostics {

    /**
     * The name of a `BillingClient.BillingResponseCode`. The values are the library's public,
     * stable constants; they're listed here (rather than referenced) so deprecated ones like
     * SERVICE_TIMEOUT still get a name.
     */
    fun codeName(code: Int): String = when (code) {
        -3 -> "SERVICE_TIMEOUT"
        -2 -> "FEATURE_NOT_SUPPORTED"
        -1 -> "SERVICE_DISCONNECTED"
        0 -> "OK"
        1 -> "USER_CANCELED"
        2 -> "SERVICE_UNAVAILABLE"
        3 -> "BILLING_UNAVAILABLE"
        4 -> "ITEM_UNAVAILABLE"
        5 -> "DEVELOPER_ERROR"
        6 -> "ERROR"
        7 -> "ITEM_ALREADY_OWNED"
        8 -> "ITEM_NOT_OWNED"
        12 -> "NETWORK_ERROR"
        else -> "UNKNOWN_$code"
    }

    /** The logcat line: "startConnection: responseCode=3 (BILLING_UNAVAILABLE) debugMessage=…". */
    fun logLine(step: String, code: Int, debugMessage: String?, detail: String? = null): String = buildString {
        append(step).append(": responseCode=").append(code).append(" (").append(codeName(code)).append(')')
        append(" debugMessage=").append(debugMessage?.takeIf { it.isNotBlank() } ?: "<none>")
        if (detail != null) append(' ').append(detail)
    }

    /** The paywall's small line: "queryProductDetails: ITEM_UNAVAILABLE" (plus a short detail). */
    fun statusLine(step: String, code: Int, detail: String? = null): String =
        if (detail == null) "$step: ${codeName(code)}" else "$step: ${codeName(code)} · $detail"
}
