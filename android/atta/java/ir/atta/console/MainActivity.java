package ir.atta.console;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
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
import java.io.ByteArrayInputStream;
import java.util.HashMap;

/**
 * آتا — پوستهٔ اندروید کنسول سرور ماینکرافت.
 * رابط وب از دارایی‌های آفلاین داخل خود APK سرو می‌شود؛ ناوبری خارجی فقط برای
 * لانچرهای بازی (ماینکرافت بدراک، پوجاو/بوت و فروشگاه نصب) آزاد است.
 */
public final class MainActivity extends Activity {
    private WebView web;
    private static final String ORIGIN = "https://atta.console.local/";
    private static final int REQ_AUDIO = 4401;
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
                // بازکردن لانچرهای بازی و فروشگاه نصب — هدف اصلی اپ
                if (scheme.equals("minecraft") || scheme.equals("ms-xbl") || scheme.equals("market")
                        || url.contains("net.kdt.pojavlaunch") || url.contains("com.mojang.minecraftpe")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    } catch (Exception e) {
                        Toast.makeText(MainActivity.this, "این لانچر روی گوشی نصب نیست؛ اول آن را نصب کن.", Toast.LENGTH_LONG).show();
                        try {
                            Intent store = new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=minecraft"));
                            store.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(store);
                        } catch (Exception ignored) { }
                    }
                    return true;
                }
                return !url.startsWith(ORIGIN);
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
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

    /** پل جاوااسکریپت: راه‌اندازی خارجی و تمام‌صفحه برای حالت بازی */
    public final class Bridge {
        @JavascriptInterface public void launchExternal(final String uriString) {
            if (uriString == null) return;
            runOnUiThread(new Runnable() { @Override public void run() {
                try {
                    Uri uri = Uri.parse(uriString);
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
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
    }

    @Override protected void onPause() { super.onPause(); if (web != null) { web.onPause(); } }
    @Override protected void onResume() { super.onResume(); if (web != null) { web.onResume(); } }
    @Override protected void onDestroy() { if (web != null) { web.removeJavascriptInterface("AndroidBridge"); web.destroy(); } super.onDestroy(); }
}
