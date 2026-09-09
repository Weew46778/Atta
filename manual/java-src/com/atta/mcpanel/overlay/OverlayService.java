package com.atta.mcpanel.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;

/** سرویس پیش‌زمینهٔ پنل شناور روی بازی */
public class OverlayService extends Service {

    public static final String CHANNEL_ID = "atta_overlay";
    private static final int NOTIF_ID = 1001;

    private static volatile boolean running = false;

    private PanelHost host;
    private boolean cfgRegistered = false;

    /** چرخش/تغییر نوار سیستم → جای‌گذاری مجدد پنل داخل کادر */
    private final BroadcastReceiver configReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (host != null) host.onConfigChanged();
        }
    };

    public static boolean isRunning() { return running; }

    public static void start(Context context) {
        if (running) return;
        running = true;
        try {
            context.startForegroundService(new Intent(context, OverlayService.class));
        } catch (Exception e) {
            running = false;
        }
    }

    public static void stop(Context context) {
        running = false;
        context.stopService(new Intent(context, OverlayService.class));
    }

    private void registerConfigReceiver() {
        if (cfgRegistered) return;
        try {
            IntentFilter f = new IntentFilter(Intent.ACTION_CONFIGURATION_CHANGED);
            if (Build.VERSION.SDK_INT >= 33) {
                // Context.RECEIVER_NOT_EXPORTED = 0x4 (ثابت در API 33 اضافه شده)
                registerReceiver(configReceiver, f, 0x4);
            } else {
                registerReceiver(configReceiver, f);
            }
            cfgRegistered = true;
        } catch (Exception ignored) {
            cfgRegistered = false;
        }
    }

    private void unregisterConfigReceiver() {
        if (!cfgRegistered) return;
        try {
            unregisterReceiver(configReceiver);
        } catch (Exception ignored) {}
        cfgRegistered = false;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "پنل شناور روی بازی", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startInForeground();
        registerConfigReceiver();
        if (host == null) {
            host = new PanelHost(this);
            host.attach();
        } else {
            host.onConfigChanged();
        }
        return START_STICKY;
    }

    private void startInForeground() {
        Intent open = new Intent(this, com.atta.mcpanel.MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(this, OverlayReceiver.class);
        PendingIntent stopPi = PendingIntent.getBroadcast(
                this, 2, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        int iconId = getResources().getIdentifier("ic_stat", "drawable", getPackageName());
        Notification n = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(iconId)
                .setContentTitle("پنل اپراتور Atta فعال است")
                .setContentText("نوار سبز لبهٔ چپ را لمس کن")
                .setContentIntent(pi)
                .addAction(0, "توقف پنل", stopPi)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
        startForeground(NOTIF_ID, n);
    }

    @Override
    public void onDestroy() {
        unregisterConfigReceiver();
        if (host != null) {
            host.detach();
            host = null;
        }
        running = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
