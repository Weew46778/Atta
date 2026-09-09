package com.atta.mcpanel.overlay.tabs;

import android.content.Context;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.atta.mcpanel.overlay.PanelHost;
import com.atta.mcpanel.overlay.UiKit;

/**
 * زبانهٔ «ابزارها» — گیم‌مود، زمان، آب‌وهوا و دستور دلخواه (بدراک/جاوا)
 * همهٔ دکمه‌ها یک‌کلیکی: اگر اتصال RCON ذخیره شده باشد، خودکار وصل و اجرا می‌شود.
 */
public final class ControlsTab {

    private ControlsTab() {}

    public static View build(final PanelHost host) {
        final Context ctx = host.getContext();
        final LinearLayout col = UiKit.vcol(ctx, 14);

        col.addView(UiKit.caption(ctx,
                "⚡ یک‌کلیک: اگر اطلاعات سرورت ذخیره باشد، خودکار وصل و دستور اجرا می‌شود.",
                false));

        // ---- گیم‌مود
        col.addView(UiKit.sectionLabel(ctx, "حالت بازی (GameMode)"));
        UiKit.chipRow(ctx, new TextView[]{
                UiKit.chip(ctx, "🟢 خلاق", UiKit.KIND_ACCENT, new Runnable() {
                    @Override public void run() { host.quickCommand("/gamemode creative {p}", true); }
                }),
                UiKit.chip(ctx, "🔵 بقا", new Runnable() {
                    @Override public void run() { host.quickCommand("/gamemode survival {p}", true); }
                }),
                UiKit.chip(ctx, "🟠 ماجراجویی", new Runnable() {
                    @Override public void run() { host.quickCommand("/gamemode adventure {p}", true); }
                }),
        }, host.cols(3), col);

        // ---- زمان
        col.addView(UiKit.sectionLabel(ctx, "زمان روز"));
        UiKit.chipRow(ctx, new TextView[]{
                UiKit.chip(ctx, "🌅 سپیده‌دم", new Runnable() {
                    @Override public void run() { host.quickCommand("/time set 0", true); }
                }),
                UiKit.chip(ctx, "☀️ ظهر", new Runnable() {
                    @Override public void run() { host.quickCommand("/time set 6000", true); }
                }),
                UiKit.chip(ctx, "🌇 غروب", new Runnable() {
                    @Override public void run() { host.quickCommand("/time set 13000", true); }
                }),
                UiKit.chip(ctx, "🌙 نیمه‌شب", new Runnable() {
                    @Override public void run() { host.quickCommand("/time set 18000", true); }
                }),
        }, host.cols(4), col);

        // ---- آب‌وهوا
        col.addView(UiKit.sectionLabel(ctx, "آب‌وهوا"));
        UiKit.chipRow(ctx, new TextView[]{
                UiKit.chip(ctx, "☀️ صاف", new Runnable() {
                    @Override public void run() { host.quickCommand("/weather clear", true); }
                }),
                UiKit.chip(ctx, "🌧️ باران", new Runnable() {
                    @Override public void run() { host.quickCommand("/weather rain", true); }
                }),
                UiKit.chip(ctx, "⛈️ رعدوبرق", new Runnable() {
                    @Override public void run() { host.quickCommand("/weather thunder", true); }
                }),
                UiKit.chip(ctx, "🌦 تغییر هوا", new Runnable() {
                    @Override public void run() { host.quickCommand("/toggledownfall", true); }
                }),
        }, host.cols(4), col);

        // ---- اکشن‌های فوری اپراتور
        col.addView(UiKit.sectionLabel(ctx, "اکشن‌های فوری", false));
        UiKit.chipRow(ctx, new TextView[]{
                UiKit.chip(ctx, "🛡 شب سخت (همه)", UiKit.KIND_DANGER, new Runnable() {
                    @Override public void run() {
                        host.quickCommand("/time set 13000", true);
                        host.quickCommand("/weather thunder", true);
                        host.quickCommand("/effect @a slowness 15 1", true);
                    }
                }),
                UiKit.chip(ctx, "💾 ذخیرهٔ فوری", new Runnable() {
                    @Override public void run() { host.sendConsole("save-all"); }
                }),
                UiKit.chip(ctx, "👥 فهرست بازیکن‌ها", new Runnable() {
                    @Override public void run() { host.sendConsole("list"); }
                }),
        }, host.cols(3), col);

        // ---- دستور دلخواه
        col.addView(UiKit.sectionLabel(ctx, "دستور دلخواه", false));
        final LinearLayout sendRow = UiKit.hrow(ctx);
        final EditText customEt = host.newInput(
                "مثلاً: /effect @a speed 60 2  (بدراک/جاوا)", InputType.TYPE_CLASS_TEXT);
        sendRow.addView(customEt, new LinearLayout.LayoutParams(
                0, UiKit.dp(ctx, 48), 1f));
        TextView send = UiKit.chip(ctx, "اجرا", UiKit.KIND_ACCENT, new Runnable() {
            @Override public void run() {
                host.quickCommand(customEt.getText().toString(), true);
            }
        });
        sendRow.addView(send, new LinearLayout.LayoutParams(
                UiKit.dp(ctx, 88), UiKit.dp(ctx, 48)));
        col.addView(sendRow, UiKit.wrapParams(sendRow, 4, 48));

        ScrollView sv = new ScrollView(ctx);
        sv.setFillViewport(true);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(col);
        return sv;
    }
}
