package com.atta.mcpanel.overlay.tabs;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.atta.mcpanel.overlay.PanelHost;
import com.atta.mcpanel.overlay.UiKit;

/**
 * زبانهٔ «آیتم‌ها» — گیو سریع لوازم/تجهیزات و کیت‌های آماده
 * شناسه‌ها هماهنگ با Minecraft Bedrock (و جاوا).
 * کیت‌ها چند دستور پشت‌سرهم می‌فرستند (فقط روی سرور RCON).
 */
public final class ItemsTab {

    private ItemsTab() {}

    private static void give(final PanelHost host, String item, String amount) {
        host.quickCommand("/give {p} " + item + " " + amount, true);
    }

    public static View build(final PanelHost host) {
        final Context ctx = host.getContext();
        final LinearLayout col = UiKit.vcol(ctx, 14);

        col.addView(UiKit.caption(ctx,
                "برای بازیکن هدف ({p}) در زبانهٔ «ابزارها» — یک‌کلیک اجرا می‌شود.",
                false));

        // ---- مصالح
        col.addView(UiKit.sectionLabel(ctx, "💎 مصالح و سنگ معدن"));
        UiKit.chipRow(ctx, new TextView[]{
                UiKit.chip(ctx, "الماس ×64", new Runnable() {
                    @Override public void run() { give(host, "diamond", "64"); }
                }),
                UiKit.chip(ctx, "ندرایت ×16", new Runnable() {
                    @Override public void run() { give(host, "netherite_ingot", "16"); }
                }),
                UiKit.chip(ctx, "زمرد ×32", new Runnable() {
                    @Override public void run() { give(host, "emerald", "32"); }
                }),
                UiKit.chip(ctx, "شمش طلا ×32", new Runnable() {
                    @Override public void run() { give(host, "gold_ingot", "32"); }
                }),
                UiKit.chip(ctx, "آهن ×64", new Runnable() {
                    @Override public void run() { give(host, "iron_ingot", "64"); }
                }),
                UiKit.chip(ctx, "رداستون ×64", new Runnable() {
                    @Override public void run() { give(host, "redstone", "64"); }
                }),
                UiKit.chip(ctx, "کوارتز ×64", new Runnable() {
                    @Override public void run() { give(host, "quartz", "64"); }
                }),
                UiKit.chip(ctx, "ابسیدین ×32", new Runnable() {
                    @Override public void run() { give(host, "obsidian", "32"); }
                }),
        }, host.cols(4), col);

        // ---- سلاح و ابزار
        col.addView(UiKit.sectionLabel(ctx, "⚔️ سلاح و ابزار"));
        UiKit.chipRow(ctx, new TextView[]{
                UiKit.chip(ctx, "شمش ندرایت", UiKit.KIND_ACCENT, new Runnable() {
                    @Override public void run() { give(host, "netherite_sword", "1"); }
                }),
                UiKit.chip(ctx, "شمش الماس", new Runnable() {
                    @Override public void run() { give(host, "diamond_sword", "1"); }
                }),
                UiKit.chip(ctx, "کلنگ ندرایت", new Runnable() {
                    @Override public void run() { give(host, "netherite_pickaxe", "1"); }
                }),
                UiKit.chip(ctx, "تبر ندرایت", new Runnable() {
                    @Override public void run() { give(host, "netherite_axe", "1"); }
                }),
                UiKit.chip(ctx, "بیل ندرایت", new Runnable() {
                    @Override public void run() { give(host, "netherite_shovel", "1"); }
                }),
                UiKit.chip(ctx, "کمان + تیر", new Runnable() {
                    @Override public void run() {
                        give(host, "bow", "1");
                        give(host, "arrow", "64");
                    }
                }),
                UiKit.chip(ctx, "سپر", new Runnable() {
                    @Override public void run() { give(host, "shield", "1"); }
                }),
                UiKit.chip(ctx, "ترس‌ناک‌ها ×32", new Runnable() {
                    @Override public void run() { give(host, "ender_pearl", "32"); }
                }),
        }, host.cols(4), col);

        // ---- زره
        col.addView(UiKit.sectionLabel(ctx, "🛡️ زره"));
        UiKit.chipRow(ctx, new TextView[]{
                UiKit.chip(ctx, "زره کامل الماس", UiKit.KIND_ACCENT, new Runnable() {
                    @Override public void run() {
                        host.kit(new String[]{
                                "/give {p} diamond_helmet 1",
                                "/give {p} diamond_chestplate 1",
                                "/give {p} diamond_leggings 1",
                                "/give {p} diamond_boots 1",
                        }, "زره کامل الماس");
                    }
                }),
                UiKit.chip(ctx, "زره کامل ندرایت", UiKit.KIND_ACCENT, new Runnable() {
                    @Override public void run() {
                        host.kit(new String[]{
                                "/give {p} netherite_helmet 1",
                                "/give {p} netherite_chestplate 1",
                                "/give {p} netherite_leggings 1",
                                "/give {p} netherite_boots 1",
                        }, "زره کامل ندرایت");
                    }
                }),
        }, host.cols(2), col);

        // ---- خوراکی‌ها
        col.addView(UiKit.sectionLabel(ctx, "🍎 خوراکی و درمان"));
        UiKit.chipRow(ctx, new TextView[]{
                UiKit.chip(ctx, "سیب طلایی ×16", new Runnable() {
                    @Override public void run() { give(host, "golden_apple", "16"); }
                }),
                UiKit.chip(ctx, "گوشت پخته ×32", new Runnable() {
                    @Override public void run() { give(host, "cooked_beef", "32"); }
                }),
                UiKit.chip(ctx, "نان ×32", new Runnable() {
                    @Override public void run() { give(host, "bread", "32"); }
                }),
                UiKit.chip(ctx, "ماهی پخته ×16", new Runnable() {
                    @Override public void run() { give(host, "cooked_cod", "16"); }
                }),
                UiKit.chip(ctx, "توتم ×8", new Runnable() {
                    @Override public void run() { give(host, "totem_of_undying", "8"); }
                }),
                UiKit.chip(ctx, "ایلیترا", new Runnable() {
                    @Override public void run() { give(host, "elytra", "1"); }
                }),
        }, host.cols(3), col);

        // ---- کیت‌های آماده (RCON)
        col.addView(UiKit.sectionLabel(ctx, "🎒 کیت‌های آماده (سرور شخصی)", false));
        col.addView(UiKit.caption(ctx, "کیت = چند دستور پشت‌سرهم؛ نیاز به اتصال RCON دارد.", true));

        UiKit.chipRow(ctx, new TextView[]{
                UiKit.chip(ctx, "🆕 کیت شروع", new Runnable() {
                    @Override public void run() {
                        host.kit(new String[]{
                                "/give {p} stone_pickaxe 1",
                                "/give {p} stone_axe 1",
                                "/give {p} bread 16",
                                "/give {p} torch 32",
                        }, "کیت شروع");
                    }
                }),
                UiKit.chip(ctx, "🏰 کیت ساختمان‌ساز", new Runnable() {
                    @Override public void run() {
                        host.kit(new String[]{
                                "/give {p} oak_planks 128",
                                "/give {p} cobblestone 128",
                                "/give {p} glass 64",
                                "/give {p} torch 64",
                        }, "کیت ساختمان‌ساز");
                    }
                }),
                UiKit.chip(ctx, "⚔️ کیت پی‌وی‌پی", new Runnable() {
                    @Override public void run() {
                        host.kit(new String[]{
                                "/give {p} netherite_sword 1",
                                "/give {p} netherite_helmet 1",
                                "/give {p} netherite_chestplate 1",
                                "/give {p} netherite_leggings 1",
                                "/give {p} netherite_boots 1",
                                "/give {p} golden_apple 16",
                        }, "کیت PvP");
                    }
                }),
                UiKit.chip(ctx, "💎 کیت الماس", new Runnable() {
                    @Override public void run() {
                        host.kit(new String[]{
                                "/give {p} diamond_sword 1",
                                "/give {p} diamond_pickaxe 1",
                                "/give {p} diamond 32",
                                "/give {p} golden_apple 8",
                        }, "کیت الماس");
                    }
                }),
        }, host.cols(2), col);

        col.addView(UiKit.caption(ctx,
                "شناسهٔ آیتم‌ها با بدراک و جاوا سازگار است (مثلاً netherite_sword, totem_of_undying).",
                true));

        ScrollView sv = new ScrollView(ctx);
        sv.setFillViewport(true);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(col);
        return sv;
    }
}
