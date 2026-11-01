package com.itsrkmahapatra.aegismesh.ui.map

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
    modifier: Modifier = Modifier
) {
    val formatter = SimpleDateFormat("EEE, dd MMM - HH:mm", Locale.getDefault())
    val currentEpoch = currentPoint?.timestampEpoch ?: (System.currentTimeMillis() + currentHourOffset * 3600_000L)
    val formattedTime = formatter.format(Date(currentEpoch))

    val windSpeed = currentPoint?.windSpeedKph ?: 0f
    val gusts = currentPoint?.windGustsKph ?: 0f
    val precip = currentPoint?.precipitationMm ?: 0f

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 8.dp,
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header: Day / Time and Cyclone Danger Indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Forecast (+${currentHourOffset}h / 168h)",
                        style = MaterialTheme.typography.labelSmall,
                        color = RadarCyan
                    )
                }

                // Severe Wind Warning Pill if gusts > 60 km/h (Cyclone scale)
                AnimatedVisibility(visible = gusts >= 60f) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (gusts >= 90f) AlertExtremeRed else AlertSevereOrange)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Gale Warning",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (gusts >= 90f) "CYCLONE GUST" else "GALE WARNING",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Realtime Metrics Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricItem(label = "Wind", value = "${windSpeed.toInt()} km/h", highlight = windSpeed > 50f)
                MetricItem(label = "Gusts", value = "${gusts.toInt()} km/h", highlight = gusts > 65f)
                MetricItem(label = "Rain", value = "${String.format(Locale.US, "%.1f", precip)} mm/h", highlight = precip > 15f)
                MetricItem(label = "Direction", value = "${currentPoint?.windDirectionDegrees?.toInt() ?: 0}°", highlight = false)
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Slider & Play/Pause Controls
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
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = RadarCyan
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

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
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (highlight) AlertSevereOrange else MaterialTheme.colorScheme.onSurface
        )
    }
}
