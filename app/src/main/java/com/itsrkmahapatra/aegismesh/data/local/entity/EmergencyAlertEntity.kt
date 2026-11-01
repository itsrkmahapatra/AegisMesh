package com.itsrkmahapatra.aegismesh.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.itsrkmahapatra.aegismesh.domain.model.AlertSeverity
import com.itsrkmahapatra.aegismesh.domain.model.EmergencyAlert

@Entity(
    tableName = "emergency_alerts",
    indices = [
        Index(value = ["severity"]),
        Index(value = ["expiresEpoch"]),
        Index(value = ["centroidLat", "centroidLon"])
    ]
)
data class EmergencyAlertEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val description: String,
    val instruction: String,
    val category: String,
    val severity: String,
    val urgency: String,
    val certainty: String,
    val event: String,
    val effectiveEpoch: Long,
    val expiresEpoch: Long,
    val areaDescription: String,
    val centroidLat: Double,
    val centroidLon: Double,
    val polygonGeoJson: String?,
    val sourceAuthority: String,
    val syncTimestampEpoch: Long,
    val hopCount: Int
) {
    fun toDomain(): EmergencyAlert = EmergencyAlert(
        id = id,
        title = title,
        description = description,
        instruction = instruction,
        category = category,
        severity = AlertSeverity.fromString(severity),
        urgency = urgency,
        certainty = certainty,
        event = event,
        effectiveEpoch = effectiveEpoch,
        expiresEpoch = expiresEpoch,
        areaDescription = areaDescription,
        centroidLat = centroidLat,
        centroidLon = centroidLon,
        polygonGeoJson = polygonGeoJson,
        sourceAuthority = sourceAuthority,
        syncTimestampEpoch = syncTimestampEpoch,
        hopCount = hopCount
    )

    companion object {
        fun fromDomain(domain: EmergencyAlert): EmergencyAlertEntity = EmergencyAlertEntity(
            id = domain.id,
            title = domain.title,
            description = domain.description,
            instruction = domain.instruction,
            category = domain.category,
            severity = domain.severity.name,
            urgency = domain.urgency,
            certainty = domain.certainty,
            event = domain.event,
            effectiveEpoch = domain.effectiveEpoch,
            expiresEpoch = domain.expiresEpoch,
            areaDescription = domain.areaDescription,
            centroidLat = domain.centroidLat,
            centroidLon = domain.centroidLon,
            polygonGeoJson = domain.polygonGeoJson,
            sourceAuthority = domain.sourceAuthority,
            syncTimestampEpoch = domain.syncTimestampEpoch,
            hopCount = domain.hopCount
        )
    }
}
