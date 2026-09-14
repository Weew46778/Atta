package com.atta.mcpanel.server;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.atta.mcpanel.aternos.AternosPanel;
import com.atta.mcpanel.core.Palette;
import com.atta.mcpanel.overlay.UiKit;
import com.atta.mcpanel.ui.Pages;
import com.atta.mcpanel.voice.Homan;

/** صفحهٔ کنسول سرور — لاگ بزرگ پرصفحه با اسکرول داخلی + ارسال دستور */
public class ConsolePage extends Activity {

    private Pages.LogBox logBox;
    private EditText cmdIn;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout col = Pages.root(this);
        col.addView(Pages.titleBar(this, "🖥 کنسول سرور", new Runnable() {
            @Override public void run() { Homan.intro(ConsolePage.this, "console"); }
        }));

        logBox = Pages.logBox(this, 430);
        logBox.append("— کنسول سرور —");

        AternosPanelHolder.attachLog(this);

        UiKit.chipRow(this, new android.widget.TextView[]{
                UiKit.chip(this, "🟢 اتصال کنسول", UiKit.KIND_ACCENT, new Runnable() {
                    @Override public void run() { AternosPanelHolder.connect(ConsolePage.this, logBox); }
                })
        }, 1, col);

        cmdIn = new EditText(this);
        cmdIn.setHint("دستور (مثلاً list یا op gaser)");
        cmdIn.setTextSize(15f);
        cmdIn.setTextColor(Palette.TEXT_MAIN);
        cmdIn.setHintTextColor(Palette.TEXT_DIM);
        cmdIn.setSingleLine(true);
        cmdIn.setInputType(InputType.TYPE_CLASS_TEXT);
        android.graphics.drawable.GradientDrawable g =
                UiKit.roundedSolid(Palette.INPUT_BG, UiKit.dp(this, 12));
        g.setStroke(1, 0x30FFFFFF);
        cmdIn.setBackground(g);
        cmdIn.setPadding(UiKit.dp(this, 12), 0, UiKit.dp(this, 12), 0);
        col.addView(cmdIn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(this, 54)));

        UiKit.chipRow(this, new android.widget.TextView[]{
                UiKit.chip(this, "📤 ارسال دستور", UiKit.KIND_ACCENT, new Runnable() {
                    @Override public void run() { send(); }
                }),
                UiKit.chip(this, "👥 list", new Runnable() {
                    @Override public void run() { sendNow("list"); }
                }),
                UiKit.chip(this, "💾 save-all", new Runnable() {
                    @Override public void run() { sendNow("save-all"); }
                })
        }, 3, col);

        col.addView(logBox.sv);
        setContentView(col);
        Homan.intro(this, "console");
    }

    private void send() {
        String c = cmdIn.getText().toString().trim();
        if (c.length() == 0) return;
        cmdIn.setText("");
        sendNow(c);
    }

    private void sendNow(String c) {
        logBox.append("> " + c);
        AternosPanelHolder.send(this, c.startsWith("/") ? c.substring(1) : c);
    }

    /** اتصال به موتور مشترک Aternos که در صفحهٔ اترنوس ساخته شده است */
    private static final class AternosPanelHolder {
        static void attachLog(ConsolePage page) { /* لاگ از طریق هنگام اتصال پر می‌شود */ }

        static void connect(final ConsolePage page, final Pages.LogBox box) {
            AternosPanel a = ServerState.get();
            if (a == null) {
                box.append("❌ اول از صفحهٔ «سرور Aternos» وارد شو و وصل شو، بعد به کنسول بیا");
                Homan.say(page, "برای کنسول، اول از صفحهٔ سرور اترنوس وصل شو.");
                return;
            }
            a.setConsoleSink(new AternosPanel.ConsoleSink() {
                @Override public void line(String l) { box.append(l); }
            });
            a.consoleConnect();
        }

        static void send(ConsolePage page, String cmd) {
            AternosPanel a = ServerState.get();
            if (a == null) {
                Homan.say(page, "اول از صفحهٔ سرور اترنوس وصل شو.");
                return;
            }
            a.sendConsoleCommand(cmd);
        }
    }
}
