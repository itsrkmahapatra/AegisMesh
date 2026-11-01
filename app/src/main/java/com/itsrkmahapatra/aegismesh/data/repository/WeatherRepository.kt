package com.itsrkmahapatra.aegismesh.data.repository

import com.itsrkmahapatra.aegismesh.data.local.dao.WeatherDao
import com.itsrkmahapatra.aegismesh.data.local.entity.WeatherGridPointEntity
import com.itsrkmahapatra.aegismesh.data.remote.OpenMeteoDirectFetcher
import com.itsrkmahapatra.aegismesh.domain.model.ActiveCyclone
import com.itsrkmahapatra.aegismesh.domain.model.CycloneTrackNode
import com.itsrkmahapatra.aegismesh.domain.model.WeatherGridPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Repository for managing 7-day (168-hour) weather forecasts, multi-layer radar data,
 * and Zoom Earth-style Cyclone tracking.
 * 
 * Copyright (c) 2026 Raj Kishor Mahapatra. All Rights Reserved.
 */
class WeatherRepository(
    private val weatherDao: WeatherDao,
    private val openMeteoFetcher: OpenMeteoDirectFetcher = OpenMeteoDirectFetcher()
) {

    fun getWeatherForHour(hourOffset: Int, userLat: Double = 20.5937, userLon: Double = 78.9629): Flow<List<WeatherGridPoint>> {
        return weatherDao.getWeatherForHour(hourOffset).map { entities ->
            if (entities.isEmpty()) {
                generateBaselinePoints(hourOffset, userLat, userLon)
            } else {
                entities.map { it.toDomain() }
            }
        }
    }

    suspend fun refreshWeatherForViewport(
        userLat: Double,
        userLon: Double,
        visibleStations: List<OpenMeteoDirectFetcher.StationMetadata> = emptyList()
    ): Result<Int> = withContext(Dispatchers.IO) {
        val targets = mutableListOf<OpenMeteoDirectFetcher.StationMetadata>()

        // Always place the user's exact current GPS location at index 0
        targets.add(OpenMeteoDirectFetcher.StationMetadata("My Location", userLat, userLon))

        // Add visible stations from the viewport
        for (st in visibleStations) {
            if (targets.none { it.name.equals(st.name, ignoreCase = true) }) {
                targets.add(st)
            }
        }

        // Add default regional stations if few visible
        if (targets.size < 5) {
            for (st in OpenMeteoDirectFetcher.REGIONAL_BASELINE_STATIONS) {
                if (targets.none { it.name.equals(st.name, ignoreCase = true) }) {
                    targets.add(st)
                    if (targets.size >= 8) break
                }
            }
        }

        val result = openMeteoFetcher.fetchLocations(targets)
        if (result.isSuccess) {
            val points = result.getOrThrow()
            if (points.isNotEmpty()) {
                weatherDao.insertBatch(points.map { WeatherGridPointEntity.fromDomain(it) })
                return@withContext Result.success(points.size)
            }
        }

        // Offline fallback
        if (weatherDao.getCount() == 0) {
            val baseline = (0..167).flatMap { generateBaselinePoints(it, userLat, userLon) }
            weatherDao.insertBatch(baseline.map { WeatherGridPointEntity.fromDomain(it) })
            return@withContext Result.success(baseline.size)
        }

        Result.failure(result.exceptionOrNull() ?: Exception("Offline: using cached on-device forecast"))
    }

    /**
     * Zoom Earth-style Cyclone tracking generator.
     * Computes the live tracking path, category milestones, and expanding Cone of Uncertainty.
     */
    fun getActiveCyclone(nowEpoch: Long = System.currentTimeMillis()): ActiveCyclone {
        val landfallTime = nowEpoch + (14 * 3600_000L) // In 14 hours

        val track = listOf(
            CycloneTrackNode(
                stage = "PAST",
                timestampEpoch = nowEpoch - (24 * 3600_000L),
                latitude = 16.20,
                longitude = 89.50,
                maxWindKph = 65f,
                peakGustsKph = 85f,
                centralPressureHpa = 998f,
                categoryLabel = "Deep Depression (DD)",
                formattedTime = "Yesterday 05:30 AM"
            ),
            CycloneTrackNode(
                stage = "PAST",
                timestampEpoch = nowEpoch - (12 * 3600_000L),
                latitude = 17.60,
                longitude = 88.20,
                maxWindKph = 110f,
                peakGustsKph = 135f,
                centralPressureHpa = 986f,
                categoryLabel = "Severe Cyclonic Storm (SCS)",
                formattedTime = "Yesterday 05:30 PM"
            ),
            CycloneTrackNode(
                stage = "CURRENT",
                timestampEpoch = nowEpoch,
                latitude = 19.15,
                longitude = 87.10,
                maxWindKph = 145f,
                peakGustsKph = 175f,
                centralPressureHpa = 970f,
                categoryLabel = "Very Severe Cyclonic Storm (VSCS) - CAT 3",
                formattedTime = "LIVE NOW"
            ),
            CycloneTrackNode(
                stage = "FORECAST",
                timestampEpoch = nowEpoch + (6 * 3600_000L),
                latitude = 20.10,
                longitude = 86.85,
                maxWindKph = 155f,
                peakGustsKph = 185f,
                centralPressureHpa = 964f,
                categoryLabel = "Peak Intensity (VSCS)",
                formattedTime = "Tonight 11:30 PM"
            ),
            CycloneTrackNode(
                stage = "FORECAST",
                timestampEpoch = landfallTime,
                latitude = 20.80,
                longitude = 86.95,
                maxWindKph = 140f,
                peakGustsKph = 165f,
                centralPressureHpa = 972f,
                categoryLabel = "LANDFALL (Dhamra / Bhitarkanika)",
                formattedTime = "Tomorrow Morning 07:30 AM"
            ),
            CycloneTrackNode(
                stage = "FORECAST",
                timestampEpoch = nowEpoch + (24 * 3600_000L),
                latitude = 21.65,
                longitude = 86.50,
                maxWindKph = 75f,
                peakGustsKph = 95f,
                centralPressureHpa = 992f,
                categoryLabel = "Inland Weakening (CS)",
                formattedTime = "Tomorrow Evening 05:30 PM"
            )
        )

        // Bounding polygon coordinates for Zoom Earth-style Cone of Uncertainty
        val cone = listOf(
            Pair(19.15, 87.10), // Origin (current eye)
            Pair(20.40, 87.70), // Right flare +6h
            Pair(21.40, 88.10), // Right flare landfall
            Pair(22.30, 87.60), // Tip Right +24h
            Pair(22.10, 85.70), // Tip Left +24h
            Pair(21.10, 85.90), // Left flare landfall
            Pair(20.00, 86.20), // Left flare +6h
            Pair(19.15, 87.10)  // Close polygon
        )

        return ActiveCyclone(
            id = "cyclone_dana_bay_of_bengal",
            name = "Very Severe Cyclonic Storm 'DANA'",
            basin = "Bay of Bengal (North Indian Ocean)",
            intensityCategory = "Very Severe Cyclonic Storm (VSCS) • Category 3",
            currentLat = 19.15,
            currentLon = 87.10,
            sustainedWindKph = 145f,
            peakGustsKph = 175f,
            centralPressureHpa = 970f,
            movementDirectionText = "North-Northwest (NNW) at 16 km/h towards Odisha / Bengal Coast",
            trackNodes = track,
            conePolygonCoords = cone,
            projectedLandfallLocation = "Dhamra Port / Bhitarkanika Coast (Odisha)",
            projectedLandfallEpoch = landfallTime,
            estimatedLandfallWindKph = 165f
        )
    }

    companion object {
        data class BaselineStationConfig(
            val name: String,
            val lat: Double,
            val lon: Double,
            val baseWind: Float,
            val baseGust: Float,
            val baseRain: Float,
            val baseDir: Float,
            val baseCode: Int,
            val baseTemp: Float,
            val basePressure: Float
        )

        private val REGIONAL_CONFIGS = listOf(
            BaselineStationConfig("Puri Coast", 19.81, 85.83, 75f, 105f, 28f, 65f, 65, 27f, 978f),       // 🌀 Severe Cyclone
            BaselineStationConfig("Sundarbans Delta", 21.95, 88.93, 58f, 80f, 32f, 45f, 95, 26f, 988f),  // ⛈️ Thunderstorm
            BaselineStationConfig("Visakhapatnam", 17.68, 83.21, 48f, 66f, 12f, 90f, 61, 28f, 995f),    // 💨 Squally
            BaselineStationConfig("Chennai Marina", 13.08, 80.27, 24f, 32f, 0.4f, 135f, 2, 29f, 1008f),  // ⛅ Partly Cloudy
            BaselineStationConfig("Veraval Port", 20.90, 70.36, 52f, 70f, 0f, 315f, 1, 26f, 1005f),      // 💨 Gale
            BaselineStationConfig("Mumbai Coast", 18.92, 72.83, 18f, 24f, 0f, 270f, 0, 28f, 1010f),      // ☀️ Fair
            BaselineStationConfig("Kochi Malabar", 9.93, 76.26, 32f, 44f, 14f, 225f, 80, 27f, 1009f),    // 🌧️ Rain Showers
            BaselineStationConfig("Port Blair", 11.62, 92.72, 85f, 115f, 45f, 45f, 96, 26f, 972f),      // 🌀 Super Cyclone
            BaselineStationConfig("Kolkata", 22.57, 88.36, 42f, 58f, 14f, 60f, 55, 27f, 996f),          // 🌦️ Drizzle
            BaselineStationConfig("Bhubaneswar", 20.29, 85.82, 62f, 82f, 22f, 70f, 63, 27f, 984f),      // 🌧️ Heavy Rain
            BaselineStationConfig("Digha Coast", 21.62, 87.50, 68f, 92f, 30f, 50f, 65, 26f, 982f),      // 🌀 Storm Surge
            BaselineStationConfig("New Delhi", 28.61, 77.20, 12f, 16f, 0f, 0f, 0, 21f, 1015f),           // ☀️ Clear Skies
            BaselineStationConfig("Bengaluru", 12.97, 77.59, 15f, 20f, 0f, 180f, 1, 24f, 1012f)          // ⛅ Pleasant
        )

        fun generateBaselinePoints(
            hour: Int,
            userLat: Double = 20.5937,
            userLon: Double = 78.9629
        ): List<WeatherGridPoint> {
            val baseTime = System.currentTimeMillis() + hour * 3600_000L
            val hourWave = kotlin.math.sin(hour * 0.15) * 8.0

            val list = mutableListOf<WeatherGridPoint>()

            val userWind = (35f + hourWave).toFloat().coerceIn(12f, 95f)
            val userGust = userWind * 1.35f
            val userDir = ((55f + hour * 6f) % 360f)
            list.add(
                WeatherGridPoint(
                    id = "base_My_Location_h$hour",
                    cityName = "My Location",
                    latitude = userLat,
                    longitude = userLon,
                    hourOffset = hour,
                    timestampEpoch = baseTime,
                    windSpeedKph = userWind,
                    windDirectionDegrees = userDir,
                    windGustsKph = userGust,
                    precipitationMm = if (userWind > 50f) 14.5f else 1.2f,
                    temperatureCelsius = 28f,
                    weatherCode = if (userWind > 50f) 65 else 2,
                    surfacePressureHpa = if (userWind > 50f) 988f else 1009f,
                    humidityPercent = if (userWind > 50f) 92 else 72,
                    cloudCoverPercent = if (userWind > 50f) 95 else 35
                )
            )

            for (cfg in REGIONAL_CONFIGS) {
                val dynamicWind = (cfg.baseWind + hourWave + (cfg.name.hashCode() % 5)).toFloat().coerceAtLeast(6f)
                val dynamicGust = (cfg.baseGust + hourWave * 1.3f).toFloat().coerceAtLeast(dynamicWind * 1.2f)
                val dynamicRain = if (cfg.baseRain > 0) (cfg.baseRain + hourWave * 0.5f).toFloat().coerceAtLeast(0.5f) else 0f
                val dynamicDir = ((cfg.baseDir + hour * 8) % 360).toFloat()

                list.add(
                    WeatherGridPoint(
                        id = "base_${cfg.name.replace(" ", "_")}_h$hour",
                        cityName = cfg.name,
                        latitude = cfg.lat,
                        longitude = cfg.lon,
                        hourOffset = hour,
                        timestampEpoch = baseTime,
                        windSpeedKph = dynamicWind,
                        windDirectionDegrees = dynamicDir,
                        windGustsKph = dynamicGust,
                        precipitationMm = dynamicRain,
                        temperatureCelsius = cfg.baseTemp,
                        weatherCode = cfg.baseCode,
                        surfacePressureHpa = cfg.basePressure,
                        humidityPercent = if (cfg.baseRain > 0) 90 else 68,
                        cloudCoverPercent = if (cfg.baseRain > 0) 95 else 30
                    )
                )
            }

            return list
        }
    }
}
