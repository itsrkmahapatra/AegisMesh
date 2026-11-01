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
 * 100% keyless, zero billing, open-access.
 * 
 * Fetches full 7-day (168-hour) meteorological forecasts directly to the user's device.
 * 
 * Copyright (c) 2026 Raj Kishor Mahapatra. All Rights Reserved.
 */
class OpenMeteoDirectFetcher(
    private val okHttpClient: OkHttpClient = NdmaDirectRssFetcher.createDefaultClient()
) {

    private val isoParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    data class StationMetadata(val name: String, val lat: Double, val lon: Double)

    companion object {
        val REGIONAL_BASELINE_STATIONS = listOf(
            StationMetadata("Puri Coast", 19.81, 85.83),
            StationMetadata("Sundarbans Delta", 21.95, 88.93),
            StationMetadata("Visakhapatnam", 17.68, 83.21),
            StationMetadata("Chennai Marina", 13.08, 80.27),
            StationMetadata("Veraval Port", 20.90, 70.36),
            StationMetadata("Mumbai Coast", 18.92, 72.83),
            StationMetadata("Kochi Malabar", 9.93, 76.26),
            StationMetadata("Port Blair", 11.62, 92.72),
            StationMetadata("Kolkata", 22.57, 88.36),
            StationMetadata("Bhubaneswar", 20.29, 85.82),
            StationMetadata("Digha Coast", 21.62, 87.50),
            StationMetadata("Kakinada", 16.98, 82.24),
            StationMetadata("Cuddalore", 11.75, 79.76),
            StationMetadata("Kandla Kutch", 23.03, 70.21),
            StationMetadata("Panaji Goa", 15.49, 73.82),
            StationMetadata("New Delhi", 28.61, 77.20),
            StationMetadata("Bengaluru", 12.97, 77.59),
            StationMetadata("Hyderabad", 17.38, 78.48),
            StationMetadata("Guwahati", 26.14, 91.73)
        )
    }

    /**
     * Dedicated direct single-location fetch for the user's exact GPS location.
     * Guaranteed high-res 168-hour forecast with zero batch overhead.
     */
    suspend fun fetchSingleLocation(
        cityName: String,
        lat: Double,
        lon: Double
    ): Result<List<WeatherGridPoint>> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.open-meteo.com/v1/forecast?" +
                    "latitude=${String.format(Locale.US, "%.4f", lat)}&" +
                    "longitude=${String.format(Locale.US, "%.4f", lon)}&" +
                    "hourly=wind_speed_10m,wind_direction_10m,wind_gusts_10m,precipitation,weathercode,temperature_2m&" +
                    "forecast_days=7&timezone=UTC"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", NdmaDirectRssFetcher.USER_AGENT)
                .header("Accept", "application/json")
                .header("Accept-Encoding", "gzip")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP ${response.code} from Open-Meteo"))
                }
                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty body"))
                val json = JSONObject(body)
                val points = mutableListOf<WeatherGridPoint>()
                parseLocationHourly(json, cityName, lat, lon, points)
                Result.success(points)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Viewport batch fetcher: queries user's location plus all points visible on the user's display.
     */
    suspend fun fetchLocations(
        stations: List<StationMetadata>
    ): Result<List<WeatherGridPoint>> = withContext(Dispatchers.IO) {
        if (stations.isEmpty()) return@withContext Result.success(emptyList())

        if (stations.size == 1) {
            val s = stations[0]
            return@withContext fetchSingleLocation(s.name, s.lat, s.lon)
        }

        try {
            // Limit batch to at most 12 stations to ensure swift response
            val targetStations = stations.take(12)
            val latStr = targetStations.joinToString(",") { String.format(Locale.US, "%.2f", it.lat) }
            val lonStr = targetStations.joinToString(",") { String.format(Locale.US, "%.2f", it.lon) }

            val url = "https://api.open-meteo.com/v1/forecast?" +
                    "latitude=$latStr&longitude=$lonStr&" +
                    "hourly=wind_speed_10m,wind_direction_10m,wind_gusts_10m,precipitation,weathercode,temperature_2m&" +
                    "forecast_days=7&timezone=UTC"

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", NdmaDirectRssFetcher.USER_AGENT)
                .header("Accept", "application/json")
                .header("Accept-Encoding", "gzip")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // Fallback to single user location if batch was rejected
                    val userStation = targetStations.first()
                    return@withContext fetchSingleLocation(userStation.name, userStation.lat, userStation.lon)
                }

                val body = response.body?.string() ?: return@withContext Result.failure(Exception("Empty body"))
                val trimmed = body.trim()
                val allPoints = mutableListOf<WeatherGridPoint>()

                if (trimmed.startsWith("[")) {
                    val jsonArray = JSONArray(trimmed)
                    for (i in 0 until jsonArray.length()) {
                        val locObj = jsonArray.getJSONObject(i)
                        val meta = targetStations.getOrElse(i) { targetStations[0] }
                        val lat = locObj.optDouble("latitude", meta.lat)
                        val lon = locObj.optDouble("longitude", meta.lon)
                        parseLocationHourly(locObj, meta.name, lat, lon, allPoints)
                    }
                } else {
                    val json = JSONObject(trimmed)
                    val meta = targetStations[0]
                    parseLocationHourly(json, meta.name, meta.lat, meta.lon, allPoints)
                }

                Result.success(allPoints)
            }
        } catch (e: Exception) {
            // Fallback to single user location if batch failed
            val userStation = stations.first()
            fetchSingleLocation(userStation.name, userStation.lat, userStation.lon)
        }
    }

    private fun parseLocationHourly(
        locObj: JSONObject,
        cityName: String,
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
        val weatherCodes = hourly.optJSONArray("weathercode") ?: JSONArray()
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

            val speed = windSpeeds.optDouble(i, 14.0).toFloat()
            val dir = windDirs.optDouble(i, 45.0).toFloat()
            val gust = windGusts.optDouble(i, speed * 1.35).toFloat()
            val precip = precips.optDouble(i, 0.0).toFloat()
            val code = weatherCodes.optInt(i, 0)
            val temp = temps.optDouble(i, 27.0).toFloat()

            val cleanName = cityName.replace(" ", "_")
            val id = "grid_${cleanName}_h$i"

            outList.add(
                WeatherGridPoint(
                    id = id,
                    cityName = cityName,
                    latitude = lat,
                    longitude = lon,
                    hourOffset = i,
                    timestampEpoch = epoch,
                    windSpeedKph = speed,
                    windDirectionDegrees = dir,
                    windGustsKph = gust,
                    precipitationMm = precip,
                    temperatureCelsius = temp,
                    weatherCode = code
                )
            )
        }
    }
}
