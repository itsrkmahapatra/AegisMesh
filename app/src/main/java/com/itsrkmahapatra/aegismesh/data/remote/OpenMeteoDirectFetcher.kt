package com.itsrkmahapatra.aegismesh.data.remote

import com.itsrkmahapatra.aegismesh.domain.model.WeatherGridPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Direct on-device Open-Meteo Weather Client.
 * Fetches 168-hour (7 days) high-resolution wind, gust vectors, and precipitation.
 * 100% Keyless, zero billing, open-access.
 */
class OpenMeteoDirectFetcher(
    private val okHttpClient: OkHttpClient = NdmaDirectRssFetcher.createDefaultClient()
) {

    private val isoParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    suspend fun fetch7DayHourlyForecast(
        latitude: Double,
        longitude: Double
    ): Result<List<WeatherGridPoint>> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.open-meteo.com/v1/forecast?" +
                    "latitude=${String.format(Locale.US, "%.4f", latitude)}&" +
                    "longitude=${String.format(Locale.US, "%.4f", longitude)}&" +
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

                val json = JSONObject(bodyString)
                val hourly = json.getJSONObject("hourly")

                val times = hourly.getJSONArray("time")
                val windSpeeds = hourly.getJSONArray("wind_speed_10m")
                val windDirs = hourly.getJSONArray("wind_direction_10m")
                val windGusts = hourly.getJSONArray("wind_gusts_10m")
                val precips = hourly.getJSONArray("precipitation")
                val temps = hourly.getJSONArray("temperature_2m")

                val points = mutableListOf<WeatherGridPoint>()
                val count = times.length()

                for (i in 0 until count) {
                    val timeStr = times.getString(i)
                    val epoch = try {
                        isoParser.parse(timeStr)?.time ?: (System.currentTimeMillis() + i * 3600_000L)
                    } catch (_: Exception) {
                        System.currentTimeMillis() + i * 3600_000L
                    }

                    val speed = windSpeeds.optDouble(i, 0.0).toFloat()
                    val dir = windDirs.optDouble(i, 0.0).toFloat()
                    val gust = windGusts.optDouble(i, 0.0).toFloat()
                    val precip = precips.optDouble(i, 0.0).toFloat()
                    val temp = temps.optDouble(i, 25.0).toFloat()

                    val id = "grid_${String.format(Locale.US, "%.2f_%.2f", latitude, longitude)}_h$i"

                    points.add(
                        WeatherGridPoint(
                            id = id,
                            latitude = latitude,
                            longitude = longitude,
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

                Result.success(points)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
