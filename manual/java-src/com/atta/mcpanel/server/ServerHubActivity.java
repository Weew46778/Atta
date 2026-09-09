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
    protected void onDestroy() {
        exec.shutdownNow();
        super.onDestroy();
    }

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
        // استایل منو: بخش فعال برجسته
        for (int i = 0; i < segViews.length; i++) {
            int kind = (i == idx) ? UiKit.KIND_ACCENT : UiKit.KIND_PLAIN;
            GradientDrawable g;
            if (i == idx) {
                g = UiKit.roundedSolid(0xFF4E8A33, UiKit.dp(this, 999));
            } else {
                g = UiKit.roundedSolid(0xFF1D2532, UiKit.dp(this, 999));
            }
            segViews[i].setBackground(g);
            segViews[i].setTextColor(i == idx ? Palette.ON_ACCENT : Palette.CHIP_TEXT);
        }
        page.removeAllViews();
        diag.setVisibility(View.GONE);
        try {
            if (idx == 0) buildVpsPage();
            else if (idx == 1) buildAternosPage();
            else buildPulsePage();
        } catch (Throwable t) {
            TextView tv = new TextView(this);
            tv.setText("❌ خطا در ساخت صفحه:\n" + t.toString());
            tv.setTextColor(0xFFFF6B6B);
            tv.setTextSize(12f);
            page.addView(tv, UiKit.wrapParams(tv, 2, 0));
        }
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
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
        row1.addView(fPort, UiKit.m(0, 46, 0.4f, 2));
        row1.addView(fUser, UiKit.m(0, 46, 0.6f, 2));
        card.addView(row1, UiKit.wrapParams(row1, 2, 0));

        fPass = input("رمز SSH", prefs.getString("vps_pass", ""),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        card.addView(fPass, UiKit.wrapParams(fPass, 2, 46));

        LinearLayout row2 = UiKit.hrow(this);
        row2.addView(UiKit.chip(this, "💾 ذخیره", new Runnable() {
            @Override public void run() { saveVps(); }
        }), UiKit.m(0, 44, 1f, 2));
        row2.addView(UiKit.chip(this, "🔄 تست اتصال", UiKit.KIND_ACCENT, new Runnable() {
            @Override public void run() { runOp("test"); }
        }), UiKit.m(0, 44, 1f, 2));
        card.addView(row2, UiKit.wrapParams(row2, 2, 0));

        LinearLayout row3 = UiKit.hrow(this);
        row3.addView(UiKit.chip(this, "🚀 نصب خودکار سرور", UiKit.KIND_PLAIN, new Runnable() {
            @Override public void run() { runOp("setup"); }
        }), UiKit.m(0, 44, 1f, 2));
        row3.addView(UiKit.chip(this, "▶ استارت", UiKit.KIND_ACCENT, new Runnable() {
            @Override public void run() { runOp("start"); }
        }), UiKit.m(0, 44, 1f, 2));
        row3.addView(UiKit.chip(this, "⏹ توقف", UiKit.KIND_DANGER, new Runnable() {
            @Override public void run() { runOp("stop"); }
        }), UiKit.m(0, 44, 1f, 2));
        card.addView(row3, UiKit.wrapParams(row3, 2, 0));

        LinearLayout row4 = UiKit.hrow(this);
        row4.addView(UiKit.chip(this, "📊 وضعیت", new Runnable() {
            @Override public void run() { runOp("status"); }
        }), UiKit.m(0, 44, 1f, 2));
        row4.addView(UiKit.chip(this, "🔐 فعال‌سازی RCON", new Runnable() {
            @Override public void run() { runOp("rcon"); }
        }), UiKit.m(0, 44, 1f, 2));
        row4.addView(UiKit.chip(this, "📜 لاگ", new Runnable() {
            @Override public void run() { runOp("log"); }
        }), UiKit.m(0, 44, 1f, 2));
        card.addView(row4, UiKit.wrapParams(row4, 2, 0));

        addLabel(card, "حافظهٔ جاوا (مثلاً 2G):");
        fMem = input("2G", prefs.getString("vps_mem", "2G"), 0);
        card.addView(fMem, UiKit.wrapParams(fMem, 2, 44));

        addLabel(card, "فرمان دلخواه به کنسول سرور (مثلاً op gaser):");
        final EditText cmd = input("op gaser / list / save-all", "", 0);
        card.addView(cmd, UiKit.wrapParams(cmd, 2, 44));
        card.addView(UiKit.chip(this, "📤 ارسال به کنسول", UiKit.KIND_ACCENT, new Runnable() {
            @Override public void run() {
                String cc = cmd.getText().toString().trim();
                if (cc.isEmpty()) { toast("فرمان را بنویس"); return; }
                runOp("cmd:" + cc);
            }
        }), UiKit.wrapParams(cmd, 2, 44));

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
    // صفحهٔ Aternos
    // =====================================================================

    private void buildAternosPage() {
        LinearLayout card = UiKit.vcol(this, 12);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF17202C, 0xFF10151D});
        g.setCornerRadius(dp(16));
        g.setStroke(1, 0x33FFFFFF);
        card.setBackground(g);

        cardTitle(card, "🌐 Aternos — سرور رایگان ابری");
        addLabel(card, "Aternos از بیرون RCON نمی‌دهد؛ مدیریت از «پنل داخل همین اپ» با اکانت خودت انجام می‌شود. "
                + "با Geyser روی نسخهٔ Paper، بدراک ۱.۲۶ هم وارد می‌شود.");

        TextView bWeb = UiKit.chip(this, "🌐 باز کردن پنل Aternos داخل اپ", UiKit.KIND_ACCENT,
                new Runnable() {
                    @Override public void run() { openAternosDialog(); }
                });
        card.addView(bWeb, UiKit.wrapParams(bWeb, 2, 46));

        LinearLayout row = UiKit.hrow(this);
        row.addView(UiKit.chip(this, "🔌 پنل", new Runnable() {
            @Override public void run() { openUrl("https://aternos.org/go/"); }
        }), UiKit.m(0, 42, 1f, 2));
        row.addView(UiKit.chip(this, "🎛 کنسول", new Runnable() {
            @Override public void run() { openUrl("https://aternos.org/server/"); }
        }), UiKit.m(0, 42, 1f, 2));
        row.addView(UiKit.chip(this, "🧩 پلاگین‌ها", new Runnable() {
            @Override public void run() { openUrl("https://aternos.org/plugins/"); }
        }), UiKit.m(0, 42, 1f, 2));
        card.addView(row, UiKit.wrapParams(row, 2, 0));

        addLabel(card, "نصب یک‌باره (راهنما):");
        TextView steps = logBox();
        steps.setText("۱) پنل ← «نرم‌افزار» ← Paper ← ذخیره\n"
                + "۲) «پلاگین‌ها» ← نصب: GeyserMC، Floodgate، ViaVersion\n"
                + "۳) تنظیمات ← RAM را تا ۳-۴GB ببر\n"
                + "۴) استارت؛ صبر کن Online شود\n"
                + "۵) در بازی «افزودن سرور» ← آدرس you.aternos.me ← پورت 19132\n"
                + "۶) نام تو در بازی: gaser\n"
                + "۷) روشن‌کردن هر بار: همین پنل داخل اپ ← استارت");
        card.addView(steps, UiKit.wrapParams(steps, 2, 210));

        page.addView(card, UiKit.wrapParams(card, 2, 0));
    }

    private void openAternosDialog() {
        try {
            final AlertDialog dlg = new AlertDialog.Builder(this).create();
            dlg.setTitle("پنل Aternos (ورود با اکانت خودت)");
            WebView wv = new WebView(this);
            WebSettings ws = wv.getSettings();
            ws.setJavaScriptEnabled(true);
            ws.setDomStorageEnabled(true);
            ws.setDatabaseEnabled(true);
            ws.setUserAgentString(ws.getUserAgentString().replace("; wv", ""));
            ws.setLoadWithOverviewMode(true);
            ws.setUseWideViewPort(true);
            wv.loadUrl("https://aternos.org/go/");

            LinearLayout box = UiKit.vcol(this, 8);
            int wvh = Math.min(dp(560),
                    Math.round(getResources().getDisplayMetrics().heightPixels * 0.7f));
            box.addView(wv, new LinearLayout.LayoutParams(-1, wvh));
            TextView bClose = UiKit.chip(this, "بستن", new Runnable() {
                @Override public void run() { dlg.dismiss(); }
            });
            box.addView(bClose, UiKit.wrapParams(bClose, 4, 46));
            dlg.setView(box);
            dlg.show();
        } catch (Exception e) {
            toast("پنل باز نشد: " + e.getMessage());
            openUrl("https://aternos.org/go/");
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

        cardTitle(card, "🔌 اتصال پالس — کنترل اوورلی روی سرور");
        addLabel(card, "زبانهٔ «سرور» در پنل روی بازی، دستورها را با این اتصال RCON می‌فرستد. "
                + "VPS: دکمهٔ «فعال‌سازی RCON» در صفحهٔ VPS و بازکردن پورت 25575 در فایروال. "
                + "Aternos معمولاً RCON بیرونی نمی‌دهد.");

        pHost = input("آدرس سرور (IP یا you.aternos.me)", prefs.getRconHost(), 0);
        card.addView(pHost, UiKit.wrapParams(pHost, 2, 46));

        LinearLayout row1 = UiKit.hrow(this);
        pPort = input("پورت", String.valueOf(prefs.getRconPort()), InputType.TYPE_CLASS_NUMBER);
        pPass = input("رمز RCON", prefs.getRconPassword(), 0);
        row1.addView(pPort, UiKit.m(0, 46, 0.35f, 2));
        row1.addView(pPass, UiKit.m(0, 46, 0.65f, 2));
        card.addView(row1, UiKit.wrapParams(row1, 2, 0));

        LinearLayout row2 = UiKit.hrow(this);
        row2.addView(UiKit.chip(this, "💾 ذخیره", new Runnable() {
            @Override public void run() { savePulse(); }
        }), UiKit.m(0, 44, 1f, 2));
        row2.addView(UiKit.chip(this, "🧪 تست اتصال RCON", UiKit.KIND_ACCENT, new Runnable() {
            @Override public void run() {
                savePulse();
                testRcon();
            }
        }), UiKit.m(0, 44, 1f, 2));
        card.addView(row2, UiKit.wrapParams(row2, 2, 0));

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
