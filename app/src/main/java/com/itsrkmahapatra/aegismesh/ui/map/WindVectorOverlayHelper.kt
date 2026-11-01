package com.itsrkmahapatra.aegismesh.ui.map

import android.graphics.Color
import com.itsrkmahapatra.aegismesh.domain.model.WeatherGridPoint
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression.color
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
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * High-performance vector rendering of 7-day hourly wind vectors, precipitation,
 * and wind speed labels onto MapLibre Native SDK via GeoJsonSource.
 */
class WindVectorOverlayHelper(private val map: MapLibreMap) {

    companion object {
        const val SOURCE_ID = "weather_vector_source"
        const val LAYER_PRECIP_HALO = "weather_precip_halo_layer"
        const val LAYER_WIND_CIRCLES = "weather_wind_circles_layer"
        const val LAYER_WIND_LABELS = "weather_wind_labels_layer"
    }

    fun setupLayers(style: Style) {
        if (style.getSource(SOURCE_ID) == null) {
            val emptyGeoJson = createGeoJsonFeatureCollection(emptyList())
            style.addSource(GeoJsonSource(SOURCE_ID, emptyGeoJson))
        }

        // 1. Precipitation outer halo layer
        if (style.getLayer(LAYER_PRECIP_HALO) == null) {
            val haloLayer = CircleLayer(LAYER_PRECIP_HALO, SOURCE_ID).apply {
                setProperties(
                    circleRadius(
                        interpolate(
                            linear(), get("precipitation"),
                            stop(0.0, 0f),
                            stop(2.0, 22f),
                            stop(10.0, 38f),
                            stop(30.0, 60f)
                        )
                    ),
                    circleColor(Color.parseColor("#38BDF8")),
                    circleOpacity(0.35f),
                    circleStrokeWidth(1.5f),
                    circleStrokeColor(Color.parseColor("#0284C7"))
                )
            }
            style.addLayer(haloLayer)
        }

        // 2. Wind speed intensity circle
        if (style.getLayer(LAYER_WIND_CIRCLES) == null) {
            val windLayer = CircleLayer(LAYER_WIND_CIRCLES, SOURCE_ID).apply {
                setProperties(
                    circleRadius(
                        interpolate(
                            linear(), get("wind_speed"),
                            stop(0.0, 16f),
                            stop(30.0, 22f),
                            stop(60.0, 30f),
                            stop(100.0, 42f)
                        )
                    ),
                    circleColor(
                        interpolate(
                            linear(), get("wind_speed"),
                            stop(0.0, color(Color.parseColor("#10B981"))),    // Calm: Green
                            stop(30.0, color(Color.parseColor("#06B6D4"))),   // Moderate: Cyan
                            stop(50.0, color(Color.parseColor("#FBBF24"))),   // Strong: Amber
                            stop(70.0, color(Color.parseColor("#F97316"))),   // Gale: Orange
                            stop(95.0, color(Color.parseColor("#EF4444")))    // Cyclone: Red
                        )
                    ),
                    circleOpacity(0.85f),
                    circleStrokeWidth(2.5f),
                    circleStrokeColor(Color.WHITE)
                )
            }
            style.addLayer(windLayer)
        }

        // 3. Prominent wind speed text labels (e.g. "45 km/h")
        if (style.getLayer(LAYER_WIND_LABELS) == null) {
            val labelLayer = SymbolLayer(LAYER_WIND_LABELS, SOURCE_ID).apply {
                setProperties(
                    textField(get("label_text")),
                    textSize(11f),
                    textColor(Color.WHITE),
                    textHaloColor(Color.parseColor("#0A0E17")),
                    textHaloWidth(2f)
                )
            }
            style.addLayer(labelLayer)
        }
    }

    fun updateWeatherData(style: Style, points: List<WeatherGridPoint>) {
        try {
            val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID)
            if (source != null) {
                val geoJson = createGeoJsonFeatureCollection(points)
                source.setGeoJson(geoJson)
            }
        } catch (_: Exception) {}
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
            val speedInt = pt.windSpeedKph.toInt()
            properties.put("wind_speed", pt.windSpeedKph)
            properties.put("label_text", "$speedInt km/h")
            properties.put("wind_direction", pt.windDirectionDegrees)
            properties.put("wind_gusts", pt.windGustsKph)
            properties.put("precipitation", pt.precipitationMm)
            properties.put("temperature", pt.temperatureCelsius)
            properties.put("hour", pt.hourOffset)
            feature.put("properties", properties)

            features.put(feature)
        }
        root.put("features", features)
        return root.toString()
    }
}
