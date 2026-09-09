package com.atta.mcpanel.rcon;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * کلاینت سبک RCON — پروتکل کنسول سرور ماینکرفت (جاوا و بدراک)
 */
public class RconConnection {

    private final String host;
    private final int port;
    private final String password;
    private final RconListener listener;
    private final int connectTimeoutMs;

    private final AtomicInteger nextId = new AtomicInteger(0);
    private final Object writeLock = new Object();

    private volatile boolean isOpen = false;
    private volatile boolean closedByUser = false;
    private volatile Socket socket;
    private volatile DataOutputStream output;
    private volatile Thread worker;

    public RconConnection(String host, int port, String password, RconListener listener) {
        this(host, port, password, listener, 7000);
    }

    public RconConnection(String host, int port, String password,
                          RconListener listener, int connectTimeoutMs) {
        this.host = host;
        this.port = port;
        this.password = password == null ? "" : password;
        this.listener = listener;
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public boolean isOpen() { return isOpen; }

    public void start() {
        if (worker != null && worker.isAlive()) return;
        closedByUser = false;
        isOpen = false;
        Thread t = new Thread(new Runnable() {
            @Override public void run() { runWorker(); }
        }, "atta-rcon");
        worker = t;
        t.start();
    }

    /** ارسال دستور؛ دستورها بدون / فرستاده می‌شوند */
    public boolean send(String command) {
        Socket s = socket;
        if (s == null || !isOpen) return false;
        String cmd = command.trim();
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        if (cmd.isEmpty()) return true;
        final int id = nextId.incrementAndGet();
        try {
            synchronized (writeLock) {
                DataOutputStream o = output;
                if (o == null) return false;
                writePacket(o, id, 2, cmd);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public void stop() {
        closedByUser = true;
        Socket s = socket;
        if (s != null) {
            try { s.close(); } catch (IOException ignored) {}
        }
        Thread w = worker;
        if (w != null) w.interrupt();
    }

    // ------------------------------------------------------------------ //

    private void runWorker() {
        try {
            listener.onConnecting();
        } catch (Exception ignored) {}
        final Socket sock = new Socket();
        socket = sock;
        try {
            sock.connect(new InetSocketAddress(host, port), connectTimeoutMs);
            sock.setTcpNoDelay(true);
            final DataInputStream input = new DataInputStream(sock.getInputStream());
            final DataOutputStream out = new DataOutputStream(sock.getOutputStream());
            output = out;

            final int authId = nextId.incrementAndGet();
            synchronized (writeLock) {
                writePacket(out, authId, 3, password);
            }

            while (!Thread.currentThread().isInterrupted()) {
                Packet p = readPacket(input);
                if (p == null) break;
                if (p.id == authId) {
                    isOpen = true;
                    fireAuth(true, "اتصال برقرار شد");
                } else if (p.id == -1) {
                    String msg = p.text.isEmpty() ? "رمز عبور نادرست است" : p.text;
                    fireAuth(false, msg);
                    try { sock.close(); } catch (IOException ignored) {}
                    return;
                } else if (p.id > 0 && p.type == 0 && !p.text.trim().isEmpty()) {
                    fireOutput(p.text);
                }
            }
            fireClosed(closedByUser ? "قطع شد" : "اتصال بسته شد");
        } catch (Exception e) {
            if (!closedByUser) {
                String reason;
                if (e instanceof IOException) {
                    reason = "اتصال ناموفق (" + e.getMessage() + ")";
                } else {
                    reason = "خطا: " + (e.getMessage() == null ? "نامشخص" : e.getMessage());
                }
                fireClosed(reason);
            }
        } finally {
            isOpen = false;
            output = null;
            socket = null;
            try { sock.close(); } catch (IOException ignored) {}
        }
    }

    private void fireAuth(final boolean ok, final String msg) {
        try { listener.onAuth(ok, msg); } catch (Exception ignored) {}
    }

    private void fireOutput(final String text) {
        try { listener.onOutput(text); } catch (Exception ignored) {}
    }

    private void fireClosed(final String reason) {
        try { listener.onClosed(reason); } catch (Exception ignored) {}
    }

    // ------------------------------------------------------------------ //

    private static class Packet {
        final int id;
        final int type;
        final String text;
        Packet(int id, int type, String text) {
            this.id = id; this.type = type; this.text = text;
        }
    }

    private static void writePacket(DataOutputStream o, int id, int type, String body)
            throws IOException {
        byte[] payload = body.getBytes("UTF-8");
        writeIntLE(o, 4 + 4 + payload.length + 2);
        writeIntLE(o, id);
        writeIntLE(o, type);
        o.write(payload);
        o.writeByte(0);
        o.writeByte(0);
        o.flush();
    }

    private static Packet readPacket(DataInputStream i) throws IOException {
        Integer length = readIntLE(i);
        if (length == null) return null;
        if (length < 10) throw new IOException("پاکت RCON نامعتبر");
        Integer id = readIntLE(i);
        if (id == null) throw new IOException("اتصال ناقص");
        Integer type = readIntLE(i);
        if (type == null) throw new IOException("اتصال ناقص");
        byte[] buf = new byte[length - 10];
        i.readFully(buf);
        String text = new String(buf, "UTF-8").trim();
        return new Packet(id, type, text);
    }

    private static void writeIntLE(DataOutputStream o, int v) throws IOException {
        o.writeByte(v & 0xFF);
        o.writeByte((v >> 8) & 0xFF);
        o.writeByte((v >> 16) & 0xFF);
        o.writeByte((v >> 24) & 0xFF);
    }

    private static Integer readIntLE(DataInputStream i) throws IOException {
        int v = 0;
        for (int k = 0; k < 4; k++) {
            int b = i.read();
            if (b < 0) return null;
            v |= b << (8 * k);
        }
        return v;
    }

    /** تست سریع اتصال برای دکمهٔ «تست» */
    public static void testConnection(final String host, final int port, final String password,
                                      final OnResult cb) {
        final boolean[] done = { false };
        RconConnection conn = new RconConnection(host, port, password, new RconListener() {
            @Override public void onAuth(boolean ok, String message) {
                if (!done[0]) {
                    done[0] = true;
                    cb.onResult(ok, ok ? "اتصال RCON برقرار شد" : "ورود رد شد: " + message);
                }
            }
            @Override public void onClosed(String reason) {
                if (!done[0]) {
                    done[0] = true;
                    cb.onResult(false, reason);
                }
            }
        }, 5000);
        conn.start();
    }

    public interface OnResult {
        void onResult(boolean ok, String message);
    }
}
