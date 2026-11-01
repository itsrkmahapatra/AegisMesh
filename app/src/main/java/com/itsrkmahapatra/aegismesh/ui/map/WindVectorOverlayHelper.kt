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
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * High-performance vector rendering of 7-day hourly wind vectors and precipitation
 * onto MapLibre Native SDK via GeoJsonSource and CircleLayers.
 */
class WindVectorOverlayHelper(private val map: MapLibreMap) {

    companion object {
        const val SOURCE_ID = "weather_vector_source"
        const val LAYER_WIND_CIRCLES = "weather_wind_circles_layer"
        const val LAYER_PRECIP_HALO = "weather_precip_halo_layer"
    }

    fun setupLayers(style: Style) {
        if (style.getSource(SOURCE_ID) == null) {
            val emptyGeoJson = createGeoJsonFeatureCollection(emptyList())
            style.addSource(GeoJsonSource(SOURCE_ID, emptyGeoJson))
        }

        // Precipitation outer halo layer
        if (style.getLayer(LAYER_PRECIP_HALO) == null) {
            val haloLayer = CircleLayer(LAYER_PRECIP_HALO, SOURCE_ID).apply {
                setProperties(
                    circleRadius(
                        interpolate(
                            linear(), get("precipitation"),
                            stop(0.0, 0f),
                            stop(2.0, 16f),
                            stop(10.0, 30f),
                            stop(30.0, 50f)
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

        // Wind speed inner intensity circle
        if (style.getLayer(LAYER_WIND_CIRCLES) == null) {
            val windLayer = CircleLayer(LAYER_WIND_CIRCLES, SOURCE_ID).apply {
                setProperties(
                    circleRadius(
                        interpolate(
                            linear(), get("wind_speed"),
                            stop(0.0, 8f),
                            stop(30.0, 14f),
                            stop(60.0, 22f),
                            stop(100.0, 32f)
                        )
                    ),
                    circleColor(
                        interpolate(
                            linear(), get("wind_speed"),
                            stop(0.0, color(Color.parseColor("#10B981"))),    // Calm: Green
                            stop(30.0, color(Color.parseColor("#06B6D4"))),   // Breeze: Cyan
                            stop(50.0, color(Color.parseColor("#FBBF24"))),   // Strong: Yellow
                            stop(75.0, color(Color.parseColor("#F97316"))),   // Gale: Orange
                            stop(100.0, color(Color.parseColor("#EF4444")))   // Cyclone: Red
                        )
                    ),
                    circleOpacity(0.85f),
                    circleStrokeWidth(2f),
                    circleStrokeColor(Color.WHITE)
                )
            }
            style.addLayer(windLayer)
        }
    }

    fun updateWeatherData(style: Style, points: List<WeatherGridPoint>) {
        val source = style.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return
        val geoJson = createGeoJsonFeatureCollection(points)
        source.setGeoJson(geoJson)
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
            properties.put("wind_speed", pt.windSpeedKph)
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
