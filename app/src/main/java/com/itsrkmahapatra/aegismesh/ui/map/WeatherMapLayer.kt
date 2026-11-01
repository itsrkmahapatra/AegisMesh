package com.itsrkmahapatra.aegismesh.ui.map

/**
 * High-level interactive map layers supported on the AegisMesh Live Radar screen.
 * Modeled after world-class platforms like Zoom Earth, Windy, and Ventusky.
 * 
 * Copyright (c) 2026 Raj Kishor Mahapatra. All Rights Reserved.
 */
enum class WeatherMapLayer(
    val id: String,
    val title: String,
    val iconEmoji: String,
    val description: String
) {
    WIND(
        id = "wind",
        title = "Wind Stream",
        iconEmoji = "💨",
        description = "Fluid particle streamlines and wind speed velocity field"
    ),
    RAIN_RADAR(
        id = "rain",
        title = "Rain & Radar",
        iconEmoji = "🌧️",
        description = "Doppler radar reflectivity (dBZ) & falling precipitation streaks"
    ),
    CYCLONE_TRACK(
        id = "cyclone",
        title = "Cyclone Tracker",
        iconEmoji = "🌀",
        description = "Zoom Earth-grade cyclone trajectory, eye vortex, and 72h cone of uncertainty"
    ),
    LIGHTNING(
        id = "lightning",
        title = "Live Lightning",
        iconEmoji = "⚡",
        description = "Real-time electric lightning flash strikes & expanding shockwaves"
    ),
    TEMPERATURE(
        id = "temp",
        title = "Temperature",
        iconEmoji = "🌡️",
        description = "Surface thermal heatmap field and ambient isolines"
    )
}
