package com.sih.app.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DiagnosisDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(diagnosis: DiagnosisEntity): Long

    @Update
    suspend fun update(diagnosis: DiagnosisEntity): Int

    @Query("SELECT * FROM diagnosis_records WHERE id = :id LIMIT 1")
    suspend fun getDiagnosisById(id: String): DiagnosisEntity?

    @Query("SELECT * FROM diagnosis_records ORDER BY createdAt DESC")
    fun getAllDiagnosesFlow(): Flow<List<DiagnosisEntity>>

    @Query("SELECT * FROM diagnosis_records ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestDiagnosis(): DiagnosisEntity?

    @Query("SELECT * FROM diagnosis_records ORDER BY createdAt DESC LIMIT 1")
    fun getLatestDiagnosisFlow(): Flow<DiagnosisEntity?>

    @Query("SELECT * FROM diagnosis_records WHERE syncStatus IN ('PENDING', 'FAILED') ORDER BY createdAt ASC")
    suspend fun getPendingDiagnoses(): List<DiagnosisEntity>

    @Query(
        """
        UPDATE diagnosis_records 
        SET syncStatus = :syncStatus, 
            backendDiagnosisId = :backendId, 
            syncedAt = :syncedAt, 
            lastSyncError = :error,
            retryCount = :retryCount
        WHERE id = :id
        """
    )
    suspend fun updateSyncStatus(
        id: String,
        syncStatus: SyncStatus,
        backendId: String?,
        syncedAt: Long?,
        error: String?,
        retryCount: Int,
    ): Int

    @Query("DELETE FROM diagnosis_records")
    suspend fun deleteAll(): Int
}
