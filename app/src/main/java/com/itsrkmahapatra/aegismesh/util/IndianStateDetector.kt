package com.itsrkmahapatra.aegismesh.util

import android.content.Context
import android.location.Geocoder
import android.os.Build
import java.util.Locale

data class StateBoundary(
    val name: String,
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
    val centroidLat: Double,
    val centroidLon: Double
)

/**
 * On-Device Indian State Detector.
 * Provides 100% offline-first state resolution using boundary boxes and centroid distance,
 * with Geocoder reverse-lookup as an enhancement when online.
 * 
 * Copyright (c) 2026 Raj Kishor Mahapatra. All Rights Reserved.
 */
object IndianStateDetector {

    val INDIAN_STATES = listOf(
        StateBoundary("Odisha", 17.78, 22.57, 81.37, 87.50, 20.9517, 85.0985),
        StateBoundary("West Bengal", 21.50, 27.20, 85.80, 89.90, 22.9868, 87.8550),
        StateBoundary("Andhra Pradesh", 12.60, 19.90, 76.70, 84.80, 15.9129, 79.7400),
        StateBoundary("Telangana", 15.80, 19.90, 77.20, 81.80, 18.1124, 79.0193),
        StateBoundary("Tamil Nadu", 8.00, 13.50, 76.20, 80.35, 11.1271, 78.6569),
        StateBoundary("Kerala", 8.30, 12.80, 74.90, 77.40, 10.8505, 76.2711),
        StateBoundary("Karnataka", 11.50, 18.50, 74.00, 78.60, 15.3173, 75.7139),
        StateBoundary("Maharashtra", 15.60, 22.00, 72.60, 80.90, 19.7515, 75.7139),
        StateBoundary("Gujarat", 20.10, 24.70, 68.10, 74.50, 22.2587, 71.1924),
        StateBoundary("Goa", 14.90, 15.80, 73.65, 74.35, 15.2993, 74.1240),
        StateBoundary("Rajasthan", 23.00, 30.20, 69.50, 78.30, 27.0238, 74.2179),
        StateBoundary("Punjab", 29.50, 32.50, 73.80, 76.90, 31.1471, 75.3412),
        StateBoundary("Haryana", 27.60, 30.90, 74.45, 77.60, 29.0588, 76.0856),
        StateBoundary("Delhi", 28.40, 28.90, 76.80, 77.40, 28.7041, 77.1025),
        StateBoundary("Uttar Pradesh", 23.80, 30.40, 77.00, 84.70, 26.8467, 80.9462),
        StateBoundary("Bihar", 24.30, 27.50, 83.30, 88.30, 25.0961, 85.3131),
        StateBoundary("Jharkhand", 21.95, 25.35, 83.30, 87.95, 23.6102, 85.2799),
        StateBoundary("Chhattisgarh", 17.75, 24.10, 80.25, 84.40, 21.2787, 81.8661),
        StateBoundary("Madhya Pradesh", 21.10, 26.90, 74.00, 82.80, 22.9734, 78.6569),
        StateBoundary("Uttarakhand", 28.70, 31.50, 77.55, 81.05, 30.0668, 79.0193),
        StateBoundary("Himachal Pradesh", 30.35, 33.25, 75.75, 79.05, 31.1048, 77.1734),
        StateBoundary("Jammu and Kashmir", 32.25, 35.50, 73.75, 77.50, 33.7782, 76.5762),
        StateBoundary("Ladakh", 32.00, 36.00, 75.50, 80.50, 34.1526, 77.5771),
        StateBoundary("Assam", 24.10, 28.00, 89.70, 96.00, 26.2006, 92.9376),
        StateBoundary("Tripura", 22.90, 24.55, 91.15, 92.35, 23.9408, 91.9882),
        StateBoundary("Meghalaya", 25.00, 26.10, 89.80, 92.80, 25.4670, 91.3662),
        StateBoundary("Manipur", 23.80, 25.70, 93.00, 94.80, 24.6637, 93.9063),
        StateBoundary("Mizoram", 21.95, 24.50, 92.25, 93.45, 23.1645, 92.9376),
        StateBoundary("Nagaland", 25.10, 27.05, 93.30, 95.25, 26.1584, 94.5624),
        StateBoundary("Arunachal Pradesh", 26.50, 29.50, 91.50, 97.40, 28.2180, 94.7278),
        StateBoundary("Sikkim", 27.05, 28.15, 88.00, 88.95, 27.5330, 88.5122),
        StateBoundary("Andaman and Nicobar Islands", 6.75, 13.70, 92.20, 94.30, 11.7401, 92.6586),
        StateBoundary("Puducherry", 11.75, 12.05, 79.65, 79.95, 11.9416, 79.8083),
        StateBoundary("Chandigarh", 30.65, 30.80, 76.70, 76.85, 30.7333, 76.7794),
        StateBoundary("Dadra and Nagar Haveli and Daman and Diu", 20.00, 20.90, 70.80, 73.20, 20.4283, 72.8397),
        StateBoundary("Lakshadweep", 8.20, 12.40, 71.50, 74.00, 10.5667, 72.6417)
    )

    /**
     * Resolves the user's state instantly and 100% offline using centroid Voronoi & bounding boxes.
     */
    fun resolveOfflineState(lat: Double, lon: Double): String {
        // 1. Check if inside exact bounding box
        val matchingStates = INDIAN_STATES.filter {
            lat >= it.minLat && lat <= it.maxLat && lon >= it.minLon && lon <= it.maxLon
        }

        if (matchingStates.size == 1) {
            return matchingStates.first().name
        }

        // 2. If multiple candidates or offshore / near coastal waters, select nearest centroid
        val candidates = if (matchingStates.isNotEmpty()) matchingStates else INDIAN_STATES
        var closestState = "Odisha"
        var minDistanceSq = Double.MAX_VALUE

        for (state in candidates) {
            val dLat = lat - state.centroidLat
            val dLon = lon - state.centroidLon
            val distSq = dLat * dLat + dLon * dLon
            if (distSq < minDistanceSq) {
                minDistanceSq = distSq
                closestState = state.name
            }
        }

        return closestState
    }

    /**
     * Attempts Geocoder reverse geocode; falls back to offline state resolver on network failure.
     */
    fun detectState(context: Context, lat: Double, lon: Double, onResult: (String) -> Unit) {
        val offlineState = resolveOfflineState(lat, lon)
        try {
            val geocoder = Geocoder(context, Locale.ENGLISH)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                geocoder.getFromLocation(lat, lon, 1) { addresses ->
                    val adminArea = addresses.firstOrNull()?.adminArea
                    onResult(adminArea ?: offlineState)
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                val adminArea = addresses?.firstOrNull()?.adminArea
                onResult(adminArea ?: offlineState)
            }
        } catch (_: Exception) {
            // Offline / Airplane Mode during cyclone emergency
            onResult(offlineState)
        }
    }
}
