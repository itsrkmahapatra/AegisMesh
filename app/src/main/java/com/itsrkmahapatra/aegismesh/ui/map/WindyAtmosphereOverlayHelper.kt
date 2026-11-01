package com.itsrkmahapatra.aegismesh.ui.map

import android.graphics.Color
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
import org.maplibre.android.style.layers.PropertyFactory.circleBlur
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * Windy-grade smooth atmospheric velocity field overlay for MapLibre Native SDK.
 * Renders smooth Gaussian atmospheric glow fields instead of clunky rectangular text chips.
 * 
 * Copyright (c) 2026 Raj Kishor Mahapatra. All Rights Reserved.
 */
class WindyAtmosphereOverlayHelper(
    private val map: MapLibreMap
) {

    companion object {
        const val SOURCE_ID = "windy_atmosphere_source"
        const val LAYER_VELOCITY_GLOW = "windy_velocity_glow_layer"
        const val LAYER_STORM_NUCLEUS = "windy_storm_nucleus_layer"
    }

    fun setupLayers(style: Style) {
        if (style.getSource(SOURCE_ID) == null) {
            val emptyGeoJson = createGeoJsonFeatureCollection(emptyList())
            style.addSource(GeoJsonSource(SOURCE_ID, emptyGeoJson))
        }

        // 1. Smooth Gaussian Atmospheric Velocity Glow (Windy Heatmap field)
        if (style.getLayer(LAYER_VELOCITY_GLOW) == null) {
            val glowLayer = CircleLayer(LAYER_VELOCITY_GLOW, SOURCE_ID).apply {
                setProperties(
                    circleRadius(
                        interpolate(
                            linear(), get("wind_speed"),
                            stop(0.0, 35f),
                            stop(30.0, 60f),
                            stop(60.0, 95f),
                            stop(100.0, 140f)
                        )
                    ),
                    circleColor(get("color_hex")),
                    circleBlur(0.88f), // Gaussian diffusion for silky smooth velocity field
                    circleOpacity(0.38f)
                )
            }
            style.addLayer(glowLayer)
        }

        // 2. High-Tech Compact Storm Core Marker (Minimalist glowing node)
        if (style.getLayer(LAYER_STORM_NUCLEUS) == null) {
            val nucleusLayer = CircleLayer(LAYER_STORM_NUCLEUS, SOURCE_ID).apply {
                setProperties(
                    circleRadius(
                        interpolate(
                            linear(), get("wind_speed"),
                            stop(0.0, 3.5f),
                            stop(30.0, 5.0f),
                            stop(60.0, 7.5f),
                            stop(100.0, 10.0f)
                        )
                    ),
                    circleColor(get("color_hex")),
                    circleOpacity(0.95f),
                    circleStrokeWidth(1.5f),
                    circleStrokeColor(Color.WHITE)
                )
            }
            style.addLayer(nucleusLayer)
        }
    }

    fun updateWeatherData(style: Style, points: List<WeatherGridPoint>) {
        if (points.isEmpty()) return

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
            feature.put("id", pt.id)

            val geom = JSONObject()
            geom.put("type", "Point")
            val coords = JSONArray()
            coords.put(pt.longitude)
            coords.put(pt.latitude)
            geom.put("coordinates", coords)
            feature.put("geometry", geom)

            val props = JSONObject()
            props.put("city_name", pt.cityName)
            props.put("wind_speed", pt.windSpeedKph.toDouble())
            props.put("wind_direction", pt.windDirectionDegrees.toDouble())
            props.put("wind_gusts", pt.windGustsKph.toDouble())
            props.put("precipitation", pt.precipitationMm.toDouble())
            props.put("temperature", pt.temperatureCelsius.toDouble())
            props.put("color_hex", pt.colorHex)
            feature.put("properties", props)

            features.put(feature)
        }
        root.put("features", features)
        return root.toString()
    }
}
