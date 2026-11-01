package com.itsrkmahapatra.aegismesh.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.itsrkmahapatra.aegismesh.domain.model.WeatherGridPoint
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.linear
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconOffset
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * Advanced multi-layer visual radar animation engine for MapLibre Native SDK.
 * Renders dynamic colored storm ripples, precipitation halos, and
 * custom Canvas Bitmap badges featuring emojis, signs, wind arrows, and danger signals.
 */
class WindVectorOverlayHelper(
    private val context: Context,
    private val map: MapLibreMap
) {

    companion object {
        const val SOURCE_ID = "weather_vector_source"
        const val LAYER_PULSE_RIPPLE = "weather_pulse_ripple_layer"
        const val LAYER_WIND_CIRCLES = "weather_wind_circles_layer"
        const val LAYER_WEATHER_CHIPS = "weather_chips_symbol_layer"
    }

    private val registeredIcons = mutableSetOf<String>()

    fun setupLayers(style: Style) {
        if (style.getSource(SOURCE_ID) == null) {
            val emptyGeoJson = createGeoJsonFeatureCollection(emptyList())
            style.addSource(GeoJsonSource(SOURCE_ID, emptyGeoJson))
        }

        // 1. Doppler Radar Pulsing Wave Ripple Halo
        if (style.getLayer(LAYER_PULSE_RIPPLE) == null) {
            val rippleLayer = CircleLayer(LAYER_PULSE_RIPPLE, SOURCE_ID).apply {
                setProperties(
                    circleRadius(
                        interpolate(
                            linear(), get("wind_speed"),
                            stop(0.0, 18f),
                            stop(30.0, 32f),
                            stop(60.0, 48f),
                            stop(100.0, 68f)
                        )
                    ),
                    circleColor(get("color_hex")),
                    circleOpacity(0.35f),
                    circleStrokeWidth(1.5f),
                    circleStrokeColor(Color.WHITE)
                )
            }
            style.addLayer(rippleLayer)
        }

        // 2. High-Visibility Core Storm Intensity Circle
        if (style.getLayer(LAYER_WIND_CIRCLES) == null) {
            val windLayer = CircleLayer(LAYER_WIND_CIRCLES, SOURCE_ID).apply {
                setProperties(
                    circleRadius(
                        interpolate(
                            linear(), get("wind_speed"),
                            stop(0.0, 12f),
                            stop(30.0, 18f),
                            stop(60.0, 24f),
                            stop(100.0, 32f)
                        )
                    ),
                    circleColor(get("color_hex")),
                    circleOpacity(0.92f),
                    circleStrokeWidth(2.5f),
                    circleStrokeColor(Color.WHITE)
                )
            }
            style.addLayer(windLayer)
        }

        // 3. Rich Symbol Badge: Emojis, City Names, Wind Arrows & Signals
        if (style.getLayer(LAYER_WEATHER_CHIPS) == null) {
            val symbolLayer = SymbolLayer(LAYER_WEATHER_CHIPS, SOURCE_ID).apply {
                setProperties(
                    iconImage(get("marker_icon_id")),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true),
                    iconOffset(arrayOf(0f, -38f)) // Placed floating right above the radar core
                )
            }
            style.addLayer(symbolLayer)
        }
    }

    fun updateWeatherData(style: Style, points: List<WeatherGridPoint>) {
        try {
            // Generate and register bitmap badges with emojis & signs for all distinct locations
            for (pt in points) {
                val iconKey = "badge_${pt.cityName.replace(" ", "_")}_${pt.windSpeedKph.toInt()}_${pt.conditionEmoji.hashCode()}"
                if (!registeredIcons.contains(iconKey)) {
                    val bitmap = createStationBadgeBitmap(pt)
                    style.addImage(iconKey, bitmap)
                    registeredIcons.add(iconKey)
                }
            }

            val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID)
            if (source != null) {
                val geoJson = createGeoJsonFeatureCollection(points)
                source.setGeoJson(geoJson)
            }
        } catch (_: Exception) {}
    }

    private fun createStationBadgeBitmap(pt: WeatherGridPoint): Bitmap {
        val speedInt = pt.windSpeedKph.toInt()
        val text = "${pt.conditionEmoji} ${pt.cityName} ${speedInt}km/h ${pt.windArrow}"

        val paintText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val textWidth = paintText.measureText(text)
        val paddingHorizontal = 24f
        val paddingVertical = 14f
        val width = (textWidth + paddingHorizontal * 2).toInt()
        val height = (paintText.textSize + paddingVertical * 2).toInt()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Parse condition color
        val bgColor = try {
            Color.parseColor(pt.colorHex)
        } catch (_: Exception) {
            Color.parseColor("#06B6D4")
        }

        // Background rounded pill
        val paintBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
            style = Paint.Style.FILL
        }
        val paintBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        val rect = RectF(2f, 2f, width - 2f, height - 2f)
        val cornerRadius = height / 2f

        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paintBg)
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paintBorder)

        // Draw text with emoji and wind arrow
        val xPos = paddingHorizontal
        val yPos = (height / 2f) - ((paintText.descent() + paintText.ascent()) / 2f)
        canvas.drawText(text, xPos, yPos, paintText)

        return bitmap
    }

    private fun createGeoJsonFeatureCollection(points: List<WeatherGridPoint>): String {
        val root = JSONObject()
        root.put("type", "FeatureCollection")

        val features = JSONArray()
        for (pt in points) {
            val feature = JSONObject()
            feature.put("type", "Feature")

            val geometry = JSONObject()
            geometry.put("type", "Point")
            val coords = JSONArray()
            coords.put(pt.longitude)
            coords.put(pt.latitude)
            geometry.put("coordinates", coords)
            feature.put("geometry", geometry)

            val properties = JSONObject()
            val iconKey = "badge_${pt.cityName.replace(" ", "_")}_${pt.windSpeedKph.toInt()}_${pt.conditionEmoji.hashCode()}"
            properties.put("marker_icon_id", iconKey)
            properties.put("city", pt.cityName)
            properties.put("wind_speed", pt.windSpeedKph)
            properties.put("wind_direction", pt.windDirectionDegrees)
            properties.put("wind_gusts", pt.windGustsKph)
            properties.put("precipitation", pt.precipitationMm)
            properties.put("temperature", pt.temperatureCelsius)
            properties.put("color_hex", pt.colorHex)
            properties.put("emoji", pt.conditionEmoji)
            properties.put("arrow", pt.windArrow)
            properties.put("signal", pt.signalBadge)
            properties.put("condition", pt.conditionText)
            feature.put("properties", properties)

            features.put(feature)
        }
        root.put("features", features)
        return root.toString()
    }
}
