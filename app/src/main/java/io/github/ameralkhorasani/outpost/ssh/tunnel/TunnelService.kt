package io.github.ameralkhorasani.outpost.ssh.tunnel

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
import androidx.core.app.NotificationCompat
import io.github.ameralkhorasani.outpost.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps the process - and therefore every open port forward - alive while the user is in
 * another app.
 *
 * Without this, opening http://localhost:8090 in Chrome backgrounds Outpost, and Android
 * is free to kill it along with the listening socket: the browser tab then fails to load
 * with nothing on screen to explain why. A foreground service with a visible notification
 * is the supported way to hold a network connection open on the user's behalf.
 */
@AndroidEntryPoint
class TunnelService : Service() {

    @Inject
    lateinit var tunnelManager: TunnelManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Android kills a service that was started with startForegroundService() and then
     * stopped without ever calling startForeground(). onCreate's collector sees the
     * current tunnel list immediately - before onStartCommand has run - so it must not
     * act on an empty list until the notification is up.
     */
    private var hasStartedForeground = false

    companion object {
        const val ACTION_STOP_ALL = "io.github.ameralkhorasani.outpost.action.STOP_ALL_TUNNELS"

        private const val CHANNEL_ID = "outpost_tunnels"
        private const val NOTIFICATION_ID = 4711

        /** Safe to call repeatedly; a second start just refreshes the notification. */
        fun start(context: Context) {
            val intent = Intent(context, TunnelService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopAll(context: Context) {
            context.startService(
                Intent(context, TunnelService::class.java).setAction(ACTION_STOP_ALL)
            )
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()

        scope.launch {
            tunnelManager.active.collectLatest { tunnels ->
                when {
                    // Nothing left to hold the process open for.
                    tunnels.isEmpty() && hasStartedForeground -> shutdown()
                    tunnels.isEmpty() -> Unit
                    else -> notificationManager().notify(NOTIFICATION_ID, buildNotification())
                }
            }
        }

        // A dropped SSH connection leaves a listening socket that accepts and then fails.
        // Reaping those keeps the notification honest about what is actually reachable.
        scope.launch {
            while (true) {
                delay(30_000)
                tunnelManager.pruneDead()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android requires the notification within a few seconds of the start request,
        // before any of the work below.
        startForegroundCompat()
        hasStartedForeground = true

        if (intent?.action == ACTION_STOP_ALL) {
            scope.launch {
                tunnelManager.stopAll()
                shutdown()
            }
        } else if (tunnelManager.active.value.isEmpty()) {
            // Started for a forward that has already gone away.
            shutdown()
        }

        return START_NOT_STICKY
    }

    private fun shutdown() {
        stopForegroundCompat()
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @Suppress("DEPRECATION")
    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
    }

    private fun buildNotification(): Notification {
        val tunnels = tunnelManager.active.value

        val title = when (tunnels.size) {
            0 -> "Closing tunnels"
            1 -> "1 tunnel open"
            else -> "${tunnels.size} tunnels open"
        }
        val body = tunnels.joinToString("\n") { tunnel ->
            "localhost:${tunnel.localPort} → ${tunnel.serverName}:${tunnel.remotePort}"
        }.ifBlank { "No active forwards" }

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopAll = PendingIntent.getService(
            this,
            1,
            Intent(this, TunnelService::class.java).setAction(ACTION_STOP_ALL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openApp)
            .addAction(0, "Stop all", stopAll)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SSH tunnels",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown while a port forward is open"
            setShowBadge(false)
        }
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager =
        getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}
