package com.atta.mcpanel.overlay.tabs;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.atta.mcpanel.core.Palette;
import com.atta.mcpanel.overlay.PanelHost;
import com.atta.mcpanel.overlay.UiKit;

/** زبانهٔ «دنیا» — سختی، گیمرول‌ها و مدیریت بازیکن‌ها */
public final class WorldTab {

    private WorldTab() {}

    public static View build(final PanelHost host) {
        final Context ctx = host.getContext();
        final LinearLayout col = UiKit.vcol(ctx, 14);

        // ---- نوار وضعیت
        final LinearLayout statusRow = UiKit.hrow(ctx);
        final View dot = UiKit.dot(ctx, Palette.DOT_OFF, 10);
        statusRow.addView(dot, new LinearLayout.LayoutParams(
                UiKit.dp(ctx, 10), UiKit.dp(ctx, 10)));
        ((LinearLayout.LayoutParams) dot.getLayoutParams()).leftMargin = UiKit.dp(ctx, 8);
        final TextView tvWorld = new TextView(ctx);
        tvWorld.setTextSize(12.5f);
        tvWorld.setTextColor(Palette.TEXT_SUB);
        statusRow.addView(tvWorld, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        final TextView btnConn = UiKit.chip(ctx, "اتصال", UiKit.KIND_ACCENT, null);
        statusRow.addView(btnConn, new LinearLayout.LayoutParams(
                UiKit.dp(ctx, 100), UiKit.dp(ctx, 42)));
        col.addView(statusRow);
        col.addView(UiKit.caption(ctx, "برای دستورهای مدیریتی سرور، اتصال RCON لازم است", true));

        // ---- سختی
        col.addView(UiKit.sectionLabel(ctx, "سختی دنیا"));
        UiKit.chipRow(ctx, new TextView[]{
                UiKit.chip(ctx, "🕊 صلح‌آمیز", new Runnable() {
                    @Override public void run() { host.quickCommand("/difficulty peaceful", true); }
                }),
                UiKit.chip(ctx, "😊 آسان", new Runnable() {
                    @Override public void run() { host.quickCommand("/difficulty easy", true); }
                }),
                UiKit.chip(ctx, "🙂 عادی", new Runnable() {
                    @Override public void run() { host.quickCommand("/difficulty normal", true); }
                }),
                UiKit.chip(ctx, "😈 سخت", new Runnable() {
                    @Override public void run() { host.quickCommand("/difficulty hard", true); }
                }),
        }, host.cols(4), col);

        // ---- گیمرول
        col.addView(UiKit.sectionLabel(ctx, "قانون‌های دنیا (Gamerule)"));
        col.addView(UiKit.caption(ctx, "لمس کوتاه = روشن · لمس بلند = خاموش", true));
        final String[][] rules = {
                {"حفظ آیتم‌ها پس از مرگ", "keepInventory"},
                {"چرخهٔ شبانه‌روز", "doDaylightCycle"},
                {"تغییر آب‌وهوا", "doWeatherCycle"},
                {"گسترش آتش", "doFireTick"},
                {"تخریب توسط موب‌ها", "mobGriefing"},
                {"زاییده‌شدن موب‌ها", "doMobSpawning"},
                {"افتادن بلوک‌ها", "doTileDrops"},
                {"افتادن آیتم موب‌ها", "doMobLoot"},
        };
        TextView[] ruleChips = new TextView[rules.length];
        for (int i = 0; i < rules.length; i++) {
            final String rule = rules[i][1];
            ruleChips[i] = UiKit.chipToggle(ctx, rules[i][0], rule,
                    new Runnable() {
                        @Override public void run() {
                            host.quickCommand("/gamerule " + rule + " true", true);
                        }
                    },
                    new Runnable() {
                        @Override public void run() {
                            host.quickCommand("/gamerule " + rule + " false", true);
                        }
                    });
        }
        UiKit.chipRow(ctx, ruleChips, host.cols(2), col);

        // ---- دنیا
        col.addView(UiKit.sectionLabel(ctx, "دنیا", false));
        UiKit.chipRow(ctx, new TextView[]{
                UiKit.chip(ctx, "🌱 بذر (Seed)", new Runnable() {
                    @Override public void run() { host.quickCommand("/seed", true); }
                }),
                UiKit.chip(ctx, "💾 ذخیرهٔ فوری", new Runnable() {
                    @Override public void run() { host.sendConsole("save-all"); }
                }),
                UiKit.chip(ctx, "🌦 تغییر هوا", new Runnable() {
                    @Override public void run() { host.quickCommand("/toggledownfall", true); }
                }),
                UiKit.chip(ctx, "🗑 پاک‌کردن آیتم‌ها", new Runnable() {
                    @Override public void run() {
                        host.quickCommand("/kill @e[type=item]", true);
                    }
                }),
        }, host.cols(2), col);

        // ---- مدیریت بازیکن
        col.addView(UiKit.sectionLabel(ctx, "مدیریت بازیکن‌ها — اپراتور سرور"));
        col.addView(UiKit.caption(ctx,
                "نام دقیق بازیکن را وارد کن؛ این دستورها فقط از طریق RCON روی سرور شخصی اجرا می‌شود",
                true));
        final String suggested = host.getPrefs().getPlayerName();
        final String[][] manage = {
                {"👑 اپ کردن", "op"},
                {"🚫 اپ برداشتن", "deop"},
                {"🥾 بیرون کردن", "kick"},
                {"🔨 بن کردن", "ban"},
                {"🕊 بخشیدن", "pardon"},
                {"📋 وایت‌لیست", "whitelist add"},
        };
        TextView[] manageChips = new TextView[manage.length];
        for (int i = 0; i < manage.length; i++) {
            final String cmd = manage[i][1];
            manageChips[i] = UiKit.chip(ctx, manage[i][0], UiKit.KIND_PLAIN,
                    cmd, new Runnable() {
                        @Override public void run() {
                            host.promptText("نام بازیکن",
                                    "نام دقیق داخل بازی (مثل Steve)",
                                    suggested.equals("@s") ? "" : suggested,
                                    new PanelHost.OnTextResult() {
                                        @Override public void onResult(String name) {
                                            host.quickCommand("/" + cmd + " " + name, false);
                                        }
                                    });
                        }
                    });
        }
        UiKit.chipRow(ctx, manageChips, host.cols(2), col);

        // ---- به‌روزرسانی وضعیت
        host.registerStateListener(new PanelHost.StateListener() {
            @Override public void onState() { updateUi(host, dot, tvWorld, btnConn); }
        });
        btnConn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (host.isRconConnected()) host.rconDisconnect();
                else host.rconConnect();
            }
        });
        btnConn.setClickable(true);
        updateUi(host, dot, tvWorld, btnConn);

        ScrollView sv = new ScrollView(ctx);
        sv.setFillViewport(true);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(col);
        return sv;
    }

    private static void updateUi(PanelHost host, View dot, TextView tv, TextView btn) {
        boolean connected = host.isRconConnected();
        boolean attempting = host.isRconAttempting();
        UiKit.dotColor(dot, connected ? Palette.DOT_OK
                : attempting ? Palette.DOT_BUSY : Palette.DOT_BAD);
        tv.setText(connected ? "به کنسول سرور وصل است — همهٔ دستورها اجرا می‌شود"
                : attempting ? "در حال اتصال به کنسول…"
                : "کنسول آفلاین است — دستورهای چت‌پذیر کپی می‌شوند");
        btn.setText(connected ? "قطع" : "اتصال");
    }
}
