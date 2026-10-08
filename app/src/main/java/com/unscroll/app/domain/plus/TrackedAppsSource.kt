package com.unscroll.app.domain.plus

import kotlinx.coroutines.flow.Flow

/** The active and paused tracked apps. Implemented by TrackedAppsRepository. */
interface TrackedAppsSource {
    val state: Flow<TrackedAppsState>

    /** Whether [packageName] is tracked right now. Waits for the first state. */
    suspend fun isActive(packageName: String): Boolean

    /** Whether swipes in [packageName] are counted: tracked, active and "Count swipes" on. */
    suspend fun countsSwipes(packageName: String): Boolean
}
