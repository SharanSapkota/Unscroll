package com.unscroll.app

import android.app.Application
import com.unscroll.app.data.plus.EntitlementRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class UnscrollApplication : Application() {

    @Inject
    lateinit var entitlement: EntitlementRepository

    override fun onCreate() {
        super.onCreate()
        // Ask Play Billing whether the user has Plus, and listen for purchase changes. Until it
        // answers (or when it can't), the last known state applies.
        entitlement.start()
    }
}
