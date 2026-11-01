package com.itsrkmahapatra.aegismesh.data.remote

import com.itsrkmahapatra.aegismesh.domain.model.WeatherGridPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Direct on-device Open-Meteo Weather Client.
 * Queries a high-resolution regional weather radar grid across Indian coastal and inland sectors.
 * 100% Keyless, zero billing, open-access.
 */
class OpenMeteoDirectFetcher(
    private val okHttpClient: OkHttpClient = NdmaDirectRssFetcher.createDefaultClient()
) {

    private val isoParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    suspend fun fetchRegionalWeatherGrid(
        userLat: Double,
        userLon: Double
    ): Result<List<WeatherGridPoint>> = withContext(Dispatchers.IO) {
        try {
            // Strategic disaster monitoring hubs across Bay of Bengal & Arabian Sea + User GPS
            val lats = listOf(
                userLat,
                19.81, // Odisha Coast (Puri/Paradip)
                21.95, // West Bengal Delta & Sundarbans
                17.68, // Andhra Coastal Belt (Visakhapatnam)
                13.08, // Tamil Nadu Coromandel (Chennai)
                21.52, // Gujarat Saurashtra (Veraval/Porbandar)
                18.92, // Maharashtra (Mumbai)
                9.93,  // Kerala Coast (Kochi)
                11.62  // Andaman & Nicobar (Cyclone genesis zone)
            )
            val lons = listOf(
                userLon,
                85.83,
                88.93,
                83.21,
                80.27,
                70.45,
                72.83,
                76.26,
                92.72
            )

            val latStr = lats.joinToString(",") { String.format(Locale.US, "%.2f", it) }
            val lonStr = lons.joinToString(",") { String.format(Locale.US, "%.2f", it) }

            val url = "https://api.open-meteo.com/v1/forecast?" +
                    "latitude=$latStr&longitude=$lonStr&" +
                    "hourly=wind_speed_10m,wind_direction_10m,wind_gusts_10m,precipitation,temperature_2m&" +
                    "forecast_days=7&timezone=UTC"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", NdmaDirectRssFetcher.USER_AGENT)
                .header("Accept", "application/json")
                .header("Accept-Encoding", "gzip")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("Open-Meteo HTTP error: ${response.code}"))
                }

                val bodyString = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty body from Open-Meteo"))

                val trimmed = bodyString.trim()
                val allPoints = mutableListOf<WeatherGridPoint>()

                if (trimmed.startsWith("[")) {
                    // Multi-location array returned by Open-Meteo
                    val jsonArray = JSONArray(trimmed)
                    for (locIdx in 0 until jsonArray.length()) {
                        val locObj = jsonArray.getJSONObject(locIdx)
                        val lat = locObj.optDouble("latitude", lats.getOrElse(locIdx) { userLat })
                        val lon = locObj.optDouble("longitude", lons.getOrElse(locIdx) { userLon })
                        parseLocationHourly(locObj, lat, lon, allPoints)
                    }
                } else {
                    // Single location object
                    val json = JSONObject(trimmed)
                    parseLocationHourly(json, userLat, userLon, allPoints)
                }

                Result.success(allPoints)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseLocationHourly(
        locObj: JSONObject,
        lat: Double,
        lon: Double,
        outList: MutableList<WeatherGridPoint>
    ) {
        val hourly = locObj.optJSONObject("hourly") ?: return
        val times = hourly.optJSONArray("time") ?: return
        val windSpeeds = hourly.optJSONArray("wind_speed_10m") ?: JSONArray()
        val windDirs = hourly.optJSONArray("wind_direction_10m") ?: JSONArray()
        val windGusts = hourly.optJSONArray("wind_gusts_10m") ?: JSONArray()
        val precips = hourly.optJSONArray("precipitation") ?: JSONArray()
        val temps = hourly.optJSONArray("temperature_2m") ?: JSONArray()

        val count = times.length()
        val baseTime = System.currentTimeMillis()

        for (i in 0 until count) {
            val timeStr = times.optString(i, "")
            val epoch = try {
                isoParser.parse(timeStr)?.time ?: (baseTime + i * 3600_000L)
            } catch (_: Exception) {
                baseTime + i * 3600_000L
            }

            val speed = windSpeeds.optDouble(i, 15.0).toFloat()
            val dir = windDirs.optDouble(i, 45.0).toFloat()
            val gust = windGusts.optDouble(i, speed * 1.3).toFloat()
            val precip = precips.optDouble(i, 0.0).toFloat()
            val temp = temps.optDouble(i, 28.0).toFloat()

            val id = "grid_${String.format(Locale.US, "%.2f_%.2f", lat, lon)}_h$i"

            outList.add(
                WeatherGridPoint(
                    id = id,
                    latitude = lat,
                    longitude = lon,
                    hourOffset = i,
                    timestampEpoch = epoch,
                    windSpeedKph = speed,
                    windDirectionDegrees = dir,
                    windGustsKph = gust,
                    precipitationMm = precip,
                    temperatureCelsius = temp
                )
            )
        }
    }
}
