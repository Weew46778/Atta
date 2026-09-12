package ir.setareh.adventure;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.net.Uri;
import android.widget.Toast;
import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * باغ قصه‌ها ۳ — پوستهٔ اندروید
 * محتوای برنامه کاملاً آفلاین از assets خوانده می‌شود و درخواست‌ها به مبدأ داخلی محدودند.
 * پل «BaghVoice» گویندگی و شنودِ بومی فارسی را در اختیارِ صفحه می‌گذارد:
 *   BaghVoice.speak(json)  -> TextToSpeech با Locale fa-IR
 *   BaghVoice.listen(json) -> SpeechRecognizer با fa-IR (اختیاری، با اجازهٔ RECORD_AUDIO)
 * اگر گوینده یا شنود روی دستگاه نباشد، خطا به صفحه برمی‌گردد و متن روی صفحه می‌ماند.
 */
public final class MainActivity extends Activity {
    private WebView web;
    private TextToSpeech tts;
    private boolean ttsReady;
    private SpeechRecognizer recognizer;
    private String pendingReport;
    private volatile String syncOrigin = "";
    private PermissionRequest pendingMicrophone;
    private ValueCallback<Uri[]> fileCallback;
    private String pendingListenCall;
    private static final String ORIGIN = "https://app.bagh.local/";
    private static final String FA = "fa-IR";

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(Color.rgb(253, 247, 255));
        getWindow().setNavigationBarColor(Color.rgb(253, 247, 255));
        initTts();
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(253, 247, 255));
        web.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
                return insets.consumeSystemWindowInsets();
            }
        });
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(s.getUserAgentString() + " BaghGhesse3/3.0");
        s.setSupportMultipleWindows(false);
        web.addJavascriptInterface(new VoiceBridge(), "BaghVoice");
        web.addJavascriptInterface(new ReportBridge(), "AndroidReport");
        web.addJavascriptInterface(new FamilyBridge(), "FamilyNative");
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onPermissionRequest(final PermissionRequest request) {
                Uri origin = request.getOrigin();
                if (origin == null || !"https".equals(origin.getScheme()) || !"app.bagh.local".equals(origin.getHost())) { request.deny(); return; }
                boolean audio = false;
                for (String r : request.getResources()) if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r)) audio = true;
                if (!audio) { request.deny(); return; }
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    request.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
                } else {
                    if (pendingMicrophone != null) pendingMicrophone.deny();
                    pendingMicrophone = request;
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 30);
                }
            }
            @Override public void onPermissionRequestCanceled(PermissionRequest request) {
                if (pendingMicrophone == request) pendingMicrophone = null;
            }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/plain"});
                try { startActivityForResult(intent, 21); }
                catch (Exception e) { fileCallback.onReceiveValue(null); fileCallback = null; }
                return true;
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !request.getUrl().toString().startsWith(ORIGIN);
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (!url.startsWith(ORIGIN)) {
                    if (!syncOrigin.isEmpty() && url.startsWith(syncOrigin + "/api/")) return null;
                    return blocked();
                }
                String path = request.getUrl().getPath();
                if (path == null || path.equals("/")) path = "/index.html";
                if (path.contains("..") || path.contains("\\")) return blocked();
                String mime = path.endsWith(".html") ? "text/html"
                    : path.endsWith(".js") ? "application/javascript"
                    : path.endsWith(".css") ? "text/css"
                    : path.endsWith(".svg") ? "image/svg+xml"
                    : path.endsWith(".png") ? "image/png"
                    : path.endsWith(".mp3") ? "audio/mpeg"
                    : path.endsWith(".woff2") ? "font/woff2"
                    : path.endsWith(".webmanifest") ? "application/manifest+json" : "text/plain";
                try {
                    HashMap<String, String> headers = new HashMap<>();
                    headers.put("X-Content-Type-Options", "nosniff");
                    return new WebResourceResponse(mime, "UTF-8", 200, "OK", headers, getAssets().open("www" + path));
                } catch (Exception e) { return blocked(); }
            }
        });
        setContentView(web);
        web.loadUrl(ORIGIN + "index.html?edition=__EDITION__");
    }

    private void initTts() {
        try {
            tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
                @Override public void onInit(int status) {
                    if (status != TextToSpeech.SUCCESS || tts == null) { ttsReady = false; return; }
                    Locale fa = new Locale("fa", "IR");
                    int available = tts.setLanguage(fa);
                    ttsReady = available != TextToSpeech.LANG_MISSING_DATA && available != TextToSpeech.LANG_NOT_SUPPORTED;
                    if (!ttsReady) {
                        available = tts.setLanguage(Locale.getDefault());
                        ttsReady = available != TextToSpeech.LANG_MISSING_DATA && available != TextToSpeech.LANG_NOT_SUPPORTED;
                    }
                    tts.setSpeechRate(0.95f);
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override public void onStart(String id) { post("start", id, null, null); }
                        @Override public void onDone(String id) { post("done", id, null, null); }
                        @SuppressWarnings("deprecation") @Override public void onError(String id) { post("error", id, "tts", null); }
                    });
                }
            });
        } catch (Exception e) { ttsReady = false; }
    }

    /** پیام به صفحه: window.__baghVoiceEvent({...}) */
    private void post(final String type, final String id, final String reason, final String text) {
        if (web == null) return;
        final String safe = esc(text);
        runOnUiThread(new Runnable() { @Override public void run() {
            String js = "window.__baghVoiceEvent && window.__baghVoiceEvent({type:'" + type
                + "',id:'" + esc(id) + "'"
                + (reason != null ? ",reason:'" + esc(reason) + "'" : "")
                + (safe != null ? ",text:'" + safe + "'" : "") + "})";
            try { web.evaluateJavascript(js, null); } catch (Exception ignored) {}
        }});
    }
    private static String esc(String v) {
        if (v == null) return null;
        return v.replace("\\", "\\\\").replace("'", "\\'").replace("\n", " ").replace("\r", " ");
    }
    private static String jsonStr(String raw, String key) {
        if (raw == null) return null;
        int i = raw.indexOf("\"" + key + "\"");
        if (i < 0) return null;
        i = raw.indexOf(':', i);
        if (i < 0) return null;
        int q = raw.indexOf('"', i + 1);
        if (q < 0) return null;
        StringBuilder out = new StringBuilder();
        for (int k = q + 1; k < raw.length(); k++) {
            char c = raw.charAt(k);
            if (c == '\\' && k + 1 < raw.length()) { out.append(raw.charAt(++k)); continue; }
            if (c == '"') break;
            out.append(c);
        }
        return out.toString();
    }
    private static double jsonNum(String raw, String key, double dflt) {
        if (raw == null) return dflt;
        int i = raw.indexOf("\"" + key + "\"");
        if (i < 0) return dflt;
        i = raw.indexOf(':', i);
        if (i < 0) return dflt;
        StringBuilder num = new StringBuilder();
        for (int k = i + 1; k < raw.length(); k++) {
            char c = raw.charAt(k);
            if ((c >= '0' && c <= '9') || c == '.' || c == '-') num.append(c);
            else if (num.length() > 0) break;
        }
        try { return Double.parseDouble(num.toString()); } catch (Exception e) { return dflt; }
    }

    public final class VoiceBridge {
        @JavascriptInterface public void speak(final String json) {
            final String id = jsonStr(json, "id");
            final String text = jsonStr(json, "text");
            final double rate = jsonNum(json, "rate", 1.0);
            if (text == null || text.length() > 8000) { post("error", id, "empty", null); return; }
            runOnUiThread(new Runnable() { @Override public void run() {
                if (!ttsReady || tts == null) { post("error", id, "no-tts", null); return; }
                tts.setSpeechRate((float) Math.max(0.5, Math.min(1.6, rate)) * 0.95f);
                HashMap<String, String> params = new HashMap<>();
                params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id);
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
            }});
        }
        @JavascriptInterface public void stop() {
            runOnUiThread(new Runnable() { @Override public void run() { if (tts != null) tts.stop(); }});
        }
        @JavascriptInterface public void voices(final String json) {
            final String id = jsonStr(json, "id");
            post("voices", id, null, ttsReady ? "fa-IR" : "");
        }
        @JavascriptInterface public void listen(final String json) {
            final String id = jsonStr(json, "id");
            runOnUiThread(new Runnable() { @Override public void run() {
                if (!SpeechRecognizer.isRecognitionAvailable(MainActivity.this)) { post("error", id, "no-asr", null); return; }
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    pendingListenCall = id;
                    requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 31);
                    return;
                }
                startListen(id);
            }});
        }
    }

    private void startListen(final String id) {
        try {
            if (recognizer != null) { try { recognizer.destroy(); } catch (Exception ignored) {} }
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            final Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, FA);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, FA);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle p) {}
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float v) { post("level", id, null, String.valueOf((int) v)); }
                @Override public void onBufferReceived(byte[] b) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onError(int e) { post("error", id, "asr", null); destroyAsr(); }
                @Override public void onResults(Bundle b) {
                    ArrayList<String> list = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    String text = (list != null && !list.isEmpty()) ? list.get(0) : "";
                    post("result", id, null, text);
                    destroyAsr();
                }
                @Override public void onPartialResults(Bundle b) {}
                @Override public void onEvent(int t, Bundle b) {}
            });
            recognizer.startListening(intent);
        } catch (Exception e) { post("error", id, "asr", null); }
    }
    private void destroyAsr() {
        if (recognizer != null) { try { recognizer.destroy(); } catch (Exception ignored) {} recognizer = null; }
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        if (code == 30 && pendingMicrophone != null) {
            if (granted) pendingMicrophone.grant(new String[]{PermissionRequest.RESOURCE_AUDIO_CAPTURE});
            else pendingMicrophone.deny();
            pendingMicrophone = null;
        }
        if (code == 31) {
            final String id = pendingListenCall; pendingListenCall = null;
            if (granted && id != null) startListen(id);
            else if (id != null) post("error", id, "denied", null);
        }
    }

    public final class FamilyBridge {
        @JavascriptInterface public void configureSync(String url) {
            if (url == null || url.isEmpty()) { syncOrigin = ""; return; }
            try {
                Uri uri = Uri.parse(url);
                if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))) return;
                syncOrigin = "https://" + uri.getAuthority();
            } catch (Exception ignored) { syncOrigin = ""; }
        }
    }

    public final class ReportBridge {
        @JavascriptInterface public void exportReport(final String text) {
            if (text == null || text.length() > 8000000) return;
            runOnUiThread(new Runnable() { @Override public void run() {
                pendingReport = text;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, "bagh-family-backup.json");
                try { startActivityForResult(intent, 20); }
                catch (Exception e) { Toast.makeText(MainActivity.this, "ذخیرهٔ فایل روی این دستگاه ممکن نیست", Toast.LENGTH_LONG).show(); }
            }});
        }
    }

    private WebResourceResponse blocked() {
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", null, new ByteArrayInputStream(new byte[0]));
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 21) {
            if (fileCallback != null) { fileCallback.onReceiveValue(result == RESULT_OK && data != null ? new Uri[]{data.getData()} : null); fileCallback = null; }
            return;
        }
        if (request == 20 && result == RESULT_OK && data != null && pendingReport != null) {
            try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                out.write(("\ufeff" + pendingReport).getBytes(StandardCharsets.UTF_8));
                Toast.makeText(this, "گزارش ذخیره شد", Toast.LENGTH_SHORT).show();
            } catch (Exception e) { Toast.makeText(this, "ذخیرهٔ گزارش انجام نشد", Toast.LENGTH_LONG).show(); }
        }
        pendingReport = null;
    }

    @Override public void onBackPressed() {
        web.evaluateJavascript("(function(){var b=document.querySelector('.m-exit');if(b){b.click();return true;}"
            + "var c=document.querySelector('.p-close');if(c){c.click();return true;}return false;})()",
            new ValueCallback<String>() { @Override public void onReceiveValue(String value) {
                if (!"true".equals(value)) new AlertDialog.Builder(MainActivity.this)
                    .setMessage("می‌خواهی فعلاً استراحت کنی؟ پیشرفتت ذخیره شده.")
                    // android.jarِ این ابزارها LambdaMetafactory ندارد؛ کلاسِ ناشناس می‌سازیم
                    .setPositiveButton("خروج", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) { finish(); }
                    })
                    .setNegativeButton("ادامهٔ بازی", null).show();
            }});
    }

    @Override protected void onPause() {
        super.onPause();
        if (tts != null) tts.stop();
        destroyAsr();
        if (web != null) { web.onPause(); web.pauseTimers(); }
    }
    @Override protected void onResume() { super.onResume(); if (web != null) { web.onResume(); web.resumeTimers(); } }
    @Override protected void onDestroy() {
        if (recognizer != null) destroyAsr();
        if (tts != null) { try { tts.stop(); tts.shutdown(); } catch (Exception ignored) {} tts = null; }
        if (web != null) {
            web.removeJavascriptInterface("BaghVoice");
            web.removeJavascriptInterface("AndroidReport");
            web.removeJavascriptInterface("FamilyNative");
            if (pendingMicrophone != null) pendingMicrophone.deny();
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            web.destroy();
        }
        super.onDestroy();
    }
}
