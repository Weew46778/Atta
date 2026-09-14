package com.atta.mcpanel.server;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.atta.mcpanel.core.AppPrefs;
import com.atta.mcpanel.core.Palette;
import com.atta.mcpanel.overlay.UiKit;
import com.atta.mcpanel.ssh.VpsRemote;
import com.atta.mcpanel.ui.Pages;
import com.atta.mcpanel.voice.Homan;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** صفحهٔ سرور VPS — مینیمال، درشت، بدون اسکرول صفحه */
public class VpsPage extends Activity {

    private AppPrefs prefs;
    private Pages.LogBox logBox;
    private final ExecutorService exec = Executors.newSingleThreadExecutor();
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new AppPrefs(this);

        LinearLayout col = Pages.root(this);
        col.addView(Pages.titleBar(this, "🛰 سرور VPS", new Runnable() {
            @Override public void run() { Homan.intro(VpsPage.this, "vps"); }
        }));

        Pages.row2(this, col,
                Pages.action(this, "⚙ اتصال VPS", 1, new Runnable() {
                    @Override public void run() { connectDialog(); }
                }),
                Pages.action(this, "🔄 تست", 0, new Runnable() {
                    @Override public void run() { vpsOp("test", null); }
                }));
        Pages.row2(this, col,
                Pages.action(this, "🚀 نصب خودکار", 0, new Runnable() {
                    @Override public void run() { vpsOp("setup", null); }
                }),
                Pages.action(this, "▶ استارت", 1, new Runnable() {
                    @Override public void run() { vpsOp("start", null); }
                }));
        Pages.row2(this, col,
                Pages.action(this, "⏹ توقف", 2, new Runnable() {
                    @Override public void run() { vpsOp("stop", null); }
                }),
                Pages.action(this, "📊 وضعیت", 0, new Runnable() {
                    @Override public void run() { vpsOp("status", null); }
                }));
        Pages.row2(this, col,
                Pages.action(this, "📤 دستور", 0, new Runnable() {
                    @Override public void run() { cmdDialog(); }
                }),
                Pages.action(this, "🔐 RCON + پالس", 0, new Runnable() {
                    @Override public void run() { rconDialog(); }
                }));

        logBox = Pages.logBox(this, 240);
        logBox.append("— سرور VPS —\nاول «اتصال VPS» را بزن و مشخصات را وارد کن.");
        col.addView(logBox.sv);

        setContentView(col);
        Homan.intro(this, "vps");
    }

    @Override
    protected void onDestroy() {
        exec.shutdownNow();
        super.onDestroy();
    }

    // =====================================================================
    // دیالوگ‌ها
    // =====================================================================

    private EditText field(LinearLayout into, String hint, String val) {
        EditText et = new EditText(this);
        et.setHint(hint);
        if (val != null) et.setText(val);
        et.setTextSize(14f);
        et.setTextColor(Palette.TEXT_MAIN);
        et.setHintTextColor(Palette.TEXT_DIM);
        et.setSingleLine(true);
        android.graphics.drawable.GradientDrawable g =
                UiKit.roundedSolid(Palette.INPUT_BG, UiKit.dp(this, 10));
        g.setStroke(1, 0x30FFFFFF);
        et.setBackground(g);
        et.setPadding(UiKit.dp(this, 10), 0, UiKit.dp(this, 10), 0);
        if (into != null) into.addView(et, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(this, 46)));
        return et;
    }

    private void connectDialog() {
        LinearLayout box = UiKit.vcol(this, 10);
        final EditText fHost = field(box, "آدرس VPS (IP)", prefs.getString("vps_host", ""));
        LinearLayout r1 = UiKit.hrow(this);
        final EditText fPort = field(null, "پورت SSH", prefs.getString("vps_port", "22"));
        final EditText fUser = field(null, "کاربر", prefs.getString("vps_user", "root"));
        r1.addView(fPort, new LinearLayout.LayoutParams(0, UiKit.dp(this, 46), 0.4f));
        r1.addView(fUser, new LinearLayout.LayoutParams(0, UiKit.dp(this, 46), 0.6f));
        box.addView(r1);
        final EditText fPass = field(box, "رمز SSH", prefs.getString("vps_pass", ""));
        fPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        final EditText fMem = field(box, "حافظهٔ جاوا (مثلاً 2G)", prefs.getString("vps_mem", "2G"));

        final AlertDialog dlg = new AlertDialog.Builder(this).create();
        dlg.setTitle("اتصال VPS");
        TextView bSave = UiKit.chip(this, "💾 ذخیره و تست", UiKit.KIND_ACCENT, new Runnable() {
            @Override public void run() {
                prefs.setString("vps_host", fHost.getText().toString().trim());
                prefs.setString("vps_port", fPort.getText().toString().trim());
                prefs.setString("vps_user", fUser.getText().toString().trim());
                prefs.setString("vps_pass", fPass.getText().toString());
                prefs.setString("vps_mem", fMem.getText().toString().trim());
                dlg.dismiss();
                vpsOp("test", null);
            }
        });
        box.addView(bSave, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(this, 48)));
        dlg.setView(box);
        dlg.show();
    }

    private void cmdDialog() {
        LinearLayout box = UiKit.vcol(this, 10);
        final EditText in = field(box, "دستور کنسول سرور (مثلاً op gaser)", "");
        final AlertDialog dlg = new AlertDialog.Builder(this).create();
        dlg.setTitle("ارسال دستور");
        TextView b = UiKit.chip(this, "📤 ارسال", UiKit.KIND_ACCENT, new Runnable() {
            @Override public void run() {
                String c = in.getText().toString().trim();
                dlg.dismiss();
                if (c.length() > 0) vpsOp("cmd", c);
            }
        });
        box.addView(b, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(this, 48)));
        dlg.setView(box);
        dlg.show();
    }

    /** فعال‌سازی RCON روی سرور + تنظیم پالس (برای پنل داخل بازی) */
    private void rconDialog() {
        LinearLayout box = UiKit.vcol(this, 10);
        final EditText rh = field(box, "آدرس برای پالس RCON (IP سرور)",
                prefs.getRconHost().length() > 0 ? prefs.getRconHost() : prefs.getString("vps_host", ""));
        LinearLayout r = UiKit.hrow(this);
        final EditText rp = field(null, "پورت", String.valueOf(prefs.getRconPort()));
        final EditText rw = field(null, "رمز", prefs.getRconPassword());
        r.addView(rp, new LinearLayout.LayoutParams(0, UiKit.dp(this, 46), 0.35f));
        r.addView(rw, new LinearLayout.LayoutParams(0, UiKit.dp(this, 46), 0.65f));
        box.addView(r);

        final AlertDialog dlg = new AlertDialog.Builder(this).create();
        dlg.setTitle("RCON و اتصال پالس");
        TextView b1 = UiKit.chip(this, "🔐 فعال‌سازی RCON روی سرور", UiKit.KIND_ACCENT, new Runnable() {
            @Override public void run() { dlg.dismiss(); vpsOp("rcon", null); }
        });
        box.addView(b1, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(this, 46)));
        TextView b2 = UiKit.chip(this, "💾 ذخیرهٔ اطلاعات پالس", new Runnable() {
            @Override public void run() {
                prefs.setRconHost(rh.getText().toString().trim());
                try {
                    prefs.setRconPort(Integer.parseInt(rp.getText().toString().trim()));
                } catch (Exception e) {
                    prefs.setRconPort(25575);
                }
                prefs.setRconPassword(rw.getText().toString());
                dlg.dismiss();
                log("✅ اطلاعات پالس ذخیره شد (پنل داخل بازی از این اتصال استفاده می‌کند)");
            }
        });
        box.addView(b2, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(this, 46)));
        dlg.setView(box);
        dlg.show();
    }

    // =====================================================================
    // عملیات SSH
    // =====================================================================

    private void log(final String line) {
        runOnUiThread(new Runnable() { @Override public void run() {
            logBox.append(line);
            Homan.adviseOnLog(VpsPage.this, line);
        }});
    }

    private void vpsOp(final String op, final String arg) {
        if (busy) { log("⏳ یک عملیات در جریان است…"); return; }
        final String host = prefs.getString("vps_host", "");
        final String pass = prefs.getString("vps_pass", "");
        if (host.isEmpty() || pass.isEmpty()) { connectDialog(); return; }
        int port;
        try { port = Integer.parseInt(prefs.getString("vps_port", "22")); }
        catch (Exception e) { port = 22; }
        final VpsRemote r = new VpsRemote(host, port, prefs.getString("vps_user", "root"), pass);
        busy = true;
        exec.execute(new Runnable() { @Override public void run() {
            try {
                String out;
                if ("test".equals(op)) {
                    log("⟳ تست اتصال به " + host + " …");
                    out = r.safeExec("echo CONNECTED; uname -a; command -v java >/dev/null 2>&1 && java -version 2>&1 | head -1 || echo 'java not installed'", 30000);
                    Homan.event(VpsPage.this, out.contains("CONNECTED") ? "vps_ok" : "vps_fail");
                } else if ("setup".equals(op)) {
                    log("🚀 نصب خودکار روی VPS (۳ تا ۱۰ دقیقه)…");
                    out = r.safeExec(vpsSetupScript(), 600000);
                    if (out.contains("نصب کامل شد")) Homan.event(VpsPage.this, "install_done");
                } else if ("start".equals(op)) {
                    String mem = prefs.getString("vps_mem", "2G");
                    if (!mem.matches("\\d+[GgMm]")) mem = "2G";
                    log("▶ استارت سرور…");
                    out = r.safeExec("cd \"$HOME/mc\" && if screen -ls 2>/dev/null | grep -q '\\bmc\\.'; then echo ALREADY-RUNNING; else screen -dmS mc java -Xms512M -Xmx" + mem + " -jar paper.jar nogui && echo STARTED; fi", 30000);
                } else if ("stop".equals(op)) {
                    log("⏹ توقف سرور…");
                    out = r.safeExec("screen -S mc -X stuff 'stop\\015' 2>/dev/null; sleep 8; if screen -ls 2>/dev/null | grep -q '\\bmc\\.'; then screen -S mc -X quit 2>/dev/null; sleep 2; pkill -f 'paper.jar' 2>/dev/null; echo FORCE-KILLED; else echo STOPPED; fi", 40000);
                } else if ("status".equals(op)) {
                    out = r.safeExec("if screen -ls 2>/dev/null | grep -q '\\bmc\\.'; then echo '● سرور در حال اجراست'; else echo '○ سرور متوقف است'; fi; tail -n 4 \"$HOME/mc/logs/latest.log\" 2>/dev/null || echo 'لاگی نیست'", 30000);
                } else if ("rcon".equals(op)) {
                    log("🔐 فعال‌سازی RCON (پورت 25575، رمز atta1234)…");
                    out = r.safeExec(rconScript(), 30000);
                    log("ℹ بعدش یک بار توقف + استارت؛ پورت 25575 را در فایروال باز کن");
                } else if ("cmd".equals(op) && arg != null) {
                    log("> " + arg);
                    String cc = arg.replace("\"", "\\\"");
                    out = r.safeExec("screen -S mc -X stuff \"" + cc + "\\015\" 2>/dev/null && echo SENT || echo 'سرور روشن نیست'", 20000);
                } else {
                    out = null;
                }
                if (out != null) log(out);
            } catch (Throwable t) {
                log("❌ " + t);
                Homan.event(VpsPage.this, "vps_fail");
            } finally {
                busy = false;
            }
        }});
    }

    private String vpsSetupScript() {
        return "set -e\n"
                + "cd \"$HOME\"\nmkdir -p mc/plugins\n"
                + "if ! command -v java >/dev/null 2>&1; then\n"
                + "  echo 'نصب OpenJDK 21…'\n"
                + "  if [ \"$(id -u)\" = \"0\" ]; then apt-get update -qq && apt-get install -y -qq openjdk-21-jre-headless screen curl >/dev/null\n"
                + "  else sudo apt-get update -qq && sudo apt-get install -y -qq openjdk-21-jre-headless screen curl >/dev/null; fi\nfi\n"
                + "command -v curl >/dev/null 2>&1 || { if [ \"$(id -u)\" = \"0\" ]; then apt-get install -y -qq curl screen >/dev/null; else sudo apt-get install -y -qq curl screen >/dev/null; fi; }\n"
                + "cd \"$HOME/mc\"\n"
                + "if [ ! -s paper.jar ]; then\n"
                + "  echo 'دانلود Paper…'\n"
                + "  VER=$(curl -s --max-time 30 https://api.papermc.io/v2/projects/paper | grep -oE '\"[0-9]+(\\.[0-9]+)+\"' | tr -d '\"' | tail -1)\n"
                + "  BUILD=$(curl -s --max-time 30 \"https://api.papermc.io/v2/projects/paper/versions/$VER/builds\" | grep -o '\"build\":[0-9]*' | grep -o '[0-9]*' | tail -1)\n"
                + "  if [ -n \"$VER\" ] && [ -n \"$BUILD\" ]; then\n"
                + "    curl -sL -o paper.jar \"https://api.papermc.io/v2/projects/paper/versions/$VER/builds/$BUILD/downloads/paper-$VER-$BUILD.jar\"\n"
                + "  else echo '❌ دریافت Paper ممکن نشد (اینترنت VPS)'; fi\nfi\n"
                + "[ -s plugins/Geyser-Spigot.jar ] || curl -sL -o plugins/Geyser-Spigot.jar \"https://downloads.geysermc.org/v2/projects/geyser/versions/latest/builds/latest/downloads/spigot\"\n"
                + "[ -s plugins/Floodgate-Spigot.jar ] || curl -sL -o plugins/Floodgate-Spigot.jar \"https://downloads.geysermc.org/v2/projects/floodgate/versions/latest/builds/latest/downloads/spigot\"\n"
                + "[ -s plugins/ViaVersion.jar ] || { URL=$(curl -s https://api.github.com/repos/ViaVersion/ViaVersion/releases/latest | grep -o '\"browser_download_url\":\"[^\"]*\\.jar\"' | head -1 | cut -d'\"' -f4); [ -n \"$URL\" ] && curl -sL -o plugins/ViaVersion.jar \"$URL\" || true; }\n"
                + "(grep -q 'eula=true' eula.txt 2>/dev/null) || echo 'eula=true' > eula.txt\n"
                + "echo '✅ نصب کامل شد'\nls -la paper.jar 2>/dev/null; java -version 2>&1 | head -1\n";
    }

    private String rconScript() {
        return "cd \"$HOME/mc\"\n"
                + "if [ -f server.properties ]; then\n"
                + "  sed -i 's/^enable-rcon=.*/enable-rcon=true/; s/^rcon.port=.*/rcon.port=25575/; s/^rcon.password=.*/rcon.password=atta1234/' server.properties\n"
                + "  grep -q '^enable-rcon' server.properties || printf '\\nenable-rcon=true\\nrcon.port=25575\\nrcon.password=atta1234\\n' >> server.properties\n"
                + "  echo 'RCON فعال شد → یک بار توقف+استارت'\n"
                + "else echo 'اول یک بار استارت بزن تا server.properties ساخته شود'; fi\n";
    }
}
