package com.atta.mcpanel.aternos;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
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
    private AlertDialog loginDlg;    // دیالوگ ورود (فقط وقتی نشست نیست)
    private boolean ready = false;
    private String serverId = "";
    private String lastServerName = "";
    private int pollTick = 0;
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

    /** آیا کوکی نشست Aternos موجود است؟ */
    public boolean hasSession() {
        try {
            String c = CookieManager.getInstance().getCookie(BASE);
            return c != null && c.matches("(?s).*ATERNOS_SESSION=[^;\\s]+.*");
        } catch (Throwable t) {
            return false;
        }
    }

    public boolean isReady() { return ready; }

    public String serverId() { return serverId; }
    public String serverName() { return lastServerName; }

    /** شروع: اگر نشست نیست دیالوگ ورود، وگرنه بارگذاری مستقیم پنل */
    public void begin() {
        uiOnUiThread(new Runnable() { @Override public void run() {
            if (!hasSession()) { showLogin(); return; }
            loadEngine();
        }});
    }

    /** ورود داخل اپ — WebView تمام‌صفحه با برداشت خودکار کوکی پس از لاگین */
    public void showLogin() {
        uiOnUiThread(new Runnable() { @Override public void run() {
            try {
                CookieManager cm = CookieManager.getInstance();
                cm.setAcceptCookie(true);
                final AlertDialog dlg = new AlertDialog.Builder(act).create();
                loginDlg = dlg;
                dlg.setTitle("ورود به Aternos (حساب خودت)");

                WebView wv = new WebView(act);
                WebSettings ws = wv.getSettings();
                ws.setJavaScriptEnabled(true);
                ws.setDomStorageEnabled(true);
                ws.setDatabaseEnabled(true);
                ws.setUserAgentString(ws.getUserAgentString().replace("; wv", ""));
                ws.setLoadWithOverviewMode(true);
                ws.setUseWideViewPort(true);
                android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true);

                wv.setWebViewClient(new WebViewClient() {
                    @Override public void onPageFinished(WebView v, String url) {
                        if (sessionAppeared()) {
                            try { CookieManager.getInstance().flush(); } catch (Throwable ignored) {}
                            log("✅ ورود انجام شد — نشست ذخیره شد");
                            dlg.dismiss();
                            loginDlg = null;
                            loadEngine();
                        }
                    }
                });
                wv.loadUrl(URL_LOGIN);

                LinearLayout box = UiKit.vcol(act, 8);
                int wvh = Math.min(UiKit.dp(act, 560),
                        Math.round(act.getResources().getDisplayMetrics().heightPixels * 0.7f));
                box.addView(wv, new LinearLayout.LayoutParams(-1, wvh));
                TextView bDone = UiKit.chip(act, "✅ وارد شدم — ادامه", UiKit.KIND_ACCENT, new Runnable() {
                    @Override public void run() {
                        if (sessionAppeared()) {
                            try { CookieManager.getInstance().flush(); } catch (Throwable ignored) {}
                            log("✅ نشست Aternos ذخیره شد");
                            dlg.dismiss();
                            loginDlg = null;
                            loadEngine();
                        } else {
                            Toast.makeText(act, "هنوز کوکی نشست نیامده — اول در صفحهٔ بالا وارد شو", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                box.addView(bDone, UiKit.wrapParams(bDone, 4, 46));

                TextView bClose = UiKit.chip(act, "بستن", new Runnable() {
                    @Override public void run() { dlg.dismiss(); loginDlg = null; }
                });
                box.addView(bClose, UiKit.wrapParams(bClose, 4, 40));
                dlg.setView(box);
                dlg.show();
            } catch (Throwable t) {
                log("❌ بازکردن ورود: " + t);
            }
        }});
    }

    private boolean sessionAppeared() {
        try {
            String c = CookieManager.getInstance().getCookie(BASE);
            return c != null && c.matches("(?s).*ATERNOS_SESSION=[^;\\s]+.*");
        } catch (Throwable t) {
            return false;
        }
    }

    /** خروج/قطع اتصال */
    public void logout() {
        uiOnUiThread(new Runnable() { @Override public void run() {
            ready = false;
            try {
                CookieManager cm = CookieManager.getInstance();
                cm.setCookie(BASE, "ATERNOS_SESSION=; Max-Age=0; path=/");
                cm.setCookie(BASE, "ATERNOS_SERVER=; Max-Age=0; path=/");
                cm.flush();
            } catch (Throwable ignored) {}
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
                    ws.setUserAgentString(ws.getUserAgentString().replace("; wv", ""));
                    engine.setBackgroundColor(Color.TRANSPARENT);
                    engine.addJavascriptInterface(new Bridge(), "AttaBridge");
                    engine.setWebViewClient(new WebViewClient() {
                        @Override public void onPageFinished(WebView v, String url) {
                            pageLoading = false;
                            lastPageLoad = System.currentTimeMillis();
                            if (url == null) return;
                            if (url.contains("/go/") || url.equals(BASE + "/") || url.equals(BASE)) {
                                // ریدایرکت به صفحهٔ ورود = نشست منقضی شده
                                if (ready || hasSession()) {
                                    ready = false;
                                    log("⚠ نشست Aternos منقضی شده — دوباره «ورود / اتصال» را بزن");
                                    cb.atGone("expired");
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

    private final class Bridge {
        @JavascriptInterface
        public void post(final String type, final String data) {
            ui.post(new Runnable() { @Override public void run() { onBridge(type, data); }});
        }
    }

    private void onBridge(String type, String data) {
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
            for (String[] s : sv) if (s[0].equals(saved)) chosen = s[0];
            if (chosen == null) chosen = sv.get(0)[0];
            serverId = chosen;
            prefs.setString("aternos_server_id", chosen);
            applyServerCookie(chosen);
            for (String[] s : sv) {
                lastServerName = s[1];
                log("🖥 سرور: " + (s[1].length() > 0 ? s[1] : ("#" + s[0])) + (s[0].equals(chosen) ? "  ← انتخاب شد" : ""));
            }
            if (sv.size() > 1) log("ℹ چند سرور داری؛ برای عوض‌کردن، در سایت Aternos سرور دلخواه را باز کن و دوباره «اتصال» بزن");
            loadEngine();
        } else if ("status".equals(type)) {
            String lsPart = data;
            String domPart = "";
            int at = data.indexOf("@@");
            if (at >= 0) {
                lsPart = data.substring(0, at);
                domPart = data.substring(at + 2);
            }
            Object o = MiniJson.parse(lsPart);
            Map<String, Object> m = MiniJson.object(o);
            if (m != null) cb.atStatus(m, domPart);
        } else if ("ready".equals(type)) {
            boolean tokenOk = data.contains("token-ok");
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
            // اگر استارت به EULA خورد، قبول کن و دوباره استارت بزن
            if (data.contains("eula")) {
                log("ℹ پذیرش EULA و تلاش دوباره برای استارت…");
                doAction("accept-eula");
                ui.postDelayed(new Runnable() { @Override public void run() { doAction("start"); }}, 1200);
            }
        } else if ("err".equals(type)) {
            log("⚠ " + data);
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

    /** فهرست سرورها از صفحهٔ /servers/ */
    private static final String JS_SERVERS =
            "(function(){try{" +
            "var out=[],seen={};var els=document.querySelectorAll('[data-id]');" +
            "for(var i=0;i<els.length;i++){var el=els[i];var id=el.getAttribute('data-id');" +
            "if(!id||seen[id])continue;seen[id]=1;" +
            "var n=el.querySelector('.server-name, .server-description, .server-body, .server-title');" +
            "var nm=n?String(n.textContent).replace(/\\s+/g,' ').trim().slice(0,60):'';" +
            "out.push({id:id,name:nm});}" +
            "AttaBridge.post('servers',JSON.stringify(out));" +
            "}catch(e){AttaBridge.post('err','servers:'+e)}})();";

    /** پس از بارگذاری صفحهٔ سرور: خواندن توکن + lastStatus + اعلام آمادگی */
    private static final String JS_READY =
            "(function(){try{" +
            "var t=window.AJAX_TOKEN||'';" +
            "if(!t){var hs=document.head.innerHTML;var m=hs.match(/\\(\\(\\)[\\s\\S]*?\\)\\)\\(\\);/);" +
            "if(m){try{(0,eval)(m[0]);}catch(e){}t=window.AJAX_TOKEN||'';}}" +
            "window.ATTA_TOKEN=t;" +
            "var ls='{}';try{ls=JSON.stringify(window.lastStatus||{})}catch(e){}" +
            "AttaBridge.post('status',ls+'@@');" +
            "AttaBridge.post('ready',t?'token-ok':'no-token');" +
            "}catch(e){AttaBridge.post('err','ready:'+e)}})();";

    /** خواندن سبک وضعیت بدون بارگذاری دوبارهٔ صفحه */
    private static final String JS_POLL =
            "(function(){try{" +
            "var ls='{}';try{ls=JSON.stringify(window.lastStatus||{})}catch(e){}" +
            "var el=document.querySelector('.status');" +
            "var dom=el?(el.className+'|'+String(el.textContent).trim()):'';" +
            "AttaBridge.post('status',ls+'@@'+dom);" +
            "}catch(e){AttaBridge.post('err','poll:'+e)}})();";

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
}
