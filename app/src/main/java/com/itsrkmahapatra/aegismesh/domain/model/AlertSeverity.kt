package com.itsrkmahapatra.aegismesh.domain.model

import androidx.compose.ui.graphics.Color

/**
 * Common Alerting Protocol (CAP 1.2) Alert Severity Levels
 * Matches official NDMA Sachet, IMD, CWC, and INCOIS severity standards.
 */
enum class AlertSeverity(val level: String, val weight: Int, val primaryColorHex: Long, val badgeText: String) {
    EXTREME("Extreme", 4, 0xFFEF4444, "EXTREME DANGER"),
    SEVERE("Severe", 3, 0xFFF97316, "SEVERE ADVISORY"),
    MODERATE("Moderate", 2, 0xFFFBBF24, "MODERATE WATCH"),
    MINOR("Minor", 1, 0xFF3B82F6, "MINOR NOTICE"),
    UNKNOWN("Unknown", 0, 0xFF94A3B8, "ADVISORY");

    fun toComposeColor(): Color = Color(primaryColorHex)

    companion object {
        fun fromString(value: String?): AlertSeverity {
            return when (value?.trim()?.lowercase()) {
                "extreme" -> EXTREME
                "severe" -> SEVERE
                "moderate" -> MODERATE
                "minor" -> MINOR
                else -> UNKNOWN
            }
        }
    }
}
