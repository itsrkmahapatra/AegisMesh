package com.itsrkmahapatra.aegismesh

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.itsrkmahapatra.aegismesh.mesh.MeshForegroundService
import com.itsrkmahapatra.aegismesh.ui.AegisRootScreen
import com.itsrkmahapatra.aegismesh.ui.alerts.AlertsViewModel
import com.itsrkmahapatra.aegismesh.ui.map.MapViewModel
import com.itsrkmahapatra.aegismesh.ui.theme.AegisMeshTheme
import java.util.concurrent.Executors

class MainActivity : ComponentActivity(), LocationListener {

    private lateinit var mapViewModel: MapViewModel
    private lateinit var alertsViewModel: AlertsViewModel
    private var locationManager: LocationManager? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locationGranted) {
            requestLiveLocationUpdates()
        }
        // Mesh service start once permissions are evaluated
        MeshForegroundService.startService(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as AegisApplication
        mapViewModel = MapViewModel(app.weatherRepository)
        alertsViewModel = AlertsViewModel(app.alertRepository)

        // Start cell tower outage monitoring
        app.cellOutageMonitor.startMonitoring(Executors.newSingleThreadExecutor())

        // Request runtime permissions required for BLE mesh and GPS radius
        checkAndRequestPermissions()

        setContent {
            AegisMeshTheme {
                val isCellOutage by app.cellOutageMonitor.isCellOutageDetected.collectAsState()

                Surface(modifier = Modifier.fillMaxSize()) {
                    AegisRootScreen(
                        mapViewModel = mapViewModel,
                        alertsViewModel = alertsViewModel,
                        isCellOutage = isCellOutage,
                        peerCount = 0 // In standalone view, managed via service
                    )
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            requestLiveLocationUpdates()
            MeshForegroundService.startService(this)
        }
    }

    private fun requestLiveLocationUpdates() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                
                locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { loc ->
                    updateLocation(loc)
                } ?: locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let { loc ->
                    updateLocation(loc)
                }

                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    15_000L, // 15s interval
                    50f,     // 50m displacement
                    this
                )
            }
        } catch (_: SecurityException) {}
    }

    private fun updateLocation(location: Location) {
        mapViewModel.updateGpsLocation(location.latitude, location.longitude)
        alertsViewModel.updateLocation(location.latitude, location.longitude)
    }

    override fun onLocationChanged(location: Location) {
        updateLocation(location)
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) {}
    override fun onProviderDisabled(provider: String) {}

    override fun onDestroy() {
        super.onDestroy()
        locationManager?.removeUpdates(this)
        val app = application as AegisApplication
        app.cellOutageMonitor.stopMonitoring()
    }
}
