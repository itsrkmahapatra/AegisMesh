package com.itsrkmahapatra.aegismesh.domain.model

/**
 * Hourly weather data point for rendering animated radar overlays,
 * wind vectors, emojis, danger signals, and precipitation on MapLibre.
 * 
 * Copyright (c) 2026 Raj Kishor Mahapatra. All Rights Reserved.
 */
data class WeatherGridPoint(
    val id: String,                     // Key format: "lat_lon_epochHour"
    val cityName: String = "Station",
    val latitude: Double,
    val longitude: Double,
    val hourOffset: Int,                // 0 to 167 (Hour in 7-day forecast)
    val timestampEpoch: Long,           // UTC epoch millis
    val windSpeedKph: Float,            // Wind speed 10m in km/h
    val windDirectionDegrees: Float,    // Wind direction 0-360 degrees
    val windGustsKph: Float,            // Wind gusts 10m in km/h
    val precipitationMm: Float,         // Hourly precipitation in mm
    val temperatureCelsius: Float,      // Temperature at 2m in Celsius
    val weatherCode: Int = 0,           // WMO Weather interpretation code
    val surfacePressureHpa: Float = 1010f, // Atmospheric surface pressure in hPa
    val humidityPercent: Int = 75,       // Relative humidity percentage (0-100%)
    val cloudCoverPercent: Int = 40      // Cloud cover percentage (0-100%)
) {
    val conditionEmoji: String get() = when {
        windSpeedKph >= 65f || windGustsKph >= 90f -> "🌀" // Cyclone
        windSpeedKph >= 45f -> "💨"                       // Gale Wind
        weatherCode in listOf(95, 96, 99) -> "⛈️"          // Thunderstorm & Lightning
        precipitationMm >= 8f || weatherCode in listOf(63, 65, 81, 82) -> "🌧️" // Heavy Rain
        precipitationMm >= 0.5f || weatherCode in listOf(51, 53, 55, 61) -> "🌦️" // Light Showers
        weatherCode in listOf(1, 2, 3) -> "⛅"             // Partly Cloudy
        weatherCode in listOf(45, 48) -> "🌫️"              // Fog
        else -> "☀️"                                       // Clear & Fair
    }

    val conditionText: String get() = when {
        windSpeedKph >= 65f || windGustsKph >= 90f -> "Severe Cyclone Gale"
        windSpeedKph >= 45f -> "Squally Wind Gusts"
        weatherCode in listOf(95, 96, 99) -> "Severe Thunderstorm"
        precipitationMm >= 8f -> "Heavy Torrential Rain"
        precipitationMm >= 0.5f -> "Scattered Rain Showers"
        weatherCode in listOf(1, 2, 3) -> "Partly Cloudy"
        weatherCode in listOf(45, 48) -> "Dense Mist & Fog"
        else -> "Clear & Fair Skies"
    }

    val windArrow: String get() = when (((windDirectionDegrees + 22.5) % 360 / 45).toInt()) {
        0 -> "↑ N"
        1 -> "↗ NE"
        2 -> "→ E"
        3 -> "↘ SE"
        4 -> "↓ S"
        5 -> "↙ SW"
        6 -> "← W"
        7 -> "↖ NW"
        else -> "↑ N"
    }

    val signalBadge: String get() = when {
        windSpeedKph >= 90f || windGustsKph >= 120f || surfacePressureHpa < 985f -> "🚨 DANGER 10 (SUPER CYCLONE)"
        windSpeedKph >= 65f || windGustsKph >= 90f || surfacePressureHpa < 995f -> "🚩 DANGER 8 (SEVERE CYCLONE)"
        windSpeedKph >= 45f -> "⚠️ GALE SIGNAL 4"
        precipitationMm >= 15f -> "🌊 FLOOD ADVISORY"
        weatherCode in listOf(95, 96, 99) -> "⚡ LIGHTNING ALERT"
        else -> "🟢 NORMAL RESILIENCE"
    }

    val colorHex: String get() = when {
        windSpeedKph >= 65f || windGustsKph >= 90f -> "#EF4444" // Crimson Red
        windSpeedKph >= 45f -> "#F97316" // Orange
        weatherCode in listOf(95, 96, 99) -> "#A855F7" // Purple
        precipitationMm >= 8f -> "#0284C7" // Deep Blue
        precipitationMm >= 0.5f -> "#06B6D4" // Cyan
        weatherCode in listOf(1, 2, 3) -> "#FBBF24" // Amber
        else -> "#10B981" // Green
    }
}
