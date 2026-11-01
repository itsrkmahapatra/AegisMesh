package com.itsrkmahapatra.aegismesh.domain.model

/**
 * Lightweight GZIP transferable payload for Google Nearby Connections P2P mesh sync.
 * Transferred over BLE / Wi-Fi Direct in <0.3s (~40-55 KB compressed).
 */
data class MeshSyncPayload(
    val version: Int = 1,
    val senderDeviceId: String,
    val broadcastTimestampEpoch: Long,
    val maxAlertTimestampEpoch: Long,
    val alerts: List<EmergencyAlert>,
    val weatherSample: List<WeatherGridPoint> = emptyList(),
    val timeToLiveHops: Int = 5 // Max 5 hops to prevent endless mesh loops
)

