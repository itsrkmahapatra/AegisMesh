package com.itsrkmahapatra.aegismesh.data.remote

import com.itsrkmahapatra.aegismesh.data.remote.parser.CapXmlParser
import com.itsrkmahapatra.aegismesh.domain.model.EmergencyAlert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Direct on-device client for official NDMA Sachet CAP RSS feed.
 * Zero intermediary servers, zero billing, 100% official data.
 */
class NdmaDirectRssFetcher(
    private val okHttpClient: OkHttpClient = createDefaultClient(),
    private val parser: CapXmlParser = CapXmlParser()
) {

    suspend fun fetchAlertsDirectly(): Result<List<EmergencyAlert>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(NDMA_SACHET_RSS_URL)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/rss+xml, application/xml, text/xml")
                .header("Accept-Encoding", "gzip, deflate")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        Exception("NDMA Sachet HTTP error code: ${response.code}")
                    )
                }

                val bodyStream = response.body?.byteStream()
                    ?: return@withContext Result.failure(Exception("Empty response body from NDMA Sachet"))

                val alerts = parser.parseRss(bodyStream)
                Result.success(alerts)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val NDMA_SACHET_RSS_URL = "https://sachet.ndma.gov.in/cap_public_website/rss/rss_india.xml"
        const val USER_AGENT = "AegisMesh-EmergencyRadar/1.0 (Android; Contact: raju.chikitigada@gmail.com)"

        fun createDefaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }
    }
}
