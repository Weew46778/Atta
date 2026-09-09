package ir.setareh.adventure;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.content.Intent;
import android.content.DialogInterface;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.ValueCallback;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.graphics.Color;
import android.widget.Toast;
import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

/** Offline-only WebView. No Internet/storage permissions, third-party SDKs, or external navigation. */
public final class MainActivity extends Activity {
    private WebView web;
    private String pendingReport;
    private static final String ORIGIN = "https://app.littlestar.local/";
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(Color.rgb(247,248,252));
        getWindow().setNavigationBarColor(Color.rgb(247,248,252));
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(247,248,252));
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
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setUserAgentString(settings.getUserAgentString()+" LittleStarAndroid/1.0");
        settings.setSupportMultipleWindows(false);
        web.addJavascriptInterface(new ReportBridge(), "AndroidReport");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !request.getUrl().toString().startsWith(ORIGIN);
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (!url.startsWith(ORIGIN)) return blocked();
                String path = request.getUrl().getPath();
                if (path == null || path.equals("/")) path = "/index.html";
                if (path.contains("..") || path.contains("\\")) return blocked();
                String mime = path.endsWith(".html") ? "text/html" : path.endsWith(".js") ? "application/javascript" : path.endsWith(".css") ? "text/css" : path.endsWith(".svg") ? "image/svg+xml" : path.endsWith(".png") ? "image/png" : path.endsWith(".mp3") ? "audio/mpeg" : path.endsWith(".woff2") ? "font/woff2" : path.endsWith(".webmanifest") ? "application/manifest+json" : "text/plain";
                try {
                    HashMap<String,String> headers = new HashMap<>();
                    headers.put("X-Content-Type-Options", "nosniff");
                    return new WebResourceResponse(mime, "UTF-8", 200, "OK", headers, getAssets().open("www"+path));
                } catch(Exception e) { return blocked(); }
            }
        });
        setContentView(web);
        web.loadUrl(ORIGIN + "index.html");
    }
    private WebResourceResponse blocked() {
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", null, new ByteArrayInputStream(new byte[0]));
    }
    public final class ReportBridge {
        @JavascriptInterface public void exportReport(final String text) {
            if (text == null || text.length() > 50000) return;
            runOnUiThread(new Runnable() { @Override public void run() {
                pendingReport = text;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TITLE, "little-star-progress.txt");
                try { startActivityForResult(intent, 20); }
                catch(Exception e) { Toast.makeText(MainActivity.this, "امکان ذخیرهٔ گزارش در این دستگاه وجود ندارد", Toast.LENGTH_LONG).show(); }
            }});
        }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if(request == 20 && result == RESULT_OK && data != null && pendingReport != null) {
            try(OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                out.write(("\ufeff"+pendingReport).getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this, "گزارش ذخیره شد", Toast.LENGTH_SHORT).show();
            } catch(Exception e) { Toast.makeText(this, "ذخیرهٔ گزارش انجام نشد", Toast.LENGTH_LONG).show(); }
        }
        pendingReport = null;
    }
    @Override public void onBackPressed() {
        web.evaluateJavascript("(function(){var b=document.getElementById('close-modal');if(b){b.click();return true;}return false;})()", new ValueCallback<String>() { @Override public void onReceiveValue(String value) {
            if(!"true".equals(value)) new AlertDialog.Builder(MainActivity.this)
                .setMessage("می‌خواهی فعلاً استراحت کنی؟ پیشرفتت ذخیره شده.")
                .setPositiveButton("خروج", new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface d, int w) { finish(); } })
                .setNegativeButton("ادامهٔ بازی", null).show();
        }});
    }
    @Override protected void onPause() { super.onPause(); if(web != null) { web.onPause(); web.pauseTimers(); } }
    @Override protected void onResume() { super.onResume(); if(web != null) { web.onResume(); web.resumeTimers(); } }
    @Override protected void onDestroy() { if(web != null) { web.removeJavascriptInterface("AndroidReport"); web.destroy(); } super.onDestroy(); }
}
