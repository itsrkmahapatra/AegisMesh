package com.itsrkmahapatra.aegismesh.util

import com.itsrkmahapatra.aegismesh.domain.model.AlertLanguage
import com.itsrkmahapatra.aegismesh.domain.model.EmergencyAlert

/**
 * On-Device Language Detection and State Filtering Engine for NDMA Emergency Alerts.
 * 
 * Rules enforced:
 * - English language: Shown to all states of India.
 * - Hindi language: Shown to all states of India.
 * - Other languages: State-specific only:
 *     - Odia -> Odisha only
 *     - Bangla -> West Bengal / Tripura only
 *     - Telugu -> Andhra Pradesh / Telangana only
 *     - Tamil -> Tamil Nadu / Puducherry only
 *     - Kannada -> Karnataka only
 *     - Malayalam -> Kerala / Lakshadweep only
 *     - Gujarati -> Gujarat only
 *     - Marathi -> Maharashtra only
 *     - Punjabi -> Punjab only
 *     - Assamese -> Assam only
 * 
 * Copyright (c) 2026 Raj Kishor Mahapatra. All Rights Reserved.
 */
object AlertLanguageHelper {

    /**
     * Inspects Unicode character blocks in the alert text and authority to determine
     * the exact language used.
     */
    fun detectLanguage(title: String, description: String = "", sourceAuthority: String = ""): AlertLanguage {
        val combined = "$title $description $sourceAuthority"

        var odia = 0
        var telugu = 0
        var tamil = 0
        var kannada = 0
        var malayalam = 0
        var gujarati = 0
        var punjabi = 0
        var bengali = 0
        var devanagari = 0
        var assameseSpecial = 0
        var marathiSpecial = 0

        for (ch in combined) {
            val code = ch.code
            when {
                // Odia: \u0B00 to \u0B7F
                code in 0x0B00..0x0B7F -> odia++
                // Telugu: \u0C00 to \u0C7F
                code in 0x0C00..0x0C7F -> telugu++
                // Tamil: \u0B80 to \u0BFF
                code in 0x0B80..0x0BFF -> tamil++
                // Kannada: \u0C80 to \u0CFF
                code in 0x0C80..0x0CFF -> kannada++
                // Malayalam: \u0D00 to \u0D7F
                code in 0x0D00..0x0D7F -> malayalam++
                // Gujarati: \u0A80 to \u0AFF
                code in 0x0A80..0x0AFF -> gujarati++
                // Punjabi / Gurmukhi: \u0A00 to \u0A7F
                code in 0x0A00..0x0A7F -> punjabi++
                // Bengali & Assamese: \u0980 to \u09FF
                code in 0x0980..0x09FF -> {
                    bengali++
                    if (code == 0x09F0 || code == 0x09F1) assameseSpecial++ // ৰ or ৱ
                }
                // Devanagari: \u0900 to \u097F
                code in 0x0900..0x097F -> {
                    devanagari++
                    if (code == 0x0933) marathiSpecial++ // ळ
                }
            }
        }

        // Distinct Regional scripts check
        if (odia >= 2) return AlertLanguage.ODIA
        if (telugu >= 2) return AlertLanguage.TELUGU
        if (tamil >= 2) return AlertLanguage.TAMIL
        if (kannada >= 2) return AlertLanguage.KANNADA
        if (malayalam >= 2) return AlertLanguage.MALAYALAM
        if (gujarati >= 2) return AlertLanguage.GUJARATI
        if (punjabi >= 2) return AlertLanguage.PUNJABI
        if (bengali >= 2) {
            if (assameseSpecial > 0 || sourceAuthority.contains("Assam", ignoreCase = true)) {
                return AlertLanguage.ASSAMESE
            }
            return AlertLanguage.BANGLA
        }

        // Devanagari script: Distinguish Marathi from Hindi
        if (devanagari >= 2) {
            val lower = combined.lowercase()
            val isMarathi = marathiSpecial > 0 ||
                    sourceAuthority.contains("Maharashtra", ignoreCase = true) ||
                    sourceAuthority.contains("Mumbai", ignoreCase = true) ||
                    lower.contains("महाराष्ट्र") || lower.contains("जिल्हा") ||
                    lower.contains("पाऊस") || lower.contains("हवामान") ||
                    lower.contains("सावधान")
            return if (isMarathi) AlertLanguage.MARATHI else AlertLanguage.HINDI
        }

        // Authority-based hints
        val lowerAuth = sourceAuthority.lowercase()
        if ((lowerAuth.contains("osdma") || lowerAuth.contains("odisha")) && odia > 0) return AlertLanguage.ODIA
        if (lowerAuth.contains("andhra") && telugu > 0) return AlertLanguage.TELUGU
        if (lowerAuth.contains("kolkata") && bengali > 0) return AlertLanguage.BANGLA

        // Default to English
        return AlertLanguage.ENGLISH
    }

    /**
     * Determines whether an emergency alert should be displayed to the user
     * given their current location state.
     * 
     * - English: Allowed for all states
     * - Hindi: Allowed for all states
     * - Other languages: Only if alert language matches the user's state
     */
    fun shouldShowAlert(alert: EmergencyAlert, userState: String?): Boolean {
        val lang = alert.detectedLanguage
        if (lang.isAllIndia()) {
            return true
        }
        return lang.matchesState(userState)
    }
}
