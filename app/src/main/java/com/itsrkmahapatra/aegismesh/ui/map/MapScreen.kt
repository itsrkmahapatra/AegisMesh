package com.itsrkmahapatra.aegismesh.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.itsrkmahapatra.aegismesh.ui.theme.AlertExtremeRed
import com.itsrkmahapatra.aegismesh.ui.theme.RadarCyan
import com.itsrkmahapatra.aegismesh.util.GeoUtils
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

@Composable
fun MapScreen(
    viewModel: MapViewModel,
    isCellOutage: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentHourOffset by viewModel.currentHourOffset.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val weatherPoints by viewModel.currentHourWeather.collectAsState()
    val userLat by viewModel.userLatitude.collectAsState()
    val userLon by viewModel.userLongitude.collectAsState()

    val mapView = remember { MapView(context) }
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var currentStyle by remember { mutableStateOf<Style?>(null) }
    var overlayHelper by remember { mutableStateOf<WindVectorOverlayHelper?>(null) }

    DisposableEffect(mapView) {
        mapView.onCreate(null)
        mapView.onStart()
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    // Reactively update weather overlay whenever weatherPoints or style changes (e.g. animation loop or scrubbing)
    LaunchedEffect(currentStyle, weatherPoints) {
        val style = currentStyle
        val helper = overlayHelper
        if (style != null && helper != null && weatherPoints.isNotEmpty()) {
            helper.updateWeatherData(style, weatherPoints)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // MapLibre Native SDK Map View (Free Pan & Zoom - No 50km Camera Lock)
        AndroidView(
            factory = {
                mapView.apply {
                    getMapAsync { map ->
                        mapInstance = map

                        map.uiSettings.isCompassEnabled = true
                        map.uiSettings.isRotateGesturesEnabled = true
                        map.uiSettings.isTiltGesturesEnabled = false
                        map.uiSettings.isZoomGesturesEnabled = true
                        map.uiSettings.isScrollGesturesEnabled = true

                        // Initial nationwide camera target centered on India
                        val initialTarget = LatLng(userLat, userLon)
                        map.cameraPosition = CameraPosition.Builder()
                            .target(initialTarget)
                            .zoom(5.5) // Shows major coastal sectors and regional weather grid
                            .build()

                        // Keyless OpenFreeMap Vector Style
                        val styleBuilder = Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")
                        map.setStyle(styleBuilder) { style ->
                            val helper = WindVectorOverlayHelper(map)
                            overlayHelper = helper
                            helper.setupLayers(style)
                            helper.updateWeatherData(style, weatherPoints)
                            currentStyle = style
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Overlay: Cell Tower Outage Banner (Triggered when STATE_OUT_OF_SERVICE)
        AnimatedVisibility(
            visible = isCellOutage,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp, start = 12.dp, end = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AlertExtremeRed.copy(alpha = 0.95f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CellTower,
                    contentDescription = "Cell Outage",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "CELL TOWERS DOWN DETECTED",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "Turn ON Airplane Mode + Bluetooth to prevent baseband modem battery drain. AegisMesh P2P is active.",
                        color = Color.White,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }

        // Floating Action Buttons (Recenter & Refresh)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = if (isCellOutage) 80.dp else 24.dp, end = 16.dp)
        ) {
            FloatingActionButton(
                onClick = {
                    mapInstance?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(userLat, userLon), 7.0),
                        800
                    )
                },
                modifier = Modifier.size(44.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = RadarCyan
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Center on GPS")
            }

            Spacer(modifier = Modifier.size(12.dp))

            FloatingActionButton(
                onClick = { viewModel.refreshWeather() },
                modifier = Modifier.size(44.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh Weather")
            }
        }

        // Bottom 7-Day / 168-Hour Weather Scrubber with real-time stats
        val activePoint = weatherPoints.minByOrNull {
            GeoUtils.calculateDistanceKm(userLat, userLon, it.latitude, it.longitude)
        } ?: weatherPoints.firstOrNull()

        TimelineScrubber(
            currentHourOffset = currentHourOffset,
            isPlaying = isPlaying,
            onHourChanged = { viewModel.setHourOffset(it) },
            onPlayPauseToggle = { viewModel.togglePlayPause() },
            currentPoint = activePoint,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
