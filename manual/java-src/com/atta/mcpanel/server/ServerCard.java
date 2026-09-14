package com.atta.mcpanel.server;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.atta.mcpanel.aternos.AternosPanel;
import com.atta.mcpanel.aternos.MiniJson;
import com.atta.mcpanel.core.AppPrefs;
import com.atta.mcpanel.core.Palette;
import com.atta.mcpanel.overlay.UiKit;
import com.atta.mcpanel.ssh.VpsRemote;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * کارت «سرور من» — مدیریت مینیمال Aternos و VPS مستقیم روی صفحهٔ اصلی.
 * بدون متن راهنما، بدون بخش اضافه: وضعیت + دکمه‌های کار + یک کادر لاگ.
 * لمس طولانی روی کادر لاگ = کپی گزارش.
 */
public final class ServerCard {

    private final Activity act;
    private final AppPrefs prefs;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService exec = Executors.newSingleThreadExecutor();

    private AternosPanel aternos;
    private LinearLayout boxAt, boxVp, consoleRow;
    private TextView statusTv, logTv, segAt, segVp;
    private int seg = 0;               // 0=Aternos  1=VPS
    private boolean busy = false;
    private boolean consoleOn = false;

    private ServerCard(Activity act) {
        this.act = act;
        this.prefs = new AppPrefs(act);
    }

    public static void attach(Activity act, LinearLayout into) {
        new ServerCard(act).build(into);
    }

    // =====================================================================
    // ساخت کارت
    // =====================================================================

    private void build(LinearLayout into) {
        LinearLayout card = UiKit.vcol(act, 12);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF17202C, 0xFF10151D});
        g.setCornerRadius(UiKit.dp(act, 18));
        g.setStroke(1, 0x33FFFFFF);
        card.setBackground(g);

        // سربرگ
        TextView title = new TextView(act);
        title.setText("🖥 سرور من");
        title.setTextSize(16f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Palette.TEXT_MAIN);
        card.addView(title, UiKit.wrapParams(title, 2, 0));

        // سوییچ Aternos / VPS
        LinearLayout segRow = UiKit.hrow(act);
        segAt = segChip("🌐 Aternos");
        segVp = segChip("🛰 VPS");
        segRow.addView(segAt, new LinearLayout.LayoutParams(0, UiKit.dp(act, 40), 1f));
        segRow.addView(segVp, new LinearLayout.LayoutParams(0, UiKit.dp(act, 40), 1f));
        card.addView(segRow, UiKit.wrapParams(segRow, 2, 6));
        applySeg();

        // خط وضعیت
        statusTv = new TextView(act);
        statusTv.setTextSize(14f);
        statusTv.setTypeface(Typeface.DEFAULT_BOLD);
        statusTv.setMinLines(1);
        statusTv.setText("Aternos: متصل نیست");
        statusTv.setTextColor(0xFF9AA7BA);
        GradientDrawable sg = UiKit.roundedSolid(0xFF0D1420, UiKit.dp(act, 10));
        sg.setStroke(1, 0x30FFFFFF);
        statusTv.setBackground(sg);
        statusTv.setPadding(UiKit.dp(act, 10), UiKit.dp(act, 8), UiKit.dp(act, 10), UiKit.dp(act, 8));
        card.addView(statusTv, UiKit.wrapParams(statusTv, 2, 6));

        // ---------------- بخش Aternos ----------------
        boxAt = UiKit.vcol(act, 0);
        UiKit.chipRow(act, new TextView[]{
                UiKit.chip(act, "🔑 ورود", UiKit.KIND_ACCENT, new Runnable() {
                    @Override public void run() { aternos().begin(); }
                }),
                UiKit.chip(act, "🔄 وضعیت", new Runnable() {
                    @Override public void run() {
                        if (aternos().isReady()) aternos().reloadServerPage();
                        else aternos().begin();
                    }
                })
        }, 2, boxAt);
        UiKit.chipRow(act, new TextView[]{
                UiKit.chip(act, "▶ استارت", UiKit.KIND_ACCENT, new Runnable() {
                    @Override public void run() { aternos().doAction("start"); }
                }),
                UiKit.chip(act, "⏹ توقف", UiKit.KIND_DANGER, new Runnable() {
                    @Override public void run() { aternos().doAction("stop"); }
                }),
                UiKit.chip(act, "🔁 ری‌استارت", new Runnable() {
                    @Override public void run() { aternos().doAction("restart"); }
                })
        }, 3, boxAt);
        UiKit.chipRow(act, new TextView[]{
                UiKit.chip(act, "🟢 کنسول", new Runnable() {
                    @Override public void run() { toggleConsole(); }
                })
        }, 1, boxAt);

        // ورودی کنسول (پنهان تا فعال‌شدن)
        consoleRow = UiKit.vcol(act, 0);
        final EditText cmdIn = new EditText(act);
        cmdIn.setHint("دستور (مثلاً list یا op gaser)");
        cmdIn.setTextSize(13.5f);
        cmdIn.setTextColor(Palette.TEXT_MAIN);
        cmdIn.setHintTextColor(Palette.TEXT_DIM);
        cmdIn.setSingleLine(true);
        GradientDrawable cg = UiKit.roundedSolid(Palette.INPUT_BG, UiKit.dp(act, 10));
        cg.setStroke(1, 0x30FFFFFF);
        cmdIn.setBackground(cg);
        cmdIn.setPadding(UiKit.dp(act, 10), 0, UiKit.dp(act, 10), 0);
        consoleRow.addView(cmdIn, UiKit.wrapParams(cmdIn, 2, 44));
        UiKit.chipRow(act, new TextView[]{
                UiKit.chip(act, "📤 ارسال دستور", UiKit.KIND_ACCENT, new Runnable() {
                    @Override public void run() {
                        String c = cmdIn.getText().toString().trim();
                        if (c.length() == 0) { toast("دستور را بنویس"); return; }
                        log("> " + c);
                        aternos().sendConsoleCommand(c.startsWith("/") ? c.substring(1) : c);
                    }
                })
        }, 1, consoleRow);
        consoleRow.setVisibility(View.GONE);
        boxAt.addView(consoleRow, UiKit.wrapParams(consoleRow, 2, 0));
        card.addView(boxAt, UiKit.wrapParams(boxAt, 2, 0));

        // ---------------- بخش VPS ----------------
        boxVp = UiKit.vcol(act, 0);
        UiKit.chipRow(act, new TextView[]{
                UiKit.chip(act, "⚙ اتصال VPS", UiKit.KIND_ACCENT, new Runnable() {
                    @Override public void run() { vpsDialog(); }
                }),
                UiKit.chip(act, "🔄 تست", new Runnable() {
                    @Override public void run() { vpsOp("test", null); }
                })
        }, 2, boxVp);
        UiKit.chipRow(act, new TextView[]{
                UiKit.chip(act, "🚀 نصب خودکار", new Runnable() {
                    @Override public void run() { vpsOp("setup", null); }
                }),
                UiKit.chip(act, "▶ استارت", UiKit.KIND_ACCENT, new Runnable() {
                    @Override public void run() { vpsOp("start", null); }
                }),
                UiKit.chip(act, "⏹ توقف", UiKit.KIND_DANGER, new Runnable() {
                    @Override public void run() { vpsOp("stop", null); }
                })
        }, 3, boxVp);
        UiKit.chipRow(act, new TextView[]{
                UiKit.chip(act, "📊 وضعیت", new Runnable() {
                    @Override public void run() { vpsOp("status", null); }
                }),
                UiKit.chip(act, "📤 دستور", new Runnable() {
                    @Override public void run() { cmdDialog(); }
                }),
                UiKit.chip(act, "🔐 RCON", new Runnable() {
                    @Override public void run() { vpsOp("rcon", null); }
                })
        }, 3, boxVp);
        card.addView(boxVp, UiKit.wrapParams(boxVp, 2, 0));

        // کادر لاگ مشترک — لمس طولانی = کپی گزارش
        logTv = new TextView(act);
        logTv.setTextSize(10.5f);
        logTv.setTextColor(0xFFD6E0EA);
        logTv.setTypeface(Typeface.MONOSPACE);
        logTv.setGravity(Gravity.START | Gravity.TOP);
        GradientDrawable lg = UiKit.roundedSolid(0xFF0A0E13, UiKit.dp(act, 10));
        lg.setStroke(1, 0x30FFFFFF);
        logTv.setBackground(lg);
        logTv.setPadding(UiKit.dp(act, 8), UiKit.dp(act, 6), UiKit.dp(act, 8), UiKit.dp(act, 6));
        logTv.setText("— سرور من —");
        logTv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { copyReport(); return true; }
        });
        card.addView(logTv, UiKit.wrapParams(logTv, 2, 150));

        into.addView(card, UiKit.wrapParams(card, 2, 0));

        // نظرسنجی سبک وضعیت Aternos
        ui.postDelayed(new Runnable() { @Override public void run() {
            try {
                if (seg == 0 && aternos != null && aternos.isReady()) aternos.poll();
            } catch (Throwable ignored) {}
            ui.postDelayed(this, 6000);
        }}, 6000);
    }

    private TextView segChip(String label) {
        final TextView tv = new TextView(act);
        tv.setText(label);
        tv.setTextSize(13f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                seg = (tv == segVp) ? 1 : 0;
                applySeg();
            }
        });
        return tv;
    }

    private void applySeg() {
        GradientDrawable on = UiKit.roundedSolid(0xFF4E8A33, UiKit.dp(act, 999));
        GradientDrawable off = UiKit.roundedSolid(0xFF1D2532, UiKit.dp(act, 999));
        segAt.setBackground(seg == 0 ? on : off);
        segVp.setBackground(seg == 1 ? on : off);
        segAt.setTextColor(seg == 0 ? 0xFFFFFFFF : 0xFF9AA7BA);
        segVp.setTextColor(seg == 1 ? 0xFFFFFFFF : 0xFF9AA7BA);
        if (boxAt != null) boxAt.setVisibility(seg == 0 ? View.VISIBLE : View.GONE);
        if (boxVp != null) boxVp.setVisibility(seg == 1 ? View.VISIBLE : View.GONE);
    }

    // =====================================================================
    // Aternos
    // =====================================================================

    private AternosPanel aternos() {
        if (aternos == null) {
            aternos = new AternosPanel(act, prefs, new AternosPanel.Ui() {
                @Override public void atLog(String line) { log(line); }
                @Override public void atStatus(Map<String, Object> ls, String dom) { renderAt(ls, dom); }
                @Override public void atReady(String serverId) {
                    setStatus("Aternos: متصل ✓", 0xFF57C15B);
                }
                @Override public void atGone(String reason) {
                    setStatus("Aternos: متصل نیست"
                            + ("expired".equals(reason) ? " — نشست منقضی؛ «ورود» را بزن" : ""), 0xFF9AA7BA);
                }
                @Override public void atConsole(String line) { log(line); }
                @Override public void atPhase(String text, int color) { setStatus("Aternos: " + text, color); }
            });
        }
        return aternos;
    }

    private void renderAt(Map<String, Object> ls, String dom) {
        if ((ls == null || ls.isEmpty()) && (dom == null || dom.trim().isEmpty())) return;
        if (ls != null && !ls.isEmpty()) {
            double st = MiniJson.num(ls, "status", -1);
            String txt; int color = 0xFF9AA7BA;
            if (st == 0) { txt = "⏻ خاموش"; color = 0xFF9AA7BA; }
            else if (st == 1) { txt = "● روشن"; color = 0xFF57C15B; }
            else if (st == 2) { txt = "⟳ در حال روشن‌شدن…"; color = 0xFFF0B45B; }
            else if (st == 3) { txt = "⏳ در حال خاموش‌شدن…"; color = 0xFFF0B45B; }
            else if (st == 6) { txt = "⏳ در حال بارگذاری…"; color = 0xFFF0B45B; }
            else if (st == 7) { txt = "❌ خطا در سرور"; color = 0xFFE46B6B; }
            else if (st == 10) { txt = "⏳ در صف — «ری‌استارت» یا صبر"; color = 0xFFF0B45B; }
            else txt = "وضعیت: " + (int) st;
            String ip = MiniJson.str(ls, "ip", "");
            if (ip.length() > 0) txt += "  •  " + ip;
            setStatus("Aternos: " + txt, color);
        } else {
            String d = dom == null ? "" : dom.toLowerCase();
            if (d.contains("offline")) setStatus("Aternos: ⏻ خاموش", 0xFF9AA7BA);
            else if (d.contains("online")) setStatus("Aternos: ● روشن", 0xFF57C15B);
            else if (d.length() > 0) setStatus("Aternos: " + dom, 0xFF9AA7BA);
        }
    }

    private void toggleConsole() {
        consoleOn = !consoleOn;
        consoleRow.setVisibility(consoleOn ? View.VISIBLE : View.GONE);
        if (consoleOn) aternos().consoleConnect();
    }

    private void setStatus(String text, int color) {
        statusTv.setText(text);
        statusTv.setTextColor(color);
    }

    // =====================================================================
    // VPS
    // =====================================================================

    private void vpsDialog() {
        LinearLayout box = UiKit.vcol(act, 10);
        final EditText fHost = field(box, "آدرس VPS (IP)", prefs.getString("vps_host", ""));
        LinearLayout r1 = UiKit.hrow(act);
        final EditText fPort = field(null, "پورت SSH", prefs.getString("vps_port", "22"));
        final EditText fUser = field(null, "کاربر", prefs.getString("vps_user", "root"));
        r1.addView(fPort, new LinearLayout.LayoutParams(0, UiKit.dp(act, 44), 0.4f));
        r1.addView(fUser, new LinearLayout.LayoutParams(0, UiKit.dp(act, 44), 0.6f));
        box.addView(r1, UiKit.wrapParams(r1, 2, 0));
        final EditText fPass = field(box, "رمز SSH", prefs.getString("vps_pass", ""));
        fPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        final EditText fMem = field(box, "حافظهٔ جاوا (مثلاً 2G)", prefs.getString("vps_mem", "2G"));

        final AlertDialog dlg = new AlertDialog.Builder(act).create();
        dlg.setTitle("اتصال VPS");
        TextView bSave = UiKit.chip(act, "💾 ذخیره و تست", UiKit.KIND_ACCENT, new Runnable() {
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
        box.addView(bSave, UiKit.wrapParams(bSave, 2, 46));
        TextView bClose = UiKit.chip(act, "بستن", new Runnable() {
            @Override public void run() { dlg.dismiss(); }
        });
        box.addView(bClose, UiKit.wrapParams(bClose, 2, 40));
        dlg.setView(box);
        dlg.show();
    }

    private void cmdDialog() {
        LinearLayout box = UiKit.vcol(act, 10);
        final EditText in = field(box, "دستور کنسول سرور (مثلاً op gaser)", "");
        final AlertDialog dlg = new AlertDialog.Builder(act).create();
        dlg.setTitle("ارسال دستور به سرور VPS");
        TextView bSend = UiKit.chip(act, "📤 ارسال", UiKit.KIND_ACCENT, new Runnable() {
            @Override public void run() {
                String c = in.getText().toString().trim();
                dlg.dismiss();
                if (c.length() > 0) vpsOp("cmd", c);
            }
        });
        box.addView(bSend, UiKit.wrapParams(bSend, 2, 46));
        dlg.setView(box);
        dlg.show();
    }

    private EditText field(LinearLayout into, String hint, String val) {
        EditText et = new EditText(act);
        et.setHint(hint);
        if (val != null) et.setText(val);
        et.setTextSize(13.5f);
        et.setTextColor(Palette.TEXT_MAIN);
        et.setHintTextColor(Palette.TEXT_DIM);
        et.setSingleLine(true);
        GradientDrawable g = UiKit.roundedSolid(Palette.INPUT_BG, UiKit.dp(act, 10));
        g.setStroke(1, 0x30FFFFFF);
        et.setBackground(g);
        et.setPadding(UiKit.dp(act, 10), 0, UiKit.dp(act, 10), 0);
        if (into != null) into.addView(et, UiKit.wrapParams(et, 2, 44));
        return et;
    }

    private void vpsOp(final String op, final String arg) {
        if (busy) { toast("یک عملیات در جریان است…"); return; }
        final String host = prefs.getString("vps_host", "");
        final String pass = prefs.getString("vps_pass", "");
        if (host.isEmpty() || pass.isEmpty()) { vpsDialog(); return; }
        int port;
        try { port = Integer.parseInt(prefs.getString("vps_port", "22")); }
        catch (Exception e) { port = 22; }
        final VpsRemote r = new VpsRemote(host, port, prefs.getString("vps_user", "root"), pass);
        busy = true;
        exec.execute(new Runnable() { @Override public void run() {
            try {
                if ("test".equals(op)) {
                    log("⟳ تست اتصال به " + host + " …");
                    log(r.safeExec("echo CONNECTED; uname -a; command -v java >/dev/null 2>&1 && java -version 2>&1 | head -1 || echo 'java not installed'", 30000));
                } else if ("setup".equals(op)) {
                    log("🚀 نصب خودکار روی VPS (چند دقیقه)…");
                    log(r.safeExec(vpsSetupScript(), 600000));
                } else if ("start".equals(op)) {
                    String mem = prefs.getString("vps_mem", "2G");
                    if (!mem.matches("\\d+[GgMm]")) mem = "2G";
                    log("▶ استارت سرور VPS…");
                    log(r.safeExec("cd \"$HOME/mc\" && if screen -ls 2>/dev/null | grep -q '\\bmc\\.'; then echo ALREADY-RUNNING; else screen -dmS mc java -Xms512M -Xmx" + mem + " -jar paper.jar nogui && echo STARTED; fi", 30000));
                } else if ("stop".equals(op)) {
                    log("⏹ توقف سرور VPS…");
                    log(r.safeExec("screen -S mc -X stuff 'stop\\015' 2>/dev/null; sleep 8; if screen -ls 2>/dev/null | grep -q '\\bmc\\.'; then screen -S mc -X quit 2>/dev/null; sleep 2; pkill -f 'paper.jar' 2>/dev/null; echo FORCE-KILLED; else echo STOPPED; fi", 40000));
                } else if ("status".equals(op)) {
                    log(r.safeExec("if screen -ls 2>/dev/null | grep -q '\\bmc\\.'; then echo '● سرور در حال اجراست'; else echo '○ سرور متوقف است'; fi; tail -n 4 \"$HOME/mc/logs/latest.log\" 2>/dev/null || echo 'لاگی نیست'", 30000));
                } else if ("rcon".equals(op)) {
                    log("🔐 فعال‌سازی RCON (پورت 25575، رمز atta1234)…");
                    log(r.safeExec(rconScript(), 30000));
                    log("ℹ بعدش یک بار توقف + استارت بزن؛ پورت 25575 را در فایروال باز کن");
                } else if ("cmd".equals(op) && arg != null) {
                    log("> " + arg);
                    String cc = arg.replace("\"", "\\\"");
                    log(r.safeExec("screen -S mc -X stuff \"" + cc + "\\015\" 2>/dev/null && echo SENT || echo 'سرور روشن نیست'", 20000));
                }
            } catch (Throwable t) {
                log("❌ " + t);
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

    // =====================================================================
    // عمومی
    // =====================================================================

    private void log(final String line) {
        ui.post(new Runnable() { @Override public void run() {
            if (logTv == null || line == null || line.length() == 0) return;
            String cur = logTv.getText().toString();
            String next = (cur.length() == 0 || cur.equals("— سرور من —")) ? line : cur + "\n" + line;
            if (next.length() > 9000) next = next.substring(next.length() - 9000);
            logTv.setText(next);
        }});
    }

    private void copyReport() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("AttaPanel — گزارش سرور\nstate:\n");
            sb.append(aternos != null ? aternos.debugState() : "aternos=null").append("\n\nlog:\n");
            sb.append(logTv.getText().toString());
            ClipboardManager cm = (ClipboardManager) act.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("atta-report", sb.toString()));
            toast("گزارش کپی شد");
        } catch (Throwable t) {
            toast("کپی نشد");
        }
    }

    private void toast(String s) {
        Toast.makeText(act, s, Toast.LENGTH_SHORT).show();
    }
}
