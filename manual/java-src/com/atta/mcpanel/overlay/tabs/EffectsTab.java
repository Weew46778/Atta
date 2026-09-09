package com.atta.mcpanel.overlay.tabs;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.atta.mcpanel.overlay.PanelHost;
import com.atta.mcpanel.overlay.UiKit;

/**
 * زبانهٔ «افکت‌ها» — افکت‌های بدراک/جاوا با یک کلیک
 */
public final class EffectsTab {

    private EffectsTab() {}

    public static View build(final PanelHost host) {
        final Context ctx = host.getContext();
        final LinearLayout col = UiKit.vcol(ctx, 14);

        col.addView(UiKit.caption(ctx,
                "مدت پیش‌فرض ۶۰ ثانیه؛ روی بازیکن هدف ({p}) اعمال می‌شود. یک‌کلیک.",
                false));

        // ---- تقویت
        col.addView(UiKit.sectionLabel(ctx, "⚡ تقویت بازیکن هدف"));
        UiKit.chipRow(ctx, new TextView[]{
                UiKit.chip(ctx, "سرعت III", UiKit.KIND_ACCENT, new Runnable() {
                    @Override public void run() { host.quickCommand("/effect {p} speed 60 2", true); }
                }),
                UiKit.chip(ctx, "پرش III", new Runnable() {
                    @Override public void run() { host.quickCommand("/effect {p} jump_boost 60 2", true); }
                }),
                UiKit.chip(ctx, "قدرت II", new Runnable() {
                    @Override public void run() { host.quickCommand("/effect {p} strength 60 1", true); }
                }),
                UiKit.chip(ctx, "مقاومت III", new Runnable() {
                    @Override public void run() { host.quickCommand("/effect {p} resistance 60 2", true); }
                }),
                UiKit.chip(ctx, "بازیابی II", new Runnable() {
                    @Override public void run() { host.quickCommand("/effect {p} regeneration 60 1", true); }
                }),
                UiKit.chip(ctx, "سرعت حفاری II", new Runnable() {
                    @Override public void run() { host.quickCommand("/effect {p} haste 60 1", true); }
                }),
                UiKit.chip(ctx, "شب‌بینی", new Runnable() {
                    @Override public void run() { host.quickCommand("/effect {p} night_vision 60 0", true); }
                }),
                UiKit.chip(ctx, "آتش‌نشدن", new Runnable() {
                    @Override public void run() { host.quickCommand("/effect {p} fire_resistance 60 0", true); }
                }),
                UiKit.chip(ctx, "تنفس زیر آب", new Runnable() {
                    @Override public void run() { host.quickCommand("/effect {p} water_breathing 60 0", true); }
                }),
                UiKit.chip(ctx, "نامرئی", new Runnable() {
                    @Override public void run() { host.quickCommand("/effect {p} invisibility 60 0", true); }
                }),
                UiKit.chip(ctx, "💚 شفای فوری", new Runnable() {
                    @Override public void run() { host.quickCommand("/effect {p} instant_health 1 255", true); }
                }),
                UiKit.chip(ctx, "🍖 سیری کامل", new Runnable() {
                    @Override public void run() { host.quickCommand("/effect {p} saturation 60 10", true); }
                }),
        }, host.cols(3), col);

        // ---- برای همه
        col.addView(UiKit.sectionLabel(ctx, "🌐 برای همهٔ بازیکن‌ها", false));
        UiKit.chipRow(ctx, new TextView[]{
                UiKit.chip(ctx, "همه سریع", new Runnable() {
                    @Override public void run() { host.quickCommand("/effect @a speed 60 2", false); }
                }),
                UiKit.chip(ctx, "همه قدرت", new Runnable() {
                    @Override public void run() { host.quickCommand("/effect @a strength 60 1", false); }
                }),
                UiKit.chip(ctx, "همه شب‌بینی", new Runnable() {
                    @Override public void run() { host.quickCommand("/effect @a night_vision 60 0", false); }
                }),
                UiKit.chip(ctx, "همه مقاومت", new Runnable() {
                    @Override public void run() { host.quickCommand("/effect @a resistance 60 1", false); }
                }),
        }, host.cols(2), col);

        // ---- روی موجودات (برای اپراتور سرور)
        col.addView(UiKit.sectionLabel(ctx, "🐉 مدیریت موجودات (اپراتور)", false));
        UiKit.chipRow(ctx, new TextView[]{
                UiKit.chip(ctx, "کندکردن موب‌های نزدیک", new Runnable() {
                    @Override public void run() {
                        host.quickCommand("/effect @e[type=!player] slowness 20 3", false);
                    }
                }),
                UiKit.chip(ctx, "تضعیف موب‌های نزدیک", new Runnable() {
                    @Override public void run() {
                        host.quickCommand("/effect @e[type=!player] weakness 20 3", false);
                    }
                }),
                UiKit.chip(ctx, "سوزاندن موب‌های نزدیک", new Runnable() {
                    @Override public void run() {
                        host.quickCommand("/effect @e[type=!player] poison 10 2", false);
                    }
                }),
                UiKit.chip(ctx, "حذف همهٔ افکت‌ها", UiKit.KIND_DANGER, new Runnable() {
                    @Override public void run() { host.quickCommand("/effect @a clear", false); }
                }),
        }, host.cols(2), col);

        ScrollView sv = new ScrollView(ctx);
        sv.setFillViewport(true);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(col);
        return sv;
    }
}
