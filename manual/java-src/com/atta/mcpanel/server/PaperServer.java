package com.atta.mcpanel.server;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * موتور دوم: Paper (جاوا) + Geyser + ViaVersion — تا کلاینت‌های بدراکِ تازه
 * (مثل ۱.۲۶.۴۵) و کلاینت‌های جاوا با نسخه‌های مختلف بتوانند وارد شوند.
 *
 * همه‌چیز در اولین اجرا دانلود می‌شود (روی خود گوشی):
 *   JRE جاوا برای اندروید arm64 از گیت‌هاب (nerion-android-jdk-build)  ← فایل .tar.xz
 *   Paper از api.papermc.io   ← آخرین نسخهٔ پایدار
 *   Geyser-Spigot از downloads.geysermc.org
 *   ViaVersion از گیت‌هاب (فقط برای کلاینت‌های جاوا با نسخهٔ متفاوت)
 *
 * گوشی باید حداقل ۶ گیگ رم/فضای خالی ~۷۰۰MB داشته باشد؛ برای بار اول حدود ۲۰۰-۳۰۰MB دانلود.
 */
public class PaperServer {

    public interface Listener {
        void onLog(String line);
        void onInstall(int percent, String phase);
        void onState(boolean running);
        void onReady(boolean ready);
    }

    // ---- JRE 25 برای اندروید arm64 (گیت‌هاب؛ قابل‌اطمینان) ----
    private static final String JRE_URL =
            "https://github.com/feeldev12/nerion-android-jdk-build/releases/download/"
                    + "jre25-android/jre25-android-aarch64.tar.xz";
    // اگر گوشی ۳۲بیتی بود (قدیمی): jre25-android-aarch32.tar.xz — ولی PocketMine هم arm64 لازم دارد

    // ---- نقطه‌های دانلود زمان‌اجرا (روی خود گوشی از اینترنت رسمی گرفته می‌شود) ----
    private static final String PAPER_API = "https://api.papermc.io/v2/projects/paper";
    private static final String GEYSER_API =
            "https://downloads.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/spigot";
    private static final String VIA_GITHUB_API = "https://api.github.com/repos/ViaVersion/ViaVersion/releases/latest";

    private static final int XMX_MB = 1536; // حافظهٔ جاوا (اگر گوشی ضعیف است و کرش کرد کمترش کنید)

    private static PaperServer inst;

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<Listener>();
    private final File dir;          // atta-paper
    private final File jreDir;
    private final File serverDir;

    private Process proc;
    private BufferedReader outR;
    private OutputStreamWriter inW;
    private Thread reader;
    private volatile boolean running = false;
    private volatile boolean busy = false;
    private volatile boolean javaPathLogged = false;
    private volatile boolean opSent = false;
    private volatile boolean geyserPatched = false;

    private PaperServer(Context c) {
        ctx = c.getApplicationContext();
        dir = new File(c.getFilesDir(), "atta-paper");
        jreDir = new File(dir, "jre");
        serverDir = new File(dir, "server");
    }

    public static synchronized PaperServer init(Context c) {
        if (inst == null) inst = new PaperServer(c.getApplicationContext());
        return inst;
    }

    public static PaperServer get() { return inst; }

    public File serverDir() { return serverDir; }
    public boolean isRunning() { return running; }
    public boolean isBusy() { return busy; }

    public boolean installed() {
        return findJava(jreDir) != null
                && new File(serverDir, "paper.jar").exists();
    }

    public void addListener(Listener l) {
        if (l != null && !listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(Listener l) { listeners.remove(l); }

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
    // نصب
    // =====================================================================

    public void installAsync() {
        if (busy) { log("⚠ نصب در حال اجراست…"); return; }
        busy = true;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    installProgress(1, "آماده‌سازی پوشه‌ها");
                    jreDir.mkdirs();
                    serverDir.mkdirs();
                    new File(serverDir, "plugins").mkdirs();

                    // ۱) JRE جاوا (arm64) — یک بار، کش می‌شود
                    File jreXz = new File(dir, "jre.tar.xz");
                    File javaBin = findJava(jreDir);
                    if (javaBin == null) {
                        if (!jreXz.exists() || jreXz.length() < 10_000_000) {
                            installProgress(8, "دانلود Java Runtime برای اندروید (حدود ۱۵۰MB، فقط بار اول)");
                            log("⟳ دانلود JRE از گیت‌هاب… (این بزرگ‌ترین دانلود است)");
                            if (!download(JRE_URL, jreXz)) throw new IOException("دانلود JRE ناموفق (jre.tar.xz)");
                        }
                        installProgress(35, "باز کردن JRE (چند دقیقه…)");
                        log("⟳ استخراج JRE…");
                        FileInputStream fi = new FileInputStream(jreXz);
                        int nFiles = XzTar.extract(fi, jreDir);
                        fi.close();
                        log("✓ JRE استخراج شد (" + nFiles + " فایل)");
                        javaBin = findJava(jreDir);
                        if (javaBin == null) throw new IOException("java در بستهٔ JRE پیدا نشد");
                        log("✓ java: " + javaBin.getAbsolutePath());
                    } else {
                        log("✓ JRE از قبل هست");
                    }

                    // ۲) موافقت‌نامهٔ EULA پاپر
                    File eula = new File(serverDir, "eula.txt");
                    if (!eula.exists()) {
                        FileOutputStream fe = new FileOutputStream(eula);
                        fe.write("eula=true\n".getBytes("UTF-8"));
                        fe.close();
                    }

                    // ۳) Paper
                    File paperJar = new File(serverDir, "paper.jar");
                    if (!paperJar.exists() || paperJar.length() < 10_000_000) {
                        installProgress(45, "پیدا کردن آخرین نسخهٔ Paper");
                        log("⟳ پرس‌وجو از api.papermc.io…");
                        String ver = paperLatestVersion();
                        log("آخرین نسخهٔ پایدار Paper: " + ver);
                        String build = paperLatestBuild(ver);
                        String name = "paper-" + ver + "-" + build + ".jar";
                        String url = PAPER_API + "/versions/" + ver + "/builds/" + build + "/downloads/" + name;
                        log("⟳ دانلود Paper (" + ver + ")…");
                        if (!download(url, paperJar)) throw new IOException("دانلود Paper ناموفق");
                        log("✓ Paper دانلود شد");
                    } else {
                        log("✓ Paper از قبل هست");
                    }

                    // ۴) Geyser (پل بدراک ← جاوا)
                    File geyser = new File(serverDir, "plugins/Geyser.jar");
                    if (!geyser.exists() || geyser.length() < 1_000_000) {
                        installProgress(70, "دانلود Geyser (پل بدراک)");
                        log("⟳ دانلود Geyser-Spigot از downloads.geysermc.org…");
                        if (!download(GEYSER_API, geyser)) throw new IOException("دانلود Geyser ناموفق");
                        log("✓ Geyser دانلود شد");
                    } else {
                        log("✓ Geyser از قبل هست");
                    }

                    // ۵) ViaVersion (نسخه‌های مختلف کلاینتِ جاوا) — اختیاری
                    File via = new File(serverDir, "plugins/ViaVersion.jar");
                    if (!via.exists()) {
                        installProgress(85, "دانلود ViaVersion (اختیاری)");
                        log("⟳ دانلود ViaVersion از گیت‌هاب…");
                        String vu = viaVersionUrl();
                        if (vu == null || !download(vu, via)) {
                            log("⚠ ViaVersion دانلود نشد (اشکالی ندارد؛ فقط برای کلاینت‌های جاوا است)");
                        } else {
                            log("✓ ViaVersion دانلود شد");
                        }
                    }

                    installProgress(97, "پایان");
                    busy = false;
                    installProgress(100, "نصب کامل شد");
                    log("✅ موتور Paper آماده است — دکمهٔ «▶ استارت Paper» را بزن.");
                    log("ℹ اولین استارت چند دقیقه طول می‌کشد (تولید دنیا).");
                } catch (Exception e) {
                    busy = false;
                    log("❌ نصب Paper ناتمام: " + e.getMessage());
                    installProgress(0, "نصب ناموفق");
                }
            }
        }, "paper-install").start();
    }

    private static String httpGet(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setRequestProperty("User-Agent", "Atta/1.7");
        c.setRequestProperty("Accept", "application/json");
        int code = c.getResponseCode();
        if (code != 200) throw new IOException("HTTP " + code);
        BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String l;
        while ((l = r.readLine()) != null) sb.append(l).append('\n');
        r.close();
        c.disconnect();
        return sb.toString();
    }

    private String paperLatestVersion() throws IOException {
        String j = httpGet(PAPER_API);
        String key = "\"stable\":\"";
        int i = j.indexOf(key);
        if (i < 0) throw new IOException("فرمت پاسخ Paper ناشناخته");
        int a = i + key.length();
        int b = j.indexOf('"', a);
        if (b < 0) throw new IOException("فرمت پاسخ Paper ناشناخته");
        return j.substring(a, b);
    }

    private String paperLatestBuild(String ver) throws IOException {
        String j = httpGet(PAPER_API + "/versions/" + ver + "/builds");
        int i = j.lastIndexOf("\"build\":");
        if (i < 0) throw new IOException("بیلد Paper پیدا نشد");
        int a = i + "\"build\":".length();
        int b = a;
        while (b < j.length() && Character.isDigit(j.charAt(b))) b++;
        return j.substring(a, b);
    }

    private String viaVersionUrl() {
        try {
            String j = httpGet(VIA_GITHUB_API);
            int i = 0;
            while (true) {
                i = j.indexOf("browser_download_url\": \"", i);
                if (i < 0) return null;
                int a = i + "browser_download_url\": \"".length();
                int b = j.indexOf('"', a);
                if (b < 0) return null;
                String u = j.substring(a, b);
                if (u.endsWith(".jar") && !u.contains("sources") && !u.contains("javadoc")) return u;
                i = b;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private boolean download(String url, File target) {
        HttpURLConnection c = null;
        try {
            target.getParentFile().mkdirs();
            File part = new File(target.getAbsolutePath() + ".part");
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(30000);
            c.setReadTimeout(300000);
            c.setRequestProperty("User-Agent", "Atta/1.7");
            int code = c.getResponseCode();
            if (code != 200) {
                log("⚠ HTTP " + code + " برای " + url);
                return false;
            }
            InputStream is = c.getInputStream();
            FileOutputStream fo = new FileOutputStream(part);
            byte[] buf = new byte[65536];
            long total = 0;
            int n;
            while ((n = is.read(buf)) > 0) {
                fo.write(buf, 0, n);
                total += n;
            }
            fo.close();
            is.close();
            if (total < 1000) { part.delete(); return false; }
            if (target.exists()) target.delete();
            return part.renameTo(target);
        } catch (Exception e) {
            log("⚠ دانلود نشد: " + e.getMessage());
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** پیدا کردن bin/java داخل درخت JRE */
    private static File findJava(File root) {
        if (root == null || !root.isDirectory()) return null;
        File bin = new File(root, "bin");
        File j = new File(bin, "java");
        if (j.isFile()) return j;
        File[] kids = root.listFiles();
        if (kids != null) {
            for (File k : kids) {
                if (!k.isDirectory()) continue;
                File f = new File(k, "bin/java");
                if (f.isFile()) return f;
            }
        }
        return null;
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
                    File javaBin = findJava(jreDir);
                    File paperJar = new File(serverDir, "paper.jar");
                    if (javaBin == null || !paperJar.exists()) {
                        log("ابتدا «نصب Paper» را بزن.");
                        busy = false;
                        return;
                    }
                    // اگر PocketMine (موتور قبلی) روشن است، اول خبر بده
                    if (EmbeddedServer.get() != null && EmbeddedServer.get().isRunning()) {
                        log("⚠ موتور PocketMine هنوز روشن است؛ اول آن را در کارت بالا متوقف کن.");
                        busy = false;
                        return;
                    }
                    log("▶ اجرای Paper (جاوا)…  — بار اول چند دقیقه طول می‌کشد");
                    ProcessBuilder pb = new ProcessBuilder(
                            javaBin.getAbsolutePath(),
                            "-Xmx" + XMX_MB + "M", "-Xms256M",
                            "-jar", "paper.jar", "nogui");
                    pb.directory(serverDir);
                    pb.redirectErrorStream(true);
                    // کتابخانه‌های بومی JRE
                    pb.environment().put("LD_LIBRARY_PATH",
                            jreDir.getAbsolutePath() + "/lib:" + jreDir.getAbsolutePath() + "/lib/server");
                    pb.environment().put("JAVA_HOME", jreDir.getAbsolutePath());
                    pb.environment().put("HOME", serverDir.getAbsolutePath());
                    proc = pb.start();
                    outR = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                    inW = new OutputStreamWriter(proc.getOutputStream());
                    opSent = false;
                    geyserPatched = false;
                    running = true;
                    busy = false;
                    state(true);
                    reader = new Thread(new Runnable() {
                        @Override public void run() { readLoop(); }
                    }, "paper-read");
                    reader.start();
                    new Thread(new Runnable() {
                        @Override public void run() {
                            try {
                                int code = proc.waitFor();
                                log("⛔ Paper متوقف شد (کد " + code + ")");
                            } catch (InterruptedException ignored) {}
                            running = false;
                            state(false);
                            proc = null;
                        }
                    }, "paper-wait").start();
                } catch (Exception e) {
                    busy = false;
                    running = false;
                    log("❌ اجرای Paper ناموفق: " + e.getMessage());
                    if (e.getMessage() != null && e.getMessage().contains("13")) {
                        log("اگر خطای Permission/اجرا بود: اپ را کامل پاک کن، گوشی را ریستارت کن و دوباره نصب کن.");
                    }
                }
            }
        }, "paper-start").start();
    }

    private void readLoop() {
        try {
            String line;
            while (proc != null && (line = outR.readLine()) != null) {
                log(line);
                if (!opSent && line.contains("For help, type")) {
                    opSent = true;
                    sendLine("op gaser");
                    log("✓ فرمان «op gaser» ارسال شد — تو اپراتور هستی");
                }
                // تنظیم Geyser: بعد از اولین اجرا، auth-type را offline کن (بدون نیاز به Xbox)
                if (!geyserPatched) {
                    File cfg = new File(serverDir, "plugins/Geyser/config.yml");
                    if (cfg.exists()) {
                        geyserPatched = patchGeyserConfig(cfg);
                        if (geyserPatched) {
                            log("⚙ تنظیم Geyser شد: auth-type=offline (ورود بدون Xbox).");
                            log("🔄 برای اعمال، یک بار «توقف» و دوباره «استارت Paper» بزن.");
                        }
                    }
                }
            }
        } catch (IOException ignored) {}
    }

    /** auth-type را در config.yml جیسیر offline کند */
    private boolean patchGeyserConfig(File cfg) {
        try {
            BufferedReader r = new BufferedReader(new InputStreamReader(
                    new FileInputStream(cfg), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String l;
            boolean changed = false;
            while ((l = r.readLine()) != null) {
                String t = l.trim();
                if (t.startsWith("auth-type:")) {
                    String v = t.substring(t.indexOf(':') + 1).trim();
                    if (v.startsWith("online")) {
                        sb.append("  auth-type: offline\n");
                        changed = true;
                        continue;
                    }
                }
                sb.append(l).append('\n');
            }
            r.close();
            if (changed) {
                FileOutputStream fo = new FileOutputStream(cfg);
                fo.write(sb.toString().getBytes("UTF-8"));
                fo.close();
            }
            return changed;
        } catch (Exception e) {
            return false;
        }
    }

    private synchronized void sendLine(String cmd) {
        try {
            if (inW != null) {
                inW.write(cmd + "\n");
                inW.flush();
            }
        } catch (IOException ignored) {}
    }

    public void console(String cmd) {
        if (!running || inW == null) {
            log("سرور Paper روشن نیست؛ دستور اجرا نشد: " + cmd);
            return;
        }
        sendLine(cmd);
        log("> " + cmd);
    }

    public void stopAsync() {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    if (proc != null && running) {
                        log("⏹ ارسال stop به Paper…");
                        sendLine("stop");
                        Thread.sleep(8000);
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
        }, "paper-stop").start();
    }

    public void restartAsync() {
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    if (running) stopAsync();
                    Thread.sleep(1000);
                    startAsync();
                } catch (Exception ignored) {}
            }
        }, "paper-restart").start();
    }

    /** مسیر فایل پیام حالت برای کارت UI */
    public String infoLine() {
        File javaBin = findJava(jreDir);
        return "JRE: " + (javaBin != null ? "✓ نصب" : "✗ نصب نشده")
                + "  |  Paper: " + (new File(serverDir, "paper.jar").exists() ? "✓" : "✗")
                + "  |  Geyser: " + (new File(serverDir, "plugins/Geyser.jar").exists() ? "✓" : "✗");
    }
}
