package com.atta.mcpanel.server;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.atta.mcpanel.aternos.AternosPanel;
import com.atta.mcpanel.aternos.MiniJson;
import com.atta.mcpanel.core.AppPrefs;
import com.atta.mcpanel.ui.Pages;
import com.atta.mcpanel.voice.Homan;
import com.atta.mcpanel.voice.PersianTts;

import java.util.Map;

/** صفحهٔ سرور Aternos — مینیمال، درشت، بدون اسکرول صفحه */
public class AternosPage extends Activity {

    private AppPrefs prefs;
    private AternosPanel aternos;
    private TextView statusTv;
    private Pages.LogBox logBox;
    private String lastStateKey = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new AppPrefs(this);
        PersianTts.init(this);
        PersianTts.setListener(new PersianTts.Listener() {
            @Override public void onSpeakFailure() { }
            @Override public void onEngine(String engine, String detail) { }
        });

        LinearLayout col = Pages.root(this);
        col.addView(Pages.titleBar(this, "🌐 سرور Aternos", new Runnable() {
            @Override public void run() { Homan.intro(AternosPage.this, "aternos"); }
        }));

        statusTv = Pages.statusLine(this, "وضعیت: متصل نیست");
        col.addView(statusTv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // دکمه‌های بزرگ — دو در هر ردیف
        Pages.row2(this, col,
                Pages.action(this, "🔑 ورود", 1, new Runnable() {
                    @Override public void run() { engine().begin(); }
                }),
                Pages.action(this, "🔄 وضعیت", 0, new Runnable() {
                    @Override public void run() {
                        if (engine().isReady()) engine().reloadServerPage();
                        else engine().begin();
                    }
                }));
        Pages.row2(this, col,
                Pages.action(this, "▶ استارت", 1, new Runnable() {
                    @Override public void run() { engine().doAction("start"); }
                }),
                Pages.action(this, "⏹ توقف", 2, new Runnable() {
                    @Override public void run() { engine().doAction("stop"); }
                }));
        Pages.row2(this, col,
                Pages.action(this, "🔁 ری‌استارت", 0, new Runnable() {
                    @Override public void run() { engine().doAction("restart"); }
                }),
                Pages.action(this, "🖥 کنسول", 0, new Runnable() {
                    @Override public void run() {
                        startActivity(new Intent(AternosPage.this, ConsolePage.class));
                    }
                }));

        logBox = Pages.logBox(this, 250);
        logBox.append("— سرور Aternos —");
        col.addView(logBox.sv);

        setContentView(col);
        Homan.intro(this, "aternos");
    }

    @Override
    protected void onDestroy() {
        if (aternos != null) {
            ServerState.clear(aternos);
            aternos.destroy();
            aternos = null;
        }
        super.onDestroy();
    }

    private AternosPanel engine() {
        if (aternos == null) {
            aternos = new AternosPanel(this, prefs, new AternosPanel.Ui() {
                @Override public void atLog(String line) {
                    logBox.append(line);
                    Homan.adviseOnLog(AternosPage.this, line);
                }
                @Override public void atStatus(Map<String, Object> ls, String dom) {
                    render(ls, dom);
                }
                @Override public void atReady(String serverId) {
                    status("وضعیت: متصل ✓", 0xFF57C15B);
                    Homan.event(AternosPage.this, "ready");
                }
                @Override public void atGone(String reason) {
                    status("وضعیت: متصل نیست"
                            + ("expired".equals(reason) ? " — نشست منقضی؛ «ورود» را بزن" : ""), 0xFF9AA7BA);
                    if ("expired".equals(reason)) Homan.event(AternosPage.this, "expired");
                }
                @Override public void atConsole(String line) { logBox.append(line); }
                @Override public void atPhase(String text, int color) {
                    status("وضعیت: " + text, color);
                }
            });
            ServerState.set(aternos);
        }
        return aternos;
    }

    private void render(Map<String, Object> ls, String dom) {
        if ((ls == null || ls.isEmpty()) && (dom == null || dom.trim().isEmpty())) return;
        if (ls != null && !ls.isEmpty()) {
            double st = MiniJson.num(ls, "status", -1);
            String txt; int color = 0xFF9AA7BA; String ev = null;
            if (st == 0) { txt = "⏻ خاموش"; color = 0xFF9AA7BA; ev = "server_off"; }
            else if (st == 1) { txt = "● روشن"; color = 0xFF57C15B; ev = "server_on"; }
            else if (st == 2 || st == 6) { txt = "⟳ در حال روشن‌شدن…"; color = 0xFFF0B45B; ev = "server_starting"; }
            else if (st == 3) { txt = "⏳ در حال خاموش‌شدن…"; color = 0xFFF0B45B; }
            else if (st == 7) { txt = "❌ خطا در سرور"; color = 0xFFE46B6B; }
            else if (st == 10) { txt = "⏳ در صف Aternos"; color = 0xFFF0B45B; }
            else txt = "وضعیت: " + (int) st;
            String ip = MiniJson.str(ls, "ip", "");
            if (ip.length() > 0) txt += "\n📍 " + ip;
            status(txt, color);
            if (ev != null && !ev.equals(lastStateKey)) {
                lastStateKey = ev;
                Homan.event(this, ev);
            }
        } else {
            String d = dom == null ? "" : dom.toLowerCase();
            if (d.contains("offline")) status("⏻ خاموش", 0xFF9AA7BA);
            else if (d.contains("online")) status("● روشن", 0xFF57C15B);
            else if (d.length() > 0) status(dom, 0xFF9AA7BA);
        }
    }

    private void status(String text, int color) {
        statusTv.setText(text);
        statusTv.setTextColor(color);
    }
}
