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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.atta.mcpanel.core.AppPrefs;
import com.atta.mcpanel.core.Palette;
import com.atta.mcpanel.rcon.RconConnection;
import com.atta.mcpanel.rcon.RconListener;
import com.atta.mcpanel.ssh.VpsRemote;

/**
 * AttaPanel v2 — مرکز سرور از راه دور.
 * همهٔ چیز «روی سرور» (VPS یا Aternos) انجام می‌شود؛ این اپ مدیریت/ساخت/کنترل را از همین‌جا انجام می‌دهد.
 * - VPS: اتصال SSH + نصب خودکار Paper+Geyser+Floodgate+ViaVersion + استارت/توقف/کنسول/لاگ/فعال‌سازی RCON
 * - Aternos: باز کردن پنل در همین اپ + راهنمای قدم‌به‌قدم + تست RCON (اگر داد)
 * - اتصال پالس (برای زبانهٔ سرور در اوورلی): اطلاعات RCON که ذخیره می‌شود و اوورلی با همان دستور می‌فرستد
 */
public class ServerHubActivity extends Activity {

    private AppPrefs prefs;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private LinearLayout content;
    private String hubLog = "";
    private TextView logTv;
    private boolean busyOp = false;

    // VPS fields
    private EditText fHost, fPort, fUser, fPass, fMem;
    private String vpsLog = "";
    private TextView vpsLogTv;

    private final java.util.concurrent.ExecutorService exec =
            java.util.concurrent.Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new AppPrefs(this);
        buildUi();
    }

    @Override
    protected void onDestroy() {
        exec.shutdownNow();
        super.onDestroy();
    }

    // =====================================================================
    // UI helpers
    // =====================================================================

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private TextView label(String t, int color, float size) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(size);
        tv.setTextColor(color);
        tv.setLineSpacing(0, 1.05f);
        return tv;
    }

    private TextView monoText(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(11.5f);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextColor(0xFFD6E0EA);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF0A0E13);
        g.setCornerRadius(dp(8));
        g.setStroke(1, 0x33FFFFFF);
        tv.setBackground(g);
        tv.setPadding(dp(8), dp(8), dp(8), dp(8));
        return tv;
    }

    private EditText input(String hint, String text, int type) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setText(text == null ? "" : text);
        et.setTextSize(13.5f);
        et.setTextColor(Palette.TEXT_MAIN);
        et.setHintTextColor(Palette.TEXT_DIM);
        et.setSingleLine(true);
        et.setInputType(type == 0 ? InputType.TYPE_CLASS_TEXT : type);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Palette.INPUT_BG);
        g.setCornerRadius(dp(10));
        g.setStroke(1, 0x30FFFFFF);
        et.setBackground(g);
        et.setPadding(dp(10), 0, dp(10), 0);
        return et;
    }

    private TextView btn(String label, int kind) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(13f);
        tv.setGravity(Gravity.CENTER);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        int bg, fg;
        if (kind == 1) { bg = Palette.ACCENT; fg = Palette.ON_ACCENT; }
        else if (kind == 2) { bg = Palette.DANGER; fg = 0xFFFFFFFF; }
        else if (kind == 3) { bg = Palette.GOLD; fg = Palette.ON_GOLD; }
        else { bg = Palette.SURFACE_HI; fg = Palette.TEXT_MAIN; }
        tv.setTextColor(fg);
        GradientDrawable g = new GradientDrawable();
        g.setColor(bg);
        g.setCornerRadius(dp(10));
        tv.setBackground(g);
        tv.setClickable(true);
        tv.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, android.view.MotionEvent ev) {
                if (ev.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
                    v.setAlpha(0.72f);
                } else if (ev.getActionMasked() == android.view.MotionEvent.ACTION_UP
                        || ev.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL) {
                    v.setAlpha(1f);
                }
                return false;
            }
        });
        return tv;
    }

    private LinearLayout hrow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF17202C, 0xFF10151D});
        g.setCornerRadius(dp(16));
        g.setStroke(1, 0x33FFFFFF);
        c.setBackground(g);
        c.setPadding(dp(12), dp(12), dp(12), dp(12));
        return c;
    }

    private LinearLayout.LayoutParams cardLp() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private void cardTitle(LinearLayout c, String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(15.5f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(Palette.TEXT_MAIN);
        c.addView(tv, new LinearLayout.LayoutParams(-1, -2));
        c.addView(space(6));
    }

    private View space(int hDp) {
        View v = new View(this);
        return v;
    }

    private void addSpace(LinearLayout c, int h) {
        View v = new View(this);
        c.addView(v, new LinearLayout.LayoutParams(-1, dp(h)));
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
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (Exception e) { toast("مرورگر باز نشد"); }
    }

    // =====================================================================
    // Root UI
    // =====================================================================

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0C1220, 0xFF10241D, 0xFF0B0F16});
        root.setBackground(bg);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        col.setPadding(dp(14), dp(10), dp(14), dp(24));

        TextView title = new TextView(this);
        title.setText("🛰 مرکز سرور — AttaPanel");
        title.setTextSize(21f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Palette.TEXT_MAIN);
        title.setGravity(Gravity.CENTER);
        col.addView(title, new LinearLayout.LayoutParams(-1, dp(46)));

        TextView sub = new TextView(this);
        sub.setText("VPS و Aternos — ساخت، مدیریت و کنسول همه از داخل همین اپ؛ هیچ سروری روی گوشی اجرا نمی‌شود");
        sub.setTextSize(11.5f);
        sub.setTextColor(Palette.TEXT_SUB);
        sub.setGravity(Gravity.CENTER);
        col.addView(sub, new LinearLayout.LayoutParams(-1, dp(34)));

        // nav
        LinearLayout nav = hrow();
        final String[] segs = {"🛰 VPS", "🌐 Aternos", "🔌 اتصال پالس"};
        for (int i = 0; i < segs.length; i++) {
            final int idx = i;
            TextView b = btn(segs[i], 0);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { show(idx); }
            });
            nav.addView(b, new LinearLayout.LayoutParams(0, dp(42), 1f));
        }
        col.addView(nav, new LinearLayout.LayoutParams(-1, dp(48)));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        col.addView(content, new LinearLayout.LayoutParams(-1, -2));

        sv.addView(col, new ScrollView.LayoutParams(-1, -2));
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        show(0);
    }

    private void show(int which) {
        content.removeAllViews();
        switch (which) {
            case 0: buildVps(); break;
            case 1: buildAternos(); break;
            default: buildLink(); break;
        }
    }

    private void note(LinearLayout c, String text) {
        TextView tv = label(text, Palette.TEXT_DIM, 11f);
        c.addView(tv, new LinearLayout.LayoutParams(-1, -2));
        addSpace(c, 6);
    }

    // =====================================================================
    // VPS
    // =====================================================================

    private void buildVps() {
        LinearLayout c = card();
        cardTitle(c, "🛰 سرور VPS (اتصال مستقیم SSH)");
        note(c, "VPS باید لینوکس (Ubuntu/Debian) و کاربر root یا sudo داشته باشد. "
                + "این اپ از راه دور: جاوا/سرور را نصب می‌کند، روشن/خاموش می‌کند و کنسول/لاگ می‌گیرد.");

        fHost = input("آدرس VPS (ip یا domain)", prefs.getString("vps_host", ""), 0);
        c.addView(fHost, new LinearLayout.LayoutParams(-1, dp(46)));
        addSpace(c, 6);
        LinearLayout row1 = hrow();
        fPort = input("پورت", prefs.getString("vps_port", "22"), InputType.TYPE_CLASS_NUMBER);
        row1.addView(fPort, new LinearLayout.LayoutParams(0, dp(46), 1f));
        fUser = input("کاربر", prefs.getString("vps_user", "root"), 0);
        row1.addView(fUser, new LinearLayout.LayoutParams(0, dp(46), 1f));
        c.addView(row1, new LinearLayout.LayoutParams(-1, dp(46)));
        addSpace(c, 6);
        fPass = input("رمز SSH", prefs.getString("vps_pass", ""), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        c.addView(fPass, new LinearLayout.LayoutParams(-1, dp(46)));
        addSpace(c, 6);

        LinearLayout row2 = hrow();
        TextView save = btn("💾 ذخیره", 0);
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { saveVpsPrefs(); }
        });
        row2.addView(save, new LinearLayout.LayoutParams(0, dp(44), 1f));
        TextView test = btn("🔄 تست اتصال", 1);
        test.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runOp("test"); }
        });
        row2.addView(test, new LinearLayout.LayoutParams(0, dp(44), 1f));
        c.addView(row2, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout row3 = hrow();
        TextView setup = btn("🚀 نصب خودکار سرور", 3);
        setup.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runOp("setup"); }
        });
        row3.addView(setup, new LinearLayout.LayoutParams(0, dp(44), 1f));
        TextView start = btn("▶ استارت", 1);
        start.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runOp("start"); }
        });
        row3.addView(start, new LinearLayout.LayoutParams(0, dp(44), 1f));
        c.addView(row3, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout row4 = hrow();
        TextView stop = btn("⏹ توقف", 2);
        stop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runOp("stop"); }
        });
        row4.addView(stop, new LinearLayout.LayoutParams(0, dp(44), 1f));
        TextView status = btn("📊 وضعیت", 0);
        status.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runOp("status"); }
        });
        row4.addView(status, new LinearLayout.LayoutParams(0, dp(44), 1f));
        TextView rcon = btn("🔐 فعال‌سازی RCON", 0);
        rcon.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runOp("rcon"); }
        });
        row4.addView(rcon, new LinearLayout.LayoutParams(0, dp(44), 1f));
        c.addView(row4, new LinearLayout.LayoutParams(-1, dp(48)));

        addSpace(c, 8);
        c.addView(label("حافظهٔ جاوا (پیش‌فرض 2G):", Palette.TEXT_SUB, 12f));
        fMem = input("مثلاً 2G یا 3G", prefs.getString("vps_mem", "2G"), 0);
        c.addView(fMem, new LinearLayout.LayoutParams(-1, dp(44)));
        addSpace(c, 8);

        LinearLayout row5 = hrow();
        TextView bLog = btn("📜 خواندن آخرین لاگ", 0);
        bLog.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { runOp("log"); }
        });
        row5.addView(bLog, new LinearLayout.LayoutParams(0, dp(44), 1f));
        TextView bCopy = btn("📋 کپی لاگ", 0);
        bCopy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { copy(vpsLog); }
        });
        row5.addView(bCopy, new LinearLayout.LayoutParams(0, dp(44), 1f));
        TextView bClear = btn("🧹", 0);
        bClear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { vpsLog = ""; vpsUpdate(); }
        });
        row5.addView(bClear, new LinearLayout.LayoutParams(0, dp(44), 0.6f));
        c.addView(row5, new LinearLayout.LayoutParams(-1, dp(48)));

        c.addView(label("فرمان دلخواه به کنسول سرور:", Palette.TEXT_SUB, 12f));
        final EditText cmd = input("مثلاً: op gaser   یا   list   یا   save-all", "", 0);
        c.addView(cmd, new LinearLayout.LayoutParams(-1, dp(44)));
        addSpace(c, 6);
        TextView bSend = btn("📤 ارسال به کنسول", 1);
        bSend.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final String cc = cmd.getText().toString().trim();
                if (cc.isEmpty()) { toast("فرمان را بنویس"); return; }
                runOp("cmd:" + cc);
            }
        });
        c.addView(bSend, new LinearLayout.LayoutParams(-1, dp(44)));

        addSpace(c, 10);
        vpsLogTv = monoText("");
        vpsLogTv.setMinHeight(dp(150));
        c.addView(vpsLogTv, new LinearLayout.LayoutParams(-1, -2));
        content.addView(c, cardLp());
        vpsLog = "— اطلاعات VPS —\nمطمئن شو سرور (VPS) از بیرون در دسترس است؛ بعد «تست اتصال» را بزن.\n";
        vpsUpdate();
    }

    private void saveVpsPrefs() {
        prefs.setString("vps_host", fHost.getText().toString().trim());
        prefs.setString("vps_port", fPort.getText().toString().trim());
        prefs.setString("vps_user", fUser.getText().toString().trim());
        prefs.setString("vps_pass", fPass.getText().toString());
        prefs.setString("vps_mem", fMem == null ? "2G" : fMem.getText().toString().trim());
        toast("ذخیره شد");
    }

    private VpsRemote vps() {
        saveVpsPrefs();
        String host = prefs.getString("vps_host", "");
        int port;
        try { port = Integer.parseInt(prefs.getString("vps_port", "22")); }
        catch (Exception e) { port = 22; }
        String user = prefs.getString("vps_user", "root");
        String pass = prefs.getString("vps_pass", "");
        if (host.isEmpty() || pass.isEmpty()) {
            vpsAppend("❌ آدرس یا رمز VPS خالی است.");
            return null;
        }
        return new VpsRemote(host, port, user, pass);
    }

    private void runOp(final String op) {
        if (busyOp) { toast("یک عملیات در جریان است…"); return; }
        final VpsRemote r = vps();
        if (r == null) return;
        busyOp = true;
        exec.execute(new Runnable() {
            @Override public void run() {
                try {
                    if (op.equals("test")) {
                        vpsAppend("⟳ تست اتصال به " + r.host + " …");
                        String out = r.safeExec("echo CONNECTED; uname -a; command -v java >/dev/null 2>&1 && java -version 2>&1 | head -1 || echo 'java not installed'; command -v screen >/dev/null 2>&1 && echo screen-ok || echo screen-missing", 30000);
                        vpsAppend(out);
                    } else if (op.equals("setup")) {
                        vpsAppend("🚀 نصب خودکار شروع شد (بسته به VPS ۳ تا ۱۰ دقیقه)…");
                        String out = r.safeExec(vpsSetupScript(), 600000);
                        vpsAppend(out);
                    } else if (op.equals("start")) {
                        String mem = fMem != null ? fMem.getText().toString().trim() : "2G";
                        if (!mem.matches("\\d+[GgMm]")) mem = "2G";
                        vpsAppend("▶ استارت سرور… (اولین اجرا چند دقیقه طول می‌کشد)");
                        String out = r.safeExec(
                                "cd \"$HOME/mc\" && if screen -ls 2>/dev/null | grep -q '\\bmc\\.'; then echo ALREADY-RUNNING; else screen -dmS mc java -Xms512M -Xmx" + mem + " -jar paper.jar nogui && echo STARTED; fi", 30000);
                        vpsAppend(out);
                    } else if (op.equals("stop")) {
                        vpsAppend("⏹ توقف…");
                        String out = r.safeExec(
                                "screen -S mc -X stuff 'stop\\015' 2>/dev/null; sleep 8; if screen -ls 2>/dev/null | grep -q '\\bmc\\.'; then screen -S mc -X quit 2>/dev/null; sleep 2; pkill -f 'paper.jar' 2>/dev/null; echo FORCE-KILLED; else echo STOPPED; fi", 40000);
                        vpsAppend(out);
                    } else if (op.equals("status")) {
                        String out = r.safeExec(
                                "if screen -ls 2>/dev/null | grep -q '\\bmc\\.'; then echo '● سرور در حال اجراست (screen: mc)'; else echo '○ سرور متوقف است'; fi; ls \"$HOME/mc/logs/latest.log\" >/dev/null 2>&1 && tail -n 4 \"$HOME/mc/logs/latest.log\" || echo 'لاگی هنوز نیست — اگر تازه استارت کردی صبر کن'", 30000);
                        vpsAppend(out);
                    } else if (op.equals("rcon")) {
                        vpsAppend("🔐 فعال‌سازی RCON (پورت 25575، رمز atta1234)…");
                        String out = r.safeExec(rconScript(), 30000);
                        vpsAppend(out);
                        vpsAppend("ℹ بعد از این حتماً سرور را ری‌استارت کن (دکمهٔ توقف + استارت).");
                    } else if (op.equals("log")) {
                        String out = r.safeExec(
                                "tail -n 160 \"$HOME/mc/logs/latest.log\" 2>/dev/null || echo 'لاگی نیست — سرور را استارت کن'", 30000);
                        vpsAppend(out);
                    } else if (op.startsWith("cmd:")) {
                        String cc = op.substring(4).replace("\"", "\\\"");
                        vpsAppend("> " + cc);
                        String out = r.safeExec(
                                "screen -S mc -X stuff \"" + cc + "\\015\" 2>/dev/null && echo SENT || echo 'سرور روشن نیست (screen mc پیدا نشد)'", 20000);
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

    private void vpsAppend(final String s) {
        ui.post(new Runnable() {
            @Override public void run() {
                vpsLog += s + "\n";
                if (vpsLog.length() > 30000) vpsLog = vpsLog.substring(vpsLog.length() - 30000);
                vpsUpdate();
            }
        });
    }

    private void vpsUpdate() {
        if (vpsLogTv != null) vpsLogTv.setText(vpsLog);
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
                + "echo '[2/6] دانلود Paper (آخرین نسخهٔ پایدار)…'\n"
                + "cd \"$HOME/mc\"\n"
                + "if [ ! -s paper.jar ]; then\n"
                + "  if command -v python3 >/dev/null 2>&1; then\n"
                + "    VER=$(python3 -c \"import json,urllib.request as u; d=json.load(u.urlopen('https://api.papermc.io/v2/projects/paper',timeout=30)); v=[x for x in d['versions'] if not any(c in x for c in ('pre','snapshot','rc'))]; print(v[-1])\" 2>/dev/null || true)\n"
                + "    if [ -n \"$VER\" ]; then\n"
                + "      BUILD=$(python3 -c \"import json,urllib.request as u; d=json.load(u.urlopen('https://api.papermc.io/v2/projects/paper/versions/VER/builds'.replace('VER', '$VER'),timeout=30)); b=[x for x in d['builds'] if x.get('channel')=='default']; print(b[-1]['build'])\" 2>/dev/null || true)\n"
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
    // Aternos
    // =====================================================================

    private void buildAternos() {
        LinearLayout c = card();
        cardTitle(c, "🌐 Aternos — سرور رایگان ابری (Paper)");
        note(c, "Aternos از بیرون RCON نمی‌دهد؛ پس مدیریت از «پنل داخل همین اپ» انجام می‌شود (با اکانت خودت وارد شو). "
                + "نسخهٔ بدراک تو (مثل ۱.۲۶.۴۵) با Geyser روی Paperِ Aternos وارد می‌شود.");

        TextView bWeb = btn("🌐 باز کردن پنل Aternos داخل اپ", 1);
        bWeb.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openAternosPanel(); }
        });
        c.addView(bWeb, new LinearLayout.LayoutParams(-1, dp(46)));
        addSpace(c, 6);
        LinearLayout row = hrow();
        TextView bPanel = btn("🔌 پنل", 0);
        bPanel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openUrl("https://aternos.org/go/"); }
        });
        row.addView(bPanel, new LinearLayout.LayoutParams(0, dp(42), 1f));
        TextView bCons = btn("🎛 کنسول", 0);
        bCons.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openUrl("https://aternos.org/server/"); }
        });
        row.addView(bCons, new LinearLayout.LayoutParams(0, dp(42), 1f));
        TextView bPlug = btn("🧩 پلاگین‌ها", 0);
        bPlug.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openUrl("https://aternos.org/plugins/"); }
        });
        row.addView(bPlug, new LinearLayout.LayoutParams(0, dp(42), 1f));
        c.addView(row, new LinearLayout.LayoutParams(-1, dp(46)));

        addSpace(c, 8);
        TextView steps = monoText("نصب یک‌باره روی Aternos:\n"
                + "1) پنل ← «نرم‌افزار» ← Paper ← ذخیره\n"
                + "2) «پلاگین‌ها» ← نصب: GeyserMC، Floodgate، ViaVersion\n"
                + "3) تنظیمات ← RAM را تا ۳-۴GB بالا ببر\n"
                + "4) استارت؛ صبر کن کنار سرور Online بیاید\n"
                + "5) در بازی «افزودن سرور» ← آدرس you.aternos.me ← پورت 19132\n"
                + "6) نامت در بازی: gaser (تا اپراتور بمانی)\n"
                + "7) هر بار برای روشن‌کردن: همین پنل را باز کن و دکمهٔ استارت را بزن");
        c.addView(steps, new LinearLayout.LayoutParams(-1, -2));
        content.addView(c, cardLp());
    }

    private void openAternosPanel() {
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
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setPadding(dp(8), 0, dp(8), dp(8));
            int maxH = Math.min(dp(560),
                    Math.round(getResources().getDisplayMetrics().heightPixels * 0.72f));
            box.addView(wv, new LinearLayout.LayoutParams(-1, maxH));
            LinearLayout btns = hrow();
            TextView close = btn("بستن", 0);
            close.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { dlg.dismiss(); }
            });
            btns.addView(close, new LinearLayout.LayoutParams(-1, dp(46), 1f));
            box.addView(btns, new LinearLayout.LayoutParams(-1, dp(50)));
            dlg.setView(box);
            dlg.show();
        } catch (Exception e) {
            toast("پنل باز نشد: " + e.getMessage());
        }
    }

    // =====================================================================
    // اتصال پالس (RCON) برای اوورلی
    // =====================================================================

    private void buildLink() {
        LinearLayout c = card();
        cardTitle(c, "🔌 اتصال پالس — کنترل اوورلی روی سرور");
        note(c, "زبانهٔ «🖥 سرور» در پنل روی بازی، دستورها را با این اتصال RCON می‌فرستد "
                + "(بدون RCON نمی‌توان از بیرون فرمان داد). اگر سرورت VPS است: «فعال‌سازی RCON» را بزن "
                + "و پورت 25575 را در فایروال VPS باز کن. برای Aternos معمولاً RCON بیرونی نمی‌دهند.");

        final EditText host = input("آدرس (you.aternos.me یا IP سرور)", prefs.getRconHost(), 0);
        c.addView(host, new LinearLayout.LayoutParams(-1, dp(44)));
        addSpace(c, 6);
        LinearLayout row1 = hrow();
        final EditText port = input("پورت", String.valueOf(prefs.getRconPort()), InputType.TYPE_CLASS_NUMBER);
        row1.addView(port, new LinearLayout.LayoutParams(0, dp(44), 1f));
        final EditText pass = input("رمز RCON", prefs.getRconPassword(), 0);
        row1.addView(pass, new LinearLayout.LayoutParams(0, dp(44), 1f));
        c.addView(row1, new LinearLayout.LayoutParams(-1, dp(44)));
        addSpace(c, 6);

        LinearLayout row2 = hrow();
        TextView save = btn("💾 ذخیره", 1);
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.setRconHost(host.getText().toString().trim());
                String pt = port.getText().toString().trim();
                prefs.setRconPort(pt.isEmpty() ? 25575 : Integer.parseInt(pt));
                prefs.setRconPassword(pass.getText().toString());
                toast("ذخیره شد");
            }
        });
        row2.addView(save, new LinearLayout.LayoutParams(0, dp(44), 1f));
        TextView test = btn("🧪 تست", 0);
        test.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.setRconHost(host.getText().toString().trim());
                String pt = port.getText().toString().trim();
                prefs.setRconPort(pt.isEmpty() ? 25575 : Integer.parseInt(pt));
                prefs.setRconPassword(pass.getText().toString());
                testRcon();
            }
        });
        row2.addView(test, new LinearLayout.LayoutParams(0, dp(44), 1f));
        c.addView(row2, new LinearLayout.LayoutParams(-1, dp(48)));

        hubLogTv = monoText("");
        hubLogTv.setMinHeight(dp(110));
        c.addView(hubLogTv, new LinearLayout.LayoutParams(-1, -2));
        content.addView(c, cardLp());
        hubLog = "";
        hubUpdate();
    }

    private TextView hubLogTv;

    private void testRcon() {
        hubAppend("⟳ اتصال به " + prefs.getRconHost() + ":" + prefs.getRconPort() + " …");
        final RconConnection[] holder = new RconConnection[1];
        holder[0] = new RconConnection(prefs.getRconHost(),
                prefs.getRconPort(), prefs.getRconPassword(), new RconListener() {
            @Override public void onAuth(boolean ok, String message) {
                hubAppend((ok ? "✅ متصل شد: " : "❌ ") + message);
                if (ok) {
                    holder[0].send("list");
                    ui.postDelayed(new Runnable() {
                        @Override public void run() { holder[0].stop(); }
                    }, 2500);
                }
            }
            @Override public void onOutput(String text) {
                hubAppend("📩 " + text);
            }
            @Override public void onClosed(String reason) {
                hubAppend("— اتصال بسته شد" + (reason == null || reason.isEmpty() ? "" : ": " + reason));
            }
        });
        holder[0].start();
    }

    private void hubAppend(final String s) {
        ui.post(new Runnable() {
            @Override public void run() {
                hubLog += s + "\n";
                if (hubLog.length() > 10000) hubLog = hubLog.substring(hubLog.length() - 10000);
                hubUpdate();
            }
        });
    }

    private void hubUpdate() {
        if (hubLogTv != null) hubLogTv.setText(hubLog);
    }
}
