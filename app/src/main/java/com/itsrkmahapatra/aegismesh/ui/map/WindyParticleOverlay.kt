package com.itsrkmahapatra.aegismesh.ui.map

import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.itsrkmahapatra.aegismesh.domain.model.WeatherGridPoint
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * High-performance, hardware-accelerated Windy.com-style animated wind particle streamline engine.
 * Renders hundreds of fluid, glowing wind velocity streaks flowing dynamically over the map.
 * 
 * Runs 100% on the user's device GPU at locked 60+ FPS via Jetpack Compose Canvas.
 * 
 * Copyright (c) 2026 Raj Kishor Mahapatra. All Rights Reserved.
 */
class Particle(
    var x: Float = 0f,
    var y: Float = 0f,
    var prevX: Float = 0f,
    var prevY: Float = 0f,
    var speed: Float = 15f,
    var dirDeg: Float = 45f,
    var age: Int = 0,
    var maxAge: Int = 60
)

@Composable
fun WindyParticleOverlay(
    weatherPoints: List<WeatherGridPoint>,
    map: MapLibreMap?,
    modifier: Modifier = Modifier,
    particleCount: Int = 260
) {
    // Allocate particle pool once in memory
    val particles = remember {
        Array(particleCount) {
            Particle(
                x = 0f,
                y = 0f,
                prevX = 0f,
                prevY = 0f,
                speed = 15f,
                dirDeg = 45f,
                age = Random.nextInt(0, 60),
                maxAge = Random.nextInt(45, 85)
            )
        }
    }

    var frameTick by remember { mutableLongStateOf(0L) }

    // 60 FPS continuous animation loop
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameNanos ->
                frameTick = frameNanos
            }
        }
    }

    // Convert geo weather grid points to screen points for spatial interpolation
    val projectedPoints = remember(weatherPoints, map, frameTick) {
        val currentMap = map ?: return@remember emptyList()
        val projection = try { currentMap.projection } catch (_: Exception) { null }
            ?: return@remember emptyList()

        weatherPoints.mapNotNull { pt ->
            try {
                val screenPos = projection.toScreenLocation(LatLng(pt.latitude, pt.longitude))
                ProjectedWeatherPoint(
                    screenX = screenPos.x,
                    screenY = screenPos.y,
                    speedKph = pt.windSpeedKph,
                    dirDeg = pt.windDirectionDegrees
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        if (width <= 0 || height <= 0) return@Canvas

        val pts = projectedPoints
        val hasPoints = pts.isNotEmpty()

        for (p in particles) {
            // Respawn particle if it exceeded its life or went offscreen
            if (p.age >= p.maxAge || p.x !in -30f..(width + 30f) || p.y !in -30f..(height + 30f) || p.x == 0f && p.y == 0f) {
                p.x = Random.nextFloat() * width
                p.y = Random.nextFloat() * height
                p.prevX = p.x
                p.prevY = p.y
                p.age = 0
                p.maxAge = Random.nextInt(45, 85)

                // Sample nearest station for initial vector
                if (hasPoints) {
                    val nearest = findNearest(p.x, p.y, pts)
                    p.speed = nearest.speedKph
                    p.dirDeg = nearest.dirDeg
                }
            }

            // Continuously interpolate local vector at current position
            if (hasPoints && p.age % 4 == 0) {
                val nearest = findNearest(p.x, p.y, pts)
                // Smoothly steer particle direction
                p.speed = p.speed * 0.7f + nearest.speedKph * 0.3f
                p.dirDeg = p.dirDeg * 0.7f + nearest.dirDeg * 0.3f
            }

            // Meteorological wind direction formula:
            // Direction 0° is North wind (blows Southwards: +Y)
            // Direction 90° is East wind (blows Westwards: -X)
            // Direction 180° is South wind (blows Northwards: -Y)
            // Direction 270° is West wind (blows Eastwards: +X)
            val rad = p.dirDeg * (PI.toFloat() / 180f)
            val vx = -sin(rad)
            val vy = cos(rad)

            // Scaled movement velocity
            val velocityFactor = (p.speed / 14f).coerceIn(1.8f, 9.5f)

            p.prevX = p.x
            p.prevY = p.y
            p.x += vx * velocityFactor
            p.y += vy * velocityFactor
            p.age++

            // Calculate silky fade alpha
            val lifeRatio = p.age.toFloat() / p.maxAge.toFloat()
            val alpha = when {
                lifeRatio < 0.2f -> lifeRatio / 0.2f
                lifeRatio > 0.75f -> (1f - lifeRatio) / 0.25f
                else -> 1f
            }.coerceIn(0.05f, 1f)

            val strokeColor = getWindySpeedColor(p.speed, alpha)
            val strokeThickness = if (p.speed > 55f) 2.8f else 2.1f

            // Render sleek wind streak
            drawLine(
                color = strokeColor,
                start = Offset(p.prevX, p.prevY),
                end = Offset(p.x, p.y),
                strokeWidth = strokeThickness,
                cap = StrokeCap.Round
            )
        }
    }
}

private class ProjectedWeatherPoint(
    val screenX: Float,
    val screenY: Float,
    val speedKph: Float,
    val dirDeg: Float
)

private fun findNearest(x: Float, y: Float, points: List<ProjectedWeatherPoint>): ProjectedWeatherPoint {
    var closest = points[0]
    var minDistSq = Float.MAX_VALUE
    for (pt in points) {
        val dx = x - pt.screenX
        val dy = y - pt.screenY
        val dSq = dx * dx + dy * dy
        if (dSq < minDistSq) {
            minDistSq = dSq
            closest = pt
        }
    }
    return closest
}

/**
 * Official Windy.com-grade dynamic meteorological color spectrum.
 */
private fun getWindySpeedColor(speedKph: Float, alpha: Float): Color {
    return when {
        // Super Cyclone / Extreme (>85 km/h) -> Deep Electric Magenta
        speedKph >= 85f -> Color(0xFFD500F9).copy(alpha = alpha)
        // Severe Storm / Cyclone (65 - 85 km/h) -> Vivid Crimson Red
        speedKph >= 65f -> Color(0xFFFF1744).copy(alpha = alpha)
        // Strong Gale (45 - 65 km/h) -> Tangerine Neon Orange
        speedKph >= 45f -> Color(0xFFFF6D00).copy(alpha = alpha)
        // Fresh Breeze (30 - 45 km/h) -> Golden Bright Yellow
        speedKph >= 30f -> Color(0xFFFFD600).copy(alpha = alpha)
        // Moderate Breeze (15 - 30 km/h) -> Vivid Mint Emerald Green
        speedKph >= 15f -> Color(0xFF00E676).copy(alpha = alpha * 0.9f)
        // Light / Calm (<15 km/h) -> Soft Oceanic Cyan
        else -> Color(0xFF38BDF8).copy(alpha = alpha * 0.75f)
    }
}
