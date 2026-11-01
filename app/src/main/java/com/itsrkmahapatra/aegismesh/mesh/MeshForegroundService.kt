package com.itsrkmahapatra.aegismesh.mesh

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.itsrkmahapatra.aegismesh.AegisApplication
import com.itsrkmahapatra.aegismesh.MainActivity
import com.itsrkmahapatra.aegismesh.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android Foreground Service maintaining persistent offline P2P mesh connectivity.
 * Uses WAKE_LOCK & battery duty cycling to keep emergency disaster relay alive.
 */
class MeshForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var connectionsManager: NearbyConnectionsManager
    private lateinit var dutyCycleOptimizer: BatteryDutyCycleOptimizer

    companion object {
        const val CHANNEL_ID = "aegis_mesh_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.itsrkmahapatra.aegismesh.action.START_MESH"
        const val ACTION_STOP = "com.itsrkmahapatra.aegismesh.action.STOP_MESH"

        fun startService(context: Context) {
            val intent = Intent(context, MeshForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MeshForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val app = application as AegisApplication
        connectionsManager = NearbyConnectionsManager(this, app.alertRepository, serviceScope)

        dutyCycleOptimizer = BatteryDutyCycleOptimizer(
            scope = serviceScope,
            onStartMesh = { connectionsManager.startMesh() },
            onStopMesh = { connectionsManager.stopMesh() }
        )

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AegisMesh:RelayWakeLock").apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundService()
                return START_NOT_STICKY
            }
            else -> {
                startForegroundWithNotification()
                acquireWakeLock()
                dutyCycleOptimizer.startOptimizationLoop()
                observePeers()
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification("AegisMesh Offline Cluster Active", "Relaying disaster alerts across nearby devices")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun observePeers() {
        serviceScope.launch {
            connectionsManager.connectedPeers.collect { peers ->
                val text = if (peers.isEmpty()) {
                    "Searching for nearby emergency nodes (Duty cycling)"
                } else {
                    "Connected to ${peers.size} peer node(s) in disaster mesh"
                }
                val notification = buildNotification("AegisMesh Offline Cluster", text)
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun acquireWakeLock() {
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(24 * 60 * 60 * 1000L) // Safe 24-hr max timeout
            }
        } catch (_: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
    }

    private fun stopForegroundService() {
        dutyCycleOptimizer.stopOptimizationLoop()
        connectionsManager.disconnectAll()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopForegroundService()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(title: String, content: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AegisMesh Offline Emergency Network",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Maintains offline Bluetooth/Wi-Fi mesh connectivity during cellular outages."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
