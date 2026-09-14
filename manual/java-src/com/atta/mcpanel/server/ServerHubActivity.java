package com.atta.mcpanel.server;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.atta.mcpanel.aternos.AternosPanel;
import com.atta.mcpanel.aternos.MiniJson;
import com.atta.mcpanel.core.AppPrefs;
import com.atta.mcpanel.core.Palette;
import com.atta.mcpanel.overlay.UiKit;
import com.atta.mcpanel.rcon.RconConnection;
import com.atta.mcpanel.rcon.RconListener;
import com.atta.mcpanel.ssh.VpsRemote;

/**
 * AttaPanel v2 — مرکز سرور از راه دور (VPS / Aternos / اتصال پالس).
 * همهٔ اجزای این صفحه با UiKit ساخته می‌شوند (همان اجزایی که در صفحهٔ اصلی اپ
 * روی دستگاه تست شده‌اند) تا هیچ صفحه‌ای «خالی» نماند؛ هر خطایی هم در همان
 * صفحه به‌صورت متن قرمز نشان داده می‌شود.
 */
public class ServerHubActivity extends Activity {

    private static final String TAG = "hub-b2";

    private AppPrefs prefs;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private LinearLayout page;            // محتوای زیر منو (هر بار نو ساخته می‌شود)
    private TextView diag;                // خط وضعیت/خطا (بالای صفحه)
    private String vpsLog = "";
    private String rconLog = "";
    private boolean busyOp = false;

    // فیلدهای VPS
    private EditText fHost, fPort, fUser, fPass, fMem;
    private TextView vpsLogTv;
    // فیلدهای اتصال پالس
    private EditText pHost, pPort, pPass;
    private TextView pulseLogTv;
    // فیلدهای Aternos
    private AternosPanel aternos;
    private TextView atConsoleTv;
    private TextView atStatusTv, atLogTv;
    private int currentSegment = 0;
    private boolean resumedFlag = false;
    private String atStatusText = "وضعیت: متصل نیست";
    private int atStatusColor = 0xFF9AA7BA;

    private final java.util.concurrent.ExecutorService exec =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new AppPrefs(this);
        try {
            buildUi();
        } catch (Throwable t) {
            // هرگز صفحهٔ خالی نشان نده
            LinearLayout fb = UiKit.vcol(this, 18);
            TextView tv = new TextView(this);
            tv.setText("❌ خطا در باز کردن مرکز سرور:\n" + t.toString());
            tv.setTextColor(0xFFFF6B6B);
            fb.addView(tv);
            setContentView(fb);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumedFlag = true;
        ui.postDelayed(atPoll, 4000);
    }

    @Override
    protected void onPause() {
        resumedFlag = false;
        ui.removeCallbacks(atPoll);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        exec.shutdownNow();
        if (aternos != null) aternos.destroy();
        super.onDestroy();
    }

    /** هر ۶ ثانیه وضعیت Aternos را تازه می‌کند (فقط وقتی بخشش باز است) */
    private final Runnable atPoll = new Runnable() {
        @Override public void run() {
            if (!resumedFlag) return;
            try {
                if (aternos != null && currentSegment == 1 && aternos.isReady()) aternos.poll();
            } catch (Throwable ignored) {}
            ui.postDelayed(this, 6000);
        }
    };

    private void diag(String s) {
        if (diag != null) {
            diag.setText(s);
            diag.setVisibility(View.VISIBLE);
        }
    }

    private void diagError(Throwable t) {
        String m = t == null ? "" : t.toString();
        diag("❌ " + m);
    }

    // =====================================================================
    // ساختار ریشه — دقیقاً الگوی MainActivity (که روی دستگاه دیده می‌شود)
    // =====================================================================

    private void buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0C1220, 0xFF10241D, 0xFF0B0F16});
        sv.setBackground(bg);

        final LinearLayout col = UiKit.vcol(this, 10);
        sv.addView(col);

        TextView title = new TextView(this);
        title.setText("🛰 مرکز سرور — AttaPanel");
        title.setTextSize(20f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Palette.TEXT_MAIN);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(8), 0, dp(2));
        col.addView(title, UiKit.wrapParams(title, 2, 0));

        TextView sub = new TextView(this);
        sub.setText("VPS و Aternos — ساخت، مدیریت و کنسول، همه از داخل همین اپ");
        sub.setTextSize(11.5f);
        sub.setTextColor(Palette.TEXT_SUB);
        sub.setGravity(Gravity.CENTER);
        col.addView(sub, UiKit.wrapParams(sub, 2, 0));

        diag = new TextView(this);
        diag.setTextSize(11f);
        diag.setTextColor(0xFFFFB3A6);
        diag.setVisibility(View.GONE);
        col.addView(diag, UiKit.wrapParams(diag, 2, 0));

        // منوی سه‌بخشی
        final TextView[] seg = new TextView[3];
        final String[] segNames = {"🛰 VPS", "🌐 Aternos", "🔌 اتصال پالس"};
        final LinearLayout segRow = UiKit.hrow(this);
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            seg[i] = UiKit.chip(this, segNames[i], UiKit.KIND_PLAIN, new Runnable() {
                @Override public void run() {
                    selectSegment(idx);
                }
            });
            final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, UiKit.dp(this, 42), 1f);
            lp.setMargins(UiKit.dp(this, 2), 0, UiKit.dp(this, 2), 0);
            segRow.addView(seg[i], lp);
        }
        col.addView(segRow, UiKit.wrapParams(segRow, 2, 0));
        segViews = seg;

        // محتوای بخش (VPS پیش‌فرض)
        page = UiKit.vcol(this, 4);
        col.addView(page, UiKit.wrapParams(page, 2, 0));

        setContentView(sv);
        selectSegment(0);
    }

    private TextView[] segViews;

    private void selectSegment(int idx) {
        currentSegment = idx;
        // استایل منو: بخش فعال برجسته
        for (int i = 0; i < segViews.length; i++) {
            GradientDrawable g = (i == idx)
                    ? UiKit.roundedSolid(0xFF4E8A33, UiKit.dp(this, 999))
                    : UiKit.roundedSolid(0xFF1D2532, UiKit.dp(this, 999));
            segViews[i].setBackground(g);
            segViews[i].setTextColor(i == idx ? Palette.ON_ACCENT : Palette.CHIP_TEXT);
        }
        page.removeAllViews();
        diag.setVisibility(View.GONE);

        // نوار وضعیت بالای صفحه: اگر صفحه ساخته نشود این خط خودش می‌گوید چرا
        final TextView status = new TextView(this);
        status.setTextSize(10.5f);
        status.setTextColor(Palette.TEXT_DIM);
        status.setText("⟳ در حال ساخت بخش…");
        page.addView(status, UiKit.wrapParams(status, 2, 0));

        final String[] names = {"VPS", "Aternos", "اتصال پالس"};
        try {
            if (idx == 0) buildVpsPage();
            else if (idx == 1) buildAternosPage();
            else buildPulsePage();
            int items = Math.max(0, page.getChildCount() - 1);
            status.setText("✔ بخش " + names[Math.max(0, Math.min(idx, names.length - 1))]
                    + " آماده است (" + items + " المان)");
            status.setTextColor(0xFF57C15B);
        } catch (Throwable t) {
            status.setText("❌ خطا در ساخت صفحه:\n" + t.toString());
            status.setTextColor(0xFFFF6B6B);
        }
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** پارامتر ردیف دکمه با ارتفاع واقعی dp (درست‌شده برای تراکم‌های بالا) */
    private LinearLayout.LayoutParams wRow(float weight, int hDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(hDp), weight);
        lp.setMargins(dp(3), 0, dp(3), dp(7));
        return lp;
    }

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }

    private void copy(String s) {
        if (s == null || s.isEmpty()) { toast("چیزی برای کپی نیست"); return; }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("atta", s));
        toast("کپی شد");
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast("مرورگر باز نشد");
        }
    }

    // =====================================================================
    // اجزای کوچک (همه با UiKit — الگوی تست‌شده)
    // =====================================================================

    /** ورودی تک‌خطی */
    private EditText input(String hint, String text, int inputType) {
        EditText et = new EditText(this);
        et.setHint(hint);
        if (text != null) et.setText(text);
        et.setTextSize(14f);
        et.setTextColor(Palette.TEXT_MAIN);
        et.setHintTextColor(Palette.TEXT_DIM);
        et.setSingleLine(true);
        et.setInputType(inputType == 0 ? InputType.TYPE_CLASS_TEXT : inputType);
        GradientDrawable g = UiKit.roundedSolid(Palette.INPUT_BG, dp(12));
        g.setStroke(1, 0x30FFFFFF);
        et.setBackground(g);
        et.setPadding(dp(12), 0, dp(12), 0);
        return et;
    }

    /** کادر لاگ تک‌فام با ارتفاع ثابت */
    private TextView logBox() {
        TextView tv = new TextView(this);
        tv.setTextSize(11f);
        tv.setTextColor(0xFFD6E0EA);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setGravity(Gravity.START | Gravity.TOP);
        GradientDrawable g = UiKit.roundedSolid(0xFF0A0E13, dp(10));
        g.setStroke(1, 0x30FFFFFF);
        tv.setBackground(g);
        tv.setPadding(dp(8), dp(6), dp(8), dp(6));
        return tv;
    }

    private void addLabel(LinearLayout c, String text) {
        TextView tv = UiKit.caption(this, text, true);
        tv.setTextSize(12f);
        c.addView(tv, UiKit.wrapParams(tv, 2, 0));
    }

    private void appendLog(final TextView tv, String line) {
        String cur = tv.getText().toString();
        String next = cur.isEmpty() ? line : cur + "\n" + line;
        if (next.length() > 18000) next = next.substring(next.length() - 18000);
        tv.setText(next);
    }

    private void cardTitle(LinearLayout c, String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(15f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(Palette.TEXT_MAIN);
        c.addView(tv, UiKit.wrapParams(tv, 2, 0));
    }

    // =====================================================================
    // صفحهٔ VPS
    // =====================================================================

    private void buildVpsPage() {
        LinearLayout card = UiKit.vcol(this, 12);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF17202C, 0xFF10151D});
        g.setCornerRadius(dp(16));
        g.setStroke(1, 0x33FFFFFF);
        card.setBackground(g);

        cardTitle(card, "🛰 سرور VPS (اتصال مستقیم SSH)");
        addLabel(card, "VPS باید لینوکس Ubuntu/Debian با کاربر root یا sudo باشد. این اپ از راه دور "
                + "جاوا و سرور Paper+Geyser را نصب می‌کند، روشن/خاموش می‌کند و کنسول/لاگ می‌گیرد.");

        fHost = input("آدرس VPS (IP یا domain)", prefs.getString("vps_host", ""), 0);
        card.addView(fHost, UiKit.wrapParams(fHost, 2, 46));

        LinearLayout row1 = UiKit.hrow(this);
        fPort = input("پورت", prefs.getString("vps_port", "22"), InputType.TYPE_CLASS_NUMBER);
        fUser = input("کاربر", prefs.getString("vps_user", "root"), 0);
        row1.addView(fPort, wRow(0.4f, 46));
        row1.addView(fUser, wRow(0.6f, 46));
        card.addView(row1, UiKit.wrapParams(row1, 2, 0));

        fPass = input("رمز SSH", prefs.getString("vps_pass", ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        card.addView(fPass, UiKit.wrapParams(fPass, 2, 46));
UiKit.chipRow(this, new TextView[]{
                UiKit.chip(this, "💾 ذخیره", new Runnable() {
                    @Override public void run() { saveVps(); }
                }),
                UiKit.chip(this, "🔄 تست اتصال", UiKit.KIND_ACCENT, new Runnable() {
                    @Override public void run() { runOp("test"); }
                })
        }, 2, card);

        
        

        

UiKit.chipRow(this, new TextView[]{
                UiKit.chip(this, "🚀 نصب خودکار سرور", new Runnable() {
                    @Override public void run() { runOp("setup"); }
                }),
                UiKit.chip(this, "▶ استارت", UiKit.KIND_ACCENT, new Runnable() {
                    @Override public void run() { runOp("start"); }
                }),
                UiKit.chip(this, "⏹ توقف", UiKit.KIND_DANGER, new Runnable() {
                    @Override public void run() { runOp("stop"); }
                })
        }, 3, card);

        UiKit.chipRow(this, new TextView[]{
                UiKit.chip(this, "📊 وضعیت", new Runnable() {
                    @Override public void run() { runOp("status"); }
                }),
                UiKit.chip(this, "🔐 فعال‌سازی RCON", new Runnable() {
                    @Override public void run() { runOp("rcon"); }
                }),
                UiKit.chip(this, "📜 لاگ", new Runnable() {
                    @Override public void run() { runOp("log"); }
                })
        }, 3, card);

        addLabel(card, "حافظهٔ جاوا (مثلاً 2G):");
        fMem = input("2G", prefs.getString("vps_mem", "2G"), 0);
        card.addView(fMem, UiKit.wrapParams(fMem, 2, 44));

        addLabel(card, "فرمان دلخواه به کنسول سرور (مثلاً op gaser):");
        final EditText cmd = input("op gaser / list / save-all", "", 0);
        card.addView(cmd, UiKit.wrapParams(cmd, 2, 44));
        

UiKit.chipRow(this, new TextView[]{
                UiKit.chip(this, "📤 ارسال به کنسول", UiKit.KIND_ACCENT, new Runnable() {
                    @Override public void run() {
                        String cc = cmd.getText().toString().trim();
                        if (cc.isEmpty()) { toast("فرمان را بنویس"); return; }
                        runOp("cmd:" + cc);
                    }
                })
        }, 1, card);

        vpsLogTv = logBox();
        card.addView(vpsLogTv, UiKit.wrapParams(vpsLogTv, 2, 230));
        vpsLog = "— اطلاعات VPS —\nVPS را در فرم بالا وارد کن و «تست اتصال» را بزن.";
        vpsLogTv.setText(vpsLog);

        page.addView(card, UiKit.wrapParams(card, 2, 0));
    }

    private void saveVps() {
        prefs.setString("vps_host", fHost.getText().toString().trim());
        prefs.setString("vps_port", fPort.getText().toString().trim());
        prefs.setString("vps_user", fUser.getText().toString().trim());
        prefs.setString("vps_pass", fPass.getText().toString());
        prefs.setString("vps_mem", fMem.getText().toString().trim());
        toast("ذخیره شد");
    }

    private void vpsAppend(final String s) {
        ui.post(new Runnable() {
            @Override public void run() {
                if (vpsLogTv == null) return;
                appendLog(vpsLogTv, s);
            }
        });
    }

    private void runOp(final String op) {
        if (busyOp) { toast("یک عملیات در جریان است…"); return; }
        saveVps();
        final String host = prefs.getString("vps_host", "");
        final String pass = prefs.getString("vps_pass", "");
        if (host.isEmpty() || pass.isEmpty()) {
            vpsAppend("❌ آدرس یا رمز VPS خالی است — اول فرم را پر کن.");
            return;
        }
        int port;
        try { port = Integer.parseInt(prefs.getString("vps_port", "22")); }
        catch (Exception e) { port = 22; }
        final String user = prefs.getString("vps_user", "root");
        final VpsRemote r = new VpsRemote(host, port, user, pass);
        busyOp = true;
        exec.execute(new Runnable() {
            @Override public void run() {
                try {
                    if (op.equals("test")) {
                        vpsAppend("⟳ تست اتصال به " + r.host + " …");
                        String out = r.safeExec("echo CONNECTED; uname -a; command -v java >/dev/null 2>&1 && java -version 2>&1 | head -1 || echo 'java not installed'; command -v screen >/dev/null 2>&1 && echo screen-ok || echo screen-missing", 30000);
                        vpsAppend(out);
                    } else if (op.equals("setup")) {
                        vpsAppend("🚀 نصب خودکار شروع شد (۳ تا ۱۰ دقیقه — لاگ همان‌جا می‌آید)…");
                        String out = r.safeExec(vpsSetupScript(), 600000);
                        vpsAppend(out);
                    } else if (op.equals("start")) {
                        String mem = prefs.getString("vps_mem", "2G");
                        if (!mem.matches("\\d+[GgMm]")) mem = "2G";
                        vpsAppend("▶ استارت سرور… (اولین اجرا چند دقیقه طول می‌کشد)");
                        String out = r.safeExec("cd \"$HOME/mc\" && if screen -ls 2>/dev/null | grep -q '\\bmc\\.'; then echo ALREADY-RUNNING; else screen -dmS mc java -Xms512M -Xmx" + mem + " -jar paper.jar nogui && echo STARTED; fi", 30000);
                        vpsAppend(out);
                    } else if (op.equals("stop")) {
                        vpsAppend("⏹ توقف…");
                        String out = r.safeExec("screen -S mc -X stuff 'stop\\015' 2>/dev/null; sleep 8; if screen -ls 2>/dev/null | grep -q '\\bmc\\.'; then screen -S mc -X quit 2>/dev/null; sleep 2; pkill -f 'paper.jar' 2>/dev/null; echo FORCE-KILLED; else echo STOPPED; fi", 40000);
                        vpsAppend(out);
                    } else if (op.equals("status")) {
                        String out = r.safeExec("if screen -ls 2>/dev/null | grep -q '\\bmc\\.'; then echo '● سرور در حال اجراست (screen: mc)'; else echo '○ سرور متوقف است'; fi; ls \"$HOME/mc/logs/latest.log\" >/dev/null 2>&1 && tail -n 4 \"$HOME/mc/logs/latest.log\" || echo 'لاگی هنوز نیست — اگر تازه استارت کردی صبر کن'", 30000);
                        vpsAppend(out);
                    } else if (op.equals("rcon")) {
                        vpsAppend("🔐 فعال‌سازی RCON (پورت 25575، رمز atta1234)…");
                        String out = r.safeExec(rconScript(), 30000);
                        vpsAppend(out);
                        vpsAppend("ℹ بعد از این سرور را ری‌استارت کن (توقف + استارت).");
                    } else if (op.equals("log")) {
                        String out = r.safeExec("tail -n 120 \"$HOME/mc/logs/latest.log\" 2>/dev/null || echo 'لاگی نیست — سرور را استارت کن'", 30000);
                        vpsAppend(out);
                    } else if (op.startsWith("cmd:")) {
                        String cc = op.substring(4).replace("\"", "\\\"");
                        vpsAppend("> " + cc);
                        String out = r.safeExec("screen -S mc -X stuff \"" + cc + "\\015\" 2>/dev/null && echo SENT || echo 'سرور روشن نیست (screen mc پیدا نشد)'", 20000);
                        vpsAppend(out);
                    }
                } catch (Throwable t) {
                    vpsAppend("❌ خطا: " + t.toString());
                } finally {
                    busyOp = false;
                }
            }
        });
    }

    private String vpsSetupScript() {
        return "set -e\n"
                + "cd \"$HOME\"\n"
                + "mkdir -p mc/plugins\n"
                + "echo '[1/6] بررسی جاوا…'\n"
                + "if ! command -v java >/dev/null 2>&1; then\n"
                + "  echo 'نصب OpenJDK 21 (چند دقیقه)…'\n"
                + "  if [ \"$(id -u)\" = \"0\" ]; then apt-get update -qq && apt-get install -y -qq openjdk-21-jre-headless screen curl tar >/dev/null\n"
                + "  else sudo apt-get update -qq && sudo apt-get install -y -qq openjdk-21-jre-headless screen curl tar >/dev/null; fi\n"
                + "fi\n"
                + "command -v curl >/dev/null 2>&1 || { if [ \"$(id -u)\" = \"0\" ]; then apt-get install -y -qq curl >/dev/null; else sudo apt-get install -y -qq curl >/dev/null; fi; }\n"
                + "command -v screen >/dev/null 2>&1 || { if [ \"$(id -u)\" = \"0\" ]; then apt-get install -y -qq screen >/dev/null; else sudo apt-get install -y -qq screen >/dev/null; fi; }\n"
                + "echo '[2/6] دانلود Paper (آخرین نسخهٔ پایدار)…'\n"
                + "cd \"$HOME/mc\"\n"
                + "if [ ! -s paper.jar ]; then\n"
                + "  if command -v python3 >/dev/null 2>&1; then\n"
                + "    VER=$(python3 -c \"import json,urllib.request as u; d=json.load(u.urlopen('https://api.papermc.io/v2/projects/paper',timeout=30)); v=[x for x in d['versions'] if not any(c in x for c in ('pre','snapshot','rc'))]; print(v[-1])\" 2>/dev/null || true)\n"
                + "    if [ -n \"$VER\" ]; then\n"
                + "      BUILD=$(python3 -c \"import json,urllib.request as u; d=json.load(u.urlopen('https://api.papermc.io/v2/projects/paper/versions/VER/builds'.replace('VER','$VER'),timeout=30)); b=[x for x in d['builds'] if x.get('channel')=='default']; print(b[-1]['build'])\" 2>/dev/null || true)\n"
                + "    fi\n"
                + "  fi\n"
                + "  if [ -z \"$VER\" ]; then VER=$(curl -s --max-time 30 https://api.papermc.io/v2/projects/paper | grep -oE '\"[0-9]+(\\.[0-9]+)+\"' | tr -d '\"' | tail -1); fi\n"
                + "  if [ -z \"$BUILD\" ]; then BUILD=$(curl -s --max-time 30 \"https://api.papermc.io/v2/projects/paper/versions/$VER/builds\" | grep -o '\"build\":[0-9]*' | grep -o '[0-9]*' | tail -1); fi\n"
                + "  if [ -n \"$VER\" ] && [ -n \"$BUILD\" ]; then\n"
                + "    echo \"نسخهٔ Paper: $VER (build $BUILD)\"\n"
                + "    curl -sL -o paper.jar \"https://api.papermc.io/v2/projects/paper/versions/$VER/builds/$BUILD/downloads/paper-$VER-$BUILD.jar\"\n"
                + "  else echo '❌ دریافت نسخهٔ Paper ممکن نشد (اینترنت VPS را چک کن)'; fi\n"
                + "fi\n"
                + "echo '[3/6] دانلود Geyser + Floodgate (ورود بدراک)…'\n"
                + "[ -s plugins/Geyser-Spigot.jar ] || curl -sL -o plugins/Geyser-Spigot.jar \"https://downloads.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/spigot\"\n"
                + "[ -s plugins/Floodgate-Spigot.jar ] || curl -sL -o plugins/Floodgate-Spigot.jar \"https://downloads.geysermc.org/v2/projects/floodgate/versions/latest/builds/latest/downloads/spigot\"\n"
                + "echo '[4/6] دانلود ViaVersion…'\n"
                + "[ -s plugins/ViaVersion.jar ] || { URL=$(curl -s https://api.github.com/repos/ViaVersion/ViaVersion/releases/latest | grep -o '\"browser_download_url\":\"[^\"]*\\.jar\"' | head -1 | cut -d'\"' -f4); [ -n \"$URL\" ] && curl -sL -o plugins/ViaVersion.jar \"$URL\" || echo 'ViaVersion نبود (اختیاری)'; }\n"
                + "echo '[5/6] EULA…'\n"
                + "(grep -q 'eula=true' eula.txt 2>/dev/null) || echo 'eula=true' > eula.txt\n"
                + "echo '[6/6] کامل شد'\n"
                + "ls -la paper.jar 2>/dev/null; ls plugins/ 2>/dev/null\n"
                + "java -version 2>&1 | head -1\n";
    }

    private String rconScript() {
        return "cd \"$HOME/mc\"\n"
                + "if [ -f server.properties ]; then\n"
                + "  sed -i 's/^enable-rcon=.*/enable-rcon=true/; s/^rcon.port=.*/rcon.port=25575/; s/^rcon.password=.*/rcon.password=atta1234/' server.properties\n"
                + "  grep -q '^enable-rcon' server.properties || printf '\\nenable-rcon=true\\nrcon.port=25575\\nrcon.password=atta1234\\n' >> server.properties\n"
                + "  echo 'RCON فعال شد → ری‌استارت کن'\n"
                + "else\n"
                + "  echo 'server.properties ساخته نشده؛ اول یک بار سرور را استارت کن تا بسازد'\n"
                + "fi\n";
    }

    // =====================================================================
    // صفحهٔ Aternos — پنل مدیریت واقعی داخل خود اپ
    // =====================================================================

    private void buildAternosPage() {
        LinearLayout card = UiKit.vcol(this, 12);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF17202C, 0xFF10151D});
        g.setCornerRadius(dp(16));
        g.setStroke(1, 0x33FFFFFF);
        card.setBackground(g);
        page.addView(card, UiKit.wrapParams(card, 2, 0));

        cardTitle(card, "🌐 Aternos — پنل مدیریت داخل اپ");

        // موتور Aternos (یک بار ساخته می‌شود)
        if (aternos == null) {
            aternos = new AternosPanel(this, prefs, new AternosPanel.Ui() {
                @Override public void atLog(String line) {
                    if (atLogTv != null) appendLog(atLogTv, line);
                }
                @Override public void atStatus(java.util.Map<String, Object> ls, String dom) {
                    atRender(ls, dom);
                }
                @Override public void atReady(String serverId) {
                    atSetStatus("وضعیت: متصل ✓ (دکمه‌ها فعال‌اند)", 0xFF57C15B);
                }
                @Override public void atGone(String reason) {
                    atSetStatus("وضعیت: متصل نیست" + ("expired".equals(reason) ? " — نشست منقضی شد، دوباره وارد شو" : ""), 0xFF9AA7BA);
                }
                @Override public void atConsole(String line) {
                    if (atConsoleTv != null) appendLog(atConsoleTv, line);
                }
                @Override public void atPhase(String text, int color) {
                    atSetStatus(text, color);
                }
            });
        }

        // ---------- ۱) اتصال ----------
        card.addView(UiKit.sectionLabel(this, "۱) اتصال به حساب Aternos"));
        TextView cap1 = UiKit.caption(this, "یک بار با اکانت خودت وارد شو؛ نشست ذخیره می‌شود و دفعات بعد بدون ورود دوباره وصل می‌شود. همهٔ دکمه‌های این صفحه روی خود سرور واقعی Aternos اجرا می‌شوند.", true);
        card.addView(cap1, UiKit.wrapParams(cap1, 2, 0));

        UiKit.chipRow(this, new TextView[]{
                UiKit.chip(this, "🔑 ورود / اتصال", UiKit.KIND_ACCENT, new Runnable() {
                    @Override public void run() { aternos.begin(); }
                }),
                UiKit.chip(this, "🔄 تازه‌سازی وضعیت", new Runnable() {
                    @Override public void run() {
                        if (aternos.isReady()) aternos.reloadServerPage();
                        else aternos.begin();
                    }
                })
        }, 2, card);

        // جعبهٔ وضعیت — ارتفاع خودکار، هرگز فشرده نمی‌شود
        atStatusTv = new TextView(this);
        atStatusTv.setTextSize(14.5f);
        atStatusTv.setTypeface(Typeface.DEFAULT_BOLD);
        atStatusTv.setLineSpacing(dp(3), 1f);
        atStatusTv.setMinLines(2);
        atStatusTv.setText(atStatusText);
        atStatusTv.setTextColor(atStatusColor);
        GradientDrawable sg = UiKit.roundedSolid(0xFF0D1420, dp(12));
        sg.setStroke(1, 0x30FFFFFF);
        atStatusTv.setBackground(sg);
        atStatusTv.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.addView(atStatusTv, UiKit.wrapParams(atStatusTv, 2, 0));

        card.addView(UiKit.space(this, 4));

        // ---------- ۲) کنترل ----------
        card.addView(UiKit.sectionLabel(this, "۲) کنترل سرور"));
        UiKit.chipRow(this, new TextView[]{
                UiKit.chip(this, "▶ استارت", UiKit.KIND_ACCENT, new Runnable() {
                    @Override public void run() { aternos.doAction("start"); }
                }),
                UiKit.chip(this, "⏹ توقف", UiKit.KIND_DANGER, new Runnable() {
                    @Override public void run() { aternos.doAction("stop"); }
                })
        }, 2, card);
        UiKit.chipRow(this, new TextView[]{
                UiKit.chip(this, "🔁 ری‌استارت", new Runnable() {
                    @Override public void run() { aternos.doAction("restart"); }
                }),
                UiKit.chip(this, "✅ تأیید صف", new Runnable() {
                    @Override public void run() { aternos.doAction("confirm"); }
                })
        }, 2, card);
        UiKit.chipRow(this, new TextView[]{
                UiKit.chip(this, "📜 قبول EULA", new Runnable() {
                    @Override public void run() { aternos.doAction("accept-eula"); }
                }),
                UiKit.chip(this, "🔒 خروج از Aternos", UiKit.KIND_DANGER, new Runnable() {
                    @Override public void run() { aternos.logout(); }
                })
        }, 2, card);

        card.addView(UiKit.space(this, 4));

        // ---------- ۳) گزارش ----------
        card.addView(UiKit.sectionLabel(this, "۳) گزارش زنده (پاسخ‌های واقعی Aternos)"));
        atLogTv = logBox();
        atLogTv.setText("— پنل Aternos —\n"
                + "۱) «🔑 ورود / اتصال» را بزن و در صفحهٔ داخل اپ وارد شو\n"
                + "۲) بعد از اتصال، دکمه‌های استارت/توقف روی سرور واقعی اجرا می‌شوند\n"
                + "هر پاسخ Aternos همین‌جا می‌آید.");
        card.addView(atLogTv, UiKit.wrapParams(atLogTv, 2, 230));
        UiKit.chipRow(this, new TextView[]{
                UiKit.chip(this, "📋 کپی گزارش کامل (برای پشتیبانی)", new Runnable() {
                    @Override public void run() {
                        try {
                            StringBuilder sb = new StringBuilder();
                            sb.append("AttaPanel 2.1.3 — گزارش Aternos\n");
                            sb.append("state:\n").append(aternos.debugState()).append("\n\n");
                            sb.append("log:\n").append(atLogTv.getText().toString());
                            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                            cm.setPrimaryClip(ClipData.newPlainText("atta-report", sb.toString()));
                            toast("گزارش کپی شد — در چت برایم بفرست");
                        } catch (Throwable t) {
                            toast("کپی نشد: " + t);
                        }
                    }
                })
        }, 1, card);

        card.addView(UiKit.space(this, 4));

        // ---------- ۴) کنسول زندهٔ سرور ----------
        card.addView(UiKit.sectionLabel(this, "۴) کنسول سرور (زنده — مثل کنسول خود Aternos)"));
        TextView cap4 = UiKit.caption(this, "سرور باید روشن باشد. «اتصال کنسول» را بزن تا خطوط زندهٔ سرور بیاید؛ بعد هر دستوری (مثلاً list یا op gaser) را بنویس و ارسال کن — دقیقاً روی کنسول خود Aternos اجرا می‌شود.", true);
        card.addView(cap4, UiKit.wrapParams(cap4, 2, 0));

        UiKit.chipRow(this, new TextView[]{
                UiKit.chip(this, "🟢 اتصال کنسول", UiKit.KIND_ACCENT, new Runnable() {
                    @Override public void run() { aternos.consoleConnect(); }
                })
        }, 1, card);

        final EditText cmdIn = input("دستور کنسول (مثلاً list یا op gaser)", "", 0);
        card.addView(cmdIn, UiKit.wrapParams(cmdIn, 2, 46));
        UiKit.chipRow(this, new TextView[]{
                UiKit.chip(this, "📤 ارسال دستور", UiKit.KIND_ACCENT, new Runnable() {
                    @Override public void run() {
                        String c = cmdIn.getText().toString().trim();
                        if (c.length() == 0) { toast("دستور را بنویس"); return; }
                        if (atConsoleTv != null) appendLog(atConsoleTv, "> " + c);
                        aternos.sendConsoleCommand(c.startsWith("/") ? c.substring(1) : c);
                    }
                }),
                UiKit.chip(this, "👥 list", new Runnable() {
                    @Override public void run() {
                        if (atConsoleTv != null) appendLog(atConsoleTv, "> list");
                        aternos.sendConsoleCommand("list");
                    }
                }),
                UiKit.chip(this, "💾 save-all", new Runnable() {
                    @Override public void run() {
                        if (atConsoleTv != null) appendLog(atConsoleTv, "> save-all");
                        aternos.sendConsoleCommand("save-all");
                    }
                })
        }, 3, card);

        atConsoleTv = logBox();
        atConsoleTv.setText("— کنسول Aternos —\nبعد از روشن‌بودن سرور، «🟢 اتصال کنسول» را بزن.");
        card.addView(atConsoleTv, UiKit.wrapParams(atConsoleTv, 2, 260));

        // ---------- کارت راهنمای یک‌باره ----------
        LinearLayout card2 = UiKit.vcol(this, 12);
        GradientDrawable g2 = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF141B26, 0xFF10151D});
        g2.setCornerRadius(dp(16));
        g2.setStroke(1, 0x2A3547);
        card2.setBackground(g2);
        page.addView(card2, UiKit.wrapParams(card2, 2, 0));

        cardTitle(card2, "🧩 تنظیم یک‌بارهٔ سرور (فقط دفعهٔ اول)");
        TextView steps = logBox();
        steps.setText("۱) سایت Aternos ← نرم‌افزار ← Paper (جاوا) ← ذخیره\n"
                + "۲) پلاگین‌ها ← نصب: Geyser، Floodgate، ViaVersion\n"
                + "۳) استارت؛ بعد از Online شدن، در بدراک: you.aternos.me\n"
                + "۴) روزانه فقط همین صفحه: «استارت» بزن و تمام");
        card2.addView(steps, UiKit.wrapParams(steps, 2, 150));
        UiKit.chipRow(this, new TextView[]{
                UiKit.chip(this, "🌐 باز کردن سایت کامل (تنظیم اولیه)", new Runnable() {
                    @Override public void run() { openUrl("https://aternos.org/server/"); }
                })
        }, 1, card2);
    }

    /** به‌روزرسانی امن جعبهٔ وضعیت */
    private void atSetStatus(String text, int color) {
        atStatusText = text;
        atStatusColor = color;
        if (atStatusTv != null) {
            atStatusTv.setText(text);
            atStatusTv.setTextColor(color);
        }
    }

    /** ترجمهٔ lastStatus پنل Aternos به متن فارسی خوانا */
    private void atRender(java.util.Map<String, Object> ls, String dom) {
        // هیچ داده‌ای نرسیده: وضعیت فعلی را بازنویسی نکن (اتصال برقرار است) — فقط لاگ
        if ((ls == null || ls.isEmpty()) && (dom == null || dom.trim().isEmpty())) {
            if (atLogTv != null) appendLog(atLogTv, "⏳ وضعیت هنوز از پنل نرسیده — اتصال برقرار است، چند ثانیه بعد دوباره می‌خواند");
            return;
        }
        StringBuilder sb = new StringBuilder();
        int color = 0xFF9AA7BA;
        if (ls != null && !ls.isEmpty()) {
            double st = MiniJson.num(ls, "status", -1);
            String txt;
            if (st == 0) { txt = "⏻ خاموش"; color = 0xFF9AA7BA; }
            else if (st == 1) { txt = "● روشن — سرور در دسترس است"; color = 0xFF57C15B; }
            else if (st == 2) { txt = "⟳ در حال روشن‌شدن… (۱-۳ دقیقه، صف رایگان)"; color = 0xFFF0B45B; }
            else if (st == 3) { txt = "⏳ در حال خاموش‌شدن…"; color = 0xFFF0B45B; }
            else if (st == 6) { txt = "⏳ در حال بارگذاری…"; color = 0xFFF0B45B; }
            else if (st == 7) { txt = "❌ خطا در سرور"; color = 0xFFE46B6B; }
            else if (st == 10) { txt = "⏳ در صف Aternos — «تأیید صف» را بزن"; color = 0xFFF0B45B; }
            else txt = "وضعیت: " + (st < 0 ? MiniJson.str(ls, "status", "?") : String.valueOf((int) st));
            sb.append(txt);

            String ip = MiniJson.str(ls, "ip", "");
            double port = MiniJson.num(ls, "port", 0);
            if (ip.length() > 0) {
                sb.append("\n📍 آدرس: ").append(ip);
                if (port > 0 && port != 25565) sb.append(":").append((int) port);
            }
            String motd = MiniJson.str(ls, "motd", "");
            if (motd.length() > 0) sb.append("\n💬 ").append(motd);

            Object pl = ls.get("playerlist");
            if (pl instanceof java.util.List && !((java.util.List<?>) pl).isEmpty()) {
                StringBuilder names = new StringBuilder();
                for (Object e : (java.util.List<?>) pl) {
                    String n;
                    if (e instanceof java.util.Map) n = MiniJson.str(e, "name", "");
                    else n = String.valueOf(e);
                    if (n == null || n.length() == 0) continue;
                    if (names.length() > 0) names.append("، ");
                    names.append(n);
                }
                if (names.length() > 0) sb.append("\n👥 آنلاین: ").append(names);
            }
            String msg = MiniJson.str(ls, "message", "");
            if (msg.length() > 0) sb.append("\nℹ ").append(msg);
        } else {
            // lastStatus خالی بود — از متن نمایشی خودِ صفحهٔ پنل ترجمه کن
            String d = dom == null ? "" : dom.toLowerCase();
            String txt;
            if (d.contains("offline")) { txt = "⏻ خاموش (از نمایش پنل)"; }
            else if (d.contains("online")) { txt = "● روشن (از نمایش پنل)"; color = 0xFF57C15B; }
            else if (d.contains("start") || d.contains("load")) { txt = "⟳ در حال روشن‌شدن… (از نمایش پنل)"; color = 0xFFF0B45B; }
            else if (d.contains("queue") || d.contains("wait")) { txt = "⏳ در صف Aternos — «تأیید صف» را بزن"; color = 0xFFF0B45B; }
            else if (d.contains("error")) { txt = "❌ خطا در سرور (از نمایش پنل)"; color = 0xFFE46B6B; }
            else txt = "پنل: " + dom;
            sb.append(txt);
        }
        atStatusText = sb.toString();
        atStatusColor = color;
        if (atStatusTv != null) {
            atStatusTv.setText(atStatusText);
            atStatusTv.setTextColor(atStatusColor);
        }
    }

    // =====================================================================
    // صفحهٔ اتصال پالس (RCON برای اوورلی)
    // =====================================================================

    private void buildPulsePage() {
        LinearLayout card = UiKit.vcol(this, 12);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF17202C, 0xFF10151D});
        g.setCornerRadius(dp(16));
        g.setStroke(1, 0x33FFFFFF);
        card.setBackground(g);

        cardTitle(card, "🔌 اتصال پالس — کنترل اوورلی روی سرور (VPS)");
        addLabel(card, "این اتصال RCON فقط برای سرور VPS است (Aternos پورت RCON نمی‌دهد). "
                + "VPS: دکمهٔ «فعال‌سازی RCON» در صفحهٔ VPS و بازکردن پورت 25575 در فایروال. "
                + "برای Aternos، کنسول کامل در تب Aternos (بخش ۴) در دسترس است.");

        pHost = input("آدرس سرور (IP یا you.aternos.me)", prefs.getRconHost(), 0);
        card.addView(pHost, UiKit.wrapParams(pHost, 2, 46));

        LinearLayout row1 = UiKit.hrow(this);
        pPort = input("پورت", String.valueOf(prefs.getRconPort()), InputType.TYPE_CLASS_NUMBER);
        pPass = input("رمز RCON", prefs.getRconPassword(), 0);
        row1.addView(pPort, wRow(0.35f, 46));
        row1.addView(pPass, wRow(0.65f, 46));
        card.addView(row1, UiKit.wrapParams(row1, 2, 0));

        

UiKit.chipRow(this, new TextView[]{
                UiKit.chip(this, "💾 ذخیره", new Runnable() {
                    @Override public void run() { savePulse(); }
                }),
                UiKit.chip(this, "🧪 تست اتصال RCON", UiKit.KIND_ACCENT, new Runnable() {
                    @Override public void run() {
                        savePulse();
                        testRcon();
                    }
                })
        }, 2, card);

        pulseLogTv = logBox();
        card.addView(pulseLogTv, UiKit.wrapParams(pulseLogTv, 2, 170));
        rconLog = "— اتصال پالس —\nاطلاعات RCON را وارد کن و «تست اتصال» را بزن.";
        pulseLogTv.setText(rconLog);

        page.addView(card, UiKit.wrapParams(card, 2, 0));
    }

    private void savePulse() {
        prefs.setRconHost(pHost.getText().toString().trim());
        String pt = pPort.getText().toString().trim();
        try {
            prefs.setRconPort(pt.isEmpty() ? 25575 : Integer.parseInt(pt));
        } catch (Exception e) {
            prefs.setRconPort(25575);
        }
        prefs.setRconPassword(pPass.getText().toString());
        toast("ذخیره شد");
    }

    private void rconAppend(final String s) {
        ui.post(new Runnable() {
            @Override public void run() {
                if (pulseLogTv != null) appendLog(pulseLogTv, s);
            }
        });
    }

    private void testRcon() {
        rconAppend("⟳ اتصال به " + prefs.getRconHost() + ":" + prefs.getRconPort() + " …");
        final RconConnection[] holder = new RconConnection[1];
        holder[0] = new RconConnection(prefs.getRconHost(),
                prefs.getRconPort(), prefs.getRconPassword(), new RconListener() {
            @Override public void onAuth(boolean ok, String message) {
                rconAppend((ok ? "✅ متصل شد: " : "❌ ") + message);
                if (ok) {
                    holder[0].send("list");
                    ui.postDelayed(new Runnable() {
                        @Override public void run() { holder[0].stop(); }
                    }, 2500);
                }
            }
            @Override public void onOutput(String text) {
                rconAppend("📩 " + text);
            }
            @Override public void onClosed(String reason) {
                rconAppend("— بسته شد" + (reason == null || reason.isEmpty() ? "" : ": " + reason));
            }
        });
        try {
            holder[0].start();
        } catch (Throwable t) {
            rconAppend("❌ " + t.toString());
        }
    }
}
