package com.unscroll.app.domain.tracking

import kotlinx.coroutines.flow.Flow

/** Emits the package name of the app in the foreground, or null when unknown or screen is off. */
interface ForegroundAppDetector {
    val foregroundApp: Flow<String?>
}

/** Emits whether the screen is on, starting with the current state. */
interface ScreenStateSource {
    val isScreenOn: Flow<Boolean>
}
