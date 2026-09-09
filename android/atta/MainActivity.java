package ir.atta.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.webkit.PermissionRequest;
import android.net.Uri;
import android.graphics.Color;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

/**
 * آتا v3 — WebView آفلاین + پلِ بومیِ AttaVoice (گفتار و شنیدن).
 * محتوا از assets/www با مبدأِ قفل‌شدهٔ https://atta.local سرو می‌شود؛ هیچ درخواستِ اینترنتی مجاز نیست.
 */
public final class MainActivity extends Activity {
    private static final String ORIGIN = "https://atta.local/";
    private WebView web;
    private TextToSpeech tts;
    private volatile boolean ttsReady = false;
    private volatile boolean hasPersian = false;
    private SpeechRecognizer asr;
    private boolean listening = false;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(Color.rgb(22, 13, 51));
        getWindow().setNavigationBarColor(Color.rgb(22, 13, 51));
        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(22, 13, 51));
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
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setTextZoom(100); /* اندازهٔ متن را خودِ اپ کنترل می‌کند */
        s.setUserAgentString(s.getUserAgentString() + " AttaAndroid/3.0");
        s.setSupportMultipleWindows(false);
        web.addJavascriptInterface(new VoiceBridge(), "AttaVoice");
        web.setWebChromeClient(new WebChromeClient());
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
                String p = path.toLowerCase(Locale.ROOT);
                String mime = p.endsWith(".html") ? "text/html"
                    : p.endsWith(".js") ? "application/javascript"
                    : p.endsWith(".css") ? "text/css"
                    : p.endsWith(".svg") ? "image/svg+xml"
                    : p.endsWith(".png") ? "image/png"
                    : p.endsWith(".woff2") ? "font/woff2"
                    : p.endsWith(".json") ? "application/json"
                    : p.endsWith(".webmanifest") ? "application/manifest+json"
                    : "text/plain";
                try {
                    HashMap<String, String> headers = new HashMap<String, String>();
                    headers.put("X-Content-Type-Options", "nosniff");
                    return new WebResourceResponse(mime, "UTF-8", 200, "OK", headers, getAssets().open("www" + path));
                } catch (Exception e) { return blocked(); }
            }
        });
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override public void onInit(int status) {
                ttsReady = status == TextToSpeech.SUCCESS;
                if (ttsReady) {
                    try {
                        int r = tts.setLanguage(new Locale("fa", "IR"));
                        hasPersian = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED;
                    } catch (Exception ignored) { hasPersian = false; }
                    if (!hasPersian) {
                        try { tts.setLanguage(new Locale("fa")); } catch (Exception ignored) {}
                    }
                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override public void onStart(String id) {}
                        @Override public void onDone(String id) { voiceEvent("onDone", quote(id)); }
                        @Override public void onError(String id) { voiceEvent("onError", quote(id)); }
                    });
                }
            }
        });
        setContentView(web);
        web.loadUrl(ORIGIN + "index.html?edition=__EDITION__&start=__START__");
    }

    private static String quote(String s) {
        return s == null ? "''" : "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }
    private void voiceEvent(String name, String arg) {
        final String js = "(function(){try{window.AttaVoiceEvents&&window.AttaVoiceEvents." + name + "&&window.AttaVoiceEvents." + name + "(" + arg + ");}catch(e){}})()";
        main.post(new Runnable() { @Override public void run() { if (web != null) web.evaluateJavascript(js, null); } });
    }

    private WebResourceResponse blocked() {
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", null, new ByteArrayInputStream(new byte[0]));
    }

    /* ---------- پلِ AttaVoice ---------- */
    public final class VoiceBridge {
        @JavascriptInterface public boolean hasPersian() { return hasPersian; }

        @JavascriptInterface public void speak(final String id, final String text, final double rate, final double pitch) {
            main.post(new Runnable() { @Override public void run() {
                if (tts == null || !ttsReady || text == null || text.trim().isEmpty()) {
                    voiceEvent("onError", quote(id));
                    return;
                }
                try {
                    tts.setSpeechRate((float) Math.min(1.6, Math.max(0.4, rate)));
                    tts.setPitch((float) Math.min(2.0, Math.max(0.5, pitch)));
                    HashMap<String, String> params = new HashMap<String, String>();
                    params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, id);
                    int r = tts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
                    if (r != TextToSpeech.SUCCESS) voiceEvent("onError", quote(id));
                } catch (Exception e) { voiceEvent("onError", quote(id)); }
            }});
        }

        @JavascriptInterface public void stop() {
            main.post(new Runnable() { @Override public void run() {
                try { if (tts != null) tts.stop(); } catch (Exception ignored) {}
                stopListening();
            }});
        }

        @JavascriptInterface public void startListen() {
            main.post(new Runnable() { @Override public void run() {
                if (listening) return;
                if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, 31);
                    return; /* پس از اجازه، دوباره صدا زده می‌شود */
                }
                beginListen();
            }});
        }

        @JavascriptInterface public void stopListen() {
            main.post(new Runnable() { @Override public void run() { stopListening(); }});
        }
    }

    private void beginListen() {
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                voiceEvent("onSpeechError", "'noservice'");
                return;
            }
            if (asr != null) { asr.destroy(); asr = null; }
            asr = SpeechRecognizer.createSpeechRecognizer(this);
            asr.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle p) {}
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float v) {}
                @Override public void onBufferReceived(byte[] b) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onError(int error) {
                    listening = false;
                    voiceEvent("onSpeechError", "'" + errName(error) + "'");
                }
                @Override public void onResults(Bundle results) {
                    listening = false;
                    ArrayList<String> list = results == null ? null : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    String best = (list != null && !list.isEmpty()) ? list.get(0) : "";
                    voiceEvent("onSpeech", quote(best));
                }
                @Override public void onPartialResults(Bundle p) {}
                @Override public void onEvent(int e, Bundle p) {}
            });
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR");
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            listening = true;
            asr.startListening(i);
        } catch (Exception e) {
            listening = false;
            voiceEvent("onSpeechError", "'error'");
        }
    }
    private void stopListening() {
        try { if (asr != null) { asr.stopListening(); asr.destroy(); } } catch (Exception ignored) {}
        asr = null; listening = false;
    }
    private static String errName(int e) {
        switch (e) {
            case SpeechRecognizer.ERROR_NO_MATCH: return "no-speech";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "timeout";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "permission";
            case SpeechRecognizer.ERROR_NETWORK: return "network";
            default: return "error";
        }
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == 31) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) beginListen();
            else voiceEvent("onSpeechError", "'permission'");
        }
    }

    @Override public void onBackPressed() {
        if (web == null) { super.onBackPressed(); return; }
        web.evaluateJavascript("(function(){try{var m=document.getElementById('modal-root');if(m&&m.firstChild){if(window.AT&&AT.ui){AT.ui.closeModal();}else{m.innerHTML='';}return 'true';}return 'false';}catch(e){return 'false';}})()",
            new android.webkit.ValueCallback<String>() { @Override public void onReceiveValue(String value) {
                if (!"\"true\"".equals(value)) new AlertDialog.Builder(MainActivity.this)
                    .setMessage("می‌خواهی فعلاً استراحت کنی؟ پیشرفتت ذخیره شده.")
                    .setPositiveButton("خروج", new DialogInterface.OnClickListener() { @Override public void onClick(DialogInterface d, int w) { finish(); } })
                    .setNegativeButton("ادامهٔ بازی", null).show();
            }});
    }

    @Override protected void onPause() {
        if (web != null) { web.evaluateJavascript("(function(){try{window.AT&&AT.voice&&AT.voice.stop();}catch(e){}})()", null); web.onPause(); web.pauseTimers(); }
        try { if (tts != null) tts.stop(); } catch (Exception ignored) {}
        stopListening();
        super.onPause();
    }
    @Override protected void onResume() { super.onResume(); if (web != null) { web.onResume(); web.resumeTimers(); } }
    @Override protected void onDestroy() {
        stopListening();
        if (web != null) { web.removeJavascriptInterface("AttaVoice"); web.destroy(); }
        try { if (tts != null) tts.shutdown(); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
