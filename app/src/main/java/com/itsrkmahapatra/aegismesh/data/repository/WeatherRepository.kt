package com.itsrkmahapatra.aegismesh.data.repository

import com.itsrkmahapatra.aegismesh.data.local.dao.WeatherDao
import com.itsrkmahapatra.aegismesh.data.local.entity.WeatherGridPointEntity
import com.itsrkmahapatra.aegismesh.data.remote.OpenMeteoDirectFetcher
import com.itsrkmahapatra.aegismesh.domain.model.WeatherGridPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class WeatherRepository(
    private val weatherDao: WeatherDao,
    private val openMeteoFetcher: OpenMeteoDirectFetcher = OpenMeteoDirectFetcher()
) {

    fun getWeatherForHour(hourOffset: Int): Flow<List<WeatherGridPoint>> {
        return weatherDao.getWeatherForHour(hourOffset).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun refreshWeatherForecast(lat: Double, lon: Double): Result<Int> = withContext(Dispatchers.IO) {
        val result = openMeteoFetcher.fetch7DayHourlyForecast(lat, lon)
        if (result.isSuccess) {
            val points = result.getOrThrow()
            if (points.isNotEmpty()) {
                weatherDao.clearOldForecast()
                weatherDao.insertBatch(points.map { WeatherGridPointEntity.fromDomain(it) })
                return@withContext Result.success(points.size)
            }
        }
        Result.failure(result.exceptionOrNull() ?: Exception("Failed to fetch weather forecast"))
    }

    suspend fun hasCachedData(): Boolean = withContext(Dispatchers.IO) {
        weatherDao.getCount() > 0
    }
}

