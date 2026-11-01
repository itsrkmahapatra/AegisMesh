package com.itsrkmahapatra.aegismesh.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itsrkmahapatra.aegismesh.data.remote.OpenMeteoDirectFetcher
import com.itsrkmahapatra.aegismesh.data.repository.WeatherRepository
import com.itsrkmahapatra.aegismesh.domain.model.ActiveCyclone
import com.itsrkmahapatra.aegismesh.domain.model.WeatherGridPoint
import com.itsrkmahapatra.aegismesh.util.GeoUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * ViewModel managing multi-layer weather radar animations:
 * - Wind Streamline Particles (Windy style)
 * - Doppler Rain Radar & Falling Precipitation
 * - Zoom Earth Cyclone Tracker with 72h Cone of Uncertainty
 * - Real-time Lightning Strike Flashes
 * - Thermal Heatmap
 * 
 * Copyright (c) 2026 Raj Kishor Mahapatra. All Rights Reserved.
 */
class MapViewModel(
    private val weatherRepository: WeatherRepository
) : ViewModel() {

    // Active radar display layer (defaults to Wind Stream)
    private val _selectedLayer = MutableStateFlow(WeatherMapLayer.WIND)
    val selectedLayer: StateFlow<WeatherMapLayer> = _selectedLayer.asStateFlow()

    // Map style URI (OpenFreeMap)
    private val _mapStyleUri = MutableStateFlow("https://tiles.openfreemap.org/styles/liberty")
    val mapStyleUri: StateFlow<String> = _mapStyleUri.asStateFlow()

    // User's live GPS coordinates
    val userLatitude = MutableStateFlow(20.5937)
    val userLongitude = MutableStateFlow(78.9629)

    private val _currentHourOffset = MutableStateFlow(0)
    val currentHourOffset: StateFlow<Int> = _currentHourOffset.asStateFlow()

    // Default to "My Location"
    private val _selectedStationName = MutableStateFlow<String?>("My Location")
    val selectedStationName: StateFlow<String?> = _selectedStationName.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Interactive 2026 Cyclone Preparedness Drill Mode
    private val _isDrillModeActive = MutableStateFlow(false)
    val isDrillModeActive: StateFlow<Boolean> = _isDrillModeActive.asStateFlow()

    private var playbackJob: Job? = null
    private var viewportFetchJob: Job? = null

    // Weather grid points for currently scrubbed hour (0 to 167)
    val currentHourWeather: StateFlow<List<WeatherGridPoint>> = combine(
        _currentHourOffset,
        userLatitude,
        userLongitude
    ) { offset, lat, lon ->
        Triple(offset, lat, lon)
    }.flatMapLatest { (offset, lat, lon) ->
        weatherRepository.getWeatherForHour(offset, lat, lon)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = WeatherRepository.generateBaselinePoints(0)
    )

    // Dynamic Cyclone Model: Detects live low-pressure eye or activates 2026 preparedness drill
    val activeCyclone: StateFlow<ActiveCyclone?> = combine(
        currentHourWeather,
        _isDrillModeActive
    ) { points, drillActive ->
        weatherRepository.getActiveCyclone(
            nowEpoch = System.currentTimeMillis(),
            points = points,
            allowSimulationDrill = drillActive
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    // Real-time distance from user to cyclone eye in km
    val distanceToCycloneKm: StateFlow<Float?> = combine(
        userLatitude,
        userLongitude,
        activeCyclone
    ) { lat, lon, cyclone ->
        if (cyclone != null) {
            GeoUtils.calculateDistanceKm(lat, lon, cyclone.currentLat, cyclone.currentLon).toFloat()
        } else null
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    // Weather point for actively selected station / location
    val activeStationPoint: StateFlow<WeatherGridPoint?> = combine(
        currentHourWeather,
        _selectedStationName,
        userLatitude,
        userLongitude
    ) { points, selectedName, lat, lon ->
        if (points.isEmpty()) null
        else if (selectedName != null) {
            points.find { it.cityName.equals(selectedName, ignoreCase = true) }
                ?: points.find { it.cityName.equals("My Location", ignoreCase = true) }
                ?: points.minByOrNull { GeoUtils.calculateDistanceKm(lat, lon, it.latitude, it.longitude) }
                ?: points.first()
        } else {
            points.find { it.cityName.equals("My Location", ignoreCase = true) }
                ?: points.minByOrNull { GeoUtils.calculateDistanceKm(lat, lon, it.latitude, it.longitude) }
                ?: points.first()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = WeatherRepository.generateBaselinePoints(0).firstOrNull()
    )

    init {
        refreshWeather()
    }

    fun selectLayer(layer: WeatherMapLayer) {
        _selectedLayer.value = layer
        if (layer == WeatherMapLayer.CYCLONE_TRACK && activeCyclone.value == null) {
            // Auto-enable 2026 drill simulation so the user can inspect Zoom Earth cone & eye vortex
            _isDrillModeActive.value = true
        }
    }

    fun toggleDrillMode() {
        _isDrillModeActive.value = !_isDrillModeActive.value
    }

    fun toggleMapStyle() {
        val current = _mapStyleUri.value
        _mapStyleUri.value = when {
            current.contains("liberty") -> "https://tiles.openfreemap.org/styles/positron"
            current.contains("positron") -> "https://tiles.openfreemap.org/styles/bright"
            else -> "https://tiles.openfreemap.org/styles/liberty"
        }
    }

    fun selectStationByName(name: String) {
        _selectedStationName.value = name
    }

    fun selectCustomCoordinate(lat: Double, lon: Double) {
        _selectedStationName.value = "Selected Point"
        viewModelScope.launch {
            weatherRepository.refreshWeatherForViewport(
                userLat = userLatitude.value,
                userLon = userLongitude.value,
                visibleStations = listOf(OpenMeteoDirectFetcher.StationMetadata("Selected Point", lat, lon))
            )
        }
    }

    fun updateGpsLocation(lat: Double, lon: Double) {
        userLatitude.value = lat
        userLongitude.value = lon
        _selectedStationName.value = "My Location"
        refreshWeather()
    }

    fun onViewportChanged(
        minLat: Double,
        maxLat: Double,
        minLon: Double,
        maxLon: Double,
        centerLat: Double,
        centerLon: Double
    ) {
        viewportFetchJob?.cancel()
        viewportFetchJob = viewModelScope.launch {
            delay(800)

            val latSpan = maxLat - minLat
            val lonSpan = maxLon - minLon

            val sampleStations = mutableListOf<OpenMeteoDirectFetcher.StationMetadata>()

            // 1. Check which nationwide recognized stations are inside the viewport
            val inBounds = OpenMeteoDirectFetcher.REGIONAL_BASELINE_STATIONS.filter {
                it.lat in minLat..maxLat && it.lon in minLon..maxLon
            }
            sampleStations.addAll(inBounds.take(10))

            // 2. If few stations visible, sample grid coordinates across the visible screen
            if (sampleStations.size < 4) {
                sampleStations.add(OpenMeteoDirectFetcher.StationMetadata("Screen Center", centerLat, centerLon))
                sampleStations.add(OpenMeteoDirectFetcher.StationMetadata("Sector NW", minLat + latSpan * 0.75, minLon + lonSpan * 0.25))
                sampleStations.add(OpenMeteoDirectFetcher.StationMetadata("Sector NE", minLat + latSpan * 0.75, minLon + lonSpan * 0.75))
                sampleStations.add(OpenMeteoDirectFetcher.StationMetadata("Sector SW", minLat + latSpan * 0.25, minLon + lonSpan * 0.25))
                sampleStations.add(OpenMeteoDirectFetcher.StationMetadata("Sector SE", minLat + latSpan * 0.25, minLon + lonSpan * 0.75))
            }

            weatherRepository.refreshWeatherForViewport(
                userLat = userLatitude.value,
                userLon = userLongitude.value,
                visibleStations = sampleStations
            )
        }
    }

    fun refreshWeather() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                weatherRepository.refreshWeatherForViewport(
                    userLat = userLatitude.value,
                    userLon = userLongitude.value
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setHourOffset(hour: Int) {
        _currentHourOffset.value = hour.coerceIn(0, 167)
    }

    fun togglePlayPause() {
        if (_isPlaying.value) {
            pausePlayback()
        } else {
            startPlayback()
        }
    }

    fun startPlayback() {
        _isPlaying.value = true
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            while (isActive && _isPlaying.value) {
                delay(400)
                val next = (_currentHourOffset.value + 1) % 168
                _currentHourOffset.value = next
            }
        }
    }

    fun pausePlayback() {
        _isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
    }

    override fun onCleared() {
        super.onCleared()
        playbackJob?.cancel()
        viewportFetchJob?.cancel()
    }
}
