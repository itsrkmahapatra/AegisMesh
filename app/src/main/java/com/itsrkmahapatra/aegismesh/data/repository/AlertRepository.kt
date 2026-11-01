package com.itsrkmahapatra.aegismesh.data.repository

import com.itsrkmahapatra.aegismesh.data.local.dao.AlertDao
import com.itsrkmahapatra.aegismesh.data.local.entity.EmergencyAlertEntity
import com.itsrkmahapatra.aegismesh.data.remote.GitHubPagesCdnFetcher
import com.itsrkmahapatra.aegismesh.data.remote.NdmaDirectRssFetcher
import com.itsrkmahapatra.aegismesh.domain.model.EmergencyAlert
import com.itsrkmahapatra.aegismesh.util.CompressionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class AlertRepository(
    private val alertDao: AlertDao,
    private val ndmaFetcher: NdmaDirectRssFetcher = NdmaDirectRssFetcher(),
    private val cdnFetcher: GitHubPagesCdnFetcher = GitHubPagesCdnFetcher()
) {

    /**
     * Flow of active disaster alerts issued within the last 24 hours.
     * No 50km restriction; displays all nationwide official alerts.
     */
    fun getActiveAlerts(): Flow<List<EmergencyAlert>> {
        val now = System.currentTimeMillis()
        val min24HoursAgo = now - (24 * 3600 * 1000L) // Strict 24-hour window
        return alertDao.getActiveAlertsLast24Hours(min24HoursAgo, now).map { list ->
            list.map { it.toDomain() }
        }
    }

    /**
     * Direct Fetch: First attempts on-device direct NDMA Sachet CAP fetch.
     * Rejects any alerts older than 24 hours and updates the Room Database.
     */
    suspend fun refreshAlerts(): Result<Int> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val min24HoursAgo = now - (24 * 3600 * 1000L)

        // Purge old alerts
        alertDao.deleteOldAlerts(min24HoursAgo, now)

        // 1. Direct NDMA Fetch (Primary direct on-device path)
        val directResult = ndmaFetcher.fetchAlertsDirectly()
        if (directResult.isSuccess) {
            val alerts = directResult.getOrThrow()
            // Strictly filter alerts to last 24 hours only
            val recentAlerts = alerts.filter { it.effectiveEpoch >= min24HoursAgo }
            if (recentAlerts.isNotEmpty()) {
                alertDao.syncMeshAlerts(recentAlerts.map { EmergencyAlertEntity.fromDomain(it) })
                return@withContext Result.success(recentAlerts.size)
            }
        }

        // 2. Fallback to GitHub Pages CDN GZIP Bundle
        val cdnResult = cdnFetcher.fetchPrecompiledBundle()
        if (cdnResult.isSuccess) {
            val alerts = cdnResult.getOrThrow()
            val recentAlerts = alerts.filter { it.effectiveEpoch >= min24HoursAgo }
            if (recentAlerts.isNotEmpty()) {
                alertDao.syncMeshAlerts(recentAlerts.map { EmergencyAlertEntity.fromDomain(it) })
                return@withContext Result.success(recentAlerts.size)
            }
        }

        Result.failure(Exception("Unable to reach NDMA or CDN. Displaying cached alerts."))
    }

    /**
     * Serializes local database active alerts into a compressed GZIP byte bundle (<55 KB)
     * for peer-to-peer Nearby Connections transfer.
     */
    suspend fun exportGzipMeshBundle(deviceId: String): ByteArray = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val min24HoursAgo = now - (24 * 3600 * 1000L)
        val activeAlerts = alertDao.getActiveAlertsSnapshot(min24HoursAgo, now).map { it.toDomain() }

        val root = JSONObject()
        root.put("v", 1)
        root.put("sender", deviceId)
        root.put("ts", now)

        val alertsArray = JSONArray()
        for (alert in activeAlerts) {
            val obj = JSONObject()
            obj.put("id", alert.id)
            obj.put("title", alert.title)
            obj.put("description", alert.description)
            obj.put("instruction", alert.instruction)
            obj.put("category", alert.category)
            obj.put("severity", alert.severity.name)
            obj.put("urgency", alert.urgency)
            obj.put("certainty", alert.certainty)
            obj.put("event", alert.event)
            obj.put("effectiveEpoch", alert.effectiveEpoch)
            obj.put("expiresEpoch", alert.expiresEpoch)
            obj.put("areaDescription", alert.areaDescription)
            obj.put("centroidLat", alert.centroidLat)
            obj.put("centroidLon", alert.centroidLon)
            obj.put("sourceAuthority", alert.sourceAuthority)
            obj.put("syncTimestampEpoch", alert.syncTimestampEpoch)
            obj.put("hop", alert.hopCount + 1)
            obj.put("languageCode", alert.languageCode)
            alertsArray.put(obj)
        }
        root.put("alerts", alertsArray)

        CompressionUtils.compressStringToGzip(root.toString())
    }

    /**
     * Ingests a received GZIP mesh payload from a peer node, checks 24h freshness,
     * and performs 'Latest Wins' timestamp resolution into Room DB.
     */
    suspend fun ingestMeshGzipBundle(gzipBytes: ByteArray): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val jsonString = CompressionUtils.decompressGzipToString(gzipBytes)
            val root = JSONObject(jsonString)
            val alertsArray = root.getJSONArray("alerts")

            val incomingEntities = mutableListOf<EmergencyAlertEntity>()
            val now = System.currentTimeMillis()
            val min24HoursAgo = now - (24 * 3600 * 1000L)

            for (i in 0 until alertsArray.length()) {
                val item = alertsArray.getJSONObject(i)
                val hop = item.optInt("hop", 1)
                if (hop > 5) continue

                val effective = item.getLong("effectiveEpoch")
                val expires = item.getLong("expiresEpoch")

                // Strict 24 hour rejection
                if (effective < min24HoursAgo || expires < now) continue

                incomingEntities.add(
                    EmergencyAlertEntity(
                        id = item.getString("id"),
                        title = item.getString("title"),
                        description = item.getString("description"),
                        instruction = item.optString("instruction", ""),
                        category = item.optString("category", "Met"),
                        severity = item.getString("severity"),
                        urgency = item.optString("urgency", "Immediate"),
                        certainty = item.optString("certainty", "Likely"),
                        event = item.optString("event", "Cyclone Advisory"),
                        effectiveEpoch = effective,
                        expiresEpoch = expires,
                        areaDescription = item.optString("areaDescription", ""),
                        centroidLat = item.optDouble("centroidLat", 20.5937),
                        centroidLon = item.optDouble("centroidLon", 78.9629),
                        polygonGeoJson = null,
                        sourceAuthority = item.optString("sourceAuthority", "NDMA Relay"),
                        syncTimestampEpoch = item.optLong("syncTimestampEpoch", now),
                        hopCount = hop,
                        languageCode = item.optString("languageCode", "en")
                    )
                )
            }

            if (incomingEntities.isNotEmpty()) {
                alertDao.syncMeshAlerts(incomingEntities)
            }

            Result.success(incomingEntities.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getLatestSyncTimestamp(): Long {
        return alertDao.getLatestSyncTimestamp() ?: 0L
    }
}
