package com.unscroll.app.data

import android.content.Context
import androidx.room.Room
import com.unscroll.app.data.db.SessionDao
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
        Room.databaseBuilder(context, UnscrollDatabase::class.java, UnscrollDatabase.NAME).build()

    @Provides
    fun provideSessionDao(database: UnscrollDatabase): SessionDao = database.sessionDao()
}
