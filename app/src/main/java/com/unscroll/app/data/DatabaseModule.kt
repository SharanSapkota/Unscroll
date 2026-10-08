package com.unscroll.app.data

import android.content.Context
import androidx.room.Room
import com.unscroll.app.data.db.ALL_MIGRATIONS
import com.unscroll.app.data.db.BlockingDao
import com.unscroll.app.data.db.FrictionDao
import com.unscroll.app.data.db.SessionDao
import com.unscroll.app.data.db.TrackedAppDao
import com.unscroll.app.data.db.UnscrollDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): UnscrollDatabase =
        Room.databaseBuilder(context, UnscrollDatabase::class.java, UnscrollDatabase.NAME)
            // Explicit migrations only: never fall back to wiping the user's history.
            .addMigrations(*ALL_MIGRATIONS)
            .build()

    @Provides
    fun provideSessionDao(database: UnscrollDatabase): SessionDao = database.sessionDao()

    @Provides
    fun provideBlockingDao(database: UnscrollDatabase): BlockingDao = database.blockingDao()

    @Provides
    fun provideFrictionDao(database: UnscrollDatabase): FrictionDao = database.frictionDao()

    @Provides
    fun provideTrackedAppDao(database: UnscrollDatabase): TrackedAppDao = database.trackedAppDao()
}
