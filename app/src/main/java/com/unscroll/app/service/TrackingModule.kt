package com.unscroll.app.service

import com.unscroll.app.domain.ApplicationScope
import com.unscroll.app.domain.time.Clock
import com.unscroll.app.domain.time.SystemClock
import com.unscroll.app.domain.tracking.ForegroundAppDetector
import com.unscroll.app.domain.tracking.ScreenStateSource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
abstract class TrackingModule {

    @Binds
    abstract fun bindForegroundAppDetector(impl: AppDetector): ForegroundAppDetector

    @Binds
    abstract fun bindScreenStateSource(impl: ScreenStateMonitor): ScreenStateSource

    companion object {
        @Provides
        fun provideClock(): Clock = SystemClock

        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(): CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
