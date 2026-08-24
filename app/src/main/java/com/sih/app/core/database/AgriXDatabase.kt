package com.sih.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [FarmEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AgriXDatabase : RoomDatabase() {
    abstract fun farmDao(): FarmDao
}
