package com.itsrkmahapatra.aegismesh.ui.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itsrkmahapatra.aegismesh.data.repository.AlertRepository
import com.itsrkmahapatra.aegismesh.domain.model.EmergencyAlert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AlertsViewModel(
    private val alertRepository: AlertRepository
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _lastRefreshResult = MutableStateFlow<String?>(null)
    val lastRefreshResult: StateFlow<String?> = _lastRefreshResult.asStateFlow()

    // Real-time active alerts flow strictly issued within the last 24 hours (Nationwide)
    val alerts: StateFlow<List<EmergencyAlert>> = alertRepository.getActiveAlerts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        refreshAlerts()
    }

    fun updateLocation(lat: Double, lon: Double) {
        // Retained for location awareness if needed; 50km restriction removed
    }

    fun refreshAlerts() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val result = alertRepository.refreshAlerts()
            if (result.isSuccess) {
                val count = result.getOrDefault(0)
                _lastRefreshResult.value = "Updated: $count active alerts in last 24 hours"
            } else {
                _lastRefreshResult.value = "Offline mode: Showing cached alerts"
            }
            _isRefreshing.value = false
        }
    }
}
