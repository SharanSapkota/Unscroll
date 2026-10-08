package com.unscroll.app.data.plus

import com.unscroll.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier

/** True in debug builds only. Gates the "force Plus" switch: release builds have no bypass. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DebugBuild

@Module
@InstallIn(SingletonComponent::class)
object PlusModule {
    @Provides
    @DebugBuild
    fun provideDebugBuild(): Boolean = BuildConfig.DEBUG
}
