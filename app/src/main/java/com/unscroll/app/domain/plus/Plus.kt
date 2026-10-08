package com.unscroll.app.domain.plus

/**
 * Unscroll Plus, the one subscription. The price is set in Google Play Console and is never
 * written in the app: the paywall shows the price Play Billing returns, in the user's currency.
 */
object PlusProduct {
    /** Subscription product id in Play Console. */
    const val PRODUCT_ID = "unscroll_plus_monthly"

    /** Its monthly base plan. The paywall offers only this plan, never a promotional offer. */
    const val BASE_PLAN_ID = "monthly"

    /** Play Store page where the user manages (or cancels) the subscription. */
    fun manageUrl(packageName: String): String =
        "https://play.google.com/store/account/subscriptions?sku=$PRODUCT_ID&package=$packageName"
}

/** What the free tier includes. Everything works for these apps; Plus only adds more apps. */
object FreeTier {
    /** How many apps a free user can track. Strings and rules all follow this one number. */
    const val FREE_APPS = 1

    /** The single limit on active apps: [FREE_APPS] for free users, unlimited with Plus. */
    fun maxActiveApps(isPlus: Boolean): Int = if (isPlus) Int.MAX_VALUE else FREE_APPS
}
