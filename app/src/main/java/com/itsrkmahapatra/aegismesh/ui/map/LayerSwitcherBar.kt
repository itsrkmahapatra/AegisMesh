package com.itsrkmahapatra.aegismesh.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itsrkmahapatra.aegismesh.ui.theme.RadarCyan

/**
 * Floating horizontal pill switcher for selecting weather radar modes:
 * - Wind Streamlines (Windy)
 * - Rain Radar & Falling Precipitation
 * - Cyclone Tracking & Cone of Uncertainty (Zoom Earth)
 * - Live Lightning Strikes
 * - Thermal Heatmap
 * 
 * Copyright (c) 2026 Raj Kishor Mahapatra. All Rights Reserved.
 */
@Composable
fun LayerSwitcherBar(
    activeLayer: WeatherMapLayer,
    onLayerSelected: (WeatherMapLayer) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (layer in WeatherMapLayer.entries) {
            val isSelected = layer == activeLayer
            val bg = if (isSelected) RadarCyan else Color(0xDD1E293B)
            val textColor = if (isSelected) Color.Black else Color.White
            val borderStroke = if (isSelected) Color.Transparent else Color(0x3338BDF8)

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(bg)
                    .border(1.dp, borderStroke, RoundedCornerShape(14.dp))
                    .clickable { onLayerSelected(layer) }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = layer.iconEmoji, fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = layer.title,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                        color = textColor
                    )
                }
            }
        }
    }
}
