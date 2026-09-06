package com.arena.mineva.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.arena.mineva.AppPrefs
import com.arena.mineva.ServerWatchdogActivity
import com.arena.mineva.server.ServerConfig
import com.arena.mineva.server.ServerProfileStore
import com.arena.mineva.server.ServerWatchdog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Persistent background watchdog.
 *
 * Keeps polling the active server even when the UI is closed, using a foreground service
 * + notification. It reads the active profile from AppPrefs and remembers the interval,
 * auto-restart flag and whether background monitoring is enabled. For VPS it uses the
 * saved SSH key (passwords are never persisted, so password-only VPS setup can still use
 * the in-app watchdog).
 */
class ServerWatchdogService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var lastNotificationText = "در حال پایش سرور..."

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification(lastNotificationText))
        if (job == null) startLoop()
        return START_STICKY
    }

    private fun startLoop() {
        job?.cancel()
        job = scope.launch {
            while (isActive) {
                try {
                    val config = ServerProfileStore.activeConfig() ?: ServerConfig()
                    val watchdog = ServerWatchdog(this@ServerWatchdogService)
                    val result = watchdog.checkOnce(
                        config = config,
                        autoRestart = AppPrefs.autoRestartEnabled,
                        sshPassword = null,
                        sshKeyPass = null
                    )
                    val text = if (result.restarted) {
                        "⚠ سرور ریاستارت شد: ${result.status.take(60)}"
                    } else {
                        "وضعیت: ${result.status.take(60)}"
                    }
                    if (text != lastNotificationText) {
                        lastNotificationText = text
                        updateNotification(text)
                    }
                } catch (t: Throwable) {
                    updateNotification("خطا در پایش: ${t.message ?: "نامشخص"}")
                }
                delay((AppPrefs.watchdogIntervalSec.coerceIn(3, 300)).toLong() * 1000L)
            }
        }
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun buildNotification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this,
            0,
            Intent(this, ServerWatchdogActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("🛡 آوا — پایش سرور")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "پایش سرور",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "پایش خودکار و ریاستارت سرور در پس‌زمینه"
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "server_watchdog"
        private const val NOTIFICATION_ID = 42

        fun start(context: Context) {
            val intent = Intent(context, ServerWatchdogService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ServerWatchdogService::class.java))
        }
    }
}
