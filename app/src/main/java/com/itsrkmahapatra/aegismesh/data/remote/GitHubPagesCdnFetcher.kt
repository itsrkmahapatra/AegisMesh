package com.itsrkmahapatra.aegismesh.data.remote

import com.itsrkmahapatra.aegismesh.domain.model.AlertSeverity
import com.itsrkmahapatra.aegismesh.domain.model.EmergencyAlert
import com.itsrkmahapatra.aegismesh.util.CompressionUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Fallback CDN fetcher for the ~55 KB GZIP bundle hosted free on GitHub Pages.
 * Used when direct NDMA RSS feed is congested or during low-bandwidth conditions.
 */
class GitHubPagesCdnFetcher(
    private val okHttpClient: OkHttpClient = NdmaDirectRssFetcher.createDefaultClient()
) {

    suspend fun fetchPrecompiledBundle(): Result<List<EmergencyAlert>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_PAGES_CDN_BUNDLE_URL)
                .header("User-Agent", NdmaDirectRssFetcher.USER_AGENT)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("CDN HTTP error: ${response.code}"))
                }

                val gzipBytes = response.body?.bytes()
                    ?: return@withContext Result.failure(Exception("Empty CDN bundle response"))

                val jsonString = CompressionUtils.decompressGzipToString(gzipBytes)
                val root = JSONObject(jsonString)
                val alertsArray = root.getJSONArray("alerts")

                val alerts = mutableListOf<EmergencyAlert>()
                for (i in 0 until alertsArray.length()) {
                    val item = alertsArray.getJSONObject(i)
                    alerts.add(
                        EmergencyAlert(
                            id = item.getString("id"),
                            title = item.getString("title"),
                            description = item.getString("description"),
                            instruction = item.optString("instruction", "Follow local administration safety advisory."),
                            category = item.optString("category", "Met"),
                            severity = AlertSeverity.fromString(item.getString("severity")),
                            urgency = item.optString("urgency", "Immediate"),
                            certainty = item.optString("certainty", "Likely"),
                            event = item.optString("event", "Severe Cyclone / Flood"),
                            effectiveEpoch = item.getLong("effectiveEpoch"),
                            expiresEpoch = item.getLong("expiresEpoch"),
                            areaDescription = item.optString("areaDescription", "Coastal India"),
                            centroidLat = item.optDouble("centroidLat", 20.5937),
                            centroidLon = item.optDouble("centroidLon", 78.9629),
                            polygonGeoJson = item.optString("polygonGeoJson", null),
                            sourceAuthority = item.optString("sourceAuthority", "NDMA Sachet"),
                            syncTimestampEpoch = item.optLong("syncTimestampEpoch", System.currentTimeMillis()),
                            hopCount = 0
                        )
                    )
                }

                Result.success(alerts)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val GITHUB_PAGES_CDN_BUNDLE_URL = "https://itsrkmahapatra.github.io/AegisMesh/bundle/latest.json.gz"
    }
}
