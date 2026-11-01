package com.itsrkmahapatra.aegismesh.util

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-performance geodetic mathematics for 50km proximity filtering,
 * camera bounding box locks, and wind vector calculations.
 */
object GeoUtils {
    private const val EARTH_RADIUS_KM = 6371.0

    /**
     * Accurate Great-Circle distance via Haversine formula.
     * Computes distance in kilometers between two GPS coordinates.
     */
    fun calculateDistanceKm(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(rLat1) * cos(rLat2) *
                sin(dLon / 2) * sin(dLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    /**
     * Computes a bounding box (SW lat/lon, NE lat/lon) centered around a GPS coordinate
     * for a given radius in kilometers. Used for MapLibre camera bounds locking.
     */
    fun calculateBoundingBox(
        centerLat: Double,
        centerLon: Double,
        radiusKm: Double
    ): BoundingBox {
        val latDelta = Math.toDegrees(radiusKm / EARTH_RADIUS_KM)
        val lonDelta = Math.toDegrees(radiusKm / (EARTH_RADIUS_KM * cos(Math.toRadians(centerLat))))

        return BoundingBox(
            minLat = centerLat - latDelta,
            minLon = centerLon - lonDelta,
            maxLat = centerLat + latDelta,
            maxLon = centerLon + lonDelta
        )
    }

    data class BoundingBox(
        val minLat: Double,
        val minLon: Double,
        val maxLat: Double,
        val maxLon: Double
    )
}

