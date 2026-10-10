package com.unscroll.app

import android.app.Application
import com.unscroll.app.data.plus.EntitlementRepository
import com.unscroll.app.data.section.SectionBlockingRepository
import com.unscroll.app.service.BlockExpiryScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class UnscrollApplication : Application() {

    @Inject
    lateinit var entitlement: EntitlementRepository

    @Inject
    lateinit var blockExpiry: BlockExpiryScheduler

    @Inject
    lateinit var sectionBlocking: SectionBlockingRepository

    override fun onCreate() {
        super.onCreate()
        // Ask Play Billing whether the user has Plus, and listen for purchase changes. Until it
        // answers (or when it can't), the last known state applies.
        entitlement.start()
        // Timed quick blocks turn themselves off when they run out, also with the app closed.
        blockExpiry.start()
        // One-time move of the old per-app "Block Reels" switches to the reels toggle.
        sectionBlocking.migrateLegacyBlockedApps()
    }
}
