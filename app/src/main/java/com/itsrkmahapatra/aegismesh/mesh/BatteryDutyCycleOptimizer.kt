package com.itsrkmahapatra.aegismesh.mesh

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Battery Duty-Cycle Optimizer for AegisMesh:
 * Alternates between Active Mesh Discovery (15 seconds) and Battery Sleep (45 seconds).
 * Prevents continuous high-drain BLE/Wi-Fi scanning during prolonged disaster grid blackouts.
 */
class BatteryDutyCycleOptimizer(
    private val scope: CoroutineScope,
    private val onStartMesh: () -> Unit,
    private val onStopMesh: () -> Unit
) {

    enum class DutyState {
        ACTIVE_SCANNING,
        POWER_SAVING_SLEEP,
        STOPPED
    }

    private val _dutyState = MutableStateFlow(DutyState.STOPPED)
    val dutyState: StateFlow<DutyState> = _dutyState.asStateFlow()

    private var loopJob: Job? = null

    companion object {
        const val ACTIVE_DURATION_MS = 15_000L  // 15 seconds active
        const val SLEEP_DURATION_MS = 45_000L   // 45 seconds sleep
    }

    fun startOptimizationLoop() {
        if (loopJob?.isActive == true) return

        loopJob = scope.launch {
            while (isActive) {
                // 1. Active Period
                _dutyState.value = DutyState.ACTIVE_SCANNING
                try {
                    onStartMesh()
                } catch (_: Exception) {}

                delay(ACTIVE_DURATION_MS)

                // 2. Battery Conservation Sleep Period
                _dutyState.value = DutyState.POWER_SAVING_SLEEP
                try {
                    onStopMesh()
                } catch (_: Exception) {}

                delay(SLEEP_DURATION_MS)
            }
        }
    }

    fun stopOptimizationLoop() {
        loopJob?.cancel()
        loopJob = null
        _dutyState.value = DutyState.STOPPED
        try {
            onStopMesh()
        } catch (_: Exception) {}
    }
}

