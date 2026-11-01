package com.itsrkmahapatra.aegismesh.domain.model

/**
 * Hourly weather data point for rendering wind vectors and precipitation on MapLibre.
 * Direct keyless Open-Meteo 168-hour (7 days) dataset.
 */
data class WeatherGridPoint(
    val id: String,                     // Key format: "lat_lon_epochHour"
    val latitude: Double,
    val longitude: Double,
    val hourOffset: Int,                // 0 to 167 (Hour in 7-day forecast)
    val timestampEpoch: Long,           // UTC epoch millis
    val windSpeedKph: Float,            // Wind speed 10m in km/h
    val windDirectionDegrees: Float,    // Wind direction 0-360 degrees
    val windGustsKph: Float,            // Wind gusts 10m in km/h
    val precipitationMm: Float,         // Hourly precipitation in mm
    val temperatureCelsius: Float       // Temperature at 2m in Celsius
)
