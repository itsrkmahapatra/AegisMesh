package com.itsrkmahapatra.aegismesh.ui.map

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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itsrkmahapatra.aegismesh.domain.model.WeatherGridPoint
import com.itsrkmahapatra.aegismesh.ui.theme.RadarCyan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Industry-grade Windy.com-style floating timeline scrubber and wind velocity color legend.
 * 
 * Copyright (c) 2026 Raj Kishor Mahapatra. All Rights Reserved.
 */
@Composable
fun TimelineScrubber(
    currentHourOffset: Int,
    isPlaying: Boolean,
    onHourChanged: (Int) -> Unit,
    onPlayPauseToggle: () -> Unit,
    currentPoint: WeatherGridPoint?,
    modifier: Modifier = Modifier
) {
    val formatter = SimpleDateFormat("EEE, dd MMM • hh:mm a", Locale.getDefault())
    val currentEpoch = currentPoint?.timestampEpoch ?: (System.currentTimeMillis() + currentHourOffset * 3600_000L)
    val formattedTime = formatter.format(Date(currentEpoch))

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xDD111827), // Dark translucent glassmorphism
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3338BDF8)),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // Top Row: Play/Pause, Forecast Time, Hour Offset Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onPlayPauseToggle,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(RadarCyan)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause Loop" else "Play 7-Day Animation",
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = formattedTime,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x3338BDF8))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (currentHourOffset == 0) "LIVE NOW" else "+${currentHourOffset}h FORECAST",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = RadarCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // 168-Hour (7-Day) Slider
            Slider(
                value = currentHourOffset.toFloat(),
                onValueChange = { onHourChanged(it.toInt()) },
                valueRange = 0f..167f,
                steps = 166,
                colors = SliderDefaults.colors(
                    thumbColor = RadarCyan,
                    activeTrackColor = RadarCyan,
                    inactiveTrackColor = Color(0x444B5563)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Windy.com-Style Wind Speed Velocity Color Legend Bar
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF38BDF8), // Calm (<15 km/h)
                                    Color(0xFF00E676), // Moderate (15 - 30 km/h)
                                    Color(0xFFFFD600), // Fresh (30 - 45 km/h)
                                    Color(0xFFFF6D00), // Strong Gale (45 - 65 km/h)
                                    Color(0xFFFF1744), // Cyclone Red (65 - 85 km/h)
                                    Color(0xFFD500F9)  // Violent Cyclone (>85 km/h)
                                )
                            )
                        )
                )

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "0", fontSize = 9.sp, color = Color(0xFF9CA3AF))
                    Text(text = "15", fontSize = 9.sp, color = Color(0xFF9CA3AF))
                    Text(text = "30", fontSize = 9.sp, color = Color(0xFF9CA3AF))
                    Text(text = "45", fontSize = 9.sp, color = Color(0xFF9CA3AF))
                    Text(text = "65", fontSize = 9.sp, color = Color(0xFF9CA3AF))
                    Text(text = "90+ km/h (Cyclone)", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF472B6))
                }
            }
        }
    }
}
