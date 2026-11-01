package com.itsrkmahapatra.aegismesh.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itsrkmahapatra.aegismesh.domain.model.WeatherGridPoint
import com.itsrkmahapatra.aegismesh.ui.theme.AlertExtremeRed
import com.itsrkmahapatra.aegismesh.ui.theme.AlertSevereOrange
import com.itsrkmahapatra.aegismesh.ui.theme.RadarCyan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TimelineScrubber(
    currentHourOffset: Int,
    isPlaying: Boolean,
    onHourChanged: (Int) -> Unit,
    onPlayPauseToggle: () -> Unit,
    currentPoint: WeatherGridPoint?,
    allStationPoints: List<WeatherGridPoint>,
    selectedStationName: String?,
    onStationSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val formatter = SimpleDateFormat("EEE, dd MMM - HH:mm", Locale.getDefault())
    val currentEpoch = currentPoint?.timestampEpoch ?: (System.currentTimeMillis() + currentHourOffset * 3600_000L)
    val formattedTime = formatter.format(Date(currentEpoch))

    val windSpeed = currentPoint?.windSpeedKph ?: 0f
    val gusts = currentPoint?.windGustsKph ?: 0f
    val precip = currentPoint?.precipitationMm ?: 0f
    val emoji = currentPoint?.conditionEmoji ?: "☀️"
    val cityName = currentPoint?.cityName ?: "Regional Radar"
    val condition = currentPoint?.conditionText ?: "Live Weather"
    val arrow = currentPoint?.windArrow ?: "↑ N"
    val signal = currentPoint?.signalBadge ?: "🟢 NORMAL"
    val temp = currentPoint?.temperatureCelsius?.toInt() ?: 28

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(10.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 8.dp,
        shadowElevation = 14.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Horizontal Station Quick-Select Chips: allows user to see unique weather across every city!
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (station in allStationPoints) {
                    val isSelected = station.cityName.equals(selectedStationName, ignoreCase = true)
                    val chipColor = try {
                        Color(android.graphics.Color.parseColor(station.colorHex))
                    } catch (_: Exception) {
                        RadarCyan
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) chipColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .clickable { onStationSelected(station.cityName) }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = station.conditionEmoji,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${station.cityName} (${station.windSpeedKph.toInt()}km/h)",
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Main Active Station Header with Emoji, Signs & Signals
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = emoji,
                            fontSize = 20.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = cityName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = "$condition • $formattedTime (+${currentHourOffset}h)",
                        style = MaterialTheme.typography.labelSmall,
                        color = RadarCyan
                    )
                }

                // Official Danger Signal Badge (Signals 1-10 / Warnings)
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            when {
                                windSpeed >= 65f -> AlertExtremeRed
                                windSpeed >= 45f -> AlertSevereOrange
                                else -> MaterialTheme.colorScheme.primaryContainer
                            }
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = signal,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Realtime Multi-Param Metric Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem(label = "Wind & Vector", value = "${windSpeed.toInt()} km/h $arrow", highlight = windSpeed > 45f)
                MetricItem(label = "Peak Gusts", value = "${gusts.toInt()} km/h", highlight = gusts > 60f)
                MetricItem(label = "Rain Rate", value = "${String.format(Locale.US, "%.1f", precip)} mm", highlight = precip > 5f)
                MetricItem(label = "Temp", value = "$temp°C", highlight = false)
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Timeline Animation Loop & Slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onPlayPauseToggle,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play Radar Animation",
                        tint = RadarCyan
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Slider(
                    value = currentHourOffset.toFloat(),
                    onValueChange = { onHourChanged(it.toInt()) },
                    valueRange = 0f..167f,
                    steps = 166,
                    colors = SliderDefaults.colors(
                        thumbColor = RadarCyan,
                        activeTrackColor = RadarCyan,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String, highlight: Boolean) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (highlight) AlertSevereOrange else MaterialTheme.colorScheme.onSurface
        )
    }
}
