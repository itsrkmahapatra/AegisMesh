package com.itsrkmahapatra.aegismesh.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itsrkmahapatra.aegismesh.domain.model.ActiveCyclone
import com.itsrkmahapatra.aegismesh.domain.model.WeatherGridPoint
import com.itsrkmahapatra.aegismesh.ui.theme.AlertExtremeRed
import com.itsrkmahapatra.aegismesh.ui.theme.AlertSevereOrange
import com.itsrkmahapatra.aegismesh.ui.theme.RadarCyan
import java.util.Locale

/**
 * Top floating Windy & Zoom Earth-grade Weather HUD Inspector.
 * 
 * Synchronized with live 2026 wall-clock time, live barometric pressure,
 * place-wise weather parameters, and interactive 2026 cyclone drill controls.
 * 
 * Copyright (c) 2026 Raj Kishor Mahapatra. All Rights Reserved.
 */
@Composable
fun WindyTopInspector(
    point: WeatherGridPoint?,
    selectedName: String?,
    activeLayer: WeatherMapLayer,
    activeCyclone: ActiveCyclone?,
    distanceToCycloneKm: Float?,
    isLoading: Boolean,
    onRecenterGps: () -> Unit,
    onRefresh: () -> Unit,
    onToggleDrillMode: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val locationTitle = when {
        selectedName.equals("My Location", ignoreCase = true) || point?.cityName.equals("My Location", ignoreCase = true) -> "📍 My Location (Current GPS)"
        selectedName != null -> "📍 $selectedName"
        point != null -> "📍 ${point.cityName}"
        else -> "📍 Detecting Live Location..."
    }

    val speed = point?.windSpeedKph?.toInt() ?: 18
    val gust = point?.windGustsKph?.toInt() ?: 24
    val temp = point?.temperatureCelsius?.toInt() ?: 28
    val precip = point?.precipitationMm ?: 0f
    val pressure = point?.surfacePressureHpa?.toInt() ?: 1008
    val humidity = point?.humidityPercent ?: 75
    val arrow = point?.windArrow ?: "↑ N"
    val signal = point?.signalBadge ?: "🟢 NORMAL"
    val condition = point?.conditionText ?: "Live Forecast"

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xDD0F172A), // Dark slate translucent glassmorphism
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3338BDF8)),
        tonalElevation = 6.dp,
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // Header Row: Location Title + Signal Badge + Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = locationTitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "$condition • Barometer: ${pressure} hPa",
                        fontSize = 11.sp,
                        color = if (pressure < 995) AlertExtremeRed else RadarCyan
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    speed >= 65 || pressure < 990 -> AlertExtremeRed
                                    speed >= 45 || pressure < 1000 -> AlertSevereOrange
                                    else -> Color(0x3310B981)
                                }
                            )
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = signal,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = onRecenterGps,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "My Location",
                            tint = RadarCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onRefresh,
                        enabled = !isLoading,
                        modifier = Modifier.size(30.dp)
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = RadarCyan
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Zoom Earth Cyclone / Basin Status Banner
            if (activeLayer == WeatherMapLayer.CYCLONE_TRACK) {
                Spacer(modifier = Modifier.height(6.dp))
                if (activeCyclone != null) {
                    val isDrill = activeCyclone.isDrillSimulation
                    val borderColor = if (isDrill) RadarCyan else AlertExtremeRed
                    val bgColor = if (isDrill) Color(0x3338BDF8) else AlertExtremeRed.copy(alpha = 0.2f)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(bgColor)
                            .border(1.dp, borderColor.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🌀", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeCyclone.name,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Eye: ${activeCyclone.currentLat}°N, ${activeCyclone.currentLon}°E • Pressure: ${activeCyclone.centralPressureHpa.toInt()} hPa • ${activeCyclone.movementDirectionText}",
                                fontSize = 10.sp,
                                color = if (isDrill) RadarCyan else Color(0xFFFCA5A5)
                            )
                            if (distanceToCycloneKm != null) {
                                Text(
                                    text = "Distance to You: ${distanceToCycloneKm.toInt()} km • Landfall: ${activeCyclone.projectedLandfallLocation}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = RadarCyan
                                )
                            }
                        }
                        if (isDrill) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xDD0F172A))
                                    .clickable { onToggleDrillMode() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "Exit Drill",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                } else {
                    // Basin is calm / clear in real-time
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x3310B981))
                            .border(1.dp, Color(0x6610B981), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "✅", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Indian Basins Clear • Live Surveillance Active",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "IMD / NDMA Live • Barometer: ${pressure} hPa • No Tropical Cyclones Detected",
                                fontSize = 10.sp,
                                color = Color(0xFFA7F3D0)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(RadarCyan)
                                .clickable { onToggleDrillMode() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "▶ 2026 Drill",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.Black
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Realtime Multi-Parameter Weather Tiles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                WindMetricTile(label = "WIND", value = "$speed km/h", highlight = speed >= 45)
                WindMetricTile(label = "PEAK GUST", value = "$gust km/h", highlight = gust >= 60)
                WindMetricTile(label = "VECTOR", value = arrow, highlight = false)
                WindMetricTile(label = "TEMP", value = "$temp°C", highlight = false)
                WindMetricTile(label = "HUMIDITY", value = "$humidity%", highlight = false)
                WindMetricTile(label = "RAIN", value = "${String.format(Locale.US, "%.1f", precip)} mm", highlight = precip >= 5f)
            }
        }
    }
}

@Composable
private fun WindMetricTile(
    label: String,
    value: String,
    highlight: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF94A3B8)
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (highlight) AlertExtremeRed else Color.White
        )
    }
}
