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

    /**
     * Returns all active official alerts issued within the last 24 hours.
     * Strict 24-hour cutoff: drops any alerts older than 24 hours.
     * Ordered by Severity (EXTREME -> SEVERE -> MODERATE) then newest issued time.
     */
    @Query("""
        SELECT * FROM emergency_alerts 
        WHERE effectiveEpoch >= :minEffectiveEpoch 
          AND expiresEpoch >= :currentTimeEpoch 
        ORDER BY 
            CASE severity 
                WHEN 'EXTREME' THEN 1 
                WHEN 'SEVERE' THEN 2 
                WHEN 'MODERATE' THEN 3 
                ELSE 4 
            END, 
            effectiveEpoch DESC
    """)
    fun getActiveAlertsLast24Hours(
        minEffectiveEpoch: Long,
        currentTimeEpoch: Long
    ): Flow<List<EmergencyAlertEntity>>

    @Query("SELECT * FROM emergency_alerts WHERE id = :id LIMIT 1")
    suspend fun getAlertById(id: String): EmergencyAlertEntity?

    @Query("SELECT MAX(effectiveEpoch) FROM emergency_alerts")
    suspend fun getLatestAlertEpoch(): Long?

    @Query("SELECT MAX(syncTimestampEpoch) FROM emergency_alerts")
    suspend fun getLatestSyncTimestamp(): Long?

    @Query("SELECT * FROM emergency_alerts WHERE effectiveEpoch >= :minEffectiveEpoch AND expiresEpoch >= :currentTimeEpoch")
    suspend fun getActiveAlertsSnapshot(minEffectiveEpoch: Long, currentTimeEpoch: Long): List<EmergencyAlertEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(alert: EmergencyAlertEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(alerts: List<EmergencyAlertEntity>)

    /**
     * Deletes alerts that are either older than 24 hours or expired.
     */
    @Query("DELETE FROM emergency_alerts WHERE effectiveEpoch < :minEffectiveEpoch OR expiresEpoch < :currentTimeEpoch")
    suspend fun deleteOldAlerts(minEffectiveEpoch: Long, currentTimeEpoch: Long)

    /**
     * Resolves conflict with 'Latest Wins' rule.
     * Only updates existing entity if incoming alert is newer or has higher severity.
     */
    @Transaction
    suspend fun syncMeshAlerts(incoming: List<EmergencyAlertEntity>) {
        val now = System.currentTimeMillis()
        val min24h = now - (24 * 3600 * 1000L)

        for (alert in incoming) {
            // Strictly reject incoming alerts older than 24 hours
            if (alert.effectiveEpoch < min24h) continue

            val existing = getAlertById(alert.id)
            if (existing == null || alert.effectiveEpoch > existing.effectiveEpoch || alert.syncTimestampEpoch > existing.syncTimestampEpoch) {
                insertOrUpdate(alert)
            }
        }
    }
}
