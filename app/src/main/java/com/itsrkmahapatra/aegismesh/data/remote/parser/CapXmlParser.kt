package com.itsrkmahapatra.aegismesh.data.remote.parser

import android.util.Xml
import com.itsrkmahapatra.aegismesh.domain.model.AlertSeverity
import com.itsrkmahapatra.aegismesh.domain.model.EmergencyAlert
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Direct On-Device SAX/PullParser for NDMA Sachet CAP RSS 2.0 Feed
 * (https://sachet.ndma.gov.in/cap_public_website/rss/rss_india.xml).
 * Zero server proxy required. Parses official IMD, CWC, and INCOIS alerts.
 */
class CapXmlParser {

    private val rfc822DateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("GMT")
    }

    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ENGLISH)

    fun parseRss(inputStream: InputStream): List<EmergencyAlert> {
        val alerts = mutableListOf<EmergencyAlert>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        var currentTag: String? = null

        // Item level state
        var insideItem = false
        var title = ""
        var description = ""
        var link = ""
        var guid = ""
        var pubDate = ""
        var instruction = ""
        var event = ""
        var severity = ""
        var urgency = ""
        var certainty = ""
        var areaDesc = ""
        var polygon = ""
        var authority = "NDMA Sachet"

        val currentTime = System.currentTimeMillis()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name

            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = tagName
                    if (tagName.equals("item", ignoreCase = true) || tagName.equals("cap:alert", ignoreCase = true)) {
                        insideItem = true
                        title = ""
                        description = ""
                        link = ""
                        guid = ""
                        pubDate = ""
                        instruction = ""
                        event = ""
                        severity = ""
                        urgency = ""
                        certainty = ""
                        areaDesc = ""
                        polygon = ""
                        authority = "NDMA / IMD / CWC"
                    }
                }

                XmlPullParser.TEXT -> {
                    if (insideItem && currentTag != null) {
                        val text = parser.text?.trim() ?: ""
                        if (text.isNotEmpty()) {
                            when (currentTag.lowercase()) {
                                "title" -> title = (title + " " + text).trim()
                                "description" -> description = (description + " " + text).trim()
                                "link" -> link = (link + text).trim()
                                "guid" -> guid = (guid + text).trim()
                                "pubdate" -> pubDate = (pubDate + text).trim()
                                "instruction", "cap:instruction" -> instruction = (instruction + " " + text).trim()
                                "event", "cap:event" -> event = (event + " " + text).trim()
                                "severity", "cap:severity" -> severity = text
                                "urgency", "cap:urgency" -> urgency = text
                                "certainty", "cap:certainty" -> certainty = text
                                "areadesc", "cap:areadesc" -> areaDesc = (areaDesc + " " + text).trim()
                                "polygon", "cap:polygon" -> polygon = (polygon + " " + text).trim()
                                "sender", "cap:sendername", "source" -> authority = text
                            }
                        }
                    }
                }

                XmlPullParser.END_TAG -> {
                    if (tagName.equals("item", ignoreCase = true) || tagName.equals("cap:alert", ignoreCase = true)) {
                        insideItem = false

                        // If CAP specific tags were missing from RSS item, extract from title/description
                        if (severity.isEmpty()) {
                            severity = extractSeverityFromText("$title $description")
                        }
                        if (event.isEmpty()) {
                            event = extractEventFromText(title)
                        }
                        if (instruction.isEmpty()) {
                            instruction = extractInstructionFromText(description)
                        }
                        if (areaDesc.isEmpty()) {
                            areaDesc = title
                        }

                        // Determine effective & expires timestamps
                        val pubDateEpoch = parseDateToEpoch(pubDate, currentTime)
                        val expiresEpoch = pubDateEpoch + (48 * 3600 * 1000L) // Default 48h active window

                        // Compute centroid coordinates from polygon or default fallback
                        val (centroidLat, centroidLon) = parsePolygonCentroid(polygon)

                        val alertId = if (guid.isNotEmpty()) guid else "alert_${title.hashCode()}_$pubDateEpoch"

                        val parsedAlert = EmergencyAlert(
                            id = alertId,
                            title = title.ifEmpty { "Severe Weather Advisory" },
                            description = cleanHtmlTags(description),
                            instruction = instruction.ifEmpty { "Follow local district disaster management authority (DDMA) instructions. Keep battery charged and emergency kit ready." },
                            category = "Met",
                            severity = AlertSeverity.fromString(severity),
                            urgency = urgency.ifEmpty { "Immediate" },
                            certainty = certainty.ifEmpty { "Likely" },
                            event = event.ifEmpty { "Cyclonic Storm / Heavy Rainfall" },
                            effectiveEpoch = pubDateEpoch,
                            expiresEpoch = expiresEpoch,
                            areaDescription = areaDesc,
                            centroidLat = centroidLat,
                            centroidLon = centroidLon,
                            polygonGeoJson = if (polygon.isNotEmpty()) polygon else null,
                            sourceAuthority = authority,
                            syncTimestampEpoch = currentTime,
                            hopCount = 0
                        )

                        alerts.add(parsedAlert)
                    }
                    currentTag = null
                }
            }
            eventType = parser.next()
        }

        return alerts
    }

    private fun extractSeverityFromText(text: String): String {
        val lower = text.lowercase()
        return when {
            lower.contains("red") || lower.contains("extreme") || lower.contains("danger") || lower.contains("super cyclone") -> "Extreme"
            lower.contains("orange") || lower.contains("severe") || lower.contains("very heavy") -> "Severe"
            lower.contains("yellow") || lower.contains("moderate") || lower.contains("heavy") -> "Moderate"
            else -> "Moderate"
        }
    }

    private fun extractEventFromText(title: String): String {
        val lower = title.lowercase()
        return when {
            lower.contains("cyclone") || lower.contains("storm") -> "Cyclonic Storm"
            lower.contains("flood") || lower.contains("inundation") -> "Flash Flood"
            lower.contains("rain") || lower.contains("rainfall") -> "Heavy Rainfall"
            lower.contains("tsunami") || lower.contains("surge") -> "Storm Surge / High Waves"
            lower.contains("thunderstorm") || lower.contains("lightning") -> "Thunderstorm & Lightning"
            lower.contains("heat") -> "Heat Wave"
            else -> "Disaster Weather Alert"
        }
    }

    private fun extractInstructionFromText(desc: String): String {
        val lower = desc.lowercase()
        return when {
            lower.contains("action suggested") || lower.contains("do's") || lower.contains("dont's") -> cleanHtmlTags(desc)
            lower.contains("cyclone") -> "Move to designated cyclone shelter. Secure loose exterior items. Disconnect electrical mains if water rises."
            lower.contains("flood") -> "Avoid crossing waterlogged causeways or bridges. Move livestock and valuables to upper floors."
            else -> "Stay indoors during gale wind gusts. Keep emergency kit and battery powerbanks ready."
        }
    }

    private fun cleanHtmlTags(raw: String): String {
        return raw.replace(Regex("<[^>]*>"), " ").replace(Regex("\\s+"), " ").trim()
    }

    private fun parsePolygonCentroid(polygonStr: String): Pair<Double, Double> {
        if (polygonStr.isBlank()) {
            return Pair(20.5937, 78.9629) // Center of India fallback
        }
        try {
            val pairs = polygonStr.trim().split(" ")
            var sumLat = 0.0
            var sumLon = 0.0
            var count = 0
            for (p in pairs) {
                val coords = p.split(",")
                if (coords.size >= 2) {
                    val lat = coords[0].toDoubleOrNull()
                    val lon = coords[1].toDoubleOrNull()
                    if (lat != null && lon != null) {
                        sumLat += lat
                        sumLon += lon
                        count++
                    }
                }
            }
            if (count > 0) {
                return Pair(sumLat / count, sumLon / count)
            }
        } catch (_: Exception) {
            // Fallback safely
        }
        return Pair(20.5937, 78.9629)
    }

    private fun parseDateToEpoch(pubDate: String, fallback: Long): Long {
        if (pubDate.isBlank()) return fallback
        return try {
            rfc822DateFormat.parse(pubDate)?.time ?: fallback
        } catch (_: Exception) {
            try {
                isoDateFormat.parse(pubDate)?.time ?: fallback
            } catch (_: Exception) {
                fallback
            }
        }
    }
}

