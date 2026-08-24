package com.sih.app.core.data

import com.sih.app.core.database.FarmDao
import com.sih.app.core.database.FarmEntity
import kotlinx.coroutines.flow.Flow

import android.util.Log

class FarmRepository(
    private val farmDao: FarmDao,
) {
    fun getFarmFlow(): Flow<FarmEntity?> = farmDao.getFarmFlow()

    suspend fun getFarm(): FarmEntity? = farmDao.getFarm()

    suspend fun hasFarmProfile(): Boolean = farmDao.getFarm() != null

    suspend fun saveFarm(
        farmName: String?,
        state: String,
        district: String,
        village: String,
        farmArea: Double,
        farmAreaUnit: String,
        soilType: String,
        currentCrop: String,
    ) {
        val now = System.currentTimeMillis()
        Log.d("AgriX_Debug", "5. [Repository] saveFarm() called. Checking existing farm profile...")
        val existing = farmDao.getFarm()
        Log.d("AgriX_Debug", "5.1. [Repository] Existing farm record: $existing")
        val entity = FarmEntity(
            id = 1L,
            farmName = farmName?.trim()?.ifBlank { null },
            state = state.trim(),
            district = district.trim(),
            village = village.trim(),
            farmArea = farmArea,
            farmAreaUnit = farmAreaUnit,
            soilType = soilType,
            currentCrop = currentCrop,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        Log.d("AgriX_Debug", "6. [DAO] Calling farmDao.saveFarm with entity: $entity")
        val rowId = farmDao.saveFarm(entity)
        Log.d("AgriX_Debug", "7. [Room] farmDao.saveFarm completed. Returned rowId=$rowId")
    }

    suspend fun deleteFarm() = farmDao.deleteFarm()
}
