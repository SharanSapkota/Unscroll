package com.unscroll.app.data.scroll

import android.content.ComponentName
import android.content.Context
import com.unscroll.app.domain.scroll.ScrollCountingRules
import com.unscroll.app.service.ScrollAccessibilityService

/**
 * Unscroll's scroll-counting service as a [ComponentName], built from the running app's own package
 * (context.packageName, the application ID "com.sharansapkota.unscroll") and the service class
 * (namespace "com.unscroll.app"), never from a hardcoded package: the application ID changed once
 * and must never be spelled out here.
 */
fun scrollServiceComponent(context: Context): ComponentName =
    ComponentName(context, ScrollAccessibilityService::class.java)

/**
 * Whether [component] is in Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES ([enabledServices], a
 * colon-separated list of flattened component names).
 */
fun isAccessibilityServiceEnabled(enabledServices: String?, component: ComponentName): Boolean =
    ScrollCountingRules.isServiceEnabled(enabledServices, component.packageName, component.className)
