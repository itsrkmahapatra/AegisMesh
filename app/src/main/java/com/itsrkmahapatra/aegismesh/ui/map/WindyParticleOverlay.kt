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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.itsrkmahapatra.aegismesh.domain.model.ActiveCyclone
import com.itsrkmahapatra.aegismesh.domain.model.WeatherGridPoint
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Universal multi-mode meteorological visual animation engine.
 * Supports:
 * 1. Wind Particles (Windy style fluid streamlines)
 * 2. Rain Radar (Doppler reflectivity & animated falling rain streaks)
 * 3. Cyclone Tracker (Zoom Earth style rotating vortex eye, cone of uncertainty, and track path)
 * 4. Live Lightning (Electric jagged lightning strikes & shockwave rings)
 * 5. Thermal Heatmap (Temperature field)
 * 
 * Runs 100% on device GPU at 60+ FPS via Compose Canvas.
 * 
 * Copyright (c) 2026 Raj Kishor Mahapatra. All Rights Reserved.
 */
class WindStreamParticle(
    var x: Float = 0f,
    var y: Float = 0f,
    var prevX: Float = 0f,
    var prevY: Float = 0f,
    var speed: Float = 15f,
    var dirDeg: Float = 45f,
    var age: Int = 0,
    var maxAge: Int = 60
)

class RainParticle(
    var x: Float = 0f,
    var y: Float = 0f,
    var length: Float = 14f,
    var speedY: Float = 18f,
    var slantX: Float = 2f
)

class LightningStrike(
    var x: Float = 0f,
    var y: Float = 0f,
    var path: Path = Path(),
    var currentFrame: Int = 0,
    var maxFrames: Int = 14
)

@Composable
fun WindyParticleOverlay(
    activeLayer: WeatherMapLayer,
    weatherPoints: List<WeatherGridPoint>,
    activeCyclone: ActiveCyclone?,
    map: MapLibreMap?,
    modifier: Modifier = Modifier
) {
    val windParticles = remember {
        Array(260) {
            WindStreamParticle(
                age = Random.nextInt(0, 60),
                maxAge = Random.nextInt(45, 85)
            )
        }
    }

    val rainParticles = remember {
        Array(180) {
            RainParticle(
                speedY = Random.nextFloat() * 10f + 16f,
                length = Random.nextFloat() * 10f + 12f
            )
        }
    }

    val activeLightningStrikes = remember { mutableListOf<LightningStrike>() }

    var frameTick by remember { mutableLongStateOf(0L) }
    var lightningCooldown by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameNanos ->
                frameTick = frameNanos
            }
        }
    }

    val projectedPoints = remember(weatherPoints, map, frameTick) {
        val currentMap = map ?: return@remember emptyList()
        val projection = try { currentMap.projection } catch (_: Exception) { null }
            ?: return@remember emptyList()

        weatherPoints.mapNotNull { pt ->
            try {
                val screenPos = projection.toScreenLocation(LatLng(pt.latitude, pt.longitude))
                ProjectedStationPoint(
                    screenX = screenPos.x,
                    screenY = screenPos.y,
                    speedKph = pt.windSpeedKph,
                    dirDeg = pt.windDirectionDegrees,
                    precipMm = pt.precipitationMm,
                    tempC = pt.temperatureCelsius,
                    isThunderstorm = pt.weatherCode in listOf(95, 96, 99) || pt.precipitationMm > 15f
                )
            } catch (_: Exception) {
                null
            }
        }
    }

    // Projected Cyclone track points and cone polygon
    val projectedCycloneData = remember(activeCyclone, map, frameTick) {
        val currentMap = map ?: return@remember null
        val cyclone = activeCyclone ?: return@remember null
        val projection = try { currentMap.projection } catch (_: Exception) { null }
            ?: return@remember null

        try {
            val eyeScreen = projection.toScreenLocation(LatLng(cyclone.currentLat, cyclone.currentLon))
            val trackScreens = cyclone.trackNodes.map { node ->
                val pos = projection.toScreenLocation(LatLng(node.latitude, node.longitude))
                Pair(pos.x, pos.y) to node
            }
            val coneScreens = cyclone.conePolygonCoords.map { (lat, lon) ->
                val pos = projection.toScreenLocation(LatLng(lat, lon))
                Offset(pos.x, pos.y)
            }
            Triple(Offset(eyeScreen.x, eyeScreen.y), trackScreens, coneScreens)
        } catch (_: Exception) {
            null
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        if (width <= 0 || height <= 0) return@Canvas

        val pts = projectedPoints
        val hasPoints = pts.isNotEmpty()

        when (activeLayer) {
            WeatherMapLayer.WIND -> {
                renderWindParticles(this, windParticles, pts, width, height, hasPoints)
            }
            WeatherMapLayer.RAIN_RADAR -> {
                renderRainRadar(this, rainParticles, pts, width, height)
            }
            WeatherMapLayer.CYCLONE_TRACK -> {
                // Background subtle wind flow + Zoom Earth Cyclone Track, Eye Vortex & Cone of Uncertainty
                renderWindParticles(this, windParticles, pts, width, height, hasPoints, opacityScale = 0.45f)
                projectedCycloneData?.let { (eyeOffset, trackNodes, coneOffsets) ->
                    renderCycloneTracking(this, eyeOffset, trackNodes, coneOffsets, frameTick)
                }
            }
            WeatherMapLayer.LIGHTNING -> {
                renderWindParticles(this, windParticles, pts, width, height, hasPoints, opacityScale = 0.35f)
                renderLiveLightning(
                    scope = this,
                    strikes = activeLightningStrikes,
                    pts = pts,
                    width = width,
                    height = height,
                    frameTick = frameTick
                )
            }
            WeatherMapLayer.TEMPERATURE -> {
                renderTemperatureHeatmap(this, pts, width, height)
            }
        }
    }
}

private class ProjectedStationPoint(
    val screenX: Float,
    val screenY: Float,
    val speedKph: Float,
    val dirDeg: Float,
    val precipMm: Float,
    val tempC: Float,
    val isThunderstorm: Boolean
)

// ==========================================
// 1. WIND STREAMLINE SIMULATION (WINDY.COM)
// ==========================================
private fun renderWindParticles(
    scope: DrawScope,
    particles: Array<WindStreamParticle>,
    pts: List<ProjectedStationPoint>,
    width: Float,
    height: Float,
    hasPoints: Boolean,
    opacityScale: Float = 1.0f
) {
    for (p in particles) {
        if (p.age >= p.maxAge || p.x !in -30f..(width + 30f) || p.y !in -30f..(height + 30f) || (p.x == 0f && p.y == 0f)) {
            p.x = Random.nextFloat() * width
            p.y = Random.nextFloat() * height
            p.prevX = p.x
            p.prevY = p.y
            p.age = 0
            p.maxAge = Random.nextInt(45, 85)

            if (hasPoints) {
                val nearest = findNearestStation(p.x, p.y, pts)
                p.speed = nearest.speedKph
                p.dirDeg = nearest.dirDeg
            }
        }

        if (hasPoints && p.age % 4 == 0) {
            val nearest = findNearestStation(p.x, p.y, pts)
            p.speed = p.speed * 0.7f + nearest.speedKph * 0.3f
            p.dirDeg = p.dirDeg * 0.7f + nearest.dirDeg * 0.3f
        }

        val rad = p.dirDeg * (PI.toFloat() / 180f)
        val vx = -sin(rad)
        val vy = cos(rad)
        val velocityFactor = (p.speed / 14f).coerceIn(1.8f, 9.5f)

        p.prevX = p.x
        p.prevY = p.y
        p.x += vx * velocityFactor
        p.y += vy * velocityFactor
        p.age++

        val lifeRatio = p.age.toFloat() / p.maxAge.toFloat()
        val alpha = (when {
            lifeRatio < 0.2f -> lifeRatio / 0.2f
            lifeRatio > 0.75f -> (1f - lifeRatio) / 0.25f
            else -> 1f
        }.coerceIn(0.05f, 1f)) * opacityScale

        val color = getWindySpeedColor(p.speed, alpha)
        scope.drawLine(
            color = color,
            start = Offset(p.prevX, p.prevY),
            end = Offset(p.x, p.y),
            strokeWidth = if (p.speed > 55f) 2.8f else 2.1f,
            cap = StrokeCap.Round
        )
    }
}

// ==========================================
// 2. RAIN & DOPPLER RADAR
// ==========================================
private fun renderRainRadar(
    scope: DrawScope,
    rainParticles: Array<RainParticle>,
    pts: List<ProjectedStationPoint>,
    width: Float,
    height: Float
) {
    // 1. Doppler Radar Reflectivity Patches at stations with rain
    for (pt in pts) {
        if (pt.precipMm > 0.1f) {
            val (radius, color) = when {
                pt.precipMm >= 25f -> 120f to Color(0x77A855F7) // Extreme / Cloudburst Purple
                pt.precipMm >= 15f -> 95f to Color(0x77EF4444)  // Severe Torrential Red
                pt.precipMm >= 7.5f -> 75f to Color(0x77F59E0B) // Heavy Amber
                pt.precipMm >= 2.5f -> 60f to Color(0x6610B981) // Moderate Green
                else -> 45f to Color(0x5506B6D4)                // Light Cyan
            }
            scope.drawCircle(
                color = color,
                radius = radius,
                center = Offset(pt.screenX, pt.screenY)
            )
            scope.drawCircle(
                color = color.copy(alpha = color.alpha * 1.5f),
                radius = radius * 0.45f,
                center = Offset(pt.screenX, pt.screenY)
            )
        }
    }

    // 2. Animated Falling Rain Streaks
    for (rp in rainParticles) {
        if (rp.y > height || rp.x !in 0f..width || (rp.x == 0f && rp.y == 0f)) {
            rp.x = Random.nextFloat() * width
            rp.y = -Random.nextFloat() * 50f
            rp.slantX = Random.nextFloat() * 3f - 1.5f
        }

        rp.y += rp.speedY
        rp.x += rp.slantX

        scope.drawLine(
            color = Color(0x9938BDF8),
            start = Offset(rp.x, rp.y),
            end = Offset(rp.x + rp.slantX, rp.y + rp.length),
            strokeWidth = 1.6f,
            cap = StrokeCap.Round
        )
    }
}

// ==========================================
// 3. ZOOM EARTH CYCLONE TRACK & CONE
// ==========================================
private fun renderCycloneTracking(
    scope: DrawScope,
    eyeOffset: Offset,
    trackNodes: List<Pair<Pair<Float, Float>, com.itsrkmahapatra.aegismesh.domain.model.CycloneTrackNode>>,
    coneOffsets: List<Offset>,
    frameTick: Long
) {
    // 1. Draw Cone of Uncertainty Funnel Polygon
    if (coneOffsets.size >= 3) {
        val conePath = Path().apply {
            moveTo(coneOffsets[0].x, coneOffsets[0].y)
            for (i in 1 until coneOffsets.size) {
                lineTo(coneOffsets[i].x, coneOffsets[i].y)
            }
            close()
        }
        // Translucent funnel fill
        scope.drawPath(
            path = conePath,
            color = Color(0x2838BDF8)
        )
        // Dashed funnel borders
        scope.drawPath(
            path = conePath,
            color = Color(0x9938BDF8),
            style = Stroke(
                width = 2.0f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
            )
        )
    }

    // 2. Draw Track Trajectory Path Line
    if (trackNodes.size >= 2) {
        val pastPath = Path()
        val forecastPath = Path()
        var hasPast = false
        var hasForecast = false

        for ((pos, node) in trackNodes) {
            val (sx, sy) = pos
            if (node.stage == "PAST" || node.stage == "CURRENT") {
                if (!hasPast) {
                    pastPath.moveTo(sx, sy)
                    hasPast = true
                } else {
                    pastPath.lineTo(sx, sy)
                }
            }
            if (node.stage == "CURRENT" || node.stage == "FORECAST") {
                if (!hasForecast) {
                    forecastPath.moveTo(sx, sy)
                    hasForecast = true
                } else {
                    forecastPath.lineTo(sx, sy)
                }
            }
        }

        // Solid white past track
        scope.drawPath(
            path = pastPath,
            color = Color.White,
            style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Dashed cyan forecast track
        scope.drawPath(
            path = forecastPath,
            color = Color(0xFF38BDF8),
            style = Stroke(
                width = 3.5f,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
            )
        )

        // Milestone category pins along the track
        for ((pos, node) in trackNodes) {
            val (sx, sy) = pos
            val pinColor = when {
                node.maxWindKph >= 140f -> Color(0xFFEF4444) // Severe Red
                node.maxWindKph >= 90f -> Color(0xFFF97316)  // Orange
                else -> Color(0xFF38BDF8)
            }
            scope.drawCircle(color = pinColor, radius = 6.5f, center = Offset(sx, sy))
            scope.drawCircle(color = Color.White, radius = 3.0f, center = Offset(sx, sy))
        }
    }

    // 3. Zoom Earth Iconic Cyclone Eye Vortex (Animated Rotating Spiral Eye)
    val rotAngle = (frameTick / 25_000_000f) % 360f
    val radRot = rotAngle * (PI.toFloat() / 180f)

    // Pulsing outer warning halo
    val pulseSize = 40f + sin(rotAngle * 0.1f) * 6f
    scope.drawCircle(
        color = Color(0x33EF4444),
        radius = pulseSize,
        center = eyeOffset
    )
    scope.drawCircle(
        color = Color(0x66EF4444),
        radius = 24f,
        center = eyeOffset,
        style = Stroke(width = 2.5f)
    )

    // Rotating spiral vortex arms
    for (arm in 0 until 4) {
        val armBase = radRot + (arm * (PI.toFloat() / 2f))
        val spiralPath = Path().apply {
            moveTo(eyeOffset.x, eyeOffset.y)
            for (step in 1..8) {
                val r = step * 4.5f
                val theta = armBase + (step * 0.35f)
                lineTo(eyeOffset.x + cos(theta) * r, eyeOffset.y + sin(theta) * r)
            }
        }
        scope.drawPath(
            path = spiralPath,
            color = Color(0xFFEF4444),
            style = Stroke(width = 2.5f, cap = StrokeCap.Round)
        )
    }

    // Cyclone Eye Center Dot
    scope.drawCircle(
        color = Color.White,
        radius = 4.5f,
        center = eyeOffset
    )
}

// ==========================================
// 4. LIVE LIGHTNING FLASHES (ZOOM EARTH)
// ==========================================
private fun renderLiveLightning(
    scope: DrawScope,
    strikes: MutableList<LightningStrike>,
    pts: List<ProjectedStationPoint>,
    width: Float,
    height: Float,
    frameTick: Long
) {
    // Periodically spawn realistic jagged electric lightning at thunderstorm coordinates
    val tStorms = pts.filter { it.isThunderstorm }
    if (tStorms.isNotEmpty() && Random.nextInt(0, 40) == 0 && strikes.size < 4) {
        val target = tStorms.random()
        val gx = target.screenX + Random.nextFloat() * 60f - 30f
        val gy = target.screenY + Random.nextFloat() * 60f - 30f

        // Generate jagged forked lightning path
        val lPath = Path().apply {
            var currX = gx + Random.nextFloat() * 40f - 20f
            var currY = gy - 160f
            moveTo(currX, currY)
            val segments = 6
            for (s in 1..segments) {
                currX += Random.nextFloat() * 32f - 16f
                currY += 26f
                lineTo(currX, currY)

                // Occasional branch fork
                if (s == 3) {
                    val forkX = currX + Random.nextFloat() * 30f - 15f
                    val forkY = currY + 22f
                    lineTo(forkX, forkY)
                    moveTo(currX, currY)
                }
            }
            lineTo(gx, gy)
        }

        strikes.add(LightningStrike(x = gx, y = gy, path = lPath, currentFrame = 0, maxFrames = 12))
    }

    // Render active lightning strikes with glowing aura and expanding shockwave
    val iter = strikes.iterator()
    while (iter.hasNext()) {
        val s = iter.next()
        val progress = s.currentFrame.toFloat() / s.maxFrames.toFloat()
        val flashAlpha = (1f - progress).coerceIn(0f, 1f)

        // 1. Electric cyan glowing aura
        scope.drawPath(
            path = s.path,
            color = Color(0xFF38BDF8).copy(alpha = flashAlpha * 0.9f),
            style = Stroke(width = 6.0f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // 2. White hot electric core
        scope.drawPath(
            path = s.path,
            color = Color.White.copy(alpha = flashAlpha),
            style = Stroke(width = 2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // 3. Ground strike impact shockwave ring
        val shockRadius = 12f + progress * 32f
        scope.drawCircle(
            color = Color(0xFF38BDF8).copy(alpha = flashAlpha * 0.7f),
            radius = shockRadius,
            center = Offset(s.x, s.y),
            style = Stroke(width = 2.0f)
        )

        s.currentFrame++
        if (s.currentFrame >= s.maxFrames) {
            iter.remove()
        }
    }
}

// ==========================================
// 5. TEMPERATURE HEATMAP
// ==========================================
private fun renderTemperatureHeatmap(
    scope: DrawScope,
    pts: List<ProjectedStationPoint>,
    width: Float,
    height: Float
) {
    for (pt in pts) {
        val (color, radius) = when {
            pt.tempC >= 38f -> Color(0x66EF4444) to 110f // Scorching Red
            pt.tempC >= 32f -> Color(0x66F97316) to 95f  // Very Warm Orange
            pt.tempC >= 26f -> Color(0x55FBBF24) to 85f  // Warm Amber
            pt.tempC >= 20f -> Color(0x5510B981) to 75f  // Mild Green
            else -> Color(0x5506B6D4) to 70f             // Cool Cyan
        }
        scope.drawCircle(
            color = color,
            radius = radius,
            center = Offset(pt.screenX, pt.screenY)
        )
    }
}

private fun findNearestStation(x: Float, y: Float, points: List<ProjectedStationPoint>): ProjectedStationPoint {
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

private fun getWindySpeedColor(speedKph: Float, alpha: Float): Color {
    return when {
        speedKph >= 85f -> Color(0xFFD500F9).copy(alpha = alpha)
        speedKph >= 65f -> Color(0xFFFF1744).copy(alpha = alpha)
        speedKph >= 45f -> Color(0xFFFF6D00).copy(alpha = alpha)
        speedKph >= 30f -> Color(0xFFFFD600).copy(alpha = alpha)
        speedKph >= 15f -> Color(0xFF00E676).copy(alpha = alpha * 0.9f)
        else -> Color(0xFF38BDF8).copy(alpha = alpha * 0.75f)
    }
}
