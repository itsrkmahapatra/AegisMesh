package com.itsrkmahapatra.aegismesh.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itsrkmahapatra.aegismesh.data.remote.OpenMeteoDirectFetcher
import com.itsrkmahapatra.aegismesh.data.repository.WeatherRepository
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
 * ViewModel managing Windy-style weather particle animations and viewport-driven data loading.
 * 
 * Copyright (c) 2026 Raj Kishor Mahapatra. All Rights Reserved.
 */
class MapViewModel(
    private val weatherRepository: WeatherRepository
) : ViewModel() {

    // User's exact live GPS location (defaults to initial center until GPS fix)
    val userLatitude = MutableStateFlow(20.5937)
    val userLongitude = MutableStateFlow(78.9629)

    private val _currentHourOffset = MutableStateFlow(0)
    val currentHourOffset: StateFlow<Int> = _currentHourOffset.asStateFlow()

    // Default to "My Location" so current location weather is ALWAYS front-and-center
    private val _selectedStationName = MutableStateFlow<String?>("My Location")
    val selectedStationName: StateFlow<String?> = _selectedStationName.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var playbackJob: Job? = null
    private var viewportFetchJob: Job? = null

    // Reactive flow of all weather points for currently scrubbed hour (Hour 0 to 167)
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

    // Weather data point for the user's selected location (defaults to My Location)
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

    /**
     * Viewport-aware loader: loads weather data for the exact area currently visible
     * on the user's display and caches all 168 hours permanently into Room DB on device.
     */
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
            delay(800) // Debounce viewport settling

            val latSpan = maxLat - minLat
            val lonSpan = maxLon - minLon

            val sampleStations = listOf(
                OpenMeteoDirectFetcher.StationMetadata("Screen Center", centerLat, centerLon),
                OpenMeteoDirectFetcher.StationMetadata("North West", minLat + latSpan * 0.75, minLon + lonSpan * 0.25),
                OpenMeteoDirectFetcher.StationMetadata("North East", minLat + latSpan * 0.75, minLon + lonSpan * 0.75),
                OpenMeteoDirectFetcher.StationMetadata("South West", minLat + latSpan * 0.25, minLon + lonSpan * 0.25),
                OpenMeteoDirectFetcher.StationMetadata("South East", minLat + latSpan * 0.25, minLon + lonSpan * 0.75)
            )

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
                delay(400) // Smooth 400ms step for fluid 7-day storm animation
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
