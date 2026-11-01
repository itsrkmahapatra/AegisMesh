package com.itsrkmahapatra.aegismesh.battery

import android.content.Context
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.ServiceState
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executor

/**
 * Monitors device cellular service state.
 * When cell towers collapse during severe cyclone landfall (STATE_OUT_OF_SERVICE),
 * the cellular baseband modem searches frantically at maximum power, draining battery.
 * This detector fires an alert prompting the user to enable Airplane Mode + Bluetooth override.
 */
class CellOutageMonitor(private val context: Context) {

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

    private val _isCellOutageDetected = MutableStateFlow(false)
    val isCellOutageDetected: StateFlow<Boolean> = _isCellOutageDetected.asStateFlow()

    private var telephonyCallback: Any? = null
    private var legacyListener: PhoneStateListener? = null

    fun startMonitoring(executor: Executor) {
        if (telephonyManager == null) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val callback = object : TelephonyCallback(), TelephonyCallback.ServiceStateListener {
                override fun onServiceStateChanged(serviceState: ServiceState) {
                    handleServiceState(serviceState.state)
                }
            }
            telephonyCallback = callback
            try {
                telephonyManager.registerTelephonyCallback(executor, callback)
            } catch (_: SecurityException) {}
        } else {
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Deprecated("Deprecated in Java")
                override fun onServiceStateChanged(serviceState: ServiceState?) {
                    serviceState?.let { handleServiceState(it.state) }
                }
            }
            legacyListener = listener
            try {
                @Suppress("DEPRECATION")
                telephonyManager.listen(listener, PhoneStateListener.LISTEN_SERVICE_STATE)
            } catch (_: SecurityException) {}
        }
    }

    private fun handleServiceState(state: Int) {
        // STATE_OUT_OF_SERVICE indicates complete cell tower collapse / no signal
        _isCellOutageDetected.value = (state == ServiceState.STATE_OUT_OF_SERVICE)
    }

    fun stopMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (telephonyCallback as? TelephonyCallback)?.let {
                try {
                    telephonyManager?.unregisterTelephonyCallback(it)
                } catch (_: Exception) {}
            }
            telephonyCallback = null
        } else {
            legacyListener?.let {
                try {
                    @Suppress("DEPRECATION")
                    telephonyManager?.listen(it, PhoneStateListener.LISTEN_NONE)
                } catch (_: Exception) {}
            }
            legacyListener = null
        }
    }
}
