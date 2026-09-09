package com.atta.mcpanel.overlay.tabs;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.atta.mcpanel.overlay.PanelHost;
import com.atta.mcpanel.overlay.UiKit;

/**
 * زبانهٔ «سازه‌ها» — ایده‌ها و قالب‌های ساخت: خانه، مزرعه، رداستون، فارم موب و…
 * هر قالب با یک لمس کپی می‌شود تا در بازی/چت بگذاری.
 */
public final class StructuresTab {

    private StructuresTab() {}

    private static final String[][] ITEMS = {
            {"🏡 خانهٔ اولیه (تخت)",
                    "سایز: ۹×۷ بلوک • دیوار چوبی + سقف تخت • در، دو پنجرهٔ شیشه‌ای، تخت، صندوق، کوره، میزکار — با ۵۰ بلوک چوب تمام می‌شود."},
            {"🏰 قلعهٔ دفاعی کوچک",
                    "سنگ ۲۵×۲۵، دیوار دورتادور ۳ بلوکی + کنگره، برج در چهار گوشه (۶×۶، ۱۰ بلوک بلندی)، خندق ۲ بلوکی دور دیوار، پل متحرک."},
            {"🌾 مزرعهٔ خودکار گندم",
                    "زمین ۱۰×۱۰ شخم‌زده + آب وسط + نرده؛ بالای هر ردیف نیم‌اسلب؟ خیر — پیستون + ناظر برای برداشت خودکار با سطل کمپوست."},
            {"🚂 ریل و ماین‌کارت سریع",
                    "خط ریل ۱۲۰ بلوکی با پیستون‌های تقویت‌کننده هر ۱۲ بلوک؛ ایستگاه رفت‌وبرگشت با ناظر + رداستون + مقایسه‌گر."},
            {"⚙ ساعت رداستونی (Redstone Clock)",
                    "دو مقایسه‌گر روبه‌روی هم + رداستون دورشان؛ تیک را با جابه‌جایی آیتم در دراپر تنظیم کن؛ مناسب تله‌ها و ماشین‌ها."},
            {"🚪 در مخفی (پیستونی)",
                    "در چوبی معمولی + پیستون چسبان پشتش + صفحهٔ فشار پنهان در دیوار؛ برای ورودی مخفی پایگاه عالی است."},
            {"🐄 فارم موب (Mob Farm)",
                    "برج ۲۸ بلوکی آب‌پایه؛ طبقهٔ اسپاون تاریک؛ قیف + صندوق در پایین؛ برای شب‌ها و گرفتن لوت خودکار."},
            {"🪣 مزرعهٔ آهن خودکار",
                    "روستایی + زامبی در قایق (برای ترساندن) + گولم آهنی؛ گولم در حوضچهٔ مرگ می‌افتد و قیف‌ها آهن را جمع می‌کنند."},
            {"🌋 تراپ (TNT) در ورودی",
                    "صفحهٔ فشار + TNT در زیر شن؛ مناسب دفاع از پایگاه در PvP — از راه دور با رداستون هم فعال می‌شود."},
            {"📦 انبار مرتب (Auto-Sorter)",
                    "ردیف صندوق‌های دوبل؛ پشت هر کدام قیف + مقایسه‌گر + آیتم قفل؛ آیتم‌ها خودکار دسته‌بندی می‌شوند."},
            {"⛵ بندر و قایق‌خانه",
                    "اسکلهٔ چوبی ۲۰ بلوکی + ۴ جای پارک قایق + فانوس دریایی ۱۲ بلوکی با درخشش؛ خانهٔ قایق کنار ساحل."},
            {"🛖 پایگاه کوهستانی",
                    "در دل کوه: ورودی پنهان، اتاق گنبدی ۱۱×۱۱، پنجره‌های شیشه‌ای رو به دره، پلکان مارپیچ به قله."},
    };

    public static View build(final PanelHost host) {
        final Context ctx = host.getContext();
        final LinearLayout col = UiKit.vcol(ctx, 10);

        col.addView(UiKit.caption(ctx,
                "📋 هر قالب را لمس کن تا متنش کپی شود؛ در چت/یادداشت بگذار و کنارش بساز.", false));
        TextView copyAll = UiKit.chip(ctx, "📋 کپی همهٔ قالب‌ها", UiKit.KIND_ACCENT, new Runnable() {
            @Override public void run() {
                StringBuilder sb = new StringBuilder();
                for (String[] it : ITEMS) sb.append(it[0]).append("\n").append(it[1]).append("\n\n");
                copy(ctx, sb.toString());
            }
        });
        col.addView(copyAll, UiKit.wrapParams(copyAll, 4, 40));

        for (final String[] it : ITEMS) {
            LinearLayout c2 = UiKit.vcol(ctx, 6);
            c2.setBackground(UiKit.roundedSolid(0x14FFFFFF, UiKit.dp(ctx, 10)));
            TextView t = UiKit.sectionLabel(ctx, it[0], false);
            c2.addView(t);
            TextView d = UiKit.caption(ctx, it[1], true);
            c2.addView(d);
            TextView b = UiKit.chip(ctx, "📋 کپی", new Runnable() {
                @Override public void run() { copy(ctx, it[0] + "\n" + it[1]); }
            });
            c2.addView(b, UiKit.wrapParams(b, 6, 34));
            col.addView(c2, UiKit.wrapParams(c2, 2, 0));
        }

        // ---- مأموریت‌های تک‌نفره
        col.addView(UiKit.sectionLabel(ctx, "🎯 مأموریت‌های تک‌نفره", false));
        final String[][] missions = {
                {"📗 مأموریت ۱ — روز نخست", "خانهٔ امن بساز • تخت و صندوق بگذار • ۱۲ گندم بکار. پاداش: جعبهٔ جوایز کنار تخت."},
                {"📘 مأموریت ۲ — شب‌زنده‌دار", "یک شب را بدون درِ خانه سر کن ولی زنده بمان (آتش/محصور). پاداش: ۱۰ زغال."},
                {"📙 مأموریت ۳ — کاشف", "به ۳ بیوم مختلف برو و از هر کدام یک بلوک خاص بیاور. پاداش: نقشه‌گردی."},
                {"📕 مأموریت ۴ — زرگر", "۵ الماس پیدا کن، کلنگ الماس بساز و با آن یک بار دیگر به عمق ۱۲ برو. پاداش: افسونِ غنیمت ۳."},
        };
        for (final String[] it : missions) {
            LinearLayout c3 = UiKit.vcol(ctx, 8);
            c3.setBackground(UiKit.roundedSolid(0x12B79BFF, UiKit.dp(ctx, 10)));
            c3.addView(UiKit.sectionLabel(ctx, it[0], false));
            TextView d3 = UiKit.caption(ctx, it[1], true);
            c3.addView(d3);
            TextView b3 = UiKit.chip(ctx, "📋 کپی مأموریت", new Runnable() {
                @Override public void run() { copy(ctx, it[0] + "\n" + it[1]); }
            });
            c3.addView(b3, UiKit.wrapParams(b3, 6, 34));
            col.addView(c3, UiKit.wrapParams(c3, 2, 0));
        }

        ScrollView sv = new ScrollView(ctx);
        sv.setFillViewport(true);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(col);
        return sv;
    }

    private static void copy(Context ctx, String s) {
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("atta", s));
        UiKit.vibrate(ctx, 25);
    }
}
