package ir.atta.console;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

/**
 * آتا — پوستهٔ اندروید کنسول سرور ماینکرافت.
 * رابط وب آفلاین سرو می‌شود؛ پل محلی ۱۲۷٫۰٫۰٫۱:۴۸۲۷۳ دستورهای اورلای بومی را
 * به وب‌ویو می‌رساند تا به سرور واقعی (پنل میزبان یا عامل وی‌پی‌اس) فرستاده شوند.
 */
public final class MainActivity extends Activity {
    private WebView web;
    private CommandBridge bridge;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private static final String ORIGIN = "https://atta.console.local/";
    private static final int REQ_AUDIO = 4401;
    static final int BRIDGE_PORT = 48273;
    private PermissionRequest pendingMedia;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(Color.rgb(13, 18, 32));
        getWindow().setNavigationBarColor(Color.rgb(13, 18, 32));
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(13, 18, 32));
        web.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
                return insets.consumeSystemWindowInsets();
            }
        });
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString() + " AttaConsole/1.0");
        settings.setSupportMultipleWindows(false);
        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(final PermissionRequest request) {
                boolean wantsAudio = false;
                for (String r : request.getResources()) {
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) wantsAudio = true;
                }
                if (!wantsAudio) { request.deny(); return; }
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    request.grant(new String[]{ PermissionRequest.RESOURCE_AUDIO_CAPTURE });
                } else {
                    pendingMedia = request;
                    requestPermissions(new String[]{ Manifest.permission.RECORD_AUDIO }, REQ_AUDIO);
                }
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String scheme = uri.getScheme() == null ? "" : uri.getScheme();
                String url = uri.toString();
                if (scheme.equals("minecraft") || scheme.equals("ms-xbl") || scheme.equals("market") || scheme.equals("https")
                        && (url.contains("aternos.org") || url.contains("net.kdt.pojavlaunch") || url.contains("com.mojang.minecraftpe"))) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "باز نشد؛ اول لانچر را نصب کن.", Toast.LENGTH_LONG).show();
                    }
                    return true;
                }
                return !url.startsWith(ORIGIN);
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                // اتصال‌های واقعی به پنل میزبان و عامل وی‌پی‌اس باید از خود وب‌ویو بروند (CORS و کوکی میزبان)
                if (url.startsWith("https://") || url.startsWith("http://")) {
                    String host = request.getUrl().getHost() == null ? "" : request.getUrl().getHost();
                    if (!url.startsWith(ORIGIN)) return null; // اجازهٔ شبکهٔ واقعی برای پنل‌ها و عامل
                }
                if (!url.startsWith(ORIGIN)) return blocked();
                String path = request.getUrl().getPath();
                if (path == null || path.equals("/") || path.equals("/atta/") || path.equals("/atta")) path = "/atta/index.html";
                if (path.contains("..") || path.contains("\\")) return blocked();
                String mime = path.endsWith(".html") ? "text/html" : path.endsWith(".js") ? "application/javascript"
                        : path.endsWith(".css") ? "text/css" : path.endsWith(".svg") ? "image/svg+xml"
                        : path.endsWith(".png") ? "image/png" : path.endsWith(".mp3") ? "audio/mpeg"
                        : path.endsWith(".woff2") ? "font/woff2" : path.endsWith(".woff") ? "font/woff"
                        : path.endsWith(".webmanifest") ? "application/manifest+json" : "text/plain";
                try {
                    HashMap<String, String> headers = new HashMap<>();
                    headers.put("X-Content-Type-Options", "nosniff");
                    return new WebResourceResponse(mime, "UTF-8", 200, "OK", headers, getAssets().open("www" + path));
                } catch (Exception e) { return blocked(); }
            }
        });
        setContentView(web);
        web.loadUrl(ORIGIN + "atta/index.html");
        bridge = new CommandBridge();
        bridge.start();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_AUDIO && pendingMedia != null) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pendingMedia.grant(new String[]{ PermissionRequest.RESOURCE_AUDIO_CAPTURE });
            } else {
                pendingMedia.deny();
            }
            pendingMedia = null;
        }
    }

    private WebResourceResponse blocked() {
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", null, new ByteArrayInputStream(new byte[0]));
    }

    /** پل جاوااسکریپت: اورلای بومی، لانچر بازی‌ها، تمام‌صفحه و نتیجهٔ پل دستورها */
    public final class Bridge {
        @JavascriptInterface public void startOverlay() {
            runOnUiThread(new Runnable() { @Override public void run() {
                if (!Settings.canDrawOverlays(MainActivity.this)) {
                    Toast.makeText(MainActivity.this, "اجازهٔ «نمایش روی برنامه‌ها» را برای آتا روشن کن، سپس دوباره بزن.", Toast.LENGTH_LONG).show();
                    try {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    } catch (Exception ignored) { }
                    return;
                }
                startService(new Intent(MainActivity.this, OverlayService.class));
                Toast.makeText(MainActivity.this, "اورلای آتا فعال شد؛ داخل بازی از لبهٔ چپ بکش.", Toast.LENGTH_LONG).show();
            }});
        }
        @JavascriptInterface public void stopOverlay() {
            stopService(new Intent(MainActivity.this, OverlayService.class));
        }
        @JavascriptInterface public boolean canOverlay() {
            return Settings.canDrawOverlays(MainActivity.this);
        }
        @JavascriptInterface public void launchExternal(final String uriString) {
            if (uriString == null) return;
            runOnUiThread(new Runnable() { @Override public void run() {
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uriString));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "لانچر پیدا نشد؛ از فروشگاه نصب کن.", Toast.LENGTH_LONG).show();
                }
            }});
        }
        @JavascriptInterface public void openLauncherPackage(final String pkg) {
            if (pkg == null) return;
            runOnUiThread(new Runnable() { @Override public void run() {
                try {
                    Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
                    if (intent != null) { intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(intent); }
                    else {
                        Intent store = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg));
                        store.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(store);
                    }
                } catch (Exception ignored) { }
            }});
        }
        @JavascriptInterface public void fullscreen(final boolean on) {
            runOnUiThread(new Runnable() { @Override public void run() {
                View decor = getWindow().getDecorView();
                if (on) decor.setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
                else decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
            }});
        }
        @JavascriptInterface public void bridgeResult(final String message) {
            runOnUiThread(new Runnable() { @Override public void run() {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            }});
        }
    }

    /** سرور پل محلی — فقط روی ۱۲۷٫۰٫۰٫۱؛ اورلای بومی دستور را اینجا می‌گذارد. */
    private final class CommandBridge extends Thread {
        private ServerSocket socket;
        CommandBridge() { super("atta-command-bridge"); }
        @Override public void run() {
            try {
                socket = new ServerSocket(BRIDGE_PORT, 16, InetAddress.getByName("127.0.0.1"));
                while (!isInterrupted()) {
                    final Socket client = socket.accept();
                    new Thread(new Runnable() { @Override public void run() { handle(client); } }).start();
                }
            } catch (Exception ignored) { }
        }
        private void handle(Socket client) {
            try {
                client.setSoTimeout(3000);
                BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                String line = in.readLine();
                if (line == null) { client.close(); return; }
                int contentLength = 0;
                String header;
                while ((header = in.readLine()) != null && !header.isEmpty()) {
                    String h = header.toLowerCase();
                    if (h.startsWith("content-length:")) contentLength = Integer.parseInt(header.substring(15).trim());
                }
                char[] buf = new char[Math.min(contentLength, 100000)];
                int read = 0;
                while (read < buf.length) { int r = in.read(buf, read, buf.length - read); if (r < 0) break; read += r; }
                final String body = new String(buf, 0, read);
                String status = "200 OK";
                String responseBody = "{\"ok\":true}";
                if (line.startsWith("POST /command")) {
                    ui.post(new Runnable() { @Override public void run() {
                        if (web != null) {
                            String safe = body.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ");
                            web.evaluateJavascript("(function(){try{window.__attaBridge&&window.__attaBridge.receive('" + safe + "');}catch(e){}})();", null);
                        }
                    }});
                } else if (line.startsWith("GET /ping")) {
                    responseBody = "{\"pong\":true}";
                } else {
                    status = "404 Not Found";
                    responseBody = "{\"ok\":false}";
                }
                byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
                OutputStream out = client.getOutputStream();
                out.write(("HTTP/1.1 " + status + "\r\nContent-Type: application/json\r\nContent-Length: " + bytes.length
                        + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                out.write(bytes);
                out.flush();
                client.close();
            } catch (Exception ignored) { }
        }
        void shutdown() { try { if (socket != null) socket.close(); } catch (Exception ignored) { } interrupt(); }
    }

    @Override protected void onPause() { super.onPause(); /* وب‌ویو زنده می‌ماند تا اورلای و اتصال واقعی کار کنند */ }
    @Override protected void onResume() { super.onResume(); if (web != null) web.onResume(); }
    @Override protected void onDestroy() {
        if (bridge != null) bridge.shutdown();
        if (web != null) { web.removeJavascriptInterface("AndroidBridge"); web.destroy(); }
        super.onDestroy();
    }
}
