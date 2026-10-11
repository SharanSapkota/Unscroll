package com.unscroll.app.data.section

import com.unscroll.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier

/** Section blocking on or off ([SectionBlockingConfig.ENABLED]). Injected, so tests can run both states. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SectionBlockingEnabled

/** The debug-only Section Inspector may be used ([SectionBlockingConfig.INSPECTOR_ALLOWED]). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SectionInspectorAllowed

/**
 * Section blocking ("Block reels only") on or off. The value comes from `sectionBlockingEnabled`
 * in app/build.gradle.kts, the one place to change it (docs/RELEASE.md "Section blocking on or
 * off"). While it is false:
 * - no app gets the reels toggle; every app has only "Block entire app" (QuickControls);
 * - the section service is removed from the manifest, and if it runs anyway (debug inspector) it
 *   never covers anything, so no detector, version lookup or node-tree reading for blocking;
 * - a stored `reelsBlockedUntil` is ignored (kept in the database, never cleared or acted on);
 * - the disclosure, the Settings row, the Home banner, the "Always / After limit" options and the
 *   paywall bullet are hidden.
 * Classes take it as `@SectionBlockingEnabled`; composables read it here.
 */
object SectionBlockingConfig {
    val ENABLED: Boolean = BuildConfig.SECTION_BLOCKING_ENABLED

    /**
     * Debug builds only: the Section Inspector, with section blocking on or, while it is off, with
     * the separate `-Punscroll.sectionInspector=true` flag. Release builds never have it.
     */
    val INSPECTOR_ALLOWED: Boolean = BuildConfig.DEBUG && (ENABLED || BuildConfig.SECTION_INSPECTOR_FLAG)

    /**
     * The section-blocking setup (Settings › Tracking › Section blocking and its disclosure) is
     * reachable: with section blocking on, or in a debug build with the inspector flag, which
     * needs the consent and the service to capture identifiers.
     */
    val SETUP_SHOWN: Boolean = ENABLED || INSPECTOR_ALLOWED
}

@Module
@InstallIn(SingletonComponent::class)
object SectionBlockingModule {
    @Provides
    @SectionBlockingEnabled
    fun provideSectionBlockingEnabled(): Boolean = SectionBlockingConfig.ENABLED

    @Provides
    @SectionInspectorAllowed
    fun provideSectionInspectorAllowed(): Boolean = SectionBlockingConfig.INSPECTOR_ALLOWED
}
