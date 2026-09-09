package com.atta.mcpanel.overlay

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
import com.atta.mcpanel.MainActivity
import com.atta.mcpanel.R

/**
 * سرویس پیش‌زمینهٔ پنل شناور روی بازی.
 * پنجرهٔ overlay را در [PanelHost] نگه می‌دارد و یک اعلان همیشگی نمایش می‌دهد.
 */
class OverlayService : Service() {

    private var host: PanelHost? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        if (host == null) {
            host = PanelHost(this)
            host?.attach()
        }
        return START_STICKY
    }

    private fun startInForeground() {
        val pi = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPi = PendingIntent.getBroadcast(
            this, 2,
            Intent(OverlayReceiver.ACTION_STOP).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(pi)
            .addAction(0, getString(R.string.notif_stop), stopPi)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    override fun onDestroy() {
        host?.detach()
        host = null
        running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val mgr = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.channel_desc)
            setShowBadge(false)
        }
        mgr.createNotificationChannel(ch)
    }

    companion object {
        private const val CHANNEL_ID = "atta_overlay"
        private const val NOTIF_ID = 1001

        @Volatile
        var running: Boolean = false
            private set

        fun isRunning(): Boolean = running

        fun start(context: Context) {
            if (running) return
            running = true // خوش‌بینانه؛ رابط بلافاصله به‌روز شود
            try {
                context.startForegroundService(Intent(context, OverlayService::class.java))
            } catch (e: Exception) {
                running = false
            }
        }

        fun stop(context: Context) {
            running = false
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
