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
 * Queries a dense, high-resolution regional weather radar grid across 20 distinct Indian cities & coastal hubs.
 * 100% Keyless, zero billing, open-access.
 */
class OpenMeteoDirectFetcher(
    private val okHttpClient: OkHttpClient = NdmaDirectRssFetcher.createDefaultClient()
) {

    private val isoParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    data class StationMetadata(val name: String, val lat: Double, val lon: Double)

    companion object {
        val NATIONAL_STATIONS = listOf(
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

    suspend fun fetchRegionalWeatherGrid(
        userLat: Double,
        userLon: Double
    ): Result<List<WeatherGridPoint>> = withContext(Dispatchers.IO) {
        try {
            val stationList = mutableListOf(StationMetadata("Your GPS Location", userLat, userLon))
            stationList.addAll(NATIONAL_STATIONS)

            val latStr = stationList.joinToString(",") { String.format(Locale.US, "%.2f", it.lat) }
            val lonStr = stationList.joinToString(",") { String.format(Locale.US, "%.2f", it.lon) }

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
                    return@withContext Result.failure(Exception("Open-Meteo HTTP error: ${response.code}"))
                }

                val bodyString = response.body?.string()
                    ?: return@withContext Result.failure(Exception("Empty body from Open-Meteo"))

                val trimmed = bodyString.trim()
                val allPoints = mutableListOf<WeatherGridPoint>()

                if (trimmed.startsWith("[")) {
                    val jsonArray = JSONArray(trimmed)
                    for (i in 0 until jsonArray.length()) {
                        val locObj = jsonArray.getJSONObject(i)
                        val meta = stationList.getOrElse(i) { StationMetadata("Station $i", userLat, userLon) }
                        val lat = locObj.optDouble("latitude", meta.lat)
                        val lon = locObj.optDouble("longitude", meta.lon)
                        parseLocationHourly(locObj, meta.name, lat, lon, allPoints)
                    }
                } else {
                    val json = JSONObject(trimmed)
                    val meta = stationList[0]
                    parseLocationHourly(json, meta.name, userLat, userLon, allPoints)
                }

                Result.success(allPoints)
            }
        } catch (e: Exception) {
            Result.failure(e)
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

            val speed = windSpeeds.optDouble(i, 12.0).toFloat()
            val dir = windDirs.optDouble(i, 45.0).toFloat()
            val gust = windGusts.optDouble(i, speed * 1.3).toFloat()
            val precip = precips.optDouble(i, 0.0).toFloat()
            val code = weatherCodes.optInt(i, 0)
            val temp = temps.optDouble(i, 27.0).toFloat()

            val id = "grid_${cityName.replace(" ", "_")}_h$i"

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
