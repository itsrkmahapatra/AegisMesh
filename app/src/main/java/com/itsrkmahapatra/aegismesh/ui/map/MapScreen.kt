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
import com.itsrkmahapatra.aegismesh.util.GeoUtils
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

/**
 * World-class Windy-grade interactive weather map screen.
 * 
 * Features:
 * - Fluid on-device wind particle streamline simulation (60+ FPS GPU Canvas).
 * - Smooth Gaussian atmospheric velocity field (zero clunky rectangular chips).
 * - Viewport-aware on-device data loading (fetches & stores all 168 hours of visible screen into Room DB).
 * - Real-time Current Location weather HUD with exact GPS coords.
 * - Interactive 168-hour timeline scrubber with Windy color scale.
 * 
 * Copyright (c) 2026 Raj Kishor Mahapatra. All Rights Reserved.
 */
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
    val activeStationPoint by viewModel.activeStationPoint.collectAsState()
    val selectedStationName by viewModel.selectedStationName.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val userLat by viewModel.userLatitude.collectAsState()
    val userLon by viewModel.userLongitude.collectAsState()

    val mapView = remember { MapView(context) }
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }
    var currentStyle by remember { mutableStateOf<Style?>(null) }
    var atmosphereHelper by remember { mutableStateOf<WindyAtmosphereOverlayHelper?>(null) }

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

    // Reactively update MapLibre atmospheric Gaussian glow when scrubbed hour or points change
    LaunchedEffect(currentStyle, weatherPoints) {
        val style = currentStyle
        val helper = atmosphereHelper
        if (style != null && helper != null && weatherPoints.isNotEmpty()) {
            helper.updateWeatherData(style, weatherPoints)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. MapLibre Native SDK Map View (OpenFreeMap Vector Tiles)
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

                        // Initial view centered on user's GPS coordinate
                        val initialTarget = LatLng(userLat, userLon)
                        map.cameraPosition = CameraPosition.Builder()
                            .target(initialTarget)
                            .zoom(6.0)
                            .build()

                        // Viewport-aware loader: whenever the user stops panning or zooming,
                        // load and cache full 168-hour weather for the visible screen display into Room DB!
                        map.addOnCameraIdleListener {
                            val bounds = try { map.projection.visibleRegion.latLngBounds } catch (_: Exception) { null }
                            if (bounds != null) {
                                viewModel.onViewportChanged(
                                    minLat = bounds.latitudeSouth,
                                    maxLat = bounds.latitudeNorth,
                                    minLon = bounds.longitudeWest,
                                    maxLon = bounds.longitudeEast,
                                    centerLat = map.cameraPosition.target?.latitude ?: ((bounds.latitudeSouth + bounds.latitudeNorth) / 2.0),
                                    centerLon = map.cameraPosition.target?.longitude ?: ((bounds.longitudeWest + bounds.longitudeEast) / 2.0)
                                )
                            }
                        }

                        // Map Tap: Tap anywhere on map to inspect weather at that exact location
                        map.addOnMapClickListener { clickedLatLng ->
                            val nearest = weatherPoints.minByOrNull {
                                GeoUtils.calculateDistanceKm(
                                    clickedLatLng.latitude, clickedLatLng.longitude,
                                    it.latitude, it.longitude
                                )
                            }
                            if (nearest != null) {
                                viewModel.selectStationByName(nearest.cityName)
                            } else {
                                viewModel.selectCustomCoordinate(clickedLatLng.latitude, clickedLatLng.longitude)
                            }
                            true
                        }

                        // OpenFreeMap vector styles
                        val styleBuilder = Style.Builder().fromUri("https://tiles.openfreemap.org/styles/liberty")
                        map.setStyle(styleBuilder) { style ->
                            val helper = WindyAtmosphereOverlayHelper(map)
                            atmosphereHelper = helper
                            helper.setupLayers(style)
                            helper.updateWeatherData(style, weatherPoints)
                            currentStyle = style
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Windy-Grade Animated Wind Particle Streamline Overlay (60+ FPS GPU Canvas)
        WindyParticleOverlay(
            weatherPoints = weatherPoints,
            map = mapInstance,
            modifier = Modifier.fillMaxSize()
        )

        // 3. Top Floating Island: Windy Weather HUD Inspector
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        ) {
            WindyTopInspector(
                point = activeStationPoint,
                selectedName = selectedStationName,
                isLoading = isLoading,
                onRecenterGps = {
                    val target = LatLng(userLat, userLon)
                    viewModel.selectStationByName("My Location")
                    mapInstance?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(target, 7.5),
                        700
                    )
                },
                onRefresh = { viewModel.refreshWeather() }
            )

            // Cell Tower Outage Banner (if disaster drops cell connectivity)
            AnimatedVisibility(
                visible = isCellOutage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AlertExtremeRed.copy(alpha = 0.95f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CellTower,
                        contentDescription = "Cell Outage",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "CELL TOWERS OUTAGE DETECTED",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "AegisMesh offline P2P BLE mesh active. Enable Airplane Mode to prevent modem battery drain.",
                            color = Color.White,
                            fontSize = 10.sp,
                            lineHeight = 13.sp
                        )
                    }
                }
            }
        }

        // 4. Bottom Floating Windy Timeline Scrubber with Color Legend
        TimelineScrubber(
            currentHourOffset = currentHourOffset,
            isPlaying = isPlaying,
            onHourChanged = { viewModel.setHourOffset(it) },
            onPlayPauseToggle = { viewModel.togglePlayPause() },
            currentPoint = activeStationPoint,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
