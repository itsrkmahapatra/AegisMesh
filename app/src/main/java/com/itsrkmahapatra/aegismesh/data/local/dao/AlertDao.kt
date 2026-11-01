package com.itsrkmahapatra.aegismesh.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.itsrkmahapatra.aegismesh.data.local.entity.EmergencyAlertEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {

    @Query("SELECT * FROM emergency_alerts WHERE expiresEpoch >= :currentTimeEpoch ORDER BY CASE severity WHEN 'EXTREME' THEN 1 WHEN 'SEVERE' THEN 2 WHEN 'MODERATE' THEN 3 ELSE 4 END, syncTimestampEpoch DESC")
    fun getAllActiveAlerts(currentTimeEpoch: Long): Flow<List<EmergencyAlertEntity>>

    @Query("SELECT * FROM emergency_alerts WHERE expiresEpoch >= :currentTimeEpoch AND centroidLat BETWEEN :minLat AND :maxLat AND centroidLon BETWEEN :minLon AND :maxLon ORDER BY CASE severity WHEN 'EXTREME' THEN 1 WHEN 'SEVERE' THEN 2 ELSE 3 END, syncTimestampEpoch DESC")
    fun getAlertsInBoundingBox(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        currentTimeEpoch: Long
    ): Flow<List<EmergencyAlertEntity>>

    @Query("SELECT * FROM emergency_alerts WHERE id = :id LIMIT 1")
    suspend fun getAlertById(id: String): EmergencyAlertEntity?

    @Query("SELECT MAX(syncTimestampEpoch) FROM emergency_alerts")
    suspend fun getLatestSyncTimestamp(): Long?

    @Query("SELECT * FROM emergency_alerts WHERE expiresEpoch >= :currentTimeEpoch")
    suspend fun getAllActiveAlertsSnapshot(currentTimeEpoch: Long): List<EmergencyAlertEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(alert: EmergencyAlertEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(alerts: List<EmergencyAlertEntity>)

    @Query("DELETE FROM emergency_alerts WHERE expiresEpoch < :currentTimeEpoch")
    suspend fun deleteExpiredAlerts(currentTimeEpoch: Long)

    /**
     * Resolves conflict with 'Latest Wins' rule.
     * Only updates existing entity if incoming alert is newer or has higher severity.
     */
    @Transaction
    suspend fun syncMeshAlerts(incoming: List<EmergencyAlertEntity>) {
        for (alert in incoming) {
            val existing = getAlertById(alert.id)
            if (existing == null || alert.syncTimestampEpoch > existing.syncTimestampEpoch) {
                insertOrUpdate(alert)
            }
        }
    }
}

