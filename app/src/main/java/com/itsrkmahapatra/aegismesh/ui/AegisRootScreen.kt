package com.itsrkmahapatra.aegismesh.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.itsrkmahapatra.aegismesh.ui.alerts.AlertsScreen
import com.itsrkmahapatra.aegismesh.ui.alerts.AlertsViewModel
import com.itsrkmahapatra.aegismesh.ui.map.MapScreen
import com.itsrkmahapatra.aegismesh.ui.map.MapViewModel
import com.itsrkmahapatra.aegismesh.ui.theme.RadarCyan

enum class AegisNavPage {
    RADAR_MAP,
    EMERGENCY_ALERTS
}

@Composable
fun AegisRootScreen(
    mapViewModel: MapViewModel,
    alertsViewModel: AlertsViewModel,
    isCellOutage: Boolean,
    peerCount: Int,
    modifier: Modifier = Modifier
) {
    var currentPage by remember { mutableStateOf(AegisNavPage.RADAR_MAP) }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                NavigationBarItem(
                    selected = currentPage == AegisNavPage.RADAR_MAP,
                    onClick = { currentPage = AegisNavPage.RADAR_MAP },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = "Live Radar"
                        )
                    },
                    label = {
                        Text(
                            text = "Live Radar",
                            fontWeight = if (currentPage == AegisNavPage.RADAR_MAP) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = RadarCyan,
                        selectedTextColor = RadarCyan,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )

                NavigationBarItem(
                    selected = currentPage == AegisNavPage.EMERGENCY_ALERTS,
                    onClick = { currentPage = AegisNavPage.EMERGENCY_ALERTS },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Official Alerts"
                        )
                    },
                    label = {
                        Text(
                            text = "Alerts & Safety",
                            fontWeight = if (currentPage == AegisNavPage.EMERGENCY_ALERTS) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = RadarCyan,
                        selectedTextColor = RadarCyan,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        when (currentPage) {
            AegisNavPage.RADAR_MAP -> {
                MapScreen(
                    viewModel = mapViewModel,
                    isCellOutage = isCellOutage,
                    modifier = Modifier.padding(innerPadding)
                )
            }
            AegisNavPage.EMERGENCY_ALERTS -> {
                AlertsScreen(
                    viewModel = alertsViewModel,
                    peerCount = peerCount,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}
