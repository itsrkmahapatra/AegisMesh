package com.itsrkmahapatra.aegismesh.domain.model

import com.itsrkmahapatra.aegismesh.util.AlertLanguageHelper

/**
 * Domain representation of an official government emergency alert (NDMA CAP RSS).
 * No user chat, 100% broadcast/read-only compliance.
 * 
 * Copyright (c) 2026 Raj Kishor Mahapatra. All Rights Reserved.
 */
data class EmergencyAlert(
    val id: String,                         // Unique CAP identifier or guid
    val title: String,                      // Event headline (e.g., Cyclone Dana Red Alert)
    val description: String,                // Full official warning text
    val instruction: String,                // Official NDMA Do's and Don'ts
    val category: String,                   // Met, Geo, Safety, etc.
    val severity: AlertSeverity,            // Extreme, Severe, Moderate
    val urgency: String,                    // Immediate, Expected, Future
    val certainty: String,                  // Observed, Likely, Possible
    val event: String,                      // Cyclone, Flash Flood, Heavy Rainfall, Tsunami
    val effectiveEpoch: Long,               // Milliseconds timestamp when warning took effect
    val expiresEpoch: Long,                 // Milliseconds timestamp when warning expires
    val areaDescription: String,            // District / Coastal Taluks affected
    val centroidLat: Double,                // Computed centroid latitude for 50km radius math
    val centroidLon: Double,                // Computed centroid longitude for 50km radius math
    val polygonGeoJson: String?,            // Optional geo-fence boundary points
    val sourceAuthority: String,            // IMD, CWC, INCOIS, SDMA
    val syncTimestampEpoch: Long,           // Last sync timestamp for 'latest wins' resolution
    val hopCount: Int = 0,                  // Mesh relay hop count (TTL decrementing)
    val languageCode: String = "en"         // en, hi, or, bn, te, ta, kn, ml, gu, mr, pa, as
) {
    /**
     * Resolves the alert language based on language code and Unicode script detection.
     */
    val detectedLanguage: AlertLanguage
        get() {
            val fromCode = AlertLanguage.fromCode(languageCode)
            return if (fromCode == AlertLanguage.ENGLISH) {
                // If marked english or default, verify whether title or description actually contains regional script
                AlertLanguageHelper.detectLanguage(title, description, sourceAuthority)
            } else {
                fromCode
            }
        }
}
