package com.itsrkmahapatra.aegismesh.mesh

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.itsrkmahapatra.aegismesh.data.repository.AlertRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * P2P Cluster Mesh Coordinator powered by Google Nearby Connections.
 * Operates keylessly over BLE & Wi-Fi Direct when cellular infrastructure fails.
 */
class NearbyConnectionsManager(
    private val context: Context,
    private val repository: AlertRepository,
    private val scope: CoroutineScope
) {

    private val connectionsClient: ConnectionsClient = Nearby.getConnectionsClient(context)
    val deviceId: String = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        ?: "aegis_${Build.MODEL}_${System.currentTimeMillis() % 10000}"

    private val _connectedPeers = MutableStateFlow<Set<String>>(emptySet())
    val connectedPeers: StateFlow<Set<String>> = _connectedPeers.asStateFlow()

    private val _meshEventLog = MutableStateFlow<String>("AegisMesh offline cluster standby")
    val meshEventLog: StateFlow<String> = _meshEventLog.asStateFlow()

    private var isAdvertising = false
    private var isDiscovering = false

    companion object {
        const val SERVICE_ID = "com.itsrkmahapatra.aegismesh.COMMUNITY_RADAR"
        val MESH_STRATEGY: Strategy = Strategy.P2P_CLUSTER
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val bytes = payload.asBytes() ?: return

                // Check if it's a handshake packet
                val handshake = MeshSyncProtocol.parseHandshake(bytes)
                if (handshake != null) {
                    handleIncomingHandshake(endpointId, handshake)
                } else {
                    // It's a compressed GZIP emergency alert bundle
                    handleIncomingGzipBundle(endpointId, bytes)
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            // Transfer status update
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            // Automatically accept peer connections in disaster emergency mesh mode
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            if (resolution.status.isSuccess) {
                _connectedPeers.value = _connectedPeers.value + endpointId
                _meshEventLog.value = "Connected to emergency node: $endpointId"

                // Send initial handshake with our max timestamp
                scope.launch {
                    val latestTs = repository.getLatestSyncTimestamp()
                    val handshakeBytes = MeshSyncProtocol.buildHandshake(deviceId, latestTs)
                    connectionsClient.sendPayload(endpointId, Payload.fromBytes(handshakeBytes))
                }
            } else {
                _connectedPeers.value = _connectedPeers.value - endpointId
            }
        }

        override fun onDisconnected(endpointId: String) {
            _connectedPeers.value = _connectedPeers.value - endpointId
            _meshEventLog.value = "Disconnected from node: $endpointId"
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            if (info.serviceId == SERVICE_ID) {
                connectionsClient.requestConnection(deviceId, endpointId, connectionLifecycleCallback)
            }
        }

        override fun onEndpointLost(endpointId: String) {
            // Endpoint lost
        }
    }

    fun startMesh() {
        startAdvertising()
        startDiscovery()
    }

    fun stopMesh() {
        stopAdvertising()
        stopDiscovery()
    }

    private fun startAdvertising() {
        if (isAdvertising) return
        val options = AdvertisingOptions.Builder().setStrategy(MESH_STRATEGY).build()
        connectionsClient.startAdvertising(deviceId, SERVICE_ID, connectionLifecycleCallback, options)
            .addOnSuccessListener {
                isAdvertising = true
                _meshEventLog.value = "Advertising P2P mesh cluster..."
            }
            .addOnFailureListener {
                isAdvertising = false
            }
    }

    private fun stopAdvertising() {
        if (!isAdvertising) return
        connectionsClient.stopAdvertising()
        isAdvertising = false
    }

    private fun startDiscovery() {
        if (isDiscovering) return
        val options = DiscoveryOptions.Builder().setStrategy(MESH_STRATEGY).build()
        connectionsClient.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
            .addOnSuccessListener {
                isDiscovering = true
                _meshEventLog.value = "Scanning for nearby emergency nodes..."
            }
            .addOnFailureListener {
                isDiscovering = false
            }
    }

    private fun stopDiscovery() {
        if (!isDiscovering) return
        connectionsClient.stopDiscovery()
        isDiscovering = false
    }

    private fun handleIncomingHandshake(endpointId: String, handshake: MeshSyncProtocol.HandshakeData) {
        scope.launch {
            val localTs = repository.getLatestSyncTimestamp()
            // If our local database has newer alerts, broadcast payload to the peer
            if (localTs > handshake.latestTimestampEpoch) {
                val gzipBundle = repository.exportGzipMeshBundle(deviceId)
                connectionsClient.sendPayload(endpointId, Payload.fromBytes(gzipBundle))
                _meshEventLog.value = "Relayed latest cyclone bundle to peer ($endpointId)"
            }
        }
    }

    private fun handleIncomingGzipBundle(endpointId: String, gzipBytes: ByteArray) {
        scope.launch {
            val result = repository.ingestMeshGzipBundle(gzipBytes)
            if (result.isSuccess) {
                val count = result.getOrDefault(0)
                _meshEventLog.value = "Synchronized $count offline alerts from node ($endpointId)"
            }
        }
    }

    fun disconnectAll() {
        connectionsClient.stopAllEndpoints()
        _connectedPeers.value = emptySet()
    }
}
