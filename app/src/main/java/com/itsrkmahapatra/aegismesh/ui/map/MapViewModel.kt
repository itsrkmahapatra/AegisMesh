package com.itsrkmahapatra.aegismesh.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

class MapViewModel(
    private val weatherRepository: WeatherRepository
) : ViewModel() {

    val userLatitude = MutableStateFlow(20.5937)
    val userLongitude = MutableStateFlow(78.9629)

    private val _currentHourOffset = MutableStateFlow(0)
    val currentHourOffset: StateFlow<Int> = _currentHourOffset.asStateFlow()

    private val _selectedStationName = MutableStateFlow<String?>("Puri Coast")
    val selectedStationName: StateFlow<String?> = _selectedStationName.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var playbackJob: Job? = null

    // Reactive flow of all 20 distinct weather stations across India for currently scrubbed hour
    val currentHourWeather: StateFlow<List<WeatherGridPoint>> = _currentHourOffset
        .flatMapLatest { offset ->
            weatherRepository.getWeatherForHour(offset)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = WeatherRepository.generateBaselinePoints(0)
        )

    // Specific weather point for the actively selected station (displayed in bottom scrubber & inspector)
    val activeStationPoint: StateFlow<WeatherGridPoint?> = combine(
        currentHourWeather,
        _selectedStationName,
        userLatitude,
        userLongitude
    ) { points, selectedName, lat, lon ->
        if (points.isEmpty()) null
        else if (selectedName != null) {
            points.find { it.cityName.equals(selectedName, ignoreCase = true) }
                ?: points.minByOrNull { GeoUtils.calculateDistanceKm(lat, lon, it.latitude, it.longitude) }
                ?: points.first()
        } else {
            points.minByOrNull { GeoUtils.calculateDistanceKm(lat, lon, it.latitude, it.longitude) }
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

    fun updateGpsLocation(lat: Double, lon: Double) {
        userLatitude.value = lat
        userLongitude.value = lon
        refreshWeather()
    }

    fun refreshWeather() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                weatherRepository.refreshWeatherForecast(userLatitude.value, userLongitude.value)
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

    private fun startPlayback() {
        _isPlaying.value = true
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            while (isActive && _isPlaying.value) {
                delay(450L) // Fluid animation advancing 1 hour every 450ms
                val next = (_currentHourOffset.value + 1) % 168
                _currentHourOffset.value = next
            }
        }
    }

    private fun pausePlayback() {
        _isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
    }
}
