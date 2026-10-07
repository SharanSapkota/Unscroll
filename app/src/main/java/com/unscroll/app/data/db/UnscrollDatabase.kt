package com.unscroll.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SessionEntity::class,
        AppLimitEntity::class,
        BlockOverrideEntity::class,
        AppFrictionEntity::class,
        NudgeLogEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class UnscrollDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    abstract fun blockingDao(): BlockingDao

    abstract fun frictionDao(): FrictionDao

    companion object {
        const val NAME = "unscroll.db"
    }
}
