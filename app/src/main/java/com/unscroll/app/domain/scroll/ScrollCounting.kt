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

    /** Swipes are only counted with consent, while the service runs. */
    fun shouldCount(consent: ScrollConsent, connected: Boolean): Boolean =
        consent == ScrollConsent.AGREED && connected

    /**
     * Whether [component] ("com.unscroll.app/com.unscroll.app.service.ScrollAccessibilityService")
     * is in the colon-separated Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES value. Entries may
     * use the short class form ("com.unscroll.app/.service.ScrollAccessibilityService").
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
