package com.unscroll.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SessionEntity::class, AppLimitEntity::class, BlockOverrideEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class UnscrollDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    abstract fun blockingDao(): BlockingDao

    companion object {
        const val NAME = "unscroll.db"
    }
}
