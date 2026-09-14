package com.atta.mcpanel.voice;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * سنتز گفتار فارسی با صدای نورال «دلارا» (fa-IR-DilaraNeural) از سرویس Edge —
 * مستقل از موتور گفتار گوشی. پیاده‌سازی مستقیم همان پروتکل edge-tts
 * (وبسوکت speech.platform.bing.com + توکن کلاینت + Sec-MS-GEC).
 */
public final class EdgeTts {

    private static final String HOST = "speech.platform.bing.com";
    private static final String PATH = "/consumer/speech/synthesize/readaloud/edge/v1";
    private static final String TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4";
    private static final String GEC_VERSION = "1-143.0.3650.75";
    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0";

    public static final String VOICE_FA_FEMALE = "fa-IR-DilaraNeural";

    private EdgeTts() {}

    /** ساخت MP3 از متن فارسی — در صورت خطا IOException */
    public static byte[] synth(String text, String voice) throws IOException {
        SSLSocket sock = null;
        try {
            String cid = UUID.randomUUID().toString().replace("-", "");
            String gec = secMsGec();
            String uri = PATH + "?TrustedClientToken=" + TOKEN
                    + "&ConnectionId=" + cid
                    + "&Sec-MS-GEC=" + gec
                    + "&Sec-MS-GEC-Version=" + URLEncoder.encode(GEC_VERSION, "UTF-8");

            SSLSocketFactory f = (SSLSocketFactory) SSLSocketFactory.getDefault();
            sock = (SSLSocket) f.createSocket(HOST, 443);
            sock.setSoTimeout(20000);
            sock.startHandshake();

            OutputStream out = sock.getOutputStream();
            InputStream in = sock.getInputStream();

            // --- دست دادن وبسوکت ---
            String key = b64Key();
            String hs = "GET " + uri + " HTTP/1.1\r\n"
                    + "Host: " + HOST + "\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Sec-WebSocket-Key: " + key + "\r\n"
                    + "Sec-WebSocket-Version: 13\r\n"
                    + "Origin: chrome-extension://jdiccldimpdaibmpdkjnbmckianbfold\r\n"
                    + "User-Agent: " + UA + "\r\n"
                    + "Accept-Encoding: gzip, deflate, br, zstd\r\n"
                    + "Accept-Language: en-US,en;q=0.9\r\n"
                    + "Pragma: no-cache\r\n"
                    + "Cache-Control: no-cache\r\n"
                    + "Cookie: muid=" + muid() + ";\r\n"
                    + "\r\n";
            out.write(hs.getBytes("UTF-8"));
            out.flush();

            String respHead = readHttpHeader(in);
            if (!respHead.contains(" 101 ")) {
                throw new IOException("edge-tts handshake: "
                        + respHead.split("\r\n")[0]);
            }

            String ts = dateStr();
            // --- speech.config ---
            String cfg = "X-Timestamp:" + ts + "\r\n"
                    + "Content-Type:application/json; charset=utf-8\r\n"
                    + "Path:speech.config\r\n\r\n"
                    + "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":{"
                    + "\"sentenceBoundaryEnabled\":\"true\",\"wordBoundaryEnabled\":\"false\"},"
                    + "\"outputFormat\":\"audio-24khz-48kbitrate-mono-mp3\"}}}}";
            sendTextFrame(out, cfg);

            // --- ssml ---
            String ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='en-US'>"
                    + "<voice name='" + voice + "'>"
                    + "<prosody pitch='+0Hz' rate='+0%' volume='+0%'>"
                    + escape(text)
                    + "</prosody></voice></speak>";
            String req = "X-RequestId:" + UUID.randomUUID().toString().replace("-", "") + "\r\n"
                    + "Content-Type:application/ssml+xml\r\n"
                    + "X-Timestamp:" + ts + "Z\r\n"
                    + "Path:ssml\r\n\r\n"
                    + ssml;
            sendTextFrame(out, req);

            // --- دریافت فریم‌ها ---
            ByteArrayOutputStream audio = new ByteArrayOutputStream();
            boolean done = false;
            while (!done) {
                int b1 = in.read();
                int b2 = in.read();
                if (b1 < 0 || b2 < 0) throw new IOException("edge-tts: connection closed");
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
                if (masked) {
                    mask = new byte[4];
                    readFully(in, mask);
                }
                byte[] payload = new byte[(int) len];
                readFully(in, payload);
                if (masked) {
                    for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i & 3];
                }

                if (opcode == 0x8) { // close
                    throw new IOException("edge-tts: server closed");
                } else if (opcode == 0x9) { // ping → pong
                    sendFrame(out, 0xA, payload);
                } else if (opcode == 0x1) { // text
                    String s = new String(payload, "UTF-8");
                    if (s.contains("Path:turn.end")) done = true;
                } else if (opcode == 0x2) { // binary: سرصفحه + صدا
                    int at = indexOf(payload, "Path:audio\r\n");
                    if (at >= 0) {
                        int start = at + "Path:audio\r\n".length();
                        audio.write(payload, start, payload.length - start);
                    }
                }
            }
            byte[] mp3 = audio.toByteArray();
            if (mp3.length < 64) throw new IOException("edge-tts: empty audio");
            return mp3;
        } finally {
            try { if (sock != null) sock.close(); } catch (Throwable ignored) {}
        }
    }

    // ------------------------------------------------------------------

    private static String secMsGec() throws IOException {
        try {
            long ticks = System.currentTimeMillis() / 1000L + 11644473600L;
            ticks -= ticks % 300;
            ticks = ticks * 10000000L;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest((ticks + TOKEN).getBytes("ASCII"));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02X", b));
            return sb.toString();
        } catch (Throwable t) {
            throw new IOException("gec: " + t);
        }
    }

    private static String dateStr() {
        SimpleDateFormat sdf = new SimpleDateFormat(
                "EEE MMM dd yyyy HH:mm:ss 'GMT+0000 (Coordinated Universal Time)'",
                Locale.US);
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }

    /** muid تصادفی — مثل edge-tts (۳۲ نویسهٔ هگز بزرگ) */
    private static String muid() {
        SecureRandom r = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 32; i++) sb.append("0123456789ABCDEF".charAt(r.nextInt(16)));
        return sb.toString();
    }

    private static String b64Key() {
        byte[] b = new byte[16];
        new SecureRandom().nextBytes(b);
        return android.util.Base64.encodeToString(b, android.util.Base64.NO_WRAP);
    }

    private static String readHttpHeader(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int prev = -1;
        while (true) {
            int c = in.read();
            if (c < 0) throw new IOException("edge-tts: eof in header");
            buf.write(c);
            if (prev == '\n' && c == '\r') {
                int n = in.read(); // \n نهایی
                if (n >= 0) buf.write(n);
                break;
            }
            prev = c;
        }
        return new String(buf.toByteArray(), "UTF-8");
    }

    private static void sendTextFrame(OutputStream out, String text) throws IOException {
        sendFrame(out, 0x1, text.getBytes("UTF-8"));
    }

    /** فریم کلاینت → سرور باید mask شود (RFC 6455) */
    private static void sendFrame(OutputStream out, int opcode, byte[] payload) throws IOException {
        int len = payload.length;
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        head.write(0x80 | opcode);
        byte[] mask = new byte[4];
        new SecureRandom().nextBytes(mask);
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
        byte[] maskedPayload = new byte[len];
        for (int i = 0; i < len; i++) maskedPayload[i] = (byte) (payload[i] ^ mask[i & 3]);
        out.write(head.toByteArray());
        out.write(maskedPayload);
        out.flush();
    }

    private static void readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n < 0) throw new IOException("edge-tts: eof in frame");
            off += n;
        }
    }

    private static int indexOf(byte[] data, String pattern) {
        byte[] p = pattern.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        outer:
        for (int i = 0; i <= data.length - p.length; i++) {
            for (int j = 0; j < p.length; j++) {
                if (data[i + j] != p[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
