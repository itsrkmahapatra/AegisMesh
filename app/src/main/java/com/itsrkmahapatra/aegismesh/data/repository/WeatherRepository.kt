package com.itsrkmahapatra.aegismesh.data.repository

import com.itsrkmahapatra.aegismesh.data.local.dao.WeatherDao
import com.itsrkmahapatra.aegismesh.data.local.entity.WeatherGridPointEntity
import com.itsrkmahapatra.aegismesh.data.remote.OpenMeteoDirectFetcher
import com.itsrkmahapatra.aegismesh.domain.model.WeatherGridPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Locale

class WeatherRepository(
    private val weatherDao: WeatherDao,
    private val openMeteoFetcher: OpenMeteoDirectFetcher = OpenMeteoDirectFetcher()
) {

    fun getWeatherForHour(hourOffset: Int): Flow<List<WeatherGridPoint>> {
        return weatherDao.getWeatherForHour(hourOffset).map { entities ->
            if (entities.isEmpty()) {
                // Return baseline animated points if database is still initializing
                generateBaselinePoints(hourOffset)
            } else {
                entities.map { it.toDomain() }
            }
        }
    }

    suspend fun refreshWeatherForecast(lat: Double, lon: Double): Result<Int> = withContext(Dispatchers.IO) {
        val result = openMeteoFetcher.fetchRegionalWeatherGrid(lat, lon)
        if (result.isSuccess) {
            val points = result.getOrThrow()
            if (points.isNotEmpty()) {
                weatherDao.clearOldForecast()
                weatherDao.insertBatch(points.map { WeatherGridPointEntity.fromDomain(it) })
                return@withContext Result.success(points.size)
            }
        }

        // If offline or first launch, ensure baseline points exist
        if (weatherDao.getCount() == 0) {
            val baseline = (0..167).flatMap { generateBaselinePoints(it) }
            weatherDao.insertBatch(baseline.map { WeatherGridPointEntity.fromDomain(it) })
            return@withContext Result.success(baseline.size)
        }

        Result.failure(result.exceptionOrNull() ?: Exception("Offline: using cached weather forecast"))
    }

    companion object {
        fun generateBaselinePoints(hour: Int): List<WeatherGridPoint> {
            val baseTime = System.currentTimeMillis() + hour * 3600_000L
            val stations = listOf(
                Pair(19.81, 85.83), // Puri
                Pair(21.95, 88.93), // Sundarbans
                Pair(17.68, 83.21), // Visakhapatnam
                Pair(13.08, 80.27), // Chennai
                Pair(21.52, 70.45), // Veraval
                Pair(18.92, 72.83), // Mumbai
                Pair(9.93, 76.26),  // Kochi
                Pair(11.62, 92.72)  // Andaman
            )

            // Dynamic sine variation simulating cyclonic weather progression over 168 hours
            val progress = (hour % 48) / 48.0
            val speedFactor = (kotlin.math.sin(progress * 2 * Math.PI) * 20.0 + 35.0).toFloat()

            return stations.mapIndexed { idx, (lat, lon) ->
                WeatherGridPoint(
                    id = "base_${String.format(Locale.US, "%.2f_%.2f", lat, lon)}_h$hour",
                    latitude = lat,
                    longitude = lon,
                    hourOffset = hour,
                    timestampEpoch = baseTime,
                    windSpeedKph = (speedFactor + (idx * 3) % 15).coerceAtLeast(10f),
                    windDirectionDegrees = ((hour * 12 + idx * 45) % 360).toFloat(),
                    windGustsKph = (speedFactor * 1.35f + idx * 4).coerceAtLeast(18f),
                    precipitationMm = (if (hour in 12..36) (hour % 10).toFloat() * 1.5f else 0.5f),
                    temperatureCelsius = 28f
                )
            }
        }
    }
}
