package com.atta.mcpanel.aternos;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.graphics.Typeface;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.atta.mcpanel.core.AppPrefs;
import com.atta.mcpanel.overlay.UiKit;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * موتور Aternos — اتصال واقعی به پنل aternos.org از داخل خود اپ.
 *
 * چطور کار می‌کند:
 *  ۱) کاربر با «ورود داخل اپ» در WebViewِ داخل اپ لاگین می‌کند (کپچا/کلودفلر/2FA خودش حل می‌شود)
 *     و کوکی ATERNOS_SESSION در CookieManager ذخیره می‌شود.
 *  ۲) یک WebView مخفی (۱×۱ پیکسل) صفحهٔ /servers/ و سپس /server/ پنل واقعی را باز می‌کند؛
 *     توکن AJAX خودِ پنل از صفحه خوانده می‌شود (window.AJAX_TOKEN یا اجرای اسکریپتِ توکن در head).
 *  ۳) دکمه‌های پنل فارسیِ اپ، همان API خودِ Aternos را از داخل صفحهٔ لاگین‌شده صدا می‌زنند:
 *       /ajax/server/start | stop | restart | confirm | accept-eula   (با TOKEN+SEC و هدر X-Requested-With)
 *     یعنی هر کلیک واقعاً روی حساب Aternos خود کاربر اعمال می‌شود.
 *  ۴) وضعیت زنده از lastStatus صفحهٔ سرور خوانده می‌شود (0=خاموش 1=روشن 2=در حال روشن‌شدن
 *     3=در حال خاموش‌شدن 6=در حال بارگذاری 7=خطا 10=در صف/نیاز به تأیید).
 *
 * اگر توکن در دسترس نباشد، به‌جای API مستقیم روی خودِ دکمه‌های واقعی پنل کلیک می‌شود (fallback).
 */
public class AternosPanel {

    public interface Ui {
        void atLog(String line);                                   // خط جدید در کادر لاگ
        void atStatus(Map<String, Object> lastStatus, String dom); // وضعیت سرور
        void atReady(String serverId);                             // موتور آماده است
        void atGone(String reason);                                // نشست از دست رفت
        void atConsole(String line);                               // خط کنسول زنده (استریم hermes)
    }

    private static final String BASE = "https://aternos.org";
    private static final String URL_SERVERS = BASE + "/servers/";
    private static final String URL_SERVER = BASE + "/server/";
    private static final String URL_LOGIN = BASE + "/go/";

    private final Activity act;
    private final AppPrefs prefs;
    private final Ui cb;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private WebView engine;          // WebView مخفی — موتور اصلی
    private FrameLayout loginOverlay; // ورود تمام‌صفحه داخل خود اکتیویتی
    private WebView loginWeb;
    private TextView loginInfo;
    private boolean ready = false;
    private boolean sessionKnownDead = false;   // نشست منقضی شده — دفعهٔ بعد مستقیم فرم ورود باز شود
    private String deadSessionValue = null;      // «مقدار» کوکی مرده — فقط مقدار جدید یعنی لاگین واقعی
    private String serverId = "";
    private String lastServerName = "";
    private int pollTick = 0;
    private int emptyPolls = 0;
    private boolean pageLoading = false;
    private long lastPageLoad = 0L;

    public AternosPanel(Activity act, AppPrefs prefs, Ui cb) {
        this.act = act;
        this.prefs = prefs;
        this.cb = cb;
        try {
            CookieManager.getInstance().setAcceptCookie(true);
        } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------------
    // نشست
    // ------------------------------------------------------------------

    /** مقدار فعلی کوکی نشست (بدون مقدار = null) */
    private String sessionValue() {
        try {
            String c = CookieManager.getInstance().getCookie(BASE);
            if (c == null) return null;
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(?:^|;\\s*)ATERNOS_SESSION=([^;\\s]+)").matcher(c);
            return m.find() ? m.group(1) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** آیا نشستِ زنده داریم؟ (کوکیِ موجود + متفاوت از مقدارِ مرده) */
    public boolean hasSession() {
        String v = sessionValue();
        return v != null && v.length() > 0 && !v.equals(deadSessionValue);
    }

    public boolean isReady() { return ready; }

    public String serverId() { return serverId; }
    public String serverName() { return lastServerName; }

    /** شروع: اگر نشست نیست دیالوگ ورود، وگرنه بارگذاری مستقیم پنل */
    public void begin() {
        uiOnUiThread(new Runnable() { @Override public void run() {
            if (!hasSession() || sessionKnownDead) {
                sessionKnownDead = false;
                showLogin();
                return;
            }
            loadEngine();
        }});
    }

    /**
     * ورود داخل اپ — تمام‌صفحه روی خود اکتیویتی (نه دیالوگ).
     * دلیل: کیبورد نرم داخل WebViewِ AlertDialog باز نمی‌شود (رفتار شناخته‌شدهٔ اندروید)؛
     * در حالت تمام‌صفحه + SOFT_INPUT_ADJUST_RESIZE کیبورد مثل مرورگر عادی کار می‌کند.
     */
    public void showLogin() {
        uiOnUiThread(new Runnable() { @Override public void run() {
            try {
                if (loginOverlay != null) return; // باز است
                CookieManager cm = CookieManager.getInstance();
                cm.setAcceptCookie(true);

                final WebView wv = new WebView(act);
                WebSettings ws = wv.getSettings();
                ws.setJavaScriptEnabled(true);
                ws.setDomStorageEnabled(true);
                ws.setDatabaseEnabled(true);
                ws.setUserAgentString(ws.getUserAgentString().replace("; wv", "").replace("Version/4.0 ", ""));
                ws.setLoadWithOverviewMode(true);
                ws.setUseWideViewPort(true);
                wv.setFocusable(true);
                wv.setFocusableInTouchMode(true);
                CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true);

                wv.setWebViewClient(new WebViewClient() {
                    @Override public void onPageFinished(WebView v, String url) {
                        // ترفند شناخته‌شده: فوکوس پایین صفحه تا اولین لمسِ فیلد ورودی کیبورد بیاورد
                        v.requestFocus(View.FOCUS_DOWN);
                        log("🔑 صفحهٔ ورود: " + (url == null ? "?" : url.replace("https://aternos.org", "")));
                        // تشخیص وضعیت صفحه (کلودفلر یا آمادهٔ ورود)
                        try {
                            v.evaluateJavascript("(function(){try{return document.title||''}catch(e){return ''}})()",
                                    new android.webkit.ValueCallback<String>() {
                                        @Override public void onReceiveValue(String value) {
                                            try {
                                                String t = value == null ? "" : value.replace("\"", "");
                                                if (t.contains("moment") || t.contains("Attention")) {
                                                    loginInfo.setText("⏳ محافظ کلودفلر فعال است — چند ثانیه صبر کن؛ اگر رد نشد با تغییر IP/VPN امتحان کن");
                                                } else if (!sessionAppeared()) {
                                                    loginInfo.setText("صفحه آماده است — نام‌کاربری و رمز Aternos را وارد کن و دکمهٔ ورود سایت را بزن");
                                                }
                                            } catch (Throwable ignored) {}
                                        }
                                    });
                        } catch (Throwable ignored) {}
                        if (sessionAppeared()) {
                            sessionKnownDead = false;
                            deadSessionValue = null;
                            deadSessionValue = null;
                            try { CookieManager.getInstance().flush(); } catch (Throwable ignored) {}
                            if (loginInfo != null) loginInfo.setText("✅ وارد شدی — در حال بازگشت به پنل…");
                            log("✅ ورود انجام شد — نشست ذخیره شد");
                            ui.postDelayed(new Runnable() { @Override public void run() {
                                closeLogin();
                                loadEngine();
                            }}, 700);
                        }
                    }
                });
                wv.loadUrl(URL_LOGIN);

                // دکمهٔ برگشت سخت‌افزاری = بستن صفحهٔ ورود
                final View.OnKeyListener backClose = new View.OnKeyListener() {
                    @Override public boolean onKey(View v, int keyCode, KeyEvent event) {
                        if (keyCode == KeyEvent.KEYCODE_BACK
                                && event.getAction() == KeyEvent.ACTION_UP) {
                            closeLogin();
                            return true;
                        }
                        return false;
                    }
                };
                wv.setOnKeyListener(backClose);

                // نوار بالا: عنوان + دکمه‌ها
                LinearLayout bar = UiKit.hrow(act);
                bar.setBackgroundColor(0xFF10151D);
                bar.setPadding(UiKit.dp(act, 8), UiKit.dp(act, 6), UiKit.dp(act, 8), UiKit.dp(act, 6));
                TextView title = new TextView(act);
                title.setText("🔑 ورود به Aternos");
                title.setTextColor(0xFFE8ECF3);
                title.setTextSize(14.5f);
                title.setTypeface(Typeface.DEFAULT_BOLD);
                bar.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
                TextView bDone = UiKit.chip(act, "✅ وارد شدم", UiKit.KIND_ACCENT, new Runnable() {
                    @Override public void run() {
                        if (sessionAppeared()) {
                            sessionKnownDead = false;
                            try { CookieManager.getInstance().flush(); } catch (Throwable ignored) {}
                            log("✅ نشست Aternos ذخیره شد");
                            closeLogin();
                            loadEngine();
                        } else {
                            Toast.makeText(act, "هنوز وارد نشده‌ای — اول در صفحهٔ زیر نام‌کاربری/رمز را بزن", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                bar.addView(bDone, new LinearLayout.LayoutParams(-2, UiKit.dp(act, 44)));
                TextView bClose = UiKit.chip(act, "بستن", UiKit.KIND_DANGER, new Runnable() {
                    @Override public void run() { closeLogin(); }
                });
                bar.addView(bClose, new LinearLayout.LayoutParams(-2, UiKit.dp(act, 44)));

                loginInfo = new TextView(act);
                loginInfo.setTextSize(11.5f);
                loginInfo.setTextColor(0xFF93A0B4);
                loginInfo.setLineSpacing(UiKit.dp(act, 2), 1f);
                loginInfo.setPadding(UiKit.dp(act, 10), UiKit.dp(act, 5), UiKit.dp(act, 10), UiKit.dp(act, 3));
                loginInfo.setText("⚠ دکمهٔ ورود با Google/Microsoft داخل اپ کار نمی‌کند — سیاست خود گوگل است (مرورگر داخلی اپ‌ها را نمی‌پذیرد).\n"
                        + "✅ با همان فرم «نام‌کاربری + رمز Aternos» وارد شو. اگر رمز نداری، در همین صفحه لینک «Forgot your password?» را بزن تا با ایمیلت یک رمز بسازی (فقط یک بار).\n"
                        + "بعد از ورود، خودکار برمی‌گردد.");

                LinearLayout box = UiKit.vcol(act, 0);
                box.setBackgroundColor(0xFF0B0F16);
                box.addView(bar, new LinearLayout.LayoutParams(-1, -2));
                box.addView(loginInfo, new LinearLayout.LayoutParams(-1, -2));

                // گزینهٔ پیشرفته: چسباندن کوکی ATERNOS_SESSION (مثلاً از کروم دسکتاپ)
                LinearLayout adv = UiKit.hrow(act);
                adv.setPadding(UiKit.dp(act, 8), 0, UiKit.dp(act, 8), UiKit.dp(act, 4));
                final EditText ck = new EditText(act);
                ck.setHint("کوکی ATERNOS_SESSION (پیشرفته)");
                ck.setTextSize(11.5f);
                ck.setTextColor(0xFFE8ECF3);
                ck.setHintTextColor(0xFF93A0B4);
                ck.setSingleLine(true);
                GradientDrawable cg = UiKit.roundedSolid(0xFF0D1420, UiKit.dp(act, 10));
                cg.setStroke(1, 0x30FFFFFF);
                ck.setBackground(cg);
                ck.setPadding(UiKit.dp(act, 8), 0, UiKit.dp(act, 8), 0);
                adv.addView(ck, new LinearLayout.LayoutParams(0, UiKit.dp(act, 40), 1f));
                TextView bApply = UiKit.chip(act, "اعمال کوکی", UiKit.KIND_ACCENT, new Runnable() {
                    @Override public void run() {
                        String v = ck.getText().toString().trim();
                        // اگر کل رشتهٔ کوکی را چسبانده، فقط مقدار را بردار
                        int eq = v.lastIndexOf('=');
                        if (eq >= 0 && v.length() - eq - 1 > 10) v = v.substring(eq + 1);
                        if (v.length() < 10) {
                            Toast.makeText(act, "کوکی معتبر نیست", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        try {
                            CookieManager cmm = CookieManager.getInstance();
                            cmm.setCookie(BASE, "ATERNOS_SESSION=" + v + "; path=/");
                            cmm.flush();
                        } catch (Throwable ignored) {}
                        log("✅ کوکی نشست اعمال شد — اتصال…");
                        closeLogin();
                        loadEngine();
                    }
                });
                adv.addView(bApply, new LinearLayout.LayoutParams(-2, UiKit.dp(act, 40)));
                box.addView(adv, new LinearLayout.LayoutParams(-1, -2));

                box.addView(wv, new LinearLayout.LayoutParams(-1, 0, 1f));

                FrameLayout overlay = new FrameLayout(act);
                overlay.setBackgroundColor(0xFF0B0F16);
                overlay.addView(box, new FrameLayout.LayoutParams(-1, -1));
                overlay.setFocusableInTouchMode(true);
                overlay.setOnKeyListener(backClose);

                ViewGroup content = (ViewGroup) act.findViewById(android.R.id.content);
                content.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
                loginOverlay = overlay;
                loginWeb = wv;

                // پنجرهٔ اکتیویتی با باز شدن کیبورد کوچک می‌شود تا فیلد ورودی دیده شود
                try {
                    act.getWindow().setSoftInputMode(
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
                                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                } catch (Throwable ignored) {}

                log("🔑 صفحهٔ ورود باز شد — کیبورد داخل خودش کار می‌کند");
            } catch (Throwable t) {
                log("❌ بازکردن ورود: " + t);
            }
        }});
    }

    /** بستن صفحهٔ ورود تمام‌صفحه */
    private void closeLogin() {
        uiOnUiThread(new Runnable() { @Override public void run() {
            try {
                if (loginOverlay != null) {
                    ViewGroup par = (ViewGroup) loginOverlay.getParent();
                    if (par != null) par.removeView(loginOverlay);
                    loginOverlay = null;
                }
                if (loginWeb != null) {
                    loginWeb.loadUrl("about:blank");
                    loginWeb.destroy();
                    loginWeb = null;
                }
            } catch (Throwable ignored) {}
            try {
                act.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
                                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
            } catch (Throwable ignored) {}
        }});
    }

    private boolean sessionAppeared() {
        // فقط وقتی «مقدار جدید» ظاهر شود لاگین حساب می‌شود — کوکی مرده هرگز
        return hasSession();
    }

    /** پاک‌کردن کوکی‌های نشست Aternos (نشست مرده را کامل برمی‌دارد) */
    private void clearSessionCookies() {
        try {
            CookieManager cm = CookieManager.getInstance();
            // هر دو فرم انقضا + حذف کوکی‌های نشست — بعضی نسخه‌های اندروید Max-Age را نادیده می‌گیرند
            cm.setCookie(BASE, "ATERNOS_SESSION=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/");
            cm.setCookie(BASE, "ATERNOS_SERVER=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/");
            try { cm.removeSessionCookie(); } catch (Throwable ignored) {}
            cm.flush();
        } catch (Throwable ignored) {}
    }

    /** خروج/قطع اتصال */
    public void logout() {
        uiOnUiThread(new Runnable() { @Override public void run() {
            ready = false;
            sessionKnownDead = false;
            deadSessionValue = null;
            clearSessionCookies();
            if (engine != null) { try { engine.loadUrl("about:blank"); } catch (Throwable ignored) {} }
            log("🔒 از Aternos خارج شدی");
            cb.atGone("logout");
        }});
    }

    // ------------------------------------------------------------------
    // موتور مخفی
    // ------------------------------------------------------------------

    private void loadEngine() {
        uiOnUiThread(new Runnable() { @Override public void run() {
            try {
                if (engine == null) {
                    engine = new WebView(act);
                    WebSettings ws = engine.getSettings();
                    ws.setJavaScriptEnabled(true);
                    ws.setDomStorageEnabled(true);
                    ws.setUserAgentString(ws.getUserAgentString().replace("; wv", "").replace("Version/4.0 ", ""));
                    engine.setBackgroundColor(Color.TRANSPARENT);
                    engine.setFocusable(false);
                    engine.setFocusableInTouchMode(false);
                    engine.addJavascriptInterface(new Bridge(), "AttaBridge");
                    engine.setWebViewClient(new WebViewClient() {
                        @Override public void onPageStarted(WebView v, String url, android.graphics.Bitmap favicon) {
                            log("⟳ باز شدن: " + (url == null ? "?" : url.replace("https://aternos.org", "")));
                        }
                        @Override public void onReceivedError(WebView v, android.webkit.WebResourceRequest req, android.webkit.WebResourceError err) {
                            try {
                                if (req.isForMainFrame()) log("❌ خطای بارگذاری صفحه (کد " + err.getErrorCode() + ") — اینترنت/کلودفلر را چک کن");
                            } catch (Throwable ignored) {}
                        }
                        @Override public void onReceivedHttpError(WebView v, android.webkit.WebResourceRequest req, android.webkit.WebResourceResponse rsp) {
                            try {
                                if (req.isForMainFrame()) log("⚠ Aternos پاسخ HTTP " + rsp.getStatusCode() + " داد");
                            } catch (Throwable ignored) {}
                        }
                        @Override public void onPageFinished(WebView v, String url) {
                            pageLoading = false;
                            lastPageLoad = System.currentTimeMillis();
                            if (url == null) return;
                            if (url.contains("/go/") || url.equals(BASE + "/") || url.equals(BASE)) {
                                // ریدایرکت به صفحهٔ ورود = نشست منقضی شده
                                if (ready || sessionValue() != null) {
                                    ready = false;
                                    sessionKnownDead = true;
                                    String dv = sessionValue();
                                    if (dv != null && dv.length() > 0) deadSessionValue = dv;
                                    clearSessionCookies();
                                    log("⚠ نشست Aternos منقضی شده — کوکی پاک شد و صفحهٔ ورود باز می‌شود");
                                    cb.atGone("expired");
                                    showLogin();
                                }
                                return;
                            }
                            if (url.contains("/servers")) {
                                v.evaluateJavascript(JS_SERVERS, null);
                            } else if (url.contains("/server") || url.contains("/panel")) {
                                v.evaluateJavascript(JS_READY, null);
                            }
                        }
                    });
                    // چسباندن ۱×۱ پیکسلی به ریشهٔ اکتیویتی تا WebView واقعاً اجرا شود
                    ViewGroup content = (ViewGroup) act.findViewById(android.R.id.content);
                    android.widget.FrameLayout.LayoutParams lp =
                            new android.widget.FrameLayout.LayoutParams(1, 1);
                    lp.gravity = Gravity.TOP | Gravity.START;
                    content.addView(engine, lp);
                }
                if (serverId != null && serverId.length() > 0) {
                    applyServerCookie(serverId);
                    pageLoading = true;
                    engine.loadUrl(URL_SERVER);
                } else {
                    pageLoading = true;
                    engine.loadUrl(URL_SERVERS);
                }
                log("⟳ اتصال به پنل Aternos…");
            } catch (Throwable t) {
                log("❌ موتور: " + t);
            }
        }});
    }

    private void applyServerCookie(String id) {
        try {
            CookieManager cm = CookieManager.getInstance();
            cm.setCookie(BASE, "ATERNOS_SERVER=" + id + "; path=/");
            cm.flush();
        } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------------
    // عملیات‌ها — دقیقاً API خود پنل Aternos
    // ------------------------------------------------------------------

    /** start | stop | restart | confirm | accept-eula */
    public void doAction(final String act_) {
        uiOnUiThread(new Runnable() { @Override public void run() {
            if (engine == null || !hasSession()) {
                log("❌ اول وارد Aternos شو (دکمهٔ ورود داخل اپ)");
                return;
            }
            String u = engine.getUrl() == null ? "" : engine.getUrl();
            boolean onServerPage = u.contains("/server") && !u.contains("/servers");
            if (!pageOk() || !onServerPage) {
                reloadServerPage();
                log("⏳ صفحهٔ سرور در حال آماده‌سازی است — وقتی «✅ متصل» را دیدی دوباره بزن");
                return;
            }
            String js = JS_ACTION.replace("%ACT%", act_);
            engine.evaluateJavascript(js, null);
            log("📤 فرمان «" + act_ + "» به پنل Aternos فرستاده شد…");
        }});
    }

    /** اتصال به کنسول زندهٔ Aternos (وبسوکت hermes خود پنل) */
    public void consoleConnect() {
        uiOnUiThread(new Runnable() { @Override public void run() {
            if (engine == null || !hasSession()) {
                log("❌ اول وارد Aternos شو (دکمهٔ ورود داخل اپ)");
                return;
            }
            String u = engine.getUrl() == null ? "" : engine.getUrl();
            if (!u.contains("aternos.org") || !pageOk()) {
                reloadServerPage();
                log("⏳ صفحهٔ پنل در حال آماده‌سازی است — وقتی «✅ متصل» را دیدی دوباره «اتصال کنسول» را بزن");
                return;
            }
            engine.evaluateJavascript(JS_HERMES, null);
            log("🟢 در حال اتصال به کنسول زندهٔ Aternos…");
        }});
    }

    /** ارسال دستور به کنسول سرور Aternos (بدون / اول) */
    public void sendConsoleCommand(String cmd) {
        final String safe = cmd == null ? "" : cmd.trim();
        if (safe.length() == 0) return;
        uiOnUiThread(new Runnable() { @Override public void run() {
            if (engine == null) {
                cb.atConsole("⚠ اول «اتصال کنسول» را بزن");
                return;
            }
            String esc = safe.replace("\\", "\\\\").replace("'", "\\'")
                    .replace("\"", "\\\"").replace("\r", " ").replace("\n", " ");
            engine.evaluateJavascript(JS_WSCMD.replace("%CMD%", esc), null);
        }});
    }

    /** وضعیت لحظه‌ای (سبک) — هر چند فراخوانی یک‌بار صفحه تازه می‌شود */
    public void poll() {
        uiOnUiThread(new Runnable() { @Override public void run() {
            if (engine == null || !ready) return;
            pollTick++;
            if (pollTick % 5 == 0 || !pageOk()) {
                reloadServerPage();
            } else {
                engine.evaluateJavascript(JS_POLL, null);
            }
        }});
    }

    private boolean pageOk() {
        return engine != null && engine.getUrl() != null
                && engine.getUrl().contains("aternos.org")
                && System.currentTimeMillis() - lastPageLoad < 120000L;
    }

    public void reloadServerPage() {
        uiOnUiThread(new Runnable() { @Override public void run() {
            if (engine == null) return;
            pageLoading = true;
            engine.loadUrl(serverId.length() > 0 ? URL_SERVER : URL_SERVERS);
        }});
    }

    public void destroy() {
        try {
            if (engine != null) {
                engine.loadUrl("about:blank");
                ((ViewGroup) engine.getParent()).removeView(engine);
                engine.destroy();
                engine = null;
            }
        } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------------
    // پل جاوااسکریپت ← جاوا
    // ------------------------------------------------------------------

    public final class Bridge {
        @JavascriptInterface
        public void post(final String type, final String data) {
            ui.post(new Runnable() { @Override public void run() { onBridge(type, data); }});
        }
    }

    private void onBridge(String type, String data) {
        try {
            if (!"status".equals(type)) {
                log("📩 " + type + ((data == null || data.length() == 0) ? "" : ": "
                        + (data.length() > 90 ? data.substring(0, 90) + "…" : data)));
            }
            if ("servers".equals(type)) {
            Object o = MiniJson.parse(data);
            List<Object> list = o instanceof List ? (List<Object>) o : null;
            if (list == null || list.isEmpty()) {
                log("⚠ هیچ سروری در فهرست پیدا نشد — در سایت Aternos یک سرور بساز");
                return;
            }
            Set<String> ids = new LinkedHashSet<String>();
            List<String[]> sv = new ArrayList<String[]>();
            for (Object e : list) {
                Map<String, Object> m = MiniJson.object(e);
                if (m == null) continue;
                String id = MiniJson.str(m, "id", "");
                String nm = MiniJson.str(m, "name", "");
                if (id.length() == 0 || !ids.add(id)) continue;
                sv.add(new String[]{id, nm});
            }
            if (sv.isEmpty()) {
                log("⚠ فهرست سرورها خالی بود");
                return;
            }
            String saved = prefs.getString("aternos_server_id", "");
            String chosen = null;
            for (String[] s2 : sv) if (s2[0].equals(saved)) chosen = s2[0];
            if (chosen == null) chosen = sv.get(0)[0];
            serverId = chosen;
            prefs.setString("aternos_server_id", chosen);
            applyServerCookie(chosen);
            for (String[] s2 : sv) {
                lastServerName = s2[1];
                log("🖥 سرور: " + (s2[1].length() > 0 ? s2[1] : ("#" + s2[0])) + (s2[0].equals(chosen) ? "  ← انتخاب شد" : ""));
            }
            if (sv.size() > 1) log("ℹ چند سرور داری؛ برای عوض‌کردن، در سایت Aternos سرور دلخواه را باز کن و دوباره «اتصال» بزن");
            loadEngine();
        } else if ("status".equals(type)) {
            // قالب: lastStatus@@DOM@@XHR — سه منبع وضعیت
            String lsPart = data, domPart = "", xhrPart = "";
            int at = data.indexOf("@@");
            if (at >= 0) {
                lsPart = data.substring(0, at);
                String rest = data.substring(at + 2);
                int at2 = rest.indexOf("@@");
                if (at2 >= 0) {
                    domPart = rest.substring(0, at2);
                    xhrPart = rest.substring(at2 + 2);
                } else {
                    domPart = rest;
                }
            }
            Map<String, Object> m = MiniJson.object(MiniJson.parse(lsPart));
            if ((m == null || m.isEmpty()) && xhrPart.trim().length() > 0) {
                Map<String, Object> xm = MiniJson.object(MiniJson.parse(xhrPart));
                if (xm != null && !xm.isEmpty()) {
                    m = xm;
                    domPart = domPart.length() > 0 ? domPart + " • از جریان خود پنل" : "از جریان خود پنل";
                }
            }
            boolean hasData = (m != null && !m.isEmpty()) || domPart.trim().length() > 0;
            if (hasData) {
                emptyPolls = 0;
                cb.atStatus(m, domPart);
            } else {
                emptyPolls++;
                log("⏳ وضعیت هنوز از پنل نرسیده (" + emptyPolls + ")…");
                if (emptyPolls == 3) {
                    log("🔄 تازه‌سازی صفحهٔ سرور…");
                    reloadServerPage();
                }
            }
        } else if ("diag".equals(type)) {
            log("🔎 " + data);
        } else if ("ready".equals(type)) {
            String sid = "";
            int bar = data.indexOf('|');
            String head = data;
            if (bar >= 0) {
                head = data.substring(0, bar);
                sid = data.substring(bar + 1).trim();
            }
            boolean tokenOk = head.contains("token-ok");
            if (sid.length() > 0 && serverId.length() == 0) {
                serverId = sid;
                prefs.setString("aternos_server_id", sid);
                applyServerCookie(sid);
                log("🖥 شناسهٔ سرور خوانده شد: " + sid);
            }
            if (!ready) {
                ready = true;
                log(tokenOk
                        ? "✅ متصل به پنل Aternos — کنترل سرور از همین‌جا انجام می‌شود"
                        : "✅ متصل شد (بدون توکن؛ عملیات‌ها با کلیک روی دکمه‌های خود پنل انجام می‌شوند)");
                cb.atReady(serverId);
            }
            pollTick = 0;
        } else if ("action".equals(type)) {
            log("📥 " + data);
            if (data.contains("eula")) {
                log("ℹ پذیرش EULA و تلاش دوباره برای استارت…");
                doAction("accept-eula");
                ui.postDelayed(new Runnable() { @Override public void run() { doAction("start"); }}, 1200);
            }
        } else if ("wss".equals(type)) {
            handleWss(data);
        } else if ("err".equals(type)) {
            log("⚠ " + data);
        }
        } catch (Throwable t) {
            log("❌ پردازش پیام پل (" + type + "): " + t);
        }
    }

    private void log(final String s) {
        ui.post(new Runnable() { @Override public void run() { cb.atLog(s); }});
    }

    private void uiOnUiThread(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else ui.post(r);
    }

    // ------------------------------------------------------------------
    // اسکریپت‌های تزریقی
    // ------------------------------------------------------------------

    /** فهرست سرورها از صفحهٔ /servers/ — با تلاش مجدد و گزارش تشخیصی */
    private static final String JS_SERVERS =
            "(function(){try{" +
            "function grab(){" +
            "var out=[],seen={};var els=document.querySelectorAll('[data-id]');" +
            "for(var i=0;i<els.length;i++){var el=els[i];var id=el.getAttribute('data-id');" +
            "if(!id||seen[id])continue;seen[id]=1;" +
            "var n=el.querySelector('.server-name, .server-description, .server-body, .server-title');" +
            "var nm=n?String(n.textContent).replace(/\\s+/g,' ').trim().slice(0,60):'';" +
            "out.push({id:id,name:nm});}" +
            "if(out.length){AttaBridge.post('servers',JSON.stringify(out));return true;}" +
            "var ti=(document.title||'');" +
            "if(ti.indexOf('Just a moment')>=0){AttaBridge.post('diag','صفحهٔ محافظ کلودفلر — چند ثانیه دیگر خودش رد می‌شود');return false;}" +
            "if(location.pathname==='/server/'||location.pathname==='/server'){AttaBridge.post('diag','مستقیم روی صفحهٔ سرور افتادیم (اکانت تک‌سرور)');return true;}" +
            "var bt=(document.body?String(document.body.innerText):'').replace(/\\s+/g,' ').trim().slice(0,200);" +
            "AttaBridge.post('diag','سروری در صفحهٔ فهرست پیدا نشد | عنوان: '+ti.slice(0,50)+' | متن: '+bt);return false;" +
            "}" +
            "if(grab())return;" +
            "var n=0;var iv=setInterval(function(){n++;if(grab()||n>6){clearInterval(iv);}},1200);" +
            "}catch(e){AttaBridge.post('err','servers:'+e)}})();";

    /** پس از بارگذاری صفحهٔ سرور: توکن + وضعیت سه‌منبعی (lastStatus + DOM + جریان XHR خود پنل) */
    private static final String JS_READY =
            "(function(){try{ var ti=(document.title||''); if(ti.indexOf('Just a moment')>=0){AttaBridge.post('diag','محافظ کلودفلر — کمی صبر کن؛ خودش رد می‌شود');return;} if(!window.ATTA_HOOKE" +
            "D){ window.ATTA_HOOKED=1;window.ATTA_STATUS=''; try{ var of=window.fetch; if(of){ window.fetch=function(){ var p=of.apply(this,arguments); try{ p.then(function(r){ r.clone().text()" +
            ".then(function(t){ try{if(t&&t.length<4000&&t.indexOf('\"status\"')>=0){window.ATTA_STATUS=t;}}catch(e){} }); }); }catch(e){} return p; }; } }catch(e){} try{ var oo=XMLHttpRequest." +
            "prototype.open; var os=XMLHttpRequest.prototype.send; XMLHttpRequest.prototype.open=function(m,u){this._atta_u=u;return oo.apply(this,arguments);}; XMLHttpRequest.prototype.send=fu" +
            "nction(){ var x=this; try{ x.addEventListener('load',function(){ try{if(x.responseText&&x.responseText.length<4000&&x.responseText.indexOf('\"status\"')>=0){window.ATTA_STATUS=x.re" +
            "sponseText;}}catch(e){} }); }catch(e){} return os.apply(this,arguments); }; }catch(e){} } var t=window.AJAX_TOKEN||''; if(!t){var hs=document.head.innerHTML;var m=hs.match(/\\(\\(" +
            "\\)[\\s\\S]*?\\)\\)\\(\\);/);if(m){try{(0,eval)(m[0]);}catch(e){}t=window.AJAX_TOKEN||'';}} window.ATTA_TOKEN=t; var sid='';var cm=document.cookie.match(/(?:^|;\\s*)ATERNOS_SERVER=" +
            "([^;]+)/);if(cm){sid=cm[1];} var ls='{}';try{ls=JSON.stringify(window.lastStatus||{});}catch(e){} var st='';try{var el=document.querySelector('.status, #status, .server-status, [cl" +
            "ass*=\"status\"]');if(el){st=String(el.className+' | '+el.textContent).replace(/\\s+/g,' ').trim().slice(0,140);}}catch(e){} AttaBridge.post('status',ls+'@@'+st+'@@'+(window.ATTA_S" +
            "TATUS||'')); AttaBridge.post('ready',(t?'token-ok':'no-token')+'|'+(sid||'')); }catch(e){AttaBridge.post('err','ready:'+e)}})();";

    /** خواندن سبک وضعیت: lastStatus + DOM + جریان شنودشدهٔ XHR پنل */
    private static final String JS_POLL =
            "(function(){try{ var ls='{}';try{ls=JSON.stringify(window.lastStatus||{});}catch(e){} var st='';try{var el=document.querySelector('.status, #status, .server-status, [class*=\"statu" +
            "s\"]');if(el){st=String(el.className+' | '+el.textContent).replace(/\\s+/g,' ').trim().slice(0,140);}}catch(e){} AttaBridge.post('status',ls+'@@'+st+'@@'+(window.ATTA_STATUS||''));" +
            " }catch(e){AttaBridge.post('err','poll:'+e)}})();";

    /** اجرای عملیات روی سرور — همان API خود پنل؛ در نبود توکن، کلیک روی دکمهٔ واقعی */
    private static final String JS_ACTION =
            "(function(){try{" +
            "var act='%ACT%';" +
            "var t=window.ATTA_TOKEN||window.AJAX_TOKEN||'';" +
            "if(!t){var b=document.getElementById(act);" +
            "if(b){b.click();AttaBridge.post('action',act+' → با کلیک روی دکمهٔ خود پنل اجرا شد');return;}" +
            "AttaBridge.post('action','❌ '+act+': نه توکن بود و نه دکمه (صفحه آماده نیست — دوباره بزن)');return;}" +
            "function rnd(){var s='';var a='abcdefghijklmnopqrstuvwxyz0123456789';" +
            "for(var i=0;i<11;i++)s+=a.charAt(Math.floor(Math.random()*a.length));return s+'00000';}" +
            "var k=rnd(),v=rnd();" +
            "document.cookie='ATERNOS_SEC_'+k+'='+v+';path=/;domain=.aternos.org';" +
            "var url='/ajax/server/'+act+'?headstart=0&access-credits=0'" +
            "+'&TOKEN='+encodeURIComponent(t)+'&SEC='+encodeURIComponent(k+':'+v);" +
            "fetch(url,{headers:{'X-Requested-With':'XMLHttpRequest'},credentials:'same-origin'})" +
            ".then(function(r){return r.text()})" +
            ".then(function(x){AttaBridge.post('action',act+' → '+x)})" +
            ".catch(function(e){AttaBridge.post('action',act+' ❌ '+e)});" +
            "}catch(e){AttaBridge.post('err','act:'+e)}})();";

    /** پردازش پیام‌های وبسوکت hermes (کنسول/وضعیت/TPS/RAM پنل Aternos) */
    private void handleWss(String data) {
        if (data == null) return;
        if ("open".equals(data)) { cb.atConsole("🟢 کنسول متصل شد — استریم خطوط فعال شد"); return; }
        if ("closed".equals(data)) { cb.atConsole("🔴 اتصال کنسول قطع شد — دوباره «اتصال کنسول» را بزن"); return; }
        if ("error".equals(data)) { cb.atConsole("⚠ خطای وبسوکت کنسول — یک بار دیگر «اتصال کنسول» را بزن"); return; }
        if ("no-socket".equals(data)) { cb.atConsole("⚠ اتصال کنسول برقرار نیست — اول «اتصال کنسول» را بزن"); return; }
        if ("sent".equals(data)) return; // خود دستور در ورودی کاربر نشان داده می‌شود
        Object o = MiniJson.parse(data);
        Map<String, Object> m = MiniJson.object(o);
        if (m == null) { cb.atConsole("ℹ " + trunc(data)); return; }
        String t = MiniJson.str(m, "type", "");
        if ("line".equals(t)) {
            cb.atConsole(MiniJson.str(m, "data", ""));
        } else if ("status".equals(t)) {
            // message یک رشتهٔ JSON است — دوباره parse می‌شود
            Map<String, Object> st = MiniJson.object(MiniJson.parse(MiniJson.str(m, "message", "")));
            if (st != null && !st.isEmpty()) cb.atStatus(st, "از استریم زندهٔ پنل");
        } else if ("heap".equals(t)) {
            Map<String, Object> d = MiniJson.object(m.get("data"));
            if (d != null) cb.atConsole("🧠 رم سرور: " + MiniJson.num(d, "usage", 0) + " MB");
        } else if ("tick".equals(t)) {
            Map<String, Object> d = MiniJson.object(m.get("data"));
            if (d != null) {
                double avg = MiniJson.num(d, "averageTickTime", 0);
                double tps = avg > 0 ? Math.min(20.0, 1000.0 / avg) : 0;
                cb.atConsole("⏱ TPS: " + String.format(java.util.Locale.US, "%.1f", tps));
            }
        } else if ("connected".equals(t)) {
            cb.atConsole("✔ استریم کنسول فعال شد — دستور بفرست (مثلاً list)");
        } else {
            cb.atConsole("ℹ " + trunc(data));
        }
    }

    private String trunc(String s) {
        if (s == null) return "";
        return s.length() > 160 ? s.substring(0, 160) + "…" : s;
    }

    /** اتصال وبسوکت hermes — کنسول زنده و ارسال دستور خود پنل Aternos */
    private static final String JS_HERMES =
            "(function(){ try{ if(window.ATTA_WS){try{window.ATTA_WS.close();}catch(e){}} var ws=new WebSocket('wss://aternos.org/hermes/'); window.ATTA_WS=ws; var ka=null; ws.onopen=function()" +
            "{ AttaBridge.post('wss','open'); try{ws.send(JSON.stringify({stream:'console',type:'start'}));}catch(e){} ka=setInterval(function(){try{ws.send('{\"type\":\"\\u2764\"}');}catch(e){" +
            "}},45000); }; ws.onmessage=function(ev){ try{AttaBridge.post('wss',String(ev.data).slice(0,1500));}catch(e){} }; ws.onclose=function(){ if(ka){clearInterval(ka);} AttaBridge.post('" +
            "wss','closed'); window.ATTA_WS=null; }; ws.onerror=function(){AttaBridge.post('wss','error');}; }catch(e){AttaBridge.post('err','hermes:'+e);} })();";

    /** ارسال دستور از طریق وبسوکت باز‌شده */
    private static final String JS_WSCMD =
            "(function(){ try{ var ws=window.ATTA_WS; if(!ws||ws.readyState!==1){AttaBridge.post('wss','no-socket');return;} ws.send(JSON.stringify({stream:'console',type:'command',data:'%CMD%'" +
            "})); AttaBridge.post('wss','sent'); }catch(e){AttaBridge.post('err','cmd:'+e);} })();";
}