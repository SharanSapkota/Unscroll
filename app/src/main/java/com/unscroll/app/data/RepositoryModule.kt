package com.unscroll.app.data

import com.unscroll.app.data.onboarding.DataStoreOnboardingRepository
import com.unscroll.app.data.onboarding.OnboardingRepository
import com.unscroll.app.data.permission.AndroidPermissionChecker
import com.unscroll.app.data.permission.PermissionChecker
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
}
