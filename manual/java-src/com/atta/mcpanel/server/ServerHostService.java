package com.atta.mcpanel.server;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

/**
 * سرویس پیش‌زمینهٔ «سرور Atta» — موتور PocketMine یا Paper را در پس‌زمینه زنده نگه می‌دارد
 * تا هنگام بازی یا استفاده از برنامه‌های دیگر خاموش نشود.
 */
public class ServerHostService extends Service {

    public static final String CHANNEL_ID = "atta_server";
    private static final int NOTIF_ID = 2001;
    private static final String ACTION_INSTALL = "com.atta.mcpanel.INSTALL";
    private static final String ACTION_START = "com.atta.mcpanel.SERVER_START";
    private static final String ACTION_STOP = "com.atta.mcpanel.SERVER_STOP";
    public static final String EXTRA_ENGINE = "engine";
    public static final String ENGINE_PMMP = "pmmp";
    public static final String ENGINE_PAPER = "paper";

    private static volatile boolean running = false;

    public static boolean isRunning() { return running; }

    public static void startInstall(Context c) { start(c, ACTION_INSTALL, ENGINE_PMMP); }
    public static void startServer(Context c) { start(c, ACTION_START, ENGINE_PMMP); }
    public static void stopServer(Context c) { start(c, ACTION_STOP, ENGINE_PMMP); }
    public static void paperInstall(Context c) { start(c, ACTION_INSTALL, ENGINE_PAPER); }
    public static void paperStart(Context c) { start(c, ACTION_START, ENGINE_PAPER); }
    public static void paperStop(Context c) { start(c, ACTION_STOP, ENGINE_PAPER); }

    private static void start(Context c, String action, String engine) {
        try {
            Intent i = new Intent(c, ServerHostService.class);
            i.setAction(action);
            i.putExtra(EXTRA_ENGINE, engine);
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                c.startForegroundService(i);
            } else {
                c.startService(i);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIF_ID, buildNotification());
        running = true;
        String action = intent == null ? "" : intent.getAction();
        String engine = intent == null ? ENGINE_PMMP : intent.getStringExtra(EXTRA_ENGINE);
        if (engine == null) engine = ENGINE_PMMP;

        EmbeddedServer pmmp = EmbeddedServer.init(this);
        if (ENGINE_PAPER.equals(engine)) {
            final PaperServer paper = PaperServer.init(this);
            if (ACTION_INSTALL.equals(action)) {
                paper.installAsync();
            } else if (ACTION_START.equals(action)) {
                if (pmmp.isRunning()) {
                    pmmp.stopAsync();
                    try { Thread.sleep(1800); } catch (InterruptedException ignored) {}
                }
                paper.startAsync();
            } else if (ACTION_STOP.equals(action)) {
                paper.stopAsync();
            }
        } else {
            if (ACTION_INSTALL.equals(action)) {
                pmmp.installAsync();
            } else if (ACTION_START.equals(action)) {
                pmmp.startAsync();
            } else if (ACTION_STOP.equals(action)) {
                pmmp.stopAsync();
            }
        }
        return START_STICKY;
    }

    private Notification buildNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "سرور Atta", NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);

        Intent open = new Intent(this, ServerCenterActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        int iconId = getResources().getIdentifier("ic_stat", "drawable", getPackageName());
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(iconId)
                .setContentTitle("سرور Atta در حال اجراست")
                .setContentText("سرور روی همین گوشی روشن است — از بازی وارد 127.0.0.1 شو")
                .setContentIntent(pi)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    @Override
    public void onDestroy() {
        running = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
