package com.atta.mcpanel.overlay.tabs;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.atta.mcpanel.core.Palette;
import com.atta.mcpanel.overlay.PanelHost;
import com.atta.mcpanel.overlay.UiKit;

/** زبانهٔ «سرور» — اتصال RCON و کنسول سرورهای شخصی */
public final class ServerTab {

    private ServerTab() {}

    private static final int LOG_LIMIT = 90000;

    public static View build(final PanelHost host) {
        final Context ctx = host.getContext();
        final LinearLayout col = UiKit.vcol(ctx, 14);

        // ---- اتصال
        col.addView(UiKit.sectionLabel(ctx, "اتصال RCON به سرور شخصی"));
        col.addView(UiKit.caption(ctx,
                "سرور روی همین دستگاه: localhost · روی سیستم دیگر: IP همان سیستم در شبکه", false));

        final EditText hostEt = host.newInput(
                "آدرس سرور", InputType.TYPE_CLASS_TEXT, host.getPrefs().getRconHost());
        final EditText portEt = host.newInput(
                "پورت", InputType.TYPE_CLASS_NUMBER,
                String.valueOf(host.getPrefs().getRconPort()));
        final EditText passEt = host.newInput(
                "رمز RCON", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD,
                host.getPrefs().getRconPassword());

        LinearLayout topRow = UiKit.hrow(ctx);
        topRow.addView(hostEt, new LinearLayout.LayoutParams(
                0, UiKit.dp(ctx, 46), 1f));
        topRow.addView(portEt, new LinearLayout.LayoutParams(
                UiKit.dp(ctx, 92), UiKit.dp(ctx, 46)));
        col.addView(topRow, UiKit.wrapParams(topRow, 4, 46));
        col.addView(passEt, UiKit.wrapParams(passEt, 4, 46));

        // ---- نوار وضعیت
        final LinearLayout statusRow = UiKit.hrow(ctx);
        statusRow.setPadding(0, UiKit.dp(ctx, 8), 0, UiKit.dp(ctx, 4));
        final View dot = UiKit.dot(ctx, Palette.DOT_OFF, 10);
        statusRow.addView(dot, new LinearLayout.LayoutParams(
                UiKit.dp(ctx, 10), UiKit.dp(ctx, 10)));
        ((LinearLayout.LayoutParams) dot.getLayoutParams()).leftMargin = UiKit.dp(ctx, 8);
        ((LinearLayout.LayoutParams) dot.getLayoutParams()).rightMargin = UiKit.dp(ctx, 2);
        final TextView tvState = new TextView(ctx);
        tvState.setTextSize(12.5f);
        tvState.setTextColor(Palette.TEXT_SUB);
        statusRow.addView(tvState, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        final TextView btnConnect = UiKit.chip(ctx, "اتصال", UiKit.KIND_ACCENT, null);
        statusRow.addView(btnConnect, new LinearLayout.LayoutParams(
                UiKit.dp(ctx, 120), UiKit.dp(ctx, 42)));
        col.addView(statusRow);

        // ---- کنسول
        col.addView(UiKit.sectionLabel(ctx, "کنسول سرور"));
        final ScrollView logScroll = new ScrollView(ctx);
        logScroll.setBackgroundColor(Palette.LOG_BG);
        logScroll.setVerticalScrollBarEnabled(false);
        logScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        final TextView logTv = new TextView(ctx);
        logTv.setTextSize(12f);
        logTv.setTextColor(Palette.TEXT_MAIN);
        logTv.setLineSpacing(0f, 1.1f);
        logTv.setPadding(UiKit.dp(ctx, 10), UiKit.dp(ctx, 8),
                UiKit.dp(ctx, 10), UiKit.dp(ctx, 8));
        logScroll.addView(logTv, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams logLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(ctx, 170));
        logLp.setMargins(UiKit.dp(ctx, 4), 0, UiKit.dp(ctx, 4), 0);
        col.addView(logScroll, logLp);

        host.registerConsoleListener(new PanelHost.ConsoleListener() {
            @Override public void onLine(String line) {
                if (logTv.length() > 0) logTv.append("\n");
                logTv.append(line);
                if (logTv.length() > LOG_LIMIT) {
                    logTv.setText(logTv.getText().subSequence(
                            logTv.length() - LOG_LIMIT, logTv.length()));
                }
                logScroll.post(new Runnable() {
                    @Override public void run() {
                        logScroll.fullScroll(View.FOCUS_DOWN);
                    }
                });
            }
        });

        // ---- ارسال دستور
        final LinearLayout sendRow = UiKit.hrow(ctx);
        final EditText inputEt = host.newInput(
                "دستور اپراتور — بدون / مثل op Steve", InputType.TYPE_CLASS_TEXT);
        sendRow.addView(inputEt, new LinearLayout.LayoutParams(
                0, UiKit.dp(ctx, 46), 1f));
        final TextView btnSend = UiKit.chip(ctx, "ارسال", UiKit.KIND_ACCENT, new Runnable() {
            @Override public void run() {
                String cmd = inputEt.getText().toString().trim();
                if (cmd.isEmpty()) return;
                logTv.append("\n> " + cmd);
                logScroll.post(new Runnable() {
                    @Override public void run() { logScroll.fullScroll(View.FOCUS_DOWN); }
                });
                host.sendConsole(cmd);
                inputEt.setText("");
            }
        });
        sendRow.addView(btnSend, new LinearLayout.LayoutParams(
                UiKit.dp(ctx, 84), UiKit.dp(ctx, 46)));
        col.addView(sendRow, UiKit.wrapParams(sendRow, 4, 46));

        // ---- دستورهای سریع
        col.addView(UiKit.sectionLabel(ctx, "دستورهای سریع سرور", false));
        UiKit.chipRow(ctx, new TextView[]{
                UiKit.chip(ctx, "👥 فهرست بازیکن‌ها", new Runnable() {
                    @Override public void run() { host.sendConsole("list"); }
                }),
                UiKit.chip(ctx, "🌱 بذر دنیا", new Runnable() {
                    @Override public void run() { host.sendConsole("seed"); }
                }),
                UiKit.chip(ctx, "💾 ذخیرهٔ فوری", new Runnable() {
                    @Override public void run() { host.sendConsole("save-all"); }
                }),
                UiKit.chip(ctx, "❓ help", new Runnable() {
                    @Override public void run() { host.sendConsole("help"); }
                }),
        }, host.cols(4), col);

        final TextView btnStop = UiKit.chip(ctx, "⛔ استاپ کامل سرور", UiKit.KIND_DANGER,
                new Runnable() {
                    @Override public void run() {
                        host.confirm("استاپ سرور",
                                "سرور برای همهٔ بازیکن‌ها بسته می‌شود. مطمئنی؟",
                                new Runnable() {
                                    @Override public void run() { host.sendConsole("stop"); }
                                });
                    }
                });
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(ctx, 46));
        stopLp.setMargins(UiKit.dp(ctx, 4), UiKit.dp(ctx, 6), UiKit.dp(ctx, 4), 0);
        col.addView(btnStop, stopLp);

        // ---- به‌روزرسانی وضعیت
        host.registerStateListener(new PanelHost.StateListener() {
            @Override public void onState() { updateState(host, dot, tvState, btnConnect); }
        });
        updateState(host, dot, tvState, btnConnect);

        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (host.isRconConnected()) {
                    host.rconDisconnect();
                } else {
                    saveFields(host, hostEt, portEt, passEt);
                    if (host.rconConnect()) {
                        logTv.append("\n→ در حال اتصال به " + host.getPrefs().getRconHost()
                                + ":" + host.getPrefs().getRconPort() + " …");
                        logScroll.post(new Runnable() {
                            @Override public void run() {
                                logScroll.fullScroll(View.FOCUS_DOWN);
                            }
                        });
                    }
                }
            }
        });
        btnConnect.setClickable(true);

        ScrollView sv = new ScrollView(ctx);
        sv.setFillViewport(true);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(col);
        return sv;
    }

    private static void saveFields(PanelHost host, EditText h, EditText p, EditText pass) {
        host.getPrefs().setRconHost(h.getText().toString());
        String pt = p.getText().toString().trim();
        host.getPrefs().setRconPort(pt.isEmpty() ? host.getPrefs().getRconPort()
                : Integer.parseInt(pt));
        host.getPrefs().setRconPassword(pass.getText().toString());
    }

    private static void updateState(PanelHost host, View dot, TextView tv, TextView btn) {
        boolean connected = host.isRconConnected();
        boolean attempting = host.isRconAttempting();
        int color = connected ? Palette.DOT_OK : (attempting ? Palette.DOT_BUSY : Palette.DOT_BAD);
        UiKit.dotColor(dot, color);
        tv.setText(connected ? "وصل است — می‌توانی دستور بدهی"
                : attempting ? "در حال اتصال…" : "آفلاین — دکمهٔ اتصال را بزن");
        tv.setTextColor(connected ? Palette.DOT_OK : Palette.TEXT_SUB);
        btn.setText(connected ? "قطع اتصال" : "اتصال");
        btn.setGravity(Gravity.CENTER);
    }
}
