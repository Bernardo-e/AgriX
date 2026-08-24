package com.sih.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FarmDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveFarm(farm: FarmEntity): Long

    @Query("SELECT * FROM farm_profile WHERE id = 1 LIMIT 1")
    suspend fun getFarm(): FarmEntity?

    @Query("SELECT * FROM farm_profile WHERE id = 1 LIMIT 1")
    fun getFarmFlow(): Flow<FarmEntity?>

    @Query("DELETE FROM farm_profile WHERE id = 1")
    suspend fun deleteFarm(): Int
}
