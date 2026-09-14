package com.atta.mcpanel.voice;

import android.content.Context;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * مدیر گفتار فارسی — صف پخش + کش صوتی + پشتیبان.
 * مسیر ۱: صدای نورال «دلارا» (Edge) — مستقل از موتور گوشی
 * مسیر ۲ (پشتیبان): سرویس ترجمهٔ گوگل (فارسی، بدون کلید)
 */
public final class PersianTts {

    public interface Listener {
        void onSpeakFailure();
        /** نتیجهٔ موتور صدا: "dilara" | "fallback" | "none" */
        void onEngine(String engine, String detail);
    }

    public static final String ENGINE_NEURAL = "dilara";
    public static final String ENGINE_FALLBACK = "fallback";
    public static final String ENGINE_NONE = "none";
    private static volatile String lastEngine = "";
    private static volatile String lastError = "";

    /** آخرین موتوری که صدا ساخت */
    public static String lastEngine() { return lastEngine; }
    public static String lastError() { return lastError; }

    private static final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<String>();
    private static Thread worker;
    private static volatile boolean stopFlag = false;
    private static volatile MediaPlayer player = null;
    private static File cacheDir;
    private static Context appCtx;
    private static final Handler ui = new Handler(Looper.getMainLooper());
    private static volatile Listener listener;
    private static int failCount = 0;
    private static boolean toastShown = false;
    private static volatile Boolean fallbackAllowed = null; // null = خوانده نشده

    /** آیا صدای پشتیبان (کیفیت پایین) مجاز است؟ پیش‌فرض: نه */
    public static boolean fallbackAllowed(Context ctx) {
        if (fallbackAllowed == null) {
            fallbackAllowed = "1".equals(new com.atta.mcpanel.core.AppPrefs(ctx).getString("homan_fallback", "0"));
        }
        return fallbackAllowed;
    }

    public static void setFallbackAllowed(Context ctx, boolean on) {
        fallbackAllowed = on;
        new com.atta.mcpanel.core.AppPrefs(ctx).setString("homan_fallback", on ? "1" : "0");
    }

    private PersianTts() {}

    public static void init(Context ctx) {
        Context ac = ctx.getApplicationContext();
        if (appCtx == null) appCtx = ac;
        if (cacheDir == null) {
            cacheDir = new File(ac.getCacheDir(), "homan");
            //noinspection ResultOfMethodCallIgnored
            cacheDir.mkdirs();
        }
        if (worker == null || !worker.isAlive()) {
            stopFlag = false;
            worker = new Thread(new Runnable() { @Override public void run() { loop(); }});
            worker.setDaemon(true);
            worker.start();
        }
    }

    public static void setListener(Listener l) { listener = l; }

    /** گفتن یک متن (به صف اضافه می‌شود) */
    public static void speak(Context ctx, String text) {
        if (text == null) return;
        text = text.trim();
        if (text.length() == 0) return;
        init(ctx);
        queue.offer(text);
    }

    /** قطع فوری گفتار و خالی‌کردن صف */
    public static void shutUp() {
        stopFlag = true;
        queue.clear();
        try {
            if (player != null) player.stop();
        } catch (Throwable ignored) {}
        stopFlag = false; // کارگر بعدی دوباره کار می‌کند
    }

    private static void loop() {
        while (true) {
            try {
                String text = queue.poll();
                if (text == null) {
                    Thread.sleep(200);
                    continue;
                }
                List<String> chunks = split(text, 300);
                for (String c : chunks) {
                    if (!queue.isEmpty() && queue.size() > 4) break; // ازدحام → رد کن
                    playChunk(c);
                }
            } catch (Throwable ignored) {}
        }
    }

    private static void playChunk(String text) {
        try {
            File f = cached(text);
            if (f == null) f = synthToFile(text);
            if (f == null) {
                failCount++;
                if (failCount >= 2 && !toastShown) {
                    toastShown = true;
                    notifyEngine(ENGINE_NONE, lastError);
                    final Listener l = listener;
                    if (l != null) ui.post(new Runnable() { @Override public void run() {
                        Listener ll = listener;
                        if (ll != null) ll.onSpeakFailure();
                    }});
                }
                return;
            }
            failCount = 0;
            final Object lock = new Object();
            MediaPlayer mp = new MediaPlayer();
            mp.setDataSource(f.getAbsolutePath());
            mp.prepare();
            player = mp;
            synchronized (lock) {
                mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    @Override public void onCompletion(MediaPlayer p) {
                        synchronized (lock) { lock.notify(); }
                    }
                });
                mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                    @Override public boolean onError(MediaPlayer p, int what, int extra) {
                        synchronized (lock) { lock.notify(); }
                        return true;
                    }
                });
                mp.start();
                lock.wait(90000);
            }
            mp.release();
            player = null;
        } catch (Throwable ignored) {}
    }

    private static boolean fallbackAllowedValue() {
        Boolean f = fallbackAllowed;
        return f != null && f;
    }

    private static void notifyEngine(final String engine, final String detail) {
        final Listener l = listener;
        if (l == null) return;
        ui.post(new Runnable() { @Override public void run() {
            Listener ll = listener;
            if (ll != null) ll.onEngine(engine, detail);
        }});
    }

    private static File cached(String text) {
        try {
            File f = new File(cacheDir, hash(text) + ".mp3");
            if (f.exists() && f.length() > 512) return f;
        } catch (Throwable ignored) {}
        return null;
    }

    private static File synthToFile(String text) {
        try {
            byte[] mp3 = null;
            String err = "";
            // دو تلاش برای صدای نورال (سوکت تازه هر بار)
            for (int i = 0; i < 2 && mp3 == null; i++) {
                try {
                    mp3 = EdgeTts.synth(text, EdgeTts.VOICE_FA_FEMALE);
                    lastEngine = ENGINE_NEURAL;
                    lastError = "";
                    failCount = 0;
                } catch (Throwable t1) {
                    err = String.valueOf(t1.getMessage() != null ? t1.getMessage() : t1);
                    lastError = err;
                }
            }
            if (mp3 == null) {
                notifyEngine(ENGINE_NONE, err);
                if (!fallbackAllowedValue()) return null;
                mp3 = googleTts(text);
                if (mp3 != null) lastEngine = ENGINE_FALLBACK;
            }
            if (mp3 == null || mp3.length < 512) return null;
            File f = new File(cacheDir, hash(text) + ".mp3");
            FileOutputStream fo = new FileOutputStream(f);
            fo.write(mp3);
            fo.close();
            return f;
        } catch (Throwable t) {
            return null;
        }
    }

    /** پشتیبان: سرویس ترجمهٔ گوگل (بدون کلید، تکه‌های ۱۹۰ نویسه) */
    private static byte[] googleTts(String text) {
        try {
            ByteArrayOutputStream all = new ByteArrayOutputStream();
            for (String chunk : split(text, 190)) {
                String u = "https://translate.google.com/translate_tts?ie=UTF-8&client=tw-ob&tl=fa&q="
                        + URLEncoder.encode(chunk, "UTF-8");
                HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                c.setRequestProperty("User-Agent",
                        "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36");
                int code = c.getResponseCode();
                if (code != 200) { c.disconnect(); return null; }
                InputStream in = c.getInputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) all.write(buf, 0, n);
                in.close();
                c.disconnect();
                Thread.sleep(250); // مهربانی با سرویس
            }
            return all.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    private static List<String> split(String text, int max) {
        List<String> out = new ArrayList<String>();
        StringBuilder cur = new StringBuilder();
        for (String w : text.split("\\s+")) {
            if (cur.length() + w.length() + 1 > max && cur.length() > 0) {
                out.add(cur.toString().trim());
                cur = new StringBuilder();
            }
            if (cur.length() > 0) cur.append(' ');
            cur.append(w);
        }
        if (cur.length() > 0) out.add(cur.toString().trim());
        return out;
    }

    private static String hash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] h = md.digest((EdgeTts.VOICE_FA_FEMALE + "|" + s).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Throwable t) {
            return String.valueOf(s.hashCode());
        }
    }
}
