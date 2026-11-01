package com.itsrkmahapatra.aegismesh.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.itsrkmahapatra.aegismesh.domain.model.WeatherGridPoint
import com.itsrkmahapatra.aegismesh.ui.theme.AlertExtremeRed
import com.itsrkmahapatra.aegismesh.ui.theme.AlertSevereOrange
import com.itsrkmahapatra.aegismesh.ui.theme.RadarCyan
import java.util.Locale

/**
 * Top floating Windy-style Weather HUD Inspector.
 * Displays real-time wind vector velocity, gusts, temperature, precipitation,
 * and port danger signals for the user's current GPS location or tapped map spot.
 * 
 * Copyright (c) 2026 Raj Kishor Mahapatra. All Rights Reserved.
 */
@Composable
fun WindyTopInspector(
    point: WeatherGridPoint?,
    selectedName: String?,
    isLoading: Boolean,
    onRecenterGps: () -> Unit,
    onRefresh: () -> Unit,
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
    val arrow = point?.windArrow ?: "↑ N"
    val signal = point?.signalBadge ?: "🟢 NORMAL"
    val condition = point?.conditionText ?: "Live Forecast"

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xDD0F172A), // Dark slate translucent glassmorphism
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3338BDF8)),
        tonalElevation = 6.dp,
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
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
                        text = condition,
                        fontSize = 11.sp,
                        color = RadarCyan
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Port Danger Signal Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    speed >= 65 -> AlertExtremeRed
                                    speed >= 45 -> AlertSevereOrange
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

                    // GPS Recenter Button
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

                    // Direct Refresh Button
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

            Spacer(modifier = Modifier.height(8.dp))

            // Metrics Grid: Wind Speed, Gust, Wind Direction, Temp, Rain
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                WindMetricTile(label = "WIND", value = "$speed km/h", highlight = speed >= 45)
                WindMetricTile(label = "GUST", value = "$gust km/h", highlight = gust >= 60)
                WindMetricTile(label = "DIRECTION", value = arrow, highlight = false)
                WindMetricTile(label = "TEMP", value = "$temp°C", highlight = false)
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
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (highlight) AlertExtremeRed else Color.White
        )
    }
}
