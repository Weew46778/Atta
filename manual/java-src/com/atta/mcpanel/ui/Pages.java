package com.atta.mcpanel.ui;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.atta.mcpanel.core.Palette;
import com.atta.mcpanel.overlay.UiKit;

/**
 * ابزار صفحات جدید — هر بخش یک صفحهٔ مستقل:
 * تیتر درشت + دکمه‌های بزرگ + کادر لاگ با اسکرول داخلی.
 */
public final class Pages {

    private Pages() {}

    /** ریشهٔ صفحه: پس‌زمینهٔ تیره + ستون عمودی RTL — بدون اسکرول (محتوا جا می‌شود) */
    public static LinearLayout root(Activity a) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0E141D, 0xFF13201A, 0xFF0C1017});
        col.setBackground(bg);
        int p = UiKit.dp(a, 14);
        col.setPadding(p, UiKit.dp(a, 12), p, UiKit.dp(a, 12));
        return col;
    }

    /** نوار عنوان: بازگشت + عنوان درشت + دکمهٔ راهنمای صوتی هومن */
    public static LinearLayout titleBar(final Activity a, String title, final Runnable onHomanHelp) {
        LinearLayout bar = UiKit.hrow(a);
        TextView back = bigChip(a, "بازگشت", 0xFF1D2532, 0xFFC7D2E2, new Runnable() {
            @Override public void run() { a.finish(); }
        });
        back.getLayoutParams().height = UiKit.dp(a, 44);
        bar.addView(back, new LinearLayout.LayoutParams(UiKit.dp(a, 88), UiKit.dp(a, 44)));

        TextView t = new TextView(a);
        t.setText(title);
        t.setTextSize(21f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Palette.TEXT_MAIN);
        bar.addView(t, new LinearLayout.LayoutParams(0, UiKit.dp(a, 48), 1f));

        if (onHomanHelp != null) {
            TextView sp = bigChip(a, "🔊 هومن", 0xFF2A3B2A, 0xFF9FE08B, onHomanHelp);
            bar.addView(sp, new LinearLayout.LayoutParams(UiKit.dp(a, 96), UiKit.dp(a, 44)));
        }
        return bar;
    }

    /** دکمهٔ بزرگ و خوانا — ارتفاع ۵۶dp، متن ۱۶sp */
    public static TextView bigChip(Activity a, String label, int bg, int fg, Runnable onClick) {
        TextView tv = new TextView(a);
        tv.setText(label);
        tv.setTextSize(16f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(fg);
        tv.setGravity(Gravity.CENTER);
        tv.setClickable(true);
        GradientDrawable g = UiKit.roundedSolid(bg, UiKit.dp(a, 14));
        g.setStroke(1, 0x30FFFFFF);
        tv.setBackground(g);
        tv.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onClick.run(); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(a, 56));
        lp.setMargins(UiKit.dp(a, 3), 0, UiKit.dp(a, 3), UiKit.dp(a, 8));
        tv.setLayoutParams(lp);
        return tv;
    }

    /** دکمهٔ بزرگ با رنگ‌های پالت اپ */
    public static TextView action(Activity a, String label, int kind, Runnable onClick) {
        if (kind == 1) return bigChip(a, label, 0xFF3A6B26, 0xFFFFFFFF, onClick);
        if (kind == 2) return bigChip(a, label, 0xFF5C2323, 0xFFFFB3B3, onClick);
        return bigChip(a, label, 0xFF232C3B, 0xFFDBE4F0, onClick);
    }

    /** ردیف دوتایی از دکمه‌های بزرگ */
    public static void row2(Activity a, LinearLayout into, TextView a1, TextView a2) {
        LinearLayout r = UiKit.hrow(a);
        r.addView(a1, new LinearLayout.LayoutParams(0, UiKit.dp(a, 56), 1f));
        r.addView(a2, new LinearLayout.LayoutParams(0, UiKit.dp(a, 56), 1f));
        into.addView(r, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    /** خط وضعیت درشت و رنگی */
    public static TextView statusLine(Activity a, String initial) {
        TextView tv = new TextView(a);
        tv.setText(initial);
        tv.setTextSize(17f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(0xFF9AA7BA);
        tv.setMinLines(2);
        GradientDrawable g = UiKit.roundedSolid(0xFF0D1420, UiKit.dp(a, 12));
        g.setStroke(1, 0x30FFFFFF);
        tv.setBackground(g);
        int p = UiKit.dp(a, 12);
        tv.setPadding(p, UiKit.dp(a, 10), p, UiKit.dp(a, 10));
        return tv;
    }

    /** کادر لاگ با اسکرول داخلی + پرش خودکار به آخرین خط + لمس طولانی = کپی */
    public static final class LogBox {
        public final ScrollView sv;
        public final TextView tv;

        LogBox(ScrollView sv, TextView tv) { this.sv = sv; this.tv = tv; }

        public void append(String line) {
            if (line == null || line.length() == 0) return;
            String cur = tv.getText().toString();
            String next = (cur.length() == 0) ? line : cur + "\n" + line;
            if (next.length() > 9000) next = next.substring(next.length() - 9000);
            tv.setText(next);
            sv.post(new Runnable() { @Override public void run() { sv.fullScroll(View.FOCUS_DOWN); } });
        }

        public String text() { return tv.getText().toString(); }
    }

    public static LogBox logBox(Activity a, int heightDp) {
        TextView tv = new TextView(a);
        tv.setTextSize(12f);
        tv.setTextColor(0xFFD6E0EA);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setGravity(Gravity.START | Gravity.TOP);
        GradientDrawable g = UiKit.roundedSolid(0xFF0A0E13, UiKit.dp(a, 10));
        g.setStroke(1, 0x30FFFFFF);
        tv.setBackground(g);
        int p = UiKit.dp(a, 8);
        tv.setPadding(p, UiKit.dp(a, 6), p, UiKit.dp(a, 6));

        ScrollView sv = new ScrollView(a);
        sv.setFillViewport(true);
        sv.addView(tv, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(a, heightDp));
        lp.setMargins(UiKit.dp(a, 2), UiKit.dp(a, 6), UiKit.dp(a, 2), 0);
        sv.setLayoutParams(lp);

        final LogBox box = new LogBox(sv, tv);
        sv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                try {
                    ClipboardManager cm = (ClipboardManager) sv.getContext()
                            .getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("atta-log", box.text()));
                    Toast.makeText(sv.getContext(), "لاگ کپی شد", Toast.LENGTH_SHORT).show();
                } catch (Throwable ignored) {}
                return true;
            }
        });
        return box;
    }
}
