package com.unscroll.app.domain.scroll

/** The user's answer on the accessibility disclosure screen. */
enum class ScrollConsent {
    /** Never asked, or the user turned the feature off again. */
    NOT_ASKED,
    AGREED,
    DECLINED,
}

/** Where scroll counting stands, from the consent, the system setting and the service connection. */
enum class ScrollCountingStatus {
    /** Off: never set up, declined, or turned off. Everything else works as usual. */
    OFF,

    /** The service was switched on in system settings without agreeing in the app: not counting. */
    NEEDS_CONSENT,

    /** Agreed, but the service isn't switched on in Accessibility settings yet. */
    NEEDS_ENABLING,

    /** Counting. */
    ACTIVE,

    /** Was counting before, but the system switched it off or it stopped (OEM battery killers). */
    NEEDS_REENABLE,
}

/** Which swipe-counting banner Home shows, if any. */
enum class SwipeBanner {
    NONE,

    /** Never enabled: "Turn on swipe counting" (dismissible for a week). */
    SET_UP,

    /** Switched on in Android without agreeing in Unscroll, so nothing is counted (dismissible). */
    FINISH_CONSENT,

    /** Was counting, then the phone switched it off (stays until fixed or turned off). */
    NEEDS_REENABLE,
}

/** Everything that decides the [ScrollCountingStatus]. */
data class ScrollCountingInputs(
    val consent: ScrollConsent,
    /** Unscroll's service is in Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES. */
    val enabledInSettings: Boolean,
    /** The service is bound and running right now. */
    val connected: Boolean,
    /** The service has connected at least once since the user agreed. */
    val everConnected: Boolean,
    /** Time since this process started, to give the system a moment to bind the service. */
    val sinceProcessStartMillis: Long = Long.MAX_VALUE,
)

object ScrollCountingRules {
    /** After a process start the system needs a moment to bind an enabled service. */
    const val BIND_GRACE_MILLIS = 5_000L

    fun status(inputs: ScrollCountingInputs): ScrollCountingStatus = with(inputs) {
        when {
            consent != ScrollConsent.AGREED ->
                if (enabledInSettings || connected) ScrollCountingStatus.NEEDS_CONSENT else ScrollCountingStatus.OFF
            connected -> ScrollCountingStatus.ACTIVE
            enabledInSettings && sinceProcessStartMillis < BIND_GRACE_MILLIS ->
                // Probably still binding; don't flash a warning.
                if (everConnected) ScrollCountingStatus.ACTIVE else ScrollCountingStatus.NEEDS_ENABLING
            everConnected -> ScrollCountingStatus.NEEDS_REENABLE
            else -> ScrollCountingStatus.NEEDS_ENABLING
        }
    }

    /** "Not now" on the set-up banner hides it this long. */
    const val BANNER_SNOOZE_MILLIS = 7L * 24 * 60 * 60 * 1_000

    /** Android 13: "Restricted setting" for apps installed outside an app store. */
    const val RESTRICTED_SETTINGS_SDK = 33

    /**
     * Home's banner. [everEnabled]: the service connected at least once (swipe stats exist); a user
     * who had it and turned it off on purpose isn't asked again, but one whose phone switched it
     * off is ([ScrollCountingStatus.NEEDS_REENABLE]).
     */
    fun banner(status: ScrollCountingStatus, everEnabled: Boolean, dismissedAt: Long?, now: Long): SwipeBanner {
        if (status == ScrollCountingStatus.NEEDS_REENABLE) return SwipeBanner.NEEDS_REENABLE
        if (status == ScrollCountingStatus.ACTIVE) return SwipeBanner.NONE
        val snoozed = dismissedAt != null && now - dismissedAt in 0 until BANNER_SNOOZE_MILLIS
        return when {
            snoozed -> SwipeBanner.NONE
            status == ScrollCountingStatus.NEEDS_CONSENT -> SwipeBanner.FINISH_CONSENT
            everEnabled -> SwipeBanner.NONE
            else -> SwipeBanner.SET_UP
        }
    }

    /**
     * Show the "Allow restricted settings" help: the user agreed and was sent to Accessibility
     * settings, came back, and the service still isn't on, on Android 13+ where sideloaded apps
     * can't switch it on until restricted settings are allowed.
     */
    fun showRestrictedHelp(status: ScrollCountingStatus, sdkInt: Int): Boolean =
        status == ScrollCountingStatus.NEEDS_ENABLING && sdkInt >= RESTRICTED_SETTINGS_SDK

    /** Swipes are only counted with consent, while the service runs. */
    fun shouldCount(consent: ScrollConsent, connected: Boolean): Boolean =
        consent == ScrollConsent.AGREED && connected

    /**
     * Whether [packageName]/[className] is in the colon-separated
     * Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES value. [packageName] is the application ID
     * (context.packageName, "com.sharansapkota.unscroll") and [className] the service class, whose
     * package (namespace "com.unscroll.app") differs: "com.sharansapkota.unscroll/com.unscroll.app.
     * service.ScrollAccessibilityService". The short form ("pkg/.service.X") only exists when the
     * class lives under the application ID.
     */
    fun isServiceEnabled(enabledServices: String?, packageName: String, className: String): Boolean {
        if (enabledServices.isNullOrBlank()) return false
        val full = "$packageName/$className"
        val short = if (className.startsWith("$packageName.")) {
            "$packageName/${className.removePrefix(packageName)}"
        } else {
            full
        }
        return enabledServices.split(':').any { entry ->
            val trimmed = entry.trim()
            trimmed.equals(full, ignoreCase = true) || trimmed.equals(short, ignoreCase = true)
        }
    }
}
