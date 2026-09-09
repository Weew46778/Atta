package com.atta.mcpanel.auto;

import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.atta.mcpanel.core.Palette;
import com.atta.mcpanel.overlay.PanelHost;
import com.atta.mcpanel.overlay.UiKit;

/**
 * کیبورد Atta — تایپ عادی (فارسی/انگلیسی/اعداد) + ارسال خودکار دستور
 *
 * وقتی دستوری از پنل در انتظار است و چت بازی باز می‌شود، این کیبورد:
 * دستور را می‌گیرد → در چت تایپ می‌کند → Enter می‌زند → خودش بسته می‌شود.
 */
public class AttaIme extends InputMethodService {

    private final Handler main = new Handler(Looper.getMainLooper());

    private boolean persian = false;
    private boolean shift = false;
    private boolean numeric = false;

    private LinearLayout root;
    private boolean sending = false;

    // چیدمان‌ها
    private static final String[][] FA_ROWS = {
            {"ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح", "ج", "چ"},
            {"ش", "س", "ی", "ب", "ل", "ا", "ت", "ن", "م", "ک", "گ"},
            {"ظ", "ط", "ز", "ر", "ذ", "د", "پ", "و", "؟", "!"},
    };
    private static final String[][] EN_ROWS = {
            {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p"},
            {"a", "s", "d", "f", "g", "h", "j", "k", "l"},
            {"z", "x", "c", "v", "b", "n", "m", ",", ".", "؟"},
    };
    private static final String[] NUM_ROWS = {
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "0",
            "/", "@", "#", "$", "%", "&", "*", "-", "_", "+",
            "(", ")", ":", ";", "'", "\"", "~", "!", "?", "،",
    };

    @Override
    public View onCreateInputView() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF141B25, 0xFF0C1119});
        bg.setCornerRadius(UiKit.dp(this, 10));
        root.setBackground(bg);
        int p = UiKit.dp(this, 6);
        root.setPadding(p, p, p, p);
        rebuild();
        return root;
    }

    private void rebuild() {
        if (root == null) return;
        root.removeAllViews();

        // نوار وضعیت (هنگام ارسال خودکار، وضعیت را نشان بده)
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView hint = new TextView(this);
        if (sending) {
            hint.setText("در حال ارسال دستور…");
            hint.setTextColor(Palette.ACCENT);
        } else if (AutoAccessibilityService.hasPending()) {
            hint.setText("Atta — دستور آمادهٔ ارسال");
            hint.setTextColor(Palette.GOLD);
        } else {
            hint.setText("Atta");
            hint.setTextColor(Palette.GOLD);
        }
        hint.setTextSize(11f);
        hint.setTypeface(Typeface.DEFAULT_BOLD);
        bar.addView(hint, new LinearLayout.LayoutParams(
                0, UiKit.dp(this, 20), 1f));

        if (!sending && !AutoAccessibilityService.hasPending()) {
            TextView clear = keyLabel("پاک‌کردن");
            clear.setTextSize(10f);
            clear.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) ic.deleteSurroundingText(1000, 0);
                }
            });
            bar.addView(clear, new LinearLayout.LayoutParams(
                    UiKit.dp(this, 100), UiKit.dp(this, 26)));
        }
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 28)));

        if (sending) {
            // حالت ارسال خودکار: فقط راهنما
            TextView note = new TextView(this);
            note.setText("دستور در حال تایپ و ارسال است…");
            note.setTextSize(14f);
            note.setGravity(Gravity.CENTER);
            note.setTextColor(Palette.TEXT_SUB);
            root.addView(note, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 80)));
            return;
        }

        // ردیف‌های حروف
        if (numeric) {
            for (int row = 0; row < 3; row++) {
                LinearLayout r = row();
                for (int k = 0; k < 10; k++) {
                    final String ch = NUM_ROWS[row * 10 + k];
                    TextView key = key(ch);
                    key.setOnClickListener(tap(ch));
                    r.addView(key, new LinearLayout.LayoutParams(0, kH(), 1f));
                }
                root.addView(r, lpRow());
            }
        } else {
            String[][] rows = persian ? FA_ROWS : EN_ROWS;
            int rowCount = 0;
            for (String[] rowKeys : rows) {
                LinearLayout r = row();
                if (rowCount == 2) {
                    TextView sh = keyLabel(persian ? "⇧" : (shift ? "⇧⬆" : "⇧"));
                    sh.setTypeface(Typeface.DEFAULT_BOLD);
                    sh.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            shift = !shift;
                            rebuild();
                        }
                    });
                    r.addView(sh, new LinearLayout.LayoutParams(0, kH(), 0.8f));
                } else if (rowCount == 0) {
                    TextView fa = keyLabel(persian ? "EN" : "فا");
                    fa.setTypeface(Typeface.DEFAULT_BOLD);
                    fa.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            persian = !persian;
                            rebuild();
                        }
                    });
                    r.addView(fa, new LinearLayout.LayoutParams(0, kH(), 0.8f));
                } else {
                    TextView num = keyLabel("۱۲۳");
                    num.setTextSize(10f);
                    num.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            numeric = true;
                            rebuild();
                        }
                    });
                    r.addView(num, new LinearLayout.LayoutParams(0, kH(), 0.8f));
                }
                for (String k : rowKeys) {
                    String out = k;
                    if (!persian && shift && k.length() == 1
                            && k.charAt(0) >= 'a' && k.charAt(0) <= 'z') {
                        out = k.toUpperCase();
                    }
                    final String commit = out;
                    TextView key = key(out);
                    key.setOnClickListener(tap(commit));
                    r.addView(key, new LinearLayout.LayoutParams(0, kH(), 1f));
                }
                root.addView(r, lpRow());
                rowCount++;
            }
        }

        // ردیف پایین: ۱۲۳، /، فاصله، پاک‌کردن، Enter
        LinearLayout bottom = row();

        TextView numBtn = keyLabel(numeric ? "ABC" : "۱۲۳");
        numBtn.setTextSize(11f);
        numBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                numeric = !numeric;
                rebuild();
            }
        });
        bottom.addView(numBtn, new LinearLayout.LayoutParams(0, kH(), 1.2f));

        TextView slash = keyLabel("/");
        slash.setOnClickListener(tap("/"));
        bottom.addView(slash, new LinearLayout.LayoutParams(0, kH(), 0.8f));

        TextView space = keyLabel(persian ? "فاصله" : "space");
        space.setTextSize(11f);
        space.setOnClickListener(tap(" "));
        bottom.addView(space, new LinearLayout.LayoutParams(0, kH(), 3.5f));

        TextView del = keyLabel("⌫");
        del.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) ic.deleteSurroundingText(1, 0);
            }
        });
        bottom.addView(del, new LinearLayout.LayoutParams(0, kH(), 1f));

        TextView enter = keyLabel("↵");
        enter.setTypeface(Typeface.DEFAULT_BOLD);
        GradientDrawable eg = UiKit.roundedSolid(Palette.ACCENT_DEEP, UiKit.dp(this, 10));
        eg.setStroke(1, 0x66FFFFFF);
        enter.setBackground(eg);
        enter.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pressEnter(); }
        });
        bottom.addView(enter, new LinearLayout.LayoutParams(0, kH(), 1.4f));

        root.addView(bottom, lpRow());
    }

    private View.OnClickListener tap(final String text) {
        return new View.OnClickListener() {
            @Override public void onClick(View v) {
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) ic.commitText(text, 1);
                shift = false;
                rebuild();
            }
        };
    }

    // =====================================================================
    // ارسال خودکار دستور
    // =====================================================================

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        final String cmd = AutoAccessibilityService.takePending();
        if (cmd != null) {
            AutoAccessibilityService.imeTookCommand();
            sending = true;
            rebuild();
            main.postDelayed(new Runnable() {
                @Override public void run() {
                    typeAndSend(cmd, 0);
                }
            }, 700);
        } else {
            sending = false;
        }
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
        sending = false;
        if (root != null) rebuild();
    }

    private void typeAndSend(final String cmd, final int attempt) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) {
            if (attempt < 3) {
                main.postDelayed(new Runnable() {
                    @Override public void run() { typeAndSend(cmd, attempt + 1); }
                }, 350);
            } else {
                done(false, cmd);
            }
            return;
        }
        ic.commitText(cmd, 1);
        main.postDelayed(new Runnable() {
            @Override public void run() { pressEnter(); }
        }, 250);
        main.postDelayed(new Runnable() {
            @Override public void run() { done(true, cmd); }
        }, 700);
    }

    private void done(boolean sent, String cmd) {
        sending = false;
        try { requestHideSelf(0); } catch (Throwable ignored) {}
        AutoAccessibilityService.finishSent();
        PanelHost h = AutoAccessibilityService.host();
        if (h != null) {
            h.vibrate();
            h.flash(sent ? "✓ دستور در بازی ارسال شد" : "ارسال نشد — دوباره امتحان کن", cmd);
        }
        if (root != null) rebuild();
    }

    private void pressEnter() {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        long t = System.currentTimeMillis();
        ic.sendKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_ENTER, 0));
        ic.sendKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_ENTER, 0));
    }

    // =====================================================================
    // ابزار ساخت
    // =====================================================================

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER);
        int m = UiKit.dp(this, 2);
        r.setPadding(m, 0, m, 0);
        return r;
    }

    private LinearLayout.LayoutParams lpRow() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiKit.dp(this, 46));
        lp.bottomMargin = UiKit.dp(this, 3);
        return lp;
    }

    private int kH() { return UiKit.dp(this, 42); }

    private TextView keyLabel(String label) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(13f);
        tv.setSingleLine(true);
        tv.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF1E2733);
        g.setCornerRadius(UiKit.dp(this, 9));
        g.setStroke(1, 0x33FFFFFF);
        tv.setBackground(g);
        tv.setTextColor(Palette.TEXT_MAIN);
        return tv;
    }

    private TextView key(String label) {
        TextView tv = keyLabel(label);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    @Override
    public void onDestroy() {
        AutoAccessibilityService.cancelArm();
        super.onDestroy();
    }
}
