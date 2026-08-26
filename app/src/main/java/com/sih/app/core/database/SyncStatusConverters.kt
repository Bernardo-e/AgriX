package com.sih.app.core.database

import androidx.room.TypeConverter

class SyncStatusConverters {
    @TypeConverter
    fun fromSyncStatus(status: SyncStatus?): String {
        return status?.name ?: SyncStatus.PENDING.name
    }

    @TypeConverter
    fun toSyncStatus(value: String?): SyncStatus {
        return try {
            if (value != null) SyncStatus.valueOf(value) else SyncStatus.PENDING
        } catch (e: Exception) {
            SyncStatus.PENDING
        }
    }
}
