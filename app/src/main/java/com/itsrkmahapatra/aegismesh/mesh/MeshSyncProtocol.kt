package com.itsrkmahapatra.aegismesh.mesh

import org.json.JSONObject
import java.nio.charset.StandardCharsets

/**
 * Handshake and message routing protocol for AegisMesh P2P cluster.
 * Ensures instant sync decision in <50ms prior to sending compressed payload.
 */
object MeshSyncProtocol {

    const val TYPE_HANDSHAKE = "AEGIS_HANDSHAKE"
    const val TYPE_REQUEST_SYNC = "AEGIS_REQ_SYNC"

    fun buildHandshake(deviceId: String, latestTimestampEpoch: Long): ByteArray {
        val json = JSONObject()
        json.put("type", TYPE_HANDSHAKE)
        json.put("device_id", deviceId)
        json.put("latest_ts", latestTimestampEpoch)
        return json.toString().toByteArray(StandardCharsets.UTF_8)
    }

    fun parseHandshake(bytes: ByteArray): HandshakeData? {
        return try {
            val str = String(bytes, StandardCharsets.UTF_8)
            val json = JSONObject(str)
            if (json.optString("type") == TYPE_HANDSHAKE) {
                HandshakeData(
                    deviceId = json.getString("device_id"),
                    latestTimestampEpoch = json.getLong("latest_ts")
                )
            } else null
        } catch (_: Exception) {
            null
        }
    }

    data class HandshakeData(
        val deviceId: String,
        val latestTimestampEpoch: Long
    )
}
