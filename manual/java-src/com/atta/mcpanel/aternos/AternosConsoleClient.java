package com.atta.mcpanel.aternos;

import android.os.Handler;
import android.os.Looper;
import android.webkit.CookieManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.security.SecureRandom;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * اتصال مستقیم به کنسول زندهٔ Aternos (وبسوکت hermes) — بدون WebView.
 * همان پروتکلی که خود پنل aternos.org در مرورگر استفاده می‌کند؛
 * کوکی نشست از CookieManager خود اپ خوانده می‌شود (نشستِ ورود کاربر).
 * این کلاینت در سرویس اوورلای هم کار می‌کند — یعنی دستورهای پنل داخل بازی
 * واقعاً روی سرور Aternos اجرا می‌شوند.
 */
public final class AternosConsoleClient {

    public interface Listener {
        void onOpen();
        void onLine(String line);
        void onClosed(String reason);
    }

    private static final String HOST = "aternos.org";
    private static final String PATH = "/hermes/";
    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36";

    private SSLSocket sock;
    private volatile boolean open = false;
    private volatile boolean closedByUser = false;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final SecureRandom rnd = new SecureRandom();
    private Listener listener;

    private AternosConsoleClient() {}

    /** آیا نشست Aternos (کوکی ورود) موجود است؟ */
    public static boolean hasSession() {
        return sessionCookies() != null;
    }

    /** کوکی‌های مورد نیاز اتصال یا null */
    public static String sessionCookies() {
        try {
            String c = CookieManager.getInstance().getCookie("https://aternos.org");
            if (c == null) return null;
            boolean hasSession = false;
            for (String p : c.split(";")) {
                String t = p.trim();
                if (t.startsWith("ATERNOS_SESSION=") && t.length() > "ATERNOS_SESSION=".length() + 8) {
                    hasSession = true;
                }
            }
            return hasSession ? c : null;
        } catch (Throwable t) {
            return null;
        }
    }

    public boolean isOpen() { return open; }

    /** برقراری اتصال (ناهمگام) */
    public static AternosConsoleClient connect(final Listener l) {
        final AternosConsoleClient c = new AternosConsoleClient();
        c.listener = l;
        Thread t = new Thread(new Runnable() { @Override public void run() { c.run(); }});
        t.setDaemon(true);
        t.start();
        return c;
    }

    public void close() {
        closedByUser = true;
        open = false;
        try { if (sock != null) sock.close(); } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------------

    private void run() {
        OutputStream out = null;
        try {
            String cookies = sessionCookies();
            if (cookies == null) {
                fail("نشست Aternos موجود نیست — اول از صفحهٔ «سرور Aternos» وارد شو");
                return;
            }
            SSLSocketFactory f = (SSLSocketFactory) SSLSocketFactory.getDefault();
            sock = (SSLSocket) f.createSocket(HOST, 443);
            sock.setSoTimeout(0); // خواندن دائم
            sock.startHandshake();
            out = sock.getOutputStream();
            InputStream in = sock.getInputStream();

            String key = b64Key();
            String extra = "";
            // ATERNOS_SERVER اگر هست همراه شود
            for (String p : cookies.split(";")) {
                String t = p.trim();
                if (t.startsWith("ATERNOS_SERVER=")) {
                    extra = "; " + t;
                    break;
                }
            }
            String sessionPart = cookies.split(";")[0].trim();
            String hs = "GET " + PATH + " HTTP/1.1\r\n"
                    + "Host: " + HOST + "\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: " + key + "\r\n"
                    + "Sec-WebSocket-Version: 13\r\n"
                    + "Origin: https://" + HOST + "\r\n"
                    + "User-Agent: " + UA + "\r\n"
                    + "Pragma: no-cache\r\n"
                    + "Cache-Control: no-cache\r\n"
                    + "Cookie: " + sessionPart + extra + "\r\n"
                    + "\r\n";
            out.write(hs.getBytes("UTF-8"));
            out.flush();

            String head = readHttpHeader(in);
            if (!head.contains(" 101 ")) {
                fail("اتصال به کنسول Aternos رد شد (" + head.split("\r\n")[0] + ")");
                return;
            }

            // شروع استریم کنسول
            sendJson(out, "{\"stream\":\"console\",\"type\":\"start\"}");
            open = true;
            ui.post(new Runnable() { @Override public void run() {
                Listener l = listener;
                if (l != null) l.onOpen();
            }});

            // ضربان هر ۴۵ ثانیه
            final OutputStream fout = out;
            final Runnable hb = new Runnable() { @Override public void run() {
                try {
                    if (open) sendText(fout, "{\"type\":\"\\u2764\"}");
                } catch (Throwable ignored) {}
                if (open) ui.postDelayed(this, 45000);
            }};
            ui.postDelayed(hb, 45000);

            // حلقهٔ دریافت
            while (open) {
                int b1 = in.read();
                int b2 = in.read();
                if (b1 < 0 || b2 < 0) break;
                int opcode = b1 & 0x0F;
                boolean masked = (b2 & 0x80) != 0;
                long len = b2 & 0x7F;
                if (len == 126) {
                    len = ((in.read() & 0xFF) << 8) | (in.read() & 0xFF);
                } else if (len == 127) {
                    len = 0;
                    for (int i = 0; i < 8; i++) len = (len << 8) | (in.read() & 0xFF);
                }
                byte[] mask = null;
                if (masked) { mask = new byte[4]; readFully(in, mask); }
                byte[] payload = new byte[(int) len];
                readFully(in, payload);
                if (masked) for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i & 3];

                if (opcode == 0x8) break; // close
                if (opcode == 0x9) { sendFrame(fout, 0xA, payload); continue; } // ping→pong
                if (opcode == 0x1) handleText(new String(payload, "UTF-8"));
            }
        } catch (Throwable t) {
            fail(String.valueOf(t.getMessage() != null ? t.getMessage() : t));
        } finally {
            open = false;
            try { if (sock != null) sock.close(); } catch (Throwable ignored) {}
            final boolean byUser = closedByUser;
            ui.post(new Runnable() { @Override public void run() {
                Listener l = listener;
                if (l != null) l.onClosed(byUser ? "بسته شد" : "قطع شد");
            }});
        }
    }

    private void handleText(String s) {
        try {
            java.util.Map<String, Object> m = MiniJson.object(MiniJson.parse(s));
            if (m == null) return;
            String t = MiniJson.str(m, "type", "");
            if ("line".equals(t)) {
                line(MiniJson.str(m, "data", ""));
            } else if ("status".equals(t)) {
                String msg = MiniJson.str(m, "message", "");
                java.util.Map<String, Object> st = MiniJson.object(MiniJson.parse(msg));
                if (st != null) {
                    String cls = MiniJson.str(st, "class", "");
                    if (cls.length() > 0) line("📊 وضعیت سرور: " + cls);
                }
            }
        } catch (Throwable ignored) {}
    }

    private void line(final String l) {
        if (l == null || l.trim().length() == 0) return;
        ui.post(new Runnable() { @Override public void run() {
            Listener x = listener;
            if (x != null) x.onLine(l.trim());
        }});
    }

    private void fail(final String reason) {
        open = false;
        ui.post(new Runnable() { @Override public void run() {
            Listener l = listener;
            if (l != null) l.onClosed(reason);
        }});
    }

    // ------------------------------------------------------------------
    // ارسال دستور
    // ------------------------------------------------------------------

    /** ارسال دستور به کنسول سرور — فقط وقتی اتصال باز است */
    public boolean send(String command) {
        if (!open || sock == null) return false;
        try {
            String esc = command.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\r", " ").replace("\n", " ");
            OutputStream out = sock.getOutputStream();
            sendJson(out, "{\"stream\":\"console\",\"type\":\"command\",\"data\":\"" + esc + "\"}");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void sendJson(OutputStream out, String json) throws IOException {
        sendText(out, json);
    }

    // ------------------------------------------------------------------
    // فریم‌های RFC 6455 (مشابه EdgeTts)
    // ------------------------------------------------------------------

    private void sendText(OutputStream out, String text) throws IOException {
        sendFrame(out, 0x1, text.getBytes("UTF-8"));
    }

    private void sendFrame(OutputStream out, int opcode, byte[] payload) throws IOException {
        int len = payload.length;
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        head.write(0x80 | opcode);
        byte[] mask = new byte[4];
        rnd.nextBytes(mask);
        if (len < 126) {
            head.write(0x80 | len);
        } else if (len < 65536) {
            head.write(0x80 | 126);
            head.write((len >> 8) & 0xFF);
            head.write(len & 0xFF);
        } else {
            head.write(0x80 | 127);
            for (int i = 7; i >= 0; i--) head.write((int) ((len >> (8 * i)) & 0xFF));
        }
        head.write(mask);
        byte[] mp = new byte[len];
        for (int i = 0; i < len; i++) mp[i] = (byte) (payload[i] ^ mask[i & 3]);
        out.write(head.toByteArray());
        out.write(mp);
        out.flush();
    }

    private String b64Key() {
        byte[] b = new byte[16];
        rnd.nextBytes(b);
        return android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP);
    }

    private static String readHttpHeader(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int prev = -1;
        while (true) {
            int c = in.read();
            if (c < 0) throw new IOException("eof in header");
            buf.write(c);
            if (prev == '\n' && c == '\r') {
                int n = in.read();
                if (n >= 0) buf.write(n);
                break;
            }
            prev = c;
        }
        return new String(buf.toByteArray(), "UTF-8");
    }

    private static void readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) throw new IOException("eof in frame");
            off += n;
        }
    }
}
