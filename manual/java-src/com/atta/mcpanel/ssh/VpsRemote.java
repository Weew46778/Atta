package com.atta.mcpanel.ssh;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

/**
 * اتصال SSH ساده به VPS برای اجرای دستور — بدون نیاز به کتابخانهٔ خارجی.
 * از JSch (کامپایل‌شده داخل اپ) استفاده می‌کند؛ GSS و فشرده‌سازی استفاده نمی‌شود.
 */
public final class VpsRemote {

    public static final class Result {
        public final int exit;
        public final String out;
        public final String err;
        Result(int e, String o, String er) { exit = e; out = o; err = er; }
        public String text() {
            StringBuilder sb = new StringBuilder();
            if (out != null && !out.isEmpty()) sb.append(out.trim());
            if (err != null && !err.isEmpty()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(err.trim());
            }
            return sb.toString().trim();
        }
    }

    public final String host;
    public final int port;
    public final String user;
    public final String pass;

    public VpsRemote(String host, int port, String user, String pass) {
        this.host = host == null ? "" : host.trim();
        this.port = port <= 0 ? 22 : port;
        this.user = user == null ? "root" : user.trim();
        this.pass = pass == null ? "" : pass;
    }

    /** اجرای یک فرمان (یا اسکریپت چندخطی) و گرفتن خروجی */
    public Result exec(String command, int timeoutMs) {
        final int deadline = timeoutMs > 0 ? timeoutMs : 60000;
        Session session = null;
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(user, host, port);
            session.setPassword(pass);
            java.util.Properties cfg = new java.util.Properties();
            cfg.put("StrictHostKeyChecking", "no");
            cfg.put("PreferredAuthentications", "password,keyboard-interactive,publickey");
            session.setConfig(cfg);
            session.setTimeout(deadline);
            session.connect(20000);
            Channel channel = session.openChannel("exec");
            ChannelExec ce = (ChannelExec) channel;
            ce.setCommand(command);
            ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
            ce.setErrStream(errBuf);
            InputStream in = ce.getInputStream();
            ce.connect(15000);
            ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            long start = System.currentTimeMillis();
            int last = 0;
            while (true) {
                if (System.currentTimeMillis() - start > deadline) {
                    try { ce.disconnect(); } catch (Exception ignored) {}
                    break;
                }
                while (in.available() > 0) {
                    int n = in.read(buf);
                    if (n <= 0) break;
                    outBuf.write(buf, 0, n);
                    last = 0;
                }
                if (ce.isClosed() && in.available() <= 0) break;
                if (++last > 40) { // حدود ۴۰۰ms بدون داده ولی هنوز باز
                    // همه را بخوان و اگر کانال هنوز باز است صبر کن
                }
                Thread.sleep(10);
            }
            int exit = ce.isClosed() ? ce.getExitStatus() : -1;
            while (in.available() > 0) {
                int n = in.read(buf);
                if (n <= 0) break;
                outBuf.write(buf, 0, n);
            }
            String err = errBuf.toString("UTF-8");
            String out = outBuf.toString("UTF-8");
            try { channel.disconnect(); } catch (Exception ignored) {}
            try { session.disconnect(); } catch (Exception ignored) {}
            return new Result(exit, out, err);
        } catch (Exception e) {
            if (session != null) { try { session.disconnect(); } catch (Exception ignored) {} }
            return new Result(-1, "", e.getMessage() == null ? "خطای اتصال" : e.getMessage());
        }
    }

    /** اجرای مطمئن با پیام‌های فارسی */
    public String safeExec(String cmd, int timeoutMs) {
        Result r = exec(cmd, timeoutMs);
        String t = r.text();
        if (r.exit == -1 && t.isEmpty()) t = "خطای ناشناخته در اتصال";
        return "exit=" + r.exit + "\n" + t;
    }
}
