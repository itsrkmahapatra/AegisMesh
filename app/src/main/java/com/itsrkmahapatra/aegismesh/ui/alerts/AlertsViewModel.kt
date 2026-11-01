package com.itsrkmahapatra.aegismesh.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itsrkmahapatra.aegismesh.data.repository.AlertRepository
import com.itsrkmahapatra.aegismesh.domain.model.EmergencyAlert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlertsViewModel(
    private val alertRepository: AlertRepository
) : ViewModel() {

    enum class AlertFilterMode {
        RADIUS_50KM,
        ALL_INDIA
    }

    private val _filterMode = MutableStateFlow(AlertFilterMode.RADIUS_50KM)
    val filterMode: StateFlow<AlertFilterMode> = _filterMode.asStateFlow()

    val userLatitude = MutableStateFlow(19.8135)
    val userLongitude = MutableStateFlow(85.8312)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _lastRefreshResult = MutableStateFlow<String?>(null)
    val lastRefreshResult: StateFlow<String?> = _lastRefreshResult.asStateFlow()

    // Alerts flow reacting to filter mode
    val alerts: StateFlow<List<EmergencyAlert>> = combine(
        _filterMode,
        userLatitude,
        userLongitude
    ) { mode, lat, lon ->
        Triple(mode, lat, lon)
    }.combine(alertRepository.getActiveNationwideAlerts()) { triple, nationwideAlerts ->
        val (mode, lat, lon) = triple
        when (mode) {
            AlertFilterMode.ALL_INDIA -> nationwideAlerts
            AlertFilterMode.RADIUS_50KM -> {
                nationwideAlerts.filter { alert ->
                    com.itsrkmahapatra.aegismesh.util.GeoUtils.calculateDistanceKm(
                        lat, lon,
                        alert.centroidLat, alert.centroidLon
                    ) <= 50.0
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        refreshAlerts()
    }

    fun setFilterMode(mode: AlertFilterMode) {
        _filterMode.value = mode
    }

    fun updateLocation(lat: Double, lon: Double) {
        userLatitude.value = lat
        userLongitude.value = lon
    }

    fun refreshAlerts() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = alertRepository.refreshAlerts()
            if (result.isSuccess) {
                _lastRefreshResult.value = "Updated: ${result.getOrDefault(0)} official alerts active"
            } else {
                _lastRefreshResult.value = "Offline mode: Showing cached & mesh alerts"
            }
            _isRefreshing.value = false
        }
    }
}

