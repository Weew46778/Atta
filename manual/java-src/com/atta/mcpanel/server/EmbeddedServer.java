package com.atta.mcpanel.server;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * سرور جاسازی‌شدهٔ Atta — PocketMine-MP داخل خود اپ، بدون Termux و بدون هیچ برنامهٔ دیگر.
 *
 * - PHP از پیش ساخته‌شدهٔ مخصوص اندروید (arm64) و PocketMine-MP.phar از گیتهاب دانلود می‌شوند
 *   (اولین اجرا، ~۳۵ مگابایت).
 * - پلاگین Atta از داخل خود APK (assets) کپی می‌شود.
 * - سرور به‌صورت یک فرایند فرزند اجرا می‌شود؛ کنسول آن از طریق stdin/stdout همان فرایند
 *   کنترل می‌شود (بدون نیاز به RCON).
 */
public class EmbeddedServer {

    public interface Listener {
        void onLog(String line);
        void onInstall(int percent, String phase);
        void onState(boolean running);
        void onReady(boolean ready);
    }

    // PHP تازهٔ ۲۰۲۶ (php-8.3 برای PocketMine-MP — شامل ext-encoding 1.0 و سازگار با PMMP 5.43/5.44)
    private static final String PHP_URL =
            "https://github.com/Dr1xyDev/AndroidPHP/releases/latest/download/php";
    private static final String INI_URL =
            "https://github.com/Dr1xyDev/AndroidPHP/releases/latest/download/php.ini";
    // برچسب نسخهٔ PHP؛ اگر این رشته با php.tag داخل پوشه فرق کند، php قدیمی دوباره دانلود می‌شود
    private static final String PHP_TAG = "pm5-php8.3-2026-06-30";
    // PocketMine-MP 5.43.2  ← بازی بدراک ۱.۲۶.۲۰ (و نسخه‌های هم‌پروتکل مثل ۱.۲۶.۲۵)
    private static final String PHAR_2620_URL =
            "https://github.com/pmmp/PocketMine-MP/releases/download/5.43.2/PocketMine-MP.phar";
    // PocketMine-MP 5.44.3  ← بازی بدراک ۱.۲۶.۳۰
    private static final String PHAR_2630_URL =
            "https://github.com/pmmp/PocketMine-MP/releases/download/5.44.3/PocketMine-MP.phar";
    // آخرین ریلیز رسمی (وقتی PMMP برای بدراک تازه مثل ۱.۲۶.۴۵ منتشر شد، همین خودکار می‌گیردش)
    private static final String PHAR_AUTO_URL =
            "https://github.com/pmmp/PocketMine-MP/releases/latest/download/PocketMine-MP.phar";

    public static final String MODE_2620 = "v2620";
    public static final String MODE_2630 = "v2630";
    public static final String MODE_AUTO = "vauto";

    private static EmbeddedServer inst;

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<Listener>();
    private final File dir;

    private Process proc;
    private BufferedReader outR;
    private OutputStreamWriter inW;
    private Thread reader;
    private volatile boolean running = false;
    private volatile boolean busy = false;
    private volatile boolean wizardAnswered = false;
    private volatile boolean licAnswered = false;
    private volatile boolean opSent = false;

    // ============ حالت نسخهٔ سرور (باید با نسخهٔ بازیِ گوشی هم‌پروتکل باشد) ============
    private String mode() {
        return ctx.getSharedPreferences("atta_engine", 0).getString("pmmp_mode", MODE_2620);
    }

    public String currentMode() { return mode(); }

    public String modeLabel() {
        String m = mode();
        if (MODE_AUTO.equals(m)) return "آخرین نسخهٔ رسمی PocketMine-MP (خودکار)";
        if (MODE_2630.equals(m)) return "PocketMine-MP 5.44.3 (برای بدراک ۱.۲۶.۳۰)";
        return "PocketMine-MP 5.43.2 (برای بدراک ۱.۲۶.۲۰/۱.۲۶.۲۵)";
    }

    /** عوض‌کردن حالت؛ فایل سرور قبلی حذف می‌شود تا با «نصب کامل خودکار» موتور تازه دانلود شود */
    public void setMode(String m) {
        if (running) { log("⛔ اول سرور را متوقف کن، بعد حالت را عوض کن."); return; }
        ctx.getSharedPreferences("atta_engine", 0).edit().putString("pmmp_mode", m).apply();
        File old = new File(dir, "PocketMine-MP.phar");
        if (old.exists()) old.delete();
        log("حالت انتخاب شد: " + modeLabel());
        log("حالا دکمهٔ «⬇ نصب کامل خودکار» را بزن تا PocketMine-MPِ هماهنگ دانلود شود.");
    }

    private EmbeddedServer(Context c) {
        ctx = c;
        dir = new File(c.getFilesDir(), "atta-server");
    }

    public static synchronized EmbeddedServer init(Context c) {
        if (inst == null) inst = new EmbeddedServer(c.getApplicationContext());
        return inst;
    }

    public static EmbeddedServer get() { return inst; }

    /** دانلود مستقیم (برای پلاگین‌ها) — بدون نیاز به نمونه */
    public static boolean downloadStatic(String url, File target) {
        HttpURLConnection c = null;
        try {
            target.getParentFile().mkdirs();
            File part = new File(target.getAbsolutePath() + ".part");
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(15000);
            c.setReadTimeout(60000);
            c.setRequestProperty("User-Agent", "Atta/1.7");
            int code = c.getResponseCode();
            if (code != 200) return false;
            InputStream is = c.getInputStream();
            FileOutputStream fo = new FileOutputStream(part);
            byte[] buf = new byte[32768];
            int n;
            long total = 0;
            while ((n = is.read(buf)) > 0) {
                fo.write(buf, 0, n);
                total += n;
            }
            fo.close();
            is.close();
            // php.ini فقط ~۳۰۰ بایت است؛ آستانه را خیلی پایین بگذار (فقط ردِ فایلِ خالی/ناقص)
            if (total < 32) { part.delete(); return false; }
            if (target.exists()) target.delete();
            return part.renameTo(target);
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    public File serverDir() { return dir; }
    public boolean isRunning() { return running; }

    public boolean installed() {
        return new File(dir, "bin/php").exists()
                && new File(dir, "PocketMine-MP.phar").exists();
    }

    public void addListener(Listener l) {
        if (l != null && !listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void log(final String line) {
        main.post(new Runnable() {
            @Override public void run() {
                for (Listener l : new ArrayList<Listener>(listeners)) {
                    try { l.onLog(line); } catch (Exception ignored) {}
                }
            }
        });
    }

    private void state(final boolean r) {
        running = r;
        main.post(new Runnable() {
            @Override public void run() {
                for (Listener l : new ArrayList<Listener>(listeners)) {
                    try { l.onState(r); } catch (Exception ignored) {}
                }
            }
        });
    }

    private void installProgress(final int p, final String ph) {
        main.post(new Runnable() {
            @Override public void run() {
                for (Listener l : new ArrayList<Listener>(listeners)) {
                    try { l.onInstall(p, ph); } catch (Exception ignored) {}
                }
            }
        });
    }

    // =====================================================================
    // نصب (دانلود + استخراج) — در پس‌زمینه
    // =====================================================================

    public void installAsync() {
        if (busy) { log("⚠ نصب در حال اجراست…"); return; }
        busy = true;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    installProgress(2, "آماده‌سازی پوشه‌ها");
                    File bin = new File(dir, "bin");
                    File plugins = new File(dir, "plugins/AttaPlugin");
                    bin.mkdirs();
                    plugins.mkdirs();

                    installProgress(6, "کپی پلاگین Atta از داخل اپ");
                    copyAsset("pmmp/AttaPlugin/plugin.yml", new File(plugins, "plugin.yml"));
                    copyAsset("pmmp/AttaPlugin/config.yml", new File(plugins, "config.yml"));
                    copyAsset("pmmp/AttaPlugin/src/Atta/AttaPlugin.php",
                            new File(plugins, "src/Atta/AttaPlugin.php"));
                    log("✓ پلاگین Atta آماده شد");

                    installProgress(14, "بررسی PHP (نسخهٔ ۲۰۲۶، php-8.3)");
                    File php = new File(bin, "php");
                    File tag = new File(bin, "php.tag");
                    String currentTag = readTag(tag);
                    boolean phpOk = php.exists() && php.length() > 20000000
                            && PHP_TAG.equals(currentTag);
                    if (!phpOk) {
                        log("⟳ دانلود PHP (php-8.3 مخصوص اندروید، حدود ۳۰ مگابایت)…");
                        if (!download(PHP_URL, php)) throw new IOException("php");
                        try {
                            java.io.FileOutputStream ft = new java.io.FileOutputStream(tag);
                            ft.write(PHP_TAG.getBytes("UTF-8"));
                            ft.close();
                        } catch (Exception ignored) {}
                        log("✓ PHP دانلود شد");
                    } else {
                        log("✓ PHP از قبل هست (نسخهٔ ۲۰۲۶)");
                    }
                    php.setExecutable(true, false);
                    php.setReadable(true, false);

                    File ini = new File(bin, "php.ini");
                    if (!ini.exists() || ini.length() == 0) {
                        installProgress(40, "دانلود پیکربندی PHP");
                        if (!download(INI_URL, ini)) throw new IOException("php.ini");
                    }

                    installProgress(55, "دانلود " + modeLabel() + " (~۴ مگابایت)");
                    log("⟳ دانلود PocketMine-MP.phar…");
                    String pharUrl = pharUrlFor(mode());
                    if (!download(pharUrl, new File(dir, "PocketMine-MP.phar")))
                        throw new IOException("phar");
                    log("✓ PocketMine-MP دانلود شد (" + modeLabel() + ")");

                    installProgress(94, "پایان نصب");
                    Thread.sleep(200);
                    installProgress(100, "نصب کامل شد");
                    busy = false;
                    log("✅ همه‌چیز آماده است — دکمهٔ «استارت» را بزن.");
                } catch (Exception e) {
                    busy = false;
                    log("❌ نصب ناتمام: " + e.getMessage());
                    installProgress(0, "نصب ناموفق");
                }
            }
        }, "atta-install").start();
    }

    private String pharUrlFor(String m) {
        if (MODE_AUTO.equals(m)) return PHAR_AUTO_URL;
        if (MODE_2630.equals(m)) return PHAR_2630_URL;
        return PHAR_2620_URL;
    }

    /** تنظیمات سرور: ورود بدون Xbox (برای اینکه بازی موقع ورود از اینترنت ایکس‌باکس بیرون نپرد) */
    private void ensureServerProps() {
        File f = new File(dir, "server.properties");
        try {
            java.util.Map<String, String> kv = new java.util.LinkedHashMap<String, String>();
            if (f.exists()) {
                BufferedReader r = new BufferedReader(new InputStreamReader(
                        new java.io.FileInputStream(f), "UTF-8"));
                String l;
                while ((l = r.readLine()) != null) {
                    int i = l.indexOf('=');
                    if (i > 0) {
                        String k = l.substring(0, i).trim();
                        if (!k.isEmpty()) kv.put(k, l.substring(i + 1).trim());
                    }
                }
                r.close();
            }
            kv.put("language", "eng");
            kv.put("xbox-auth", "false");
            kv.put("motd", "Atta Server");
            kv.put("server-port", "19132");
            StringBuilder sb = new StringBuilder();
            for (java.util.Map.Entry<String, String> e : kv.entrySet()) {
                sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
            }
            FileOutputStream fo = new FileOutputStream(f);
            fo.write(sb.toString().getBytes("UTF-8"));
            fo.close();
            log("⚙ xbox-auth=false شد؛ برای ورود از گوشی خودت دیگر نیاز به اتصال ایکس‌باکس نیست");
        } catch (Exception e) {
            log("⚠ تنظیم server.properties نشد: " + e.getMessage());
        }
    }

    private String readTag(File f) {
        try {
            java.io.FileInputStream fi = new java.io.FileInputStream(f);
            BufferedReader r = new BufferedReader(new InputStreamReader(fi, "UTF-8"));
            String s = r.readLine();
            r.close();
            return s == null ? "" : s;
        } catch (Exception e) {
            return "";
        }
    }

    private void copyAsset(String path, File target) throws IOException {
        // پوشهٔ والد را (در صورت نیاز به‌صورت تو‌در‌تو) بساز تا ENOENT رخ ندهد
        File parent = target.getParentFile();
        if (parent != null) parent.mkdirs();
        InputStream is = ctx.getAssets().open(path);
        FileOutputStream fo = new FileOutputStream(target);
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) > 0) fo.write(buf, 0, n);
        fo.close();
        is.close();
    }

    private boolean download(String url, File target) {
        HttpURLConnection c = null;
        try {
            target.getParentFile().mkdirs();
            File part = new File(target.getAbsolutePath() + ".part");
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(15000);
            c.setReadTimeout(60000);
            c.setRequestProperty("User-Agent", "Atta/1.7");
            int code = c.getResponseCode();
            if (code != 200) {
                log("⚠ HTTP " + code + " برای " + url);
                return false;
            }
            InputStream is = c.getInputStream();
            FileOutputStream fo = new FileOutputStream(part);
            byte[] buf = new byte[32768];
            int n;
            long total = 0;
            while ((n = is.read(buf)) > 0) {
                fo.write(buf, 0, n);
                total += n;
            }
            fo.close();
            is.close();
            // php.ini فقط ~۳۰۰ بایت است؛ آستانه را خیلی پایین بگذار (فقط ردِ فایلِ خالی/ناقص)
            if (total < 32) { part.delete(); return false; }
            if (target.exists()) target.delete();
            return part.renameTo(target);
        } catch (Exception e) {
            log("⚠ دانلود نشد: " + e.getMessage());
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    // =====================================================================
    // اجرا / توقف / کنسول
    // =====================================================================

    public void startAsync() {
        if (busy || running) return;
        busy = true;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    if (!installed()) {
                        log("ابتدا نصب کن (دکمهٔ «نصب خودکار»).");
                        busy = false;
                        return;
                    }
                    File php = new File(dir, "bin/php");
                    File ini = new File(dir, "bin/php.ini");
                    if (!ini.exists() || ini.length() == 0) {
                        log("⚠ php.ini پیدا نشد — دکمهٔ «نصب کامل خودکار» را دوباره بزن.");
                        busy = false;
                        return;
                    }
                    ensureServerProps();
                    // اطمینان از اجرایی‌بودن برای همه
                    try {
                        php.setExecutable(true, false);
                        php.setReadable(true, false);
                    } catch (Exception ignored) {}

                    Process p = null;
                    Exception firstErr = null;
                    // ۱) اجرای مستقیم
                    try {
                        ProcessBuilder pb = new ProcessBuilder(
                                php.getAbsolutePath(),
                                "-c", ini.getAbsolutePath(),
                                "PocketMine-MP.phar",
                                "--no-wizard");
                        pb.directory(dir);
                        pb.redirectErrorStream(true);
                        pb.environment().put("PHPRC", ini.getAbsolutePath());
                        log("▶ اجرای PocketMine-MP… (" + modeLabel() + ")");
                        p = pb.start();
                    } catch (Exception e1) {
                        firstErr = e1;
                        log("⚠ اجرای مستقیم نشد (" + e1.getMessage() + ")");
                        // ۲) روش جایگزین: اجرا از طریق linker سیستم (ترفند استاندارد)
                        try {
                            File linker = new File("/system/bin/linker64");
                            if (!linker.exists()) linker = new File("/system/bin/linker");
                            if (linker.exists()) {
                                log("⟳ تلاش با linker سیستم…");
                                ProcessBuilder pb2 = new ProcessBuilder(
                                        linker.getAbsolutePath(),
                                        php.getAbsolutePath(),
                                        "-c", ini.getAbsolutePath(),
                                        "PocketMine-MP.phar",
                                        "--no-wizard");
                                pb2.directory(dir);
                                pb2.redirectErrorStream(true);
                                pb2.environment().put("PHPRC", ini.getAbsolutePath());
                                p = pb2.start();
                            } else {
                                throw new Exception("linker پیدا نشد");
                            }
                        } catch (Exception e2) {
                            // ۳) اگر اندروید ۱۰+ باشد و باز هم EACCES بدهد: مشکل برچسب SELinux
                            log("❌ اجرا ناموفق: " + e2.getMessage());
                            int sdk = android.os.Build.VERSION.SDK_INT;
                            boolean eacces = firstErr.getMessage() != null
                                    && firstErr.getMessage().contains("13");
                            if (eacces) {
                                log("⚠ گوشی اندروید " + sdk
                                        + " اجازهٔ اجرا از پوشهٔ اپ را نمی‌دهد.");
                                log("راه‌حل قطعی:");
                                log("۱) این اپ را کامل پاک کن  ۲) گوشی را یک‌بار خاموش/روشن کن"
                                        + "  ۳) دوباره نصب کن (نسخهٔ ۱.۷.۳+)");
                                log("(این محدودیت امنیتی اندروید است؛ بعد از پاک‌کردن کامل و ریستارت،"
                                        + " برچسب امنیتی درست می‌شود و سرور اجرا می‌شود)");
                            }
                            busy = false;
                            running = false;
                            return;
                        }
                    }
                    proc = p;
                    outR = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                    inW = new OutputStreamWriter(proc.getOutputStream());
                    wizardAnswered = false;
                    licAnswered = false;
                    opSent = false;
                    running = true;
                    busy = false;
                    state(true);
                    reader = new Thread(new Runnable() {
                        @Override public void run() { readLoop(); }
                    }, "atta-server-read");
                    reader.start();
                    // نگهبانی: اگر فرایند مُرد
                    new Thread(new Runnable() {
                        @Override public void run() {
                            try {
                                int code = proc.waitFor();
                                log("⛔ سرور متوقف شد (کد " + code + ")");
                            } catch (InterruptedException ignored) {}
                            running = false;
                            state(false);
                            proc = null;
                        }
                    }, "atta-server-wait").start();
                } catch (Exception e) {
                    log("❌ اجرا ناموفق: " + e.getMessage());
                    busy = false;
                    running = false;
                }
            }
        }, "atta-start").start();
    }

    private void readLoop() {
        try {
            String line;
            while (proc != null && (line = outR.readLine()) != null) {
                log(line);
                // ویزارد اولین اجرای PocketMine-MP: خودکار زبان را انتخاب کن (کاربر هیچ تایپی نمی‌کند)
                if (!wizardAnswered && line.contains("select a language")) {
                    wizardAnswered = true;
                    sendLine("eng");
                }
                // پذیرش خودکار مجوز LGPL در ویزارد — پرامپت فقط «y» را می‌پذیرد
                if (!licAnswered && (line.contains("accept the License")
                        || line.contains("accept the license"))) {
                    licAnswered = true;
                    sendLine("y");
                }
                // به‌محض آماده‌شدن کنسول (Done)، gaser را خودکار اپراتور کن
                if (!opSent && line.contains("For help, type")) {
                    opSent = true;
                    sendLine("op gaser");
                    log("✓ فرمان «op gaser» ارسال شد — تو اپراتور سرور هستی");
                }
            }
        } catch (IOException ignored) {}
    }

    private synchronized void sendLine(String cmd) {
        try {
            if (inW != null) {
                inW.write(cmd + "\n");
                inW.flush();
            }
        } catch (IOException ignored) {}
    }

    public void stopAsync() {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    if (proc != null && running) {
                        log("⏹ ارسال stop به سرور…");
                        try {
                            if (inW != null) {
                                inW.write("stop\n");
                                inW.flush();
                            }
                        } catch (IOException ignored) {}
                        Thread.sleep(6000);
                        if (proc.isAlive()) {
                            log("…اجبار به بستن");
                            proc.destroy();
                        }
                    }
                } catch (Exception ignored) {}
                running = false;
                proc = null;
                state(false);
            }
        }, "atta-stop").start();
    }

    public void restartAsync() {
        new Thread(new Runnable() {
            @Override public void run() {
                stopAsync();
                try { Thread.sleep(8000); } catch (InterruptedException ignored) {}
                if (!running) startAsync();
            }
        }, "atta-restart").start();
    }

    /** ارسال یک دستور به کنسول سرور (stdin) */
    public void console(String cmd) {
        if (!running || inW == null) {
            log("سرور روشن نیست؛ دستور اجرا نشد: " + cmd);
            return;
        }
        sendLine(cmd);
        log("> " + cmd);
    }
}
