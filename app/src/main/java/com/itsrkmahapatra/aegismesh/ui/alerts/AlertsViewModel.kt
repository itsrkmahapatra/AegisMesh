package com.itsrkmahapatra.aegismesh.ui.alerts

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itsrkmahapatra.aegismesh.data.repository.AlertRepository
import com.itsrkmahapatra.aegismesh.domain.model.AlertLanguage
import com.itsrkmahapatra.aegismesh.domain.model.EmergencyAlert
import com.itsrkmahapatra.aegismesh.util.AlertLanguageHelper
import com.itsrkmahapatra.aegismesh.util.IndianStateDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel managing official government emergency alerts.
 * Enforces client-side language & location filtering on the user's device:
 * - English: Displayed to all users across India.
 * - Hindi: Displayed to all users across India.
 * - Regional languages: State-specific only (Odia for Odisha only, Bangla for West Bengal only, etc.).
 * 
 * Copyright (c) 2026 Raj Kishor Mahapatra. All Rights Reserved.
 */
class AlertsViewModel(
    private val alertRepository: AlertRepository
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _lastRefreshResult = MutableStateFlow<String?>(null)
    val lastRefreshResult: StateFlow<String?> = _lastRefreshResult.asStateFlow()

    // User's detected state (Defaults to "Odisha" or resolved GPS location)
    private val _userState = MutableStateFlow("Odisha")
    val userState: StateFlow<String> = _userState.asStateFlow()

    // Whether current state filter is automatically driven by live GPS
    private val _isAutoGpsState = MutableStateFlow(true)
    val isAutoGpsState: StateFlow<Boolean> = _isAutoGpsState.asStateFlow()

    // Optional manual state override (e.g. user manually switches to "West Bengal" to check alerts)
    private val _selectedStateOverride = MutableStateFlow<String?>(null)
    val selectedStateOverride: StateFlow<String?> = _selectedStateOverride.asStateFlow()

    // Effective state currently being used for filtering
    val activeStateName: StateFlow<String> = combine(
        _userState,
        _selectedStateOverride
    ) { detected, override ->
        override ?: detected
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = "Odisha"
    )

    // Unfiltered raw alerts (last 24 hours)
    val rawAlerts: StateFlow<List<EmergencyAlert>> = alertRepository.getActiveAlerts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Filtered alerts: English & Hindi for all states, regional only for matching state
    val alerts: StateFlow<List<EmergencyAlert>> = combine(
        rawAlerts,
        activeStateName
    ) { allAlerts, targetState ->
        if (targetState == "ALL") {
            // Testing / Inspection mode: show all alerts
            allAlerts
        } else {
            allAlerts.filter { alert ->
                AlertLanguageHelper.shouldShowAlert(alert, targetState)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Stats: count of alerts hidden due to other regional languages
    val filteredOutCount: StateFlow<Int> = combine(
        rawAlerts,
        alerts
    ) { raw, filtered ->
        (raw.size - filtered.size).coerceAtLeast(0)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    init {
        refreshAlerts()
    }

    /**
     * Resolves the user's state from live GPS on device (100% offline-first + Geocoder if online).
     */
    fun updateLocation(lat: Double, lon: Double, context: Context? = null) {
        val offlineState = IndianStateDetector.resolveOfflineState(lat, lon)
        if (_isAutoGpsState.value) {
            _userState.value = offlineState
        }

        if (context != null) {
            IndianStateDetector.detectState(context, lat, lon) { resolvedState ->
                if (_isAutoGpsState.value && resolvedState.isNotBlank()) {
                    _userState.value = resolvedState
                }
            }
        }
    }

    /**
     * User manually taps a state chip (e.g. "West Bengal", "Odisha", or "ALL").
     */
    fun selectState(stateName: String) {
        if (stateName.equals("AUTO", ignoreCase = true)) {
            _selectedStateOverride.value = null
            _isAutoGpsState.value = true
        } else {
            _selectedStateOverride.value = stateName
            _isAutoGpsState.value = false
        }
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
