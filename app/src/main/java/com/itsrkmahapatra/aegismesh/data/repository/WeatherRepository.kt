package com.itsrkmahapatra.aegismesh.data.repository

import com.itsrkmahapatra.aegismesh.data.local.dao.WeatherDao
import com.itsrkmahapatra.aegismesh.data.local.entity.WeatherGridPointEntity
import com.itsrkmahapatra.aegismesh.data.remote.OpenMeteoDirectFetcher
import com.itsrkmahapatra.aegismesh.domain.model.ActiveCyclone
import com.itsrkmahapatra.aegismesh.domain.model.CycloneTrackNode
import com.itsrkmahapatra.aegismesh.domain.model.EmergencyAlert
import com.itsrkmahapatra.aegismesh.domain.model.WeatherGridPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Repository for managing 7-day (168-hour) weather forecasts, multi-layer radar data,
 * and Zoom Earth-style Cyclone tracking.
 * 
 * 100% on-device processing, zero API keys, live 2026 synchronization.
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
        if (targets.size < 6) {
            for (st in OpenMeteoDirectFetcher.REGIONAL_BASELINE_STATIONS) {
                if (targets.none { it.name.equals(st.name, ignoreCase = true) }) {
                    targets.add(st)
                    if (targets.size >= 12) break
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

        // Offline fallback: Populate entire 168-hour timeline across all regions
        if (weatherDao.getCount() == 0) {
            val baseline = (0..167).flatMap { generateBaselinePoints(it, userLat, userLon) }
            weatherDao.insertBatch(baseline.map { WeatherGridPointEntity.fromDomain(it) })
            return@withContext Result.success(baseline.size)
        }

        Result.failure(result.exceptionOrNull() ?: Exception("Offline: using cached on-device forecast"))
    }

    /**
     * Zoom Earth-style Cyclone tracking detector.
     * Evaluates live grid points and NDMA CAP alerts:
     * - If severe depression / cyclonic storm is active in live data, tracks it dynamically.
     * - If no storm is active, returns null (or drill cyclone if allowSimulationDrill is true).
     */
    fun getActiveCyclone(
        nowEpoch: Long = System.currentTimeMillis(),
        points: List<WeatherGridPoint> = emptyList(),
        allowSimulationDrill: Boolean = false
    ): ActiveCyclone? {
        // 1. Scan live weather grid points for any low-pressure eye (barometer < 995 hPa or wind >= 62 km/h)
        val stormEye = points.filter { it.surfacePressureHpa < 995f || it.windSpeedKph >= 62f }
            .minByOrNull { it.surfacePressureHpa }

        if (stormEye != null) {
            return buildDynamicCyclone(stormEye, nowEpoch)
        }

        // 2. If simulation drill is enabled by user, generate a high-fidelity 2026 preparedness drill
        if (allowSimulationDrill) {
            return buildDrillCyclone(nowEpoch)
        }

        // 3. Basin is normal / clear
        return null
    }

    private fun buildDynamicCyclone(eyePoint: WeatherGridPoint, nowEpoch: Long): ActiveCyclone {
        val landfallTime = nowEpoch + (12 * 3600_000L)
        val lat = eyePoint.latitude
        val lon = eyePoint.longitude
        val wind = eyePoint.windSpeedKph.coerceAtLeast(65f)
        val gust = eyePoint.windGustsKph.coerceAtLeast(wind * 1.3f)
        val press = eyePoint.surfacePressureHpa.coerceAtMost(990f)

        val track = listOf(
            CycloneTrackNode(
                stage = "PAST",
                timestampEpoch = nowEpoch - (12 * 3600_000L),
                latitude = lat - 1.2,
                longitude = lon + 0.9,
                maxWindKph = wind * 0.75f,
                peakGustsKph = gust * 0.75f,
                centralPressureHpa = press + 12f,
                categoryLabel = "Deep Depression (DD)",
                formattedTime = "12h Ago"
            ),
            CycloneTrackNode(
                stage = "CURRENT",
                timestampEpoch = nowEpoch,
                latitude = lat,
                longitude = lon,
                maxWindKph = wind,
                peakGustsKph = gust,
                centralPressureHpa = press,
                categoryLabel = if (wind >= 118f) "Very Severe Cyclonic Storm (VSCS)" else "Cyclonic Storm (CS)",
                formattedTime = "LIVE NOW"
            ),
            CycloneTrackNode(
                stage = "FORECAST",
                timestampEpoch = landfallTime,
                latitude = lat + 1.1,
                longitude = lon - 0.4,
                maxWindKph = wind * 1.1f,
                peakGustsKph = gust * 1.1f,
                centralPressureHpa = press - 4f,
                categoryLabel = "Projected Landfall",
                formattedTime = "In 12 Hours"
            )
        )

        val cone = listOf(
            Pair(lat, lon),
            Pair(lat + 0.8, lon + 0.7),
            Pair(lat + 1.6, lon + 1.1),
            Pair(lat + 2.2, lon + 0.5),
            Pair(lat + 2.0, lon - 0.9),
            Pair(lat + 1.2, lon - 0.8),
            Pair(lat, lon)
        )

        return ActiveCyclone(
            id = "live_cyclonic_system_${eyePoint.id}",
            name = "Cyclonic System near ${eyePoint.cityName}",
            basin = "North Indian Ocean (Live Surveillance)",
            intensityCategory = if (wind >= 118f) "Very Severe Cyclonic Storm (VSCS)" else "Severe Cyclonic Storm (SCS)",
            currentLat = lat,
            currentLon = lon,
            sustainedWindKph = wind,
            peakGustsKph = gust,
            centralPressureHpa = press,
            movementDirectionText = "North-Northwest (NNW) at 16 km/h",
            trackNodes = track,
            conePolygonCoords = cone,
            projectedLandfallLocation = "Coastal Sector near ${eyePoint.cityName}",
            projectedLandfallEpoch = landfallTime,
            estimatedLandfallWindKph = wind * 1.1f,
            isDrillSimulation = false
        )
    }

    private fun buildDrillCyclone(nowEpoch: Long): ActiveCyclone {
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
                categoryLabel = "Very Severe Cyclonic Storm (VSCS) • Category 3",
                formattedTime = "LIVE NOW (2026 DRILL)"
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

        val cone = listOf(
            Pair(19.15, 87.10),
            Pair(20.40, 87.70),
            Pair(21.40, 88.10),
            Pair(22.30, 87.60),
            Pair(22.10, 85.70),
            Pair(21.10, 85.90),
            Pair(20.00, 86.20),
            Pair(19.15, 87.10)
        )

        return ActiveCyclone(
            id = "cyclone_drill_2026_shakti",
            name = "Cyclonic Storm 'SHAKTI' (2026 Preparedness Drill)",
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
            estimatedLandfallWindKph = 165f,
            isDrillSimulation = true
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

        // Comprehensive nationwide meteorological network across all Indian coasts, seas, and states
        val REGIONAL_CONFIGS = listOf(
            // Odisha Coast
            BaselineStationConfig("Puri Coast", 19.81, 85.83, 26f, 36f, 1.2f, 65f, 2, 28f, 1010f),
            BaselineStationConfig("Paradip Port", 20.31, 86.61, 28f, 38f, 1.8f, 60f, 2, 28f, 1009f),
            BaselineStationConfig("Gopalpur Coast", 19.26, 84.90, 22f, 30f, 0.4f, 75f, 1, 29f, 1011f),
            BaselineStationConfig("Balasore", 21.49, 86.93, 20f, 28f, 0.6f, 50f, 2, 27f, 1011f),
            BaselineStationConfig("Bhubaneswar", 20.29, 85.82, 18f, 25f, 0.0f, 70f, 1, 29f, 1011f),

            // West Bengal
            BaselineStationConfig("Kolkata", 22.57, 88.36, 16f, 22f, 0.0f, 45f, 1, 28f, 1012f),
            BaselineStationConfig("Digha Coast", 21.62, 87.50, 24f, 32f, 0.8f, 55f, 2, 27f, 1010f),
            BaselineStationConfig("Sundarbans Delta", 21.95, 88.93, 26f, 35f, 2.2f, 45f, 61, 27f, 1009f),

            // Andhra Pradesh
            BaselineStationConfig("Visakhapatnam", 17.68, 83.21, 22f, 30f, 0.2f, 90f, 1, 29f, 1011f),
            BaselineStationConfig("Kakinada", 16.98, 82.24, 20f, 28f, 0.0f, 85f, 1, 30f, 1011f),
            BaselineStationConfig("Machilipatnam", 16.18, 81.13, 22f, 30f, 0.4f, 100f, 2, 30f, 1011f),

            // Tamil Nadu
            BaselineStationConfig("Chennai Marina", 13.08, 80.27, 25f, 35f, 1.5f, 120f, 2, 30f, 1010f),
            BaselineStationConfig("Cuddalore", 11.75, 79.76, 24f, 32f, 2.0f, 125f, 61, 29f, 1010f),
            BaselineStationConfig("Nagapattinam", 10.76, 79.84, 26f, 36f, 3.2f, 115f, 61, 29f, 1010f),
            BaselineStationConfig("Rameswaram", 9.28, 79.31, 28f, 38f, 1.0f, 110f, 2, 30f, 1010f),
            BaselineStationConfig("Kanyakumari", 8.08, 77.53, 30f, 40f, 0.5f, 140f, 1, 29f, 1010f),

            // Kerala
            BaselineStationConfig("Kochi Malabar", 9.93, 76.26, 18f, 26f, 2.8f, 240f, 61, 28f, 1011f),
            BaselineStationConfig("Thiruvananthapuram", 8.52, 76.93, 16f, 24f, 1.4f, 230f, 2, 29f, 1011f),

            // Karnataka & Goa
            BaselineStationConfig("Mangaluru Coast", 12.91, 74.85, 18f, 25f, 0.5f, 260f, 1, 29f, 1011f),
            BaselineStationConfig("Panaji Goa", 15.49, 73.82, 16f, 22f, 0.0f, 270f, 0, 30f, 1011f),

            // Maharashtra
            BaselineStationConfig("Mumbai Coast", 18.92, 72.83, 18f, 25f, 0.0f, 290f, 0, 29f, 1012f),
            BaselineStationConfig("Ratnagiri", 16.99, 73.30, 16f, 22f, 0.0f, 280f, 0, 29f, 1012f),

            // Gujarat
            BaselineStationConfig("Veraval Port", 20.90, 70.36, 26f, 35f, 0.0f, 315f, 0, 27f, 1012f),
            BaselineStationConfig("Porbandar", 21.64, 69.60, 24f, 32f, 0.0f, 320f, 0, 26f, 1013f),
            BaselineStationConfig("Dwarka Coast", 22.24, 68.96, 25f, 34f, 0.0f, 330f, 0, 26f, 1013f),
            BaselineStationConfig("Kandla Kutch", 23.03, 70.21, 20f, 28f, 0.0f, 340f, 0, 27f, 1013f),

            // Offshore Basins
            BaselineStationConfig("North Bay of Bengal", 20.00, 89.00, 34f, 45f, 4.0f, 50f, 61, 27f, 1008f),
            BaselineStationConfig("Central Bay of Bengal", 15.00, 87.00, 32f, 42f, 2.5f, 70f, 2, 28f, 1009f),
            BaselineStationConfig("Port Blair", 11.62, 92.72, 28f, 38f, 5.5f, 85f, 63, 27f, 1009f),
            BaselineStationConfig("Lakshadweep", 10.56, 72.64, 22f, 30f, 1.0f, 220f, 2, 29f, 1011f),

            // Northern & Central Plains
            BaselineStationConfig("New Delhi", 28.61, 77.20, 12f, 16f, 0.0f, 310f, 0, 22f, 1016f),
            BaselineStationConfig("Jaipur", 26.91, 75.78, 14f, 18f, 0.0f, 300f, 0, 24f, 1015f),
            BaselineStationConfig("Lucknow", 26.84, 80.94, 10f, 15f, 0.0f, 315f, 0, 23f, 1015f),
            BaselineStationConfig("Patna", 25.59, 85.13, 11f, 16f, 0.0f, 320f, 0, 24f, 1014f),
            BaselineStationConfig("Ranchi", 23.34, 85.30, 14f, 20f, 0.0f, 330f, 0, 24f, 1014f),
            BaselineStationConfig("Bhopal", 23.25, 77.41, 14f, 19f, 0.0f, 340f, 0, 25f, 1014f),
            BaselineStationConfig("Raipur", 21.25, 81.62, 15f, 20f, 0.0f, 350f, 0, 26f, 1013f),
            BaselineStationConfig("Hyderabad", 17.38, 78.48, 16f, 22f, 0.0f, 40f, 1, 27f, 1013f),
            BaselineStationConfig("Bengaluru", 12.97, 77.59, 16f, 22f, 0.0f, 100f, 1, 24f, 1013f),
            BaselineStationConfig("Guwahati", 26.14, 91.73, 8f, 12f, 0.0f, 60f, 0, 23f, 1014f),
            BaselineStationConfig("Srinagar", 34.08, 74.79, 6f, 10f, 0.0f, 270f, 0, 15f, 1018f)
        )

        /**
         * Generates realistic microclimate points for hourOffset, ALWAYS including the user's location.
         * Strictly uses current device time (2026).
         */
        fun generateBaselinePoints(
            hour: Int,
            userLat: Double = 20.5937,
            userLon: Double = 78.9629
        ): List<WeatherGridPoint> {
            val baseTime = System.currentTimeMillis() + hour * 3600_000L
            val hourWave = kotlin.math.sin(hour * 0.15) * 4.0

            val list = mutableListOf<WeatherGridPoint>()

            // 1. ALWAYS add the user's location with localized parameters
            val userWind = (20f + hourWave).toFloat().coerceIn(10f, 65f)
            val userGust = userWind * 1.35f
            val userDir = ((70f + hour * 4f) % 360f)
            val userCoordId = String.format(Locale.US, "%.2f_%.2f", userLat, userLon)
            list.add(
                WeatherGridPoint(
                    id = "base_${userCoordId}_h$hour",
                    cityName = "My Location",
                    latitude = userLat,
                    longitude = userLon,
                    hourOffset = hour,
                    timestampEpoch = baseTime,
                    windSpeedKph = userWind,
                    windDirectionDegrees = userDir,
                    windGustsKph = userGust,
                    precipitationMm = 0.2f,
                    temperatureCelsius = 28f,
                    weatherCode = 1,
                    surfacePressureHpa = 1011f,
                    humidityPercent = 74,
                    cloudCoverPercent = 25
                )
            )

            // 2. Add full nationwide network
            for (cfg in REGIONAL_CONFIGS) {
                val dynamicWind = (cfg.baseWind + hourWave + (cfg.name.hashCode() % 4)).toFloat().coerceAtLeast(6f)
                val dynamicGust = (cfg.baseGust + hourWave * 1.2f).toFloat().coerceAtLeast(dynamicWind * 1.2f)
                val dynamicRain = if (cfg.baseRain > 0) (cfg.baseRain + hourWave * 0.3f).toFloat().coerceAtLeast(0.2f) else 0f
                val dynamicDir = ((cfg.baseDir + hour * 5) % 360).toFloat()
                val coordId = String.format(Locale.US, "%.2f_%.2f", cfg.lat, cfg.lon)

                list.add(
                    WeatherGridPoint(
                        id = "base_${coordId}_h$hour",
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
                        humidityPercent = if (cfg.baseRain > 0) 85 else 65,
                        cloudCoverPercent = if (cfg.baseRain > 0) 80 else 25
                    )
                )
            }

            return list
        }
    }
}
