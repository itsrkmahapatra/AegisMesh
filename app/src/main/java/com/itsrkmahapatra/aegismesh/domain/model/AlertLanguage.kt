package com.itsrkmahapatra.aegismesh.domain.model

/**
 * Supported official alert languages for NDMA Sachet CAP alerts across India.
 * 
 * Rules:
 * - English: Displayed to all users across all states of India.
 * - Hindi: Displayed to all users across all states of India.
 * - Regional Languages: State-specific only (e.g. Odia -> Odisha only, Bangla -> West Bengal only).
 * 
 * Copyright (c) 2026 Raj Kishor Mahapatra. All Rights Reserved.
 */
enum class AlertLanguage(
    val code: String,
    val displayName: String,
    val nativeLabel: String,
    val iconEmoji: String,
    val associatedStates: List<String>
) {
    ENGLISH(
        code = "en",
        displayName = "English",
        nativeLabel = "English",
        iconEmoji = "🌐",
        associatedStates = emptyList() // All states across India
    ),
    HINDI(
        code = "hi",
        displayName = "Hindi",
        nativeLabel = "हिन्दी",
        iconEmoji = "🇮🇳",
        associatedStates = emptyList() // All states across India
    ),
    ODIA(
        code = "or",
        displayName = "Odia",
        nativeLabel = "ଓଡ଼ିଆ",
        iconEmoji = "🌊",
        associatedStates = listOf("Odisha", "Orissa")
    ),
    BANGLA(
        code = "bn",
        displayName = "Bengali",
        nativeLabel = "বাংলা",
        iconEmoji = "🌾",
        associatedStates = listOf("West Bengal", "Tripura")
    ),
    TELUGU(
        code = "te",
        displayName = "Telugu",
        nativeLabel = "తెలుగు",
        iconEmoji = "⚡",
        associatedStates = listOf("Andhra Pradesh", "Telangana")
    ),
    TAMIL(
        code = "ta",
        displayName = "Tamil",
        nativeLabel = "தமிழ்",
        iconEmoji = "🏛️",
        associatedStates = listOf("Tamil Nadu", "Puducherry")
    ),
    KANNADA(
        code = "kn",
        displayName = "Kannada",
        nativeLabel = "ಕನ್ನಡ",
        iconEmoji = "🌿",
        associatedStates = listOf("Karnataka")
    ),
    MALAYALAM(
        code = "ml",
        displayName = "Malayalam",
        nativeLabel = "മലയാളം",
        iconEmoji = "🌴",
        associatedStates = listOf("Kerala", "Lakshadweep")
    ),
    GUJARATI(
        code = "gu",
        displayName = "Gujarati",
        nativeLabel = "ગુજરાતી",
        iconEmoji = "🦁",
        associatedStates = listOf("Gujarat", "Dadra and Nagar Haveli and Daman and Diu", "Daman and Diu")
    ),
    MARATHI(
        code = "mr",
        displayName = "Marathi",
        nativeLabel = "मराठी",
        iconEmoji = "🚩",
        associatedStates = listOf("Maharashtra", "Goa")
    ),
    PUNJABI(
        code = "pa",
        displayName = "Punjabi",
        nativeLabel = "ਪੰਜਾਬੀ",
        iconEmoji = "🌾",
        associatedStates = listOf("Punjab", "Chandigarh")
    ),
    ASSAMESE(
        code = "as",
        displayName = "Assamese",
        nativeLabel = "অসমীয়া",
        iconEmoji = "🦏",
        associatedStates = listOf("Assam")
    );

    /**
     * Returns true if this language is broadcast nationwide to all states of India.
     */
    fun isAllIndia(): Boolean = this == ENGLISH || this == HINDI

    /**
     * Checks if this regional language is relevant to the given user state.
     */
    fun matchesState(stateName: String?): Boolean {
        if (isAllIndia()) return true
        if (stateName.isNullOrBlank()) return false
        val normalized = stateName.trim().lowercase()
        return associatedStates.any {
            val target = it.lowercase()
            normalized == target || normalized.contains(target) || target.contains(normalized)
        }
    }

    companion object {
        fun fromCode(code: String): AlertLanguage {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: ENGLISH
        }
    }
}
