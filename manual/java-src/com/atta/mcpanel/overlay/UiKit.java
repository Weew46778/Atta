package com.atta.mcpanel.overlay;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.atta.mcpanel.core.Palette;

/** ابزار ساخت المان‌های رابط پنل */
public final class UiKit {

    public static final int KIND_PLAIN = 0;
    public static final int KIND_ACCENT = 1;
    public static final int KIND_DANGER = 2;

    private UiKit() {}

    public static int dp(Context ctx, float v) {
        return Math.round(ctx.getResources().getDisplayMetrics().density * v);
    }

    public static void vibrate(Context ctx, long ms) {
        try {
            Vibrator vib = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
            if (vib != null && vib.hasVibrator()) {
                vib.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------------ //

    /** یک دکمهٔ چیپ با لمس کوتاه */
    public static TextView chip(Context ctx, String label) {
        return chip(ctx, label, KIND_PLAIN, null, null);
    }

    public static TextView chip(Context ctx, String label, Runnable onClick) {
        return chip(ctx, label, KIND_PLAIN, null, onClick);
    }

    public static TextView chip(Context ctx, String label, int kind, Runnable onClick) {
        return chip(ctx, label, kind, null, onClick);
    }

    public static TextView chip(Context ctx, String label, int kind,
                                String hint, Runnable onClick) {
        TextView tv = baseChip(ctx, label, kind, hint);
        if (onClick != null) {
            tv.setClickable(true);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { onClick.run(); }
            });
        }
        return tv;
    }

    /** چیپ با لمس کوتاه و بلند */
    public static TextView chipToggle(Context ctx, String label, String hint,
                                      final Runnable onClick, final Runnable onLongClick) {
        TextView tv = baseChip(ctx, label, KIND_PLAIN, hint);
        if (onClick != null) {
            tv.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { onClick.run(); }
            });
        }
        if (onLongClick != null) {
            tv.setLongClickable(true);
            tv.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    onLongClick.run();
                    return true;
                }
            });
        }
        tv.setClickable(onClick != null || onLongClick != null);
        return tv;
    }

    private static TextView baseChip(Context ctx, String label, int kind, String hint) {
        final TextView tv = new TextView(ctx);
        float density = ctx.getResources().getDisplayMetrics().density;
        tv.setText(label);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(12.5f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setSingleLine(true);
        int pad = Math.round(9 * density);
        tv.setPadding(pad, Math.round(7 * density), pad, Math.round(7 * density));
        int normal;
        int pressed;
        int fg;
        if (kind == KIND_ACCENT) {
            normal = Palette.ACCENT_DEEP; pressed = Palette.ACCENT; fg = Palette.CHIP_TEXT;
        } else if (kind == KIND_DANGER) {
            normal = Palette.DANGER_BG; pressed = Palette.DANGER; fg = Palette.DANGER_TEXT;
        } else {
            normal = Palette.CHIP_BG; pressed = Palette.CHIP_BG_DOWN; fg = Palette.CHIP_TEXT;
        }
        tv.setBackground(roundedBg(normal, pressed, 14 * density));
        tv.setTextColor(fg);
        if (hint != null) tv.setContentDescription(hint);
        return tv;
    }

    /** نقطهٔ وضعیت گرد */
    public static View dot(Context ctx, int color, int sizeDp) {
        View v = new View(ctx);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        v.setBackground(g);
        int s = dp(ctx, sizeDp);
        v.setLayoutParams(new LinearLayout.LayoutParams(s, s));
        return v;
    }

    public static void dotColor(View dot, int color) {
        if (dot == null || !(dot.getBackground() instanceof GradientDrawable)) return;
        ((GradientDrawable) dot.getBackground().mutate()).setColor(color);
    }

    public static StateListDrawable roundedBg(int normalColor, int pressedColor, float radiusPx) {
        GradientDrawable normal = new GradientDrawable();
        normal.setColor(normalColor);
        normal.setCornerRadius(radiusPx);
        GradientDrawable pressed = new GradientDrawable();
        pressed.setColor(pressedColor);
        pressed.setCornerRadius(radiusPx);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, pressed);
        s.addState(new int[]{}, normal);
        return s;
    }

    public static GradientDrawable roundedSolid(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    /** عنوان بخش */
    public static TextView sectionLabel(Context ctx, String text) {
        return sectionLabel(ctx, text, true);
    }

    public static TextView sectionLabel(Context ctx, String text, boolean accent) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(13f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(accent ? Palette.ACCENT : Palette.GOLD);
        tv.setPadding(dp(ctx, 4), dp(ctx, 14), dp(ctx, 4), dp(ctx, 5));
        return tv;
    }

    /** توضیح کوتاه */
    public static TextView caption(Context ctx, String text, boolean dim) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(11.5f);
        tv.setTextColor(dim ? Palette.TEXT_DIM : Palette.TEXT_SUB);
        tv.setPadding(dp(ctx, 4), dp(ctx, 0), dp(ctx, 4), dp(ctx, 3));
        return tv;
    }

    public static View divider(Context ctx) {
        View v = new View(ctx);
        v.setBackgroundColor(Palette.STROKE);
        return v;
    }

    public static View space(Context ctx, int hDp) {
        View v = new View(ctx);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, hDp)));
        return v;
    }

    /** ستون عمودی RTL با حاشیه */
    public static LinearLayout vcol(Context ctx, int padDp) {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int p = dp(ctx, padDp);
        col.setPadding(p, dp(ctx, 6), p, dp(ctx, 8));
        return col;
    }

    /** ردیف افقی RTL */
    public static LinearLayout hrow(Context ctx) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    public static LinearLayout.LayoutParams m(int w, int h, float weight, int mDp) {
        LinearLayout.LayoutParams lp;
        if (w < 0) lp = new LinearLayout.LayoutParams(0, h, weight);
        else lp = new LinearLayout.LayoutParams(w, h);
        if (mDp > 0) lp.setMargins(mDp, mDp, mDp, mDp);
        return lp;
    }

    /** چینش چیپ‌ها در ردیف‌های هم‌عرض */
    public static void chipRow(Context ctx, TextView[] chips, int perRow, LinearLayout into) {
        int i = 0;
        while (i < chips.length) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            row.setLayoutParams(rp);
            row.setPadding(dp(ctx, 2), 0, dp(ctx, 2), 0);
            int n = Math.min(perRow, chips.length - i);
            for (int k = 0; k < n; k++) {
                TextView tv = chips[i + k];
                // متن خودتنظیم: اگر کادر باریک بود، قلم کوچک می‌شود تا بریده نشود
                if (Build.VERSION.SDK_INT >= 26) {
                    tv.setAutoSizeTextTypeUniformWithConfiguration(
                            8, 12, 1, TypedValue.COMPLEX_UNIT_SP);
                }
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, dp(ctx, 44), 1f);
                lp.setMargins(dp(ctx, 3), 0, dp(ctx, 3), dp(ctx, 7));
                row.addView(tv, lp);
            }
            into.addView(row);
            i += n;
        }
    }

    /** پارامتر چیدمان تمام‌عرض؛ hDp=0 یعنی WRAP_CONTENT */
    public static LinearLayout.LayoutParams wrapParams(View v, int mDp, int hDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                hDp > 0 ? dp(v.getContext(), hDp)
                        : LinearLayout.LayoutParams.WRAP_CONTENT);
        int m = dp(v.getContext(), mDp);
        lp.setMargins(m, 0, m, 0);
        return lp;
    }

    public static int alphaColor(int rgb, float alpha) {
        int a = Math.round(255 * Math.max(0f, Math.min(1f, alpha)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    public static int parse(String hex) {
        return Color.parseColor(hex);
    }
}
