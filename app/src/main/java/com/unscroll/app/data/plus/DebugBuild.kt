package com.unscroll.app.data.plus

import com.unscroll.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Qualifier
import javax.inject.Singleton

/** True in debug builds only. Gates the "force Plus" switch: release builds have no bypass. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DebugBuild

/** Payments on or off ([PlusConfig.BILLING_ENABLED]). Injected, so tests can run both states. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BillingEnabled

/**
 * Payments (Unscroll Plus) on or off. The value comes from `billingEnabled` in app/build.gradle.kts,
 * the one place to change it (docs/RELEASE.md). While it is false:
 * - every formerly Plus feature is unlocked for everyone (EntitlementRepository.isPlus is true);
 * - no BillingClient is created ([NoBillingGateway]), so Play Billing is never called;
 * - the paywall, the free-app pick, the "Plus ended" banner and every Plus row are hidden.
 * Classes take it as `@BillingEnabled`; composables read it here.
 */
object PlusConfig {
    val BILLING_ENABLED: Boolean = BuildConfig.BILLING_ENABLED
}

@Module
@InstallIn(SingletonComponent::class)
object PlusModule {
    @Provides
    @DebugBuild
    fun provideDebugBuild(): Boolean = BuildConfig.DEBUG

    @Provides
    @BillingEnabled
    fun provideBillingEnabled(): Boolean = PlusConfig.BILLING_ENABLED

    /** Play Billing only with payments on; otherwise PlayBillingGateway is never even constructed. */
    @Provides
    @Singleton
    fun provideBillingGateway(
        @BillingEnabled billingEnabled: Boolean,
        playBilling: Provider<PlayBillingGateway>,
    ): BillingGateway = if (billingEnabled) playBilling.get() else NoBillingGateway
}
