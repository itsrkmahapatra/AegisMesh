package com.itsrkmahapatra.aegismesh.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.itsrkmahapatra.aegismesh.domain.model.WeatherGridPoint

@Entity(
    tableName = "weather_grid_points",
    indices = [
        Index(value = ["hourOffset"]),
        Index(value = ["latitude", "longitude"]),
        Index(value = ["cityName"])
    ]
)
data class WeatherGridPointEntity(
    @PrimaryKey
    val id: String, // e.g. "lat_lon_hour"
    val cityName: String,
    val latitude: Double,
    val longitude: Double,
    val hourOffset: Int,
    val timestampEpoch: Long,
    val windSpeedKph: Float,
    val windDirectionDegrees: Float,
    val windGustsKph: Float,
    val precipitationMm: Float,
    val temperatureCelsius: Float,
    val weatherCode: Int
) {
    fun toDomain(): WeatherGridPoint = WeatherGridPoint(
        id = id,
        cityName = cityName,
        latitude = latitude,
        longitude = longitude,
        hourOffset = hourOffset,
        timestampEpoch = timestampEpoch,
        windSpeedKph = windSpeedKph,
        windDirectionDegrees = windDirectionDegrees,
        windGustsKph = windGustsKph,
        precipitationMm = precipitationMm,
        temperatureCelsius = temperatureCelsius,
        weatherCode = weatherCode
    )

    companion object {
        fun fromDomain(domain: WeatherGridPoint): WeatherGridPointEntity = WeatherGridPointEntity(
            id = domain.id,
            cityName = domain.cityName,
            latitude = domain.latitude,
            longitude = domain.longitude,
            hourOffset = domain.hourOffset,
            timestampEpoch = domain.timestampEpoch,
            windSpeedKph = domain.windSpeedKph,
            windDirectionDegrees = domain.windDirectionDegrees,
            windGustsKph = domain.windGustsKph,
            precipitationMm = domain.precipitationMm,
            temperatureCelsius = domain.temperatureCelsius,
            weatherCode = domain.weatherCode
        )
    }
}
