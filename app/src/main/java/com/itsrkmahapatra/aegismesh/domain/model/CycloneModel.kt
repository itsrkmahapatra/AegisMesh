package com.itsrkmahapatra.aegismesh.domain.model

/**
 * Domain model for Zoom Earth-style Cyclone & Tropical Storm tracking.
 * Includes past trajectory, live storm eye coordinates, forecast cone of uncertainty,
 * and coastal landfall predictions.
 * 
 * Copyright (c) 2026 Raj Kishor Mahapatra. All Rights Reserved.
 */
data class CycloneTrackNode(
    val stage: String,                  // "PAST", "CURRENT", "FORECAST"
    val timestampEpoch: Long,
    val latitude: Double,
    val longitude: Double,
    val maxWindKph: Float,
    val peakGustsKph: Float,
    val centralPressureHpa: Float,
    val categoryLabel: String,          // "Category 3", "Extremely Severe Cyclonic Storm", etc.
    val formattedTime: String
)

data class ActiveCyclone(
    val id: String,
    val name: String,                   // e.g. "Severe Cyclonic Storm DANA"
    val basin: String,                  // "Bay of Bengal (North Indian Ocean)"
    val intensityCategory: String,      // "Very Severe Cyclonic Storm (VSCS)"
    val currentLat: Double,
    val currentLon: Double,
    val sustainedWindKph: Float,
    val peakGustsKph: Float,
    val centralPressureHpa: Float,
    val movementDirectionText: String,  // "North-Northwest (NNW) at 16 km/h"
    val trackNodes: List<CycloneTrackNode>,
    val conePolygonCoords: List<Pair<Double, Double>>, // Polygon bounding coordinates for Cone of Uncertainty
    val projectedLandfallLocation: String, // e.g. "Dhamra Port / Puri Coast (Odisha)"
    val projectedLandfallEpoch: Long,
    val estimatedLandfallWindKph: Float
)
