package com.atta.mcpanel.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * باز کردن بایگانی‌های .tar.xz روی اندروید — بدون هیچ ابزار خارجی.
 * xz توسط org.tukaani.xz (مالکیت عمومی) و tar با یک خوانندهٔ سادهٔ ustar انجام می‌شود.
 *
 * نکتهٔ مهم: نام‌های بلند GNU (L/K) و PAX (x/g) پشتیبانی می‌شوند، چون بایگانی‌های
 * jlink جاوا معمولاً نام‌های بلند دارند و اگر نادیده گرفته شوند هدر بعدی نامِ خالی
 * می‌گیرد و باز کردنش خطای «Invalid file path» می‌دهد.
 */
public class XzTar {

    /** استخراج کل بایگانی؛ تعداد فایل‌های استخراج‌شده را برمی‌گرداند */
    public static int extract(InputStream xzIn, File targetDir) throws IOException {
        int extracted = 0;
        long entries = 0;
        InputStream in = new org.tukaani.xz.XZInputStream(xzIn);
        byte[] hdr = new byte[512];
        String pendingName = null;
        String pendingLink = null;
        boolean sawAny = false;
        while (true) {
            int read = readFully(in, hdr, 0, 512);
            if (read == -1) break;
            if (read < 512) throw new IOException("tar: سربرگ ناقص");
            if (isZeroBlock(hdr)) {
                // معمولاً دو بلوک صفر پایان است؛ پایان را می‌پذیریم
                break;
            }
            entries++;
            sawAny = true;
            String name = tarString(hdr, 0, 100);
            String prefix = tarString(hdr, 345, 155);
            String fullName = prefix.isEmpty() ? name : prefix + "/" + name;
            String sizeStr = tarString(hdr, 124, 12);
            long size = 0;
            try { size = Long.parseLong(sizeStr.isEmpty() ? "0" : sizeStr, 8); }
            catch (Exception ignored) {}
            char type = (char) hdr[156];
            String link = tarString(hdr, 157, 100);
            if (fullName.endsWith("/")) type = '5';

            switch (type) {
                case 'L': { // نام بلند GNU — نام واقعیِ ورودی بعد
                    byte[] data = new byte[(int) Math.min(size, 8192)];
                    int got = 0;
                    while (got < size && got < data.length) {
                        int r = in.read(data, got, (int) Math.min(size - got, data.length - got));
                        if (r < 0) break;
                        got += r;
                    }
                    pendingName = new String(data, 0, got, "UTF-8").trim();
                    skipN(in, size - got);
                    skipPad(in, size);
                    continue;
                }
                case 'K': { // لینک بلند GNU
                    byte[] data = new byte[(int) Math.min(size, 8192)];
                    int got = 0;
                    while (got < size && got < data.length) {
                        int r = in.read(data, got, (int) Math.min(size - got, data.length - got));
                        if (r < 0) break;
                        got += r;
                    }
                    pendingLink = new String(data, 0, got, "UTF-8").trim();
                    skipN(in, size - got);
                    skipPad(in, size);
                    continue;
                }
                case 'x':      // PAX — متادیتا (path/linkpath بلند)
                case 'g': {
                    String meta = readAsciiN(in, size);
                    skipPad(in, size);
                    String[] records = meta.split("\\n");
                    for (String rec : records) {
                        int sp = rec.indexOf(' ');
                        if (sp < 0) continue;
                        String body = rec.substring(sp + 1);
                        int eq = body.indexOf('=');
                        if (eq < 0) continue;
                        String key = body.substring(0, eq);
                        String val = body.substring(eq + 1);
                        if ("path".equals(key)) pendingName = val;
                        else if ("linkpath".equals(key)) pendingLink = val;
                    }
                    continue;
                }
                default:
                    break;
            }

            // نام نهایی: اگر نام بلند ثبت شده بود از آن استفاده کن (یک‌بار مصرف)
            if (pendingName != null && !pendingName.isEmpty()) {
                fullName = pendingName;
            }
            pendingName = null;

            String clean = sanitize(fullName);
            if (clean.isEmpty()) {
                skipN(in, size);
                skipPad(in, size);
                continue;
            }

            File target = new File(targetDir, clean);
            switch (type) {
                case '5': // دایرکتوری
                    target.mkdirs();
                    break;
                case '2': { // symlink — به‌جای ساخت لینک، از مقصد رونوشت می‌گیریم
                    String linkTarget = (pendingLink != null && !pendingLink.isEmpty())
                            ? pendingLink : link;
                    pendingLink = null;
                    try {
                        if (!linkTarget.isEmpty()) {
                            File dest = linkTarget.startsWith("/")
                                    ? new File(targetDir, sanitize(linkTarget))
                                    : new File(target.getParentFile(), linkTarget);
                            if (dest.isDirectory()) {
                                target.mkdirs();
                            } else if (dest.isFile()) {
                                if (!target.getParentFile().exists()) target.getParentFile().mkdirs();
                                copyFile(dest, target);
                                applyMode(target);
                                extracted++;
                            }
                        }
                    } catch (Exception ignored) {}
                    break;
                }
                case '0':
                case '\0':
                case ' ': { // فایل عادی
                    try {
                        if (!target.getParentFile().exists()) target.getParentFile().mkdirs();
                        OutputStream fo = new FileOutputStream(target);
                        copyN(in, fo, size);
                        fo.close();
                        applyMode(target);
                        extracted++;
                    } catch (Exception e) {
                        // یک ورودی خراب کل نصب را نشکند — رد شو
                        skipN(in, size);
                    }
                    break;
                }
                default:
                    skipN(in, size);
            }
            skipPad(in, size);
        }
        in.close();
        if (entries > 0 && extracted == 0) {
            throw new IOException("هیچ فایلی از بایگانی استخراج نشد (ناشناخته بودن قالب)");
        }
        return extracted;
    }

    /** پاک‌سازی نام مسیر: نسبی‌کردن، حذف «..» و اجزای خالی؛ خروجی خالی یعنی ردّ امن */
    private static String sanitize(String raw) {
        if (raw == null) return "";
        String p = raw;
        while (p.startsWith("/")) p = p.substring(1);
        String[] parts = p.split("/");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty() || ".".equals(part)) continue;
            if ("..".equals(part)) continue; // جلوگیری از خروج از پوشهٔ مقصد
            if (part.indexOf('\u0000') >= 0) continue;
            if (sb.length() > 0) sb.append('/');
            sb.append(part);
        }
        return sb.toString();
    }

    private static void applyMode(File f) {
        try {
            f.setExecutable(true, false);
            f.setReadable(true, false);
        } catch (Exception ignored) {}
    }

    private static String tarString(byte[] b, int off, int len) {
        int end = off + len;
        while (end > off && (b[end - 1] == 0 || b[end - 1] == ' ')) end--;
        if (end <= off) return "";
        return new String(b, off, end - off, java.nio.charset.StandardCharsets.UTF_8).trim();
    }

    private static boolean isZeroBlock(byte[] b) {
        for (int i = 0; i < 512; i++) if (b[i] != 0) return false;
        return true;
    }

    private static int readFully(InputStream in, byte[] b, int off, int len) throws IOException {
        int first = in.read(b, off, len);
        if (first <= 0) return first;
        int got = first;
        while (got < len) {
            int r = in.read(b, off + got, len - got);
            if (r < 0) break;
            got += r;
        }
        return got;
    }

    private static void copyN(InputStream in, OutputStream out, long n) throws IOException {
        byte[] buf = new byte[65536];
        long left = n;
        while (left > 0) {
            int want = (int) Math.min(buf.length, left);
            int got = in.read(buf, 0, want);
            if (got < 0) throw new IOException("tar: فایل ناقص");
            out.write(buf, 0, got);
            left -= got;
        }
    }

    private static void copyFile(File src, File dst) throws IOException {
        InputStream fi = new FileInputStream(src);
        OutputStream fo = new FileOutputStream(dst);
        byte[] buf = new byte[65536];
        int n;
        while ((n = fi.read(buf)) > 0) fo.write(buf, 0, n);
        fo.close();
        fi.close();
    }

    private static String readAsciiN(InputStream in, long n) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (long i = 0; i < n; i++) {
            int c = in.read();
            if (c < 0) throw new IOException("tar: ناتمام");
            sb.append((char) c);
        }
        return sb.toString();
    }

    private static void skipN(InputStream in, long n) throws IOException {
        while (n > 0) {
            long s = in.skip(n);
            if (s <= 0) {
                if (in.read() < 0) throw new IOException("tar: ناتمام");
                s = 1;
            }
            n -= s;
        }
    }

    private static void skipPad(InputStream in, long size) throws IOException {
        long pad = (512 - (size % 512)) % 512;
        skipN(in, pad);
    }
}
