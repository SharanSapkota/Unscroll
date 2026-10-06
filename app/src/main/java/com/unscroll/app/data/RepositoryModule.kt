package com.unscroll.app.data

import com.unscroll.app.data.onboarding.DataStoreOnboardingRepository
import com.unscroll.app.data.onboarding.OnboardingRepository
import com.unscroll.app.data.permission.AndroidPermissionChecker
import com.unscroll.app.data.permission.PermissionChecker
import com.unscroll.app.data.session.SessionRepository
import com.unscroll.app.data.tracking.TrackingPreferences
import com.unscroll.app.data.usage.UsageRepository
import com.unscroll.app.domain.insights.UsageDataSource
import com.unscroll.app.domain.session.HeartbeatStore
import com.unscroll.app.domain.session.SessionStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindPermissionChecker(impl: AndroidPermissionChecker): PermissionChecker

    @Binds
    @Singleton
    abstract fun bindOnboardingRepository(
        impl: DataStoreOnboardingRepository,
    ): OnboardingRepository

    @Binds
    abstract fun bindSessionStore(impl: SessionRepository): SessionStore

    @Binds
    abstract fun bindHeartbeatStore(impl: TrackingPreferences): HeartbeatStore

    @Binds
    abstract fun bindUsageDataSource(impl: UsageRepository): UsageDataSource
}
