package com.atta.mcpanel.overlay;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ViewFlipper;

import com.atta.mcpanel.auto.AutoAccessibilityService;
import com.atta.mcpanel.core.AppPrefs;
import com.atta.mcpanel.core.Palette;
import com.atta.mcpanel.overlay.tabs.ControlsTab;
import com.atta.mcpanel.overlay.tabs.EffectsTab;
import com.atta.mcpanel.overlay.tabs.ItemsTab;
import com.atta.mcpanel.overlay.tabs.ServerTab;
import com.atta.mcpanel.overlay.tabs.ChallengesTab;
import com.atta.mcpanel.overlay.tabs.SettingsTab;
import com.atta.mcpanel.overlay.tabs.StructuresTab;
import com.atta.mcpanel.overlay.tabs.WorldTab;
import com.atta.mcpanel.rcon.RconConnection;
import com.atta.mcpanel.rcon.RconListener;

import java.util.ArrayList;
import java.util.List;

/**
 * میزبان پنل شناور Atta — نسخهٔ ۱٫۴٫۰
 *
 * - زبانه‌ها همیشه «سر جای خودشان» می‌مانند؛ صفحهٔ کناری به‌صورت یک کادرِ
 *   جدا در سمت راستِ زبانه‌ها باز می‌شود؛ مجموع دو کادر کمتر از نصف صفحه است.
 * - طراحی برای حالت افقی: هر دو کادر داخل حاشیهٔ امن (نوار وضعیت/ناچ/ژست) هستند.
 * - ۳۰ ثانیه بی‌کاری → بسته‌شدن صفحه؛ ۳۰ ثانیهٔ بعد → جمع‌شدن زبانه‌ها.
 */
public class PanelHost {

    public interface StateListener { void onState(); }
    public interface ConsoleListener { void onLine(String line); }

    // ---------- ثابت‌ها ----------
    private static final long IDLE_PAGE_MS = 30000L;
    private static final long IDLE_CHIPS_MS = 30000L;
    private static final long STAGGER_MS = 60;
    private static final long POP_MS = 300;
    private static final long PAGE_OPEN_MS = 300;
    private static final long PAGE_CLOSE_MS = 220;

    private static final String[] TABS = {"ابزارها", "آیتم‌ها", "افکت‌ها", "دنیا", "سرور", "چالش", "سازه‌ها", "تنظیمات"};
    private static final String[] ICONS = {"🧰", "🎁", "✨", "🌍", "🖥", "⚔️", "🏗️", "⚙️"};

    // رنگ هویت هر زبانه: نوار بالای صفحه، نقطه و نوار ته صفحه با همین رنگ می‌شوند
    private static final int[] TAB_TINT = {
            0xFF7CCB58,  // ابزارها — سبز
            0xFFE8B84B,  // آیتم‌ها — طلایی
            0xFFB79BFF,  // افکت‌ها — بنفش
            0xFF5BD6E8,  // دنیا — فیروزه‌ای
            0xFF6FA8FF,  // سرور — آبی
            0xFFFF6B6B,  // چالش — قرمز
            0xFFFFA35C,  // سازه‌ها — نارنجی
            0xFF9AA5B1,  // تنظیمات — خاکستری
    };

    private enum St { NONE, CHIPS, PAGE }

    private final OverlayService service;
    private final Context ctx;
    private final WindowManager wm;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AppPrefs prefs;

    // ---------- پنجره‌ها ----------
    private View handleView;
    private WindowManager.LayoutParams handleLp;
    private View chipsView;
    private WindowManager.LayoutParams chipsLp;
    private View pageView;
    private WindowManager.LayoutParams pageLp;
    private TextView flashTv;
    private WindowManager.LayoutParams flashLp;

    private St state = St.NONE;
    private boolean pageAnimBusy = false;

    // ---------- نوار نورانی ----------
    private ObjectAnimator glowAnim;

    // ---------- زبانه‌ها ----------
    private final List<TextView> chips = new ArrayList<TextView>();
    private ViewFlipper flipper;
    private TextView pageTitle;
    private View themeDot;
    private View themeStrip;
    private GradientDrawable headBarBg;
    private int currentTab = 0;
    private boolean pagesBuilt = false;

    // ---------- حاشیهٔ امن ----------
    private int insTop = 0, insBottom = 0, insLeft = 0, insRight = 0;

    // ---------- کالیبرهٔ دکمهٔ چت ----------
    private View calWin;
    private WindowManager.LayoutParams calLp;
    private View calCtrl;
    private WindowManager.LayoutParams calCtrlLp;

    // ---------- نسل / تایمر ----------
    private int gen = 0;
    private Runnable idleTask;
    private boolean typing = false;

    // ---------- صف ارسال خودکار چت (روش دوم) ----------
    private boolean chatBusy = false;
    private boolean chatManual = false;
    private Runnable chatManualTimeout;
    private final List<String> chatQueue = new ArrayList<String>();

    // ---------- RCON ----------
    private RconConnection rcon;
    private final List<StateListener> stateListeners = new ArrayList<StateListener>();
    private final List<ConsoleListener> consoleListeners = new ArrayList<ConsoleListener>();
    private final List<String> pendingCommands = new ArrayList<String>();

    private EditText activeInput;

    public PanelHost(OverlayService service) {
        this.service = service;
        this.ctx = service;
        this.wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        this.prefs = new AppPrefs(ctx);
    }

    public Context getContext() { return ctx; }
    public AppPrefs getPrefs() { return prefs; }

    // =====================================================================
    // ابعاد: زبانه‌ها ۱۲۸dp + صفحهٔ کناری → مجموع کمتر از نصف صفحه
    // =====================================================================

    private int screenW() { return ctx.getResources().getDisplayMetrics().widthPixels; }
    private int screenH() { return ctx.getResources().getDisplayMetrics().heightPixels; }

    private int chipsOffsetX() {
        int base = UiKit.dp(ctx, 4);
        return insLeft > UiKit.dp(ctx, 6) ? insLeft : base;
    }

    private int chipsWidthPx() { return UiKit.dp(ctx, 128); }

    private int chipsGapPx() { return UiKit.dp(ctx, 8); }

    /** عرض کادر صفحه — طوری که مجموع دو کادر کمتر از نصف صفحهٔ افقی باشد */
    private int pageWidthPx() {
        int availW = screenW() - UiKit.dp(ctx, 24) - insLeft - insRight;
        int target = screenW() / 2
                - chipsOffsetX() - chipsWidthPx() - chipsGapPx() - UiKit.dp(ctx, 12);
        int w = Math.min(availW - chipsWidthPx() - chipsGapPx(),
                Math.max(UiKit.dp(ctx, 224), target));
        return Math.max(w, UiKit.dp(ctx, 180));
    }

    /** عرض مفید محتوای داخل کادر صفحه */
    public int contentW() {
        return pageWidthPx() - UiKit.dp(ctx, 16);
    }

    /** تعداد ستون متناسب برای ردیف‌های چیپ داخل صفحه */
    public int cols(int preferred) {
        int cw = contentW();
        int c = Math.max(1, cw / UiKit.dp(ctx, 104));
        return Math.min(preferred, c);
    }

    private int topPad() {
        return Math.max(insTop + UiKit.dp(ctx, 10), UiKit.dp(ctx, 18));
    }
    private int botPad() {
        return Math.max(insBottom + UiKit.dp(ctx, 10), UiKit.dp(ctx, 18));
    }
    private int usableH() {
        return Math.max(screenH() - topPad() - botPad(), UiKit.dp(ctx, 120));
    }

    /** حاشیهٔ امن: نوار وضعیت/نوار دستی/ناچ */
    public void measureInsets() {
        int t = 0, b = 0, l = 0, r = 0;
        try {
            if (Build.VERSION.SDK_INT >= 23 && handleView != null) {
                WindowInsets wi = handleView.getRootWindowInsets();
                if (wi != null) {
                    t = Math.max(t, wi.getSystemWindowInsetTop());
                    b = Math.max(b, wi.getSystemWindowInsetBottom());
                    l = Math.max(l, wi.getSystemWindowInsetLeft());
                    r = Math.max(r, wi.getSystemWindowInsetRight());
                }
            }
        } catch (Exception ignored) {}
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                DisplayCutout cut = wm.getDefaultDisplay().getCutout();
                if (cut != null) {
                    t = Math.max(t, cut.getSafeInsetTop());
                    b = Math.max(b, cut.getSafeInsetBottom());
                    l = Math.max(l, cut.getSafeInsetLeft());
                    r = Math.max(r, cut.getSafeInsetRight());
                }
            }
        } catch (Exception ignored) {}
        if (t == 0) t = sysRes("status_bar_height");
        if (b == 0) b = sysRes("navigation_bar_height");
        insTop = t;
        insBottom = b;
        insLeft = l;
        insRight = r;
        applyPlacement();
    }

    private int sysRes(String name) {
        try {
            int id = ctx.getResources().getIdentifier(name, "dimen", "android");
            if (id > 0) return ctx.getResources().getDimensionPixelSize(id);
        } catch (Exception ignored) {}
        return 0;
    }

    public void onConfigChanged() {
        try {
            measureInsets();
            if (flashTv != null && flashTv.getParent() != null) {
                try { wm.updateViewLayout(flashTv, flashLp); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    /** جای‌گذاری همهٔ پنجره‌ها: زبانه‌ها لبهٔ چپ، صفحه بلافاصله کنارشان */
    private void applyPlacement() {
        if (handleLp != null) {
            handleLp.gravity = Gravity.START | Gravity.TOP;
            handleLp.x = UiKit.dp(ctx, 4)
                    + (insLeft > UiKit.dp(ctx, 6) ? insLeft : 0);
            int hh = handleLp.height;
            handleLp.y = topPad() + Math.max(0, (usableH() - hh) / 2);
            if (handleView != null && handleView.getParent() != null) {
                try { wm.updateViewLayout(handleView, handleLp); } catch (Exception ignored) {}
            }
        }
        if (chipsLp != null) {
            chipsLp.gravity = Gravity.START | Gravity.TOP;
            chipsLp.x = chipsOffsetX();
            int est = chipsLp.height;
            int ch = Math.min(est, usableH());
            chipsLp.height = ch;
            chipsLp.y = topPad() + Math.max(0, (usableH() - ch) / 2);
            if (chipsView != null && chipsView.getParent() != null) {
                try { wm.updateViewLayout(chipsView, chipsLp); } catch (Exception ignored) {}
            }
        }
        if (pageLp != null) {
            int pw = pageWidthPx();
            int ph = usableH() - UiKit.dp(ctx, 4);
            pageLp.width = pw;
            pageLp.height = ph;
            pageLp.x = chipsOffsetX() + chipsWidthPx() + chipsGapPx();
            pageLp.y = topPad() + 2;
            if (pageView != null && pageView.getParent() != null) {
                try { wm.updateViewLayout(pageView, pageLp); } catch (Exception ignored) {}
            }
        }
    }

    private int pageTargetX() {
        return chipsOffsetX() + chipsWidthPx() + chipsGapPx();
    }

    // =====================================================================
    // مدیریت پنجره
    // =====================================================================

    private WindowManager.LayoutParams mkLp(int w, int h, int gravity) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                w, h,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = gravity;
        return lp;
    }

    private void addWin(View v, WindowManager.LayoutParams lp) {
        if (v == null) return;
        try {
            if (v.getParent() == null) wm.addView(v, lp);
            else wm.updateViewLayout(v, lp);
        } catch (Exception ignored) {}
    }

    private void removeWin(View v) {
        if (v == null) return;
        try {
            if (v.getParent() != null) wm.removeView(v);
        } catch (Exception ignored) {}
    }

    // =====================================================================
    // attach / detach
    // =====================================================================

    public void attach() {
        if (handleView != null) return;
        buildHandle();
        buildChips();
        buildPage();
        state = St.NONE;
        showHandleWindow();
        startGlow();
        main.post(new Runnable() {
            @Override public void run() { measureInsets(); }
        });
        flash("نوار سبز لبهٔ چپ را لمس کن", "دو کادر کنار هم باز می‌شود؛ کمتر از نصف صفحه");
    }

    public void detach() {
        gen++;
        cancelIdle();
        cancelChatManualTimeout();
        AutoAccessibilityService.cancelArm();
        AutoAccessibilityService.clearHost(this);
        chatQueue.clear();
        chatBusy = false;
        chatManual = false;
        stopGlow();
        releaseConsoleFocus();
        removeWin(calWin);
        removeWin(calCtrl);
        calWin = null;
        calCtrl = null;
        removeWin(handleView);
        removeWin(chipsView);
        removeWin(pageView);
        removeWin(flashTv);
        if (rcon != null) {
            rcon.stop();
            rcon = null;
        }
        pendingCommands.clear();
        main.removeCallbacksAndMessages(null);
        stateListeners.clear();
        consoleListeners.clear();
        handleView = null;
        chipsView = null;
        pageView = null;
        chips.clear();
        state = St.NONE;
    }

    // =====================================================================
    // نوار لبه — باریک با نور متحرک
    // =====================================================================

    private void buildHandle() {
        FrameLayout root = new FrameLayout(ctx);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        GradientDrawable glass = new GradientDrawable();
        glass.setColor(Palette.alphaColor(0x0A0F16, 0.6f));
        glass.setCornerRadius(UiKit.dp(ctx, 14));
        glass.setStroke(1, 0x2EFFFFFF);
        root.setBackground(glass);

        FrameLayout area = new FrameLayout(ctx);

        View rail = new View(ctx);
        GradientDrawable railBg = new GradientDrawable();
        railBg.setShape(GradientDrawable.RECTANGLE);
        railBg.setCornerRadius(UiKit.dp(ctx, 2));
        railBg.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        railBg.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        railBg.setColors(new int[]{Palette.HANDLE_START, Palette.HANDLE_END});
        rail.setBackground(railBg);
        FrameLayout.LayoutParams railLp = new FrameLayout.LayoutParams(
                UiKit.dp(ctx, 4), UiKit.dp(ctx, 168),
                Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        railLp.topMargin = UiKit.dp(ctx, 10);
        area.addView(rail, railLp);

        final View glow = new View(ctx);
        GradientDrawable glowBg = new GradientDrawable();
        glowBg.setShape(GradientDrawable.OVAL);
        glowBg.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glowBg.setColors(new int[]{0xFFFFFFFF, Palette.HANDLE_START});
        glow.setBackground(glowBg);
        FrameLayout.LayoutParams glowLp = new FrameLayout.LayoutParams(
                UiKit.dp(ctx, 8), UiKit.dp(ctx, 8),
                Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        glowLp.topMargin = UiKit.dp(ctx, 12);
        area.addView(glow, glowLp);
        glowAnim = ObjectAnimator.ofFloat(glow, "translationY",
                0f, UiKit.dp(ctx, 152));
        glowAnim.setDuration(2400);
        glowAnim.setRepeatCount(ObjectAnimator.INFINITE);
        glowAnim.setRepeatMode(ObjectAnimator.REVERSE);
        glowAnim.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());

        FrameLayout.LayoutParams areaLp = new FrameLayout.LayoutParams(
                UiKit.dp(ctx, 10), UiKit.dp(ctx, 188), Gravity.CENTER);
        root.addView(area, areaLp);

        root.setClickable(true);
        root.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onHandleTap(); }
        });

        handleView = root;
        handleLp = mkLp(UiKit.dp(ctx, 20), UiKit.dp(ctx, 210),
                Gravity.START | Gravity.CENTER_VERTICAL);
    }

    private void startGlow() {
        if (glowAnim != null && !glowAnim.isStarted()) glowAnim.start();
    }

    private void stopGlow() {
        if (glowAnim != null) glowAnim.cancel();
    }

    private void showHandleWindow() {
        if (handleView == null) return;
        addWin(handleView, handleLp);
    }

    private void onHandleTap() {
        interact();
        if (state == St.NONE) openChips();
        else collapseAll();
    }

    // =====================================================================
    // کادر زبانه‌ها (۱۲۸dp — همیشه سر جای خودش)
    // =====================================================================

    private void buildChips() {
        ScrollView sv = new ScrollView(ctx);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.setVerticalScrollBarEnabled(false);
        sv.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        GradientDrawable card = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Palette.alphaColor(0x1C2431, 0.96f),
                        Palette.alphaColor(0x121823, 0.96f)});
        card.setCornerRadius(UiKit.dp(ctx, 20));
        card.setStroke(1, 0x40FFFFFF);
        sv.setBackground(card);
        sv.setPadding(UiKit.dp(ctx, 6), UiKit.dp(ctx, 6),
                UiKit.dp(ctx, 6), UiKit.dp(ctx, 8));

        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout head = UiKit.hrow(ctx);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView hTitle = new TextView(ctx);
        hTitle.setText("⚡ Atta");
        hTitle.setTextSize(10.5f);
        hTitle.setTypeface(Typeface.DEFAULT_BOLD);
        hTitle.setTextColor(Palette.GOLD);
        head.addView(hTitle, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView btnClose = new TextView(ctx);
        btnClose.setText("✕");
        btnClose.setTextSize(13f);
        btnClose.setTypeface(Typeface.DEFAULT_BOLD);
        btnClose.setTextColor(Palette.DANGER_TEXT);
        btnClose.setGravity(Gravity.CENTER);
        GradientDrawable cb = UiKit.roundedSolid(
                Palette.alphaColor(Palette.DANGER_BG, 0.9f), UiKit.dp(ctx, 999));
        cb.setStroke(1, 0x55FFB3A6);
        btnClose.setBackground(cb);
        btnClose.setClickable(true);
        btnClose.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { collapseAll(); }
        });
        head.addView(btnClose, new LinearLayout.LayoutParams(
                UiKit.dp(ctx, 30), UiKit.dp(ctx, 30)));
        col.addView(head, new LinearLayout.LayoutParams(
                UiKit.dp(ctx, 116), UiKit.dp(ctx, 36)));

        for (int i = 0; i < TABS.length; i++) {
            final int index = i;
            final TextView chip = new TextView(ctx);
            chip.setText(ICONS[i] + " " + TABS[i]);
            chip.setTextSize(12f);
            chip.setTypeface(Typeface.DEFAULT_BOLD);
            chip.setGravity(Gravity.CENTER);
            chip.setSingleLine(true);
            if (Build.VERSION.SDK_INT >= 26) {
                chip.setAutoSizeTextTypeUniformWithConfiguration(
                        9, 12, 1, TypedValue.COMPLEX_UNIT_SP);
            }
            chip.setClickable(true);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { onChipTap(index); }
            });
            chips.add(chip);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    UiKit.dp(ctx, 116), UiKit.dp(ctx, 48));
            lp.bottomMargin = UiKit.dp(ctx, 6);
            col.addView(chip, lp);
            styleChip(chip, false, TAB_TINT[i]);
        }

        int estH = UiKit.dp(ctx, 36 + TABS.length * (48 + 6) + 14);
        estH = Math.min(estH, screenH() - UiKit.dp(ctx, 70));
        sv.addView(col, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        chipsView = sv;
        chipsLp = mkLp(UiKit.dp(ctx, 128), estH,
                Gravity.START | Gravity.CENTER_VERTICAL);
    }

    private void styleChip(TextView chip, boolean active, int tint) {
        float d = ctx.getResources().getDisplayMetrics().density;
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(16 * d);
        if (active) {
            int r = (tint >> 16) & 0xFF;
            int g = (tint >> 8) & 0xFF;
            int b = tint & 0xFF;
            int deep = Color.rgb(Math.round(r * 0.5f),
                    Math.round(g * 0.5f), Math.round(b * 0.5f));
            bg.setGradientType(GradientDrawable.LINEAR_GRADIENT);
            bg.setOrientation(GradientDrawable.Orientation.TL_BR);
            bg.setColors(new int[]{tint, deep});
            bg.setStroke(Math.round(1.5f * d), Palette.GOLD);
            chip.setTextColor(Palette.ON_ACCENT);
        } else {
            bg.setColor(Palette.alphaColor(Palette.SURFACE_HI, 0.95f));
            bg.setStroke(Math.round(1 * d), 0x3DFFFFFF);
            chip.setTextColor(Palette.CHIP_TEXT);
        }
        chip.setBackground(bg);
    }

    private void refreshChipStyles() {
        for (int i = 0; i < chips.size(); i++) {
            styleChip(chips.get(i), state == St.PAGE && currentTab == i, TAB_TINT[i]);
        }
    }

    private void openChips() {
        if (state != St.NONE) return;
        state = St.CHIPS;
        final int myGen = ++gen;
        cancelIdle();
        removeWin(handleView);
        addWin(chipsView, chipsLp);
        applyPlacement();
        for (int i = 0; i < chips.size(); i++) {
            final View c = chips.get(i);
            c.setAlpha(0f);
            c.setTranslationX(-UiKit.dp(ctx, 60));
            c.setScaleX(0.8f);
            c.setScaleY(0.8f);
            main.postDelayed(new Runnable() {
                @Override public void run() {
                    if (myGen != gen) return;
                    c.animate().translationX(0f).alpha(1f).scaleX(1f).scaleY(1f)
                            .setDuration(POP_MS)
                            .setInterpolator(new OvershootInterpolator(1.6f))
                            .start();
                }
            }, i * STAGGER_MS);
        }
        UiKit.vibrate(ctx, 12);
        main.postDelayed(new Runnable() {
            @Override public void run() {
                if (myGen == gen && state == St.CHIPS) armIdle(IDLE_CHIPS_MS);
            }
        }, chips.size() * STAGGER_MS + POP_MS + 100);
    }

    private void hideChipsSequence(final int myGen) {
        state = St.NONE;
        cancelIdle();
        for (int i = chips.size() - 1; i >= 0; i--) {
            final View c = chips.get(i);
            final long delay = (chips.size() - 1 - i) * 45L;
            main.postDelayed(new Runnable() {
                @Override public void run() {
                    if (myGen != gen) return;
                    c.animate().translationX(-UiKit.dp(ctx, 70)).alpha(0f)
                            .scaleX(0.85f).scaleY(0.85f)
                            .setDuration(180)
                            .start();
                }
            }, delay);
        }
        main.postDelayed(new Runnable() {
            @Override public void run() {
                if (myGen != gen) return;
                removeWin(chipsView);
                showHandleWindow();
            }
        }, chips.size() * 45L + 240);
    }

    private void closeChips() {
        if (state != St.CHIPS) return;
        hideChipsSequence(++gen);
    }

    // =====================================================================
    // منطق زبانه‌ها — زبانه‌ها همیشه در جای خود می‌مانند
    // =====================================================================

    private void onChipTap(int index) {
        interact();
        if (pageAnimBusy) return;
        if (index < 0 || index >= TABS.length) return;
        pulse(chips.get(index));
        if (state == St.PAGE) {
            if (currentTab == index) closePageOnly();  // زبانهٔ باز دوباره = بستن
            else openPage(index);
            return;
        }
        if (state == St.NONE) {
            openChips();
            final int target = index;
            main.postDelayed(new Runnable() {
                @Override public void run() {
                    if (state == St.CHIPS) openPage(target);
                }
            }, 220);
            return;
        }
        openPage(index);
    }

    private void openPage(int index) {
        if (pageAnimBusy) return;
        if (index < 0 || index >= TABS.length) return;
        if (!pagesBuilt) {
            try {
                buildPages();
            } catch (Exception e) {
                flash("خطا در باز کردن زبانه", e == null ? "" : e.getMessage());
                return;
            }
        }
        if (state == St.CHIPS) {
            state = St.PAGE;
            currentTab = index;
            refreshChipStyles();
            setPageTitle();
            flipper.setDisplayedChild(index);
            showPageWindow();   // صفحه کنار زبانه‌ها؛ زبانه‌ها حذف نمی‌شوند
        } else if (state == St.PAGE) {
            int old = currentTab;
            currentTab = index;
            refreshChipStyles();
            setPageTitle();
            boolean fwd = index > old;
            flipper.setInAnimation(pageAnim(true, fwd));
            flipper.setOutAnimation(pageAnim(false, fwd));
            flipper.setDisplayedChild(index);
            armIdle(IDLE_PAGE_MS);
        }
    }

    private void buildPages() {
        View[] pages = new View[]{
                ControlsTab.build(this),
                ItemsTab.build(this),
                EffectsTab.build(this),
                WorldTab.build(this),
                ServerTab.build(this),
                ChallengesTab.build(this),
                StructuresTab.build(this),
                SettingsTab.build(this),
        };
        for (View p : pages) {
            flipper.addView(p, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }
        pagesBuilt = true;
    }

    // =====================================================================
    // کادر صفحه — جدا از کادر زبانه‌ها، دقیقاً سمت راست آن
    // =====================================================================

    private void buildPage() {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setBackground(bgPage());
        box.setKeepScreenOn(true);
        box.setClickable(true);
        box.setPadding(UiKit.dp(ctx, 6), 0, UiKit.dp(ctx, 6), 0);
        box.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent ev) {
                if (ev.getActionMasked() == MotionEvent.ACTION_DOWN) touchInteract();
                return false;
            }
        });

        LinearLayout header = UiKit.hrow(ctx);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(UiKit.dp(ctx, 8), 0, UiKit.dp(ctx, 4), 0);
        themeDot = UiKit.dot(ctx, Palette.ACCENT, 10);
        header.addView(themeDot, new LinearLayout.LayoutParams(
                UiKit.dp(ctx, 10), UiKit.dp(ctx, 10)));
        pageTitle = new TextView(ctx);
        pageTitle.setTextSize(15f);
        pageTitle.setTypeface(Typeface.DEFAULT_BOLD);
        pageTitle.setTextColor(Palette.TEXT_MAIN);
        header.addView(pageTitle, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = new TextView(ctx);
        close.setText("✕");
        close.setTextSize(14f);
        close.setTextColor(Palette.TEXT_SUB);
        close.setGravity(Gravity.CENTER);
        GradientDrawable cb = UiKit.roundedSolid(
                Palette.alphaColor(Palette.DANGER_BG, 0.85f), UiKit.dp(ctx, 999));
        cb.setStroke(1, 0x66FFB3A6);
        close.setBackground(cb);
        close.setClickable(true);
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { closePageOnly(); }
        });
        header.addView(close, new LinearLayout.LayoutParams(
                UiKit.dp(ctx, 30), UiKit.dp(ctx, 30)));
        box.addView(header, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(ctx, 50)));

        View hr = new View(ctx);
        headBarBg = new GradientDrawable();
        headBarBg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
        headBarBg.setColors(new int[]{Palette.ACCENT, 0x007CCB58});
        hr.setBackground(headBarBg);
        box.addView(hr, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(ctx, 2)));

        flipper = new ViewFlipper(ctx);
        box.addView(flipper, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        // نوار رنگی هویت زبانه در ته صفحه
        themeStrip = new View(ctx);
        box.addView(themeStrip, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(ctx, 3)));

        pageView = box;
        pageLp = mkLp(pageWidthPx(), screenH() - UiKit.dp(ctx, 20),
                Gravity.START | Gravity.TOP);
    }

    private GradientDrawable bgPage() {
        GradientDrawable g = new GradientDrawable();
        g.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        g.setOrientation(GradientDrawable.Orientation.TL_BR);
        g.setColors(new int[]{Palette.alphaColor(0x223041, 0.99f),
                Palette.alphaColor(0x10151D, 0.99f)});
        float r = UiKit.dp(ctx, 18);
        g.setCornerRadii(new float[]{r, r, r, r, r, r, r, r});
        g.setStroke(1, 0x40FFFFFF);
        return g;
    }

    private void showPageWindow() {
        if (pageAnimBusy || pageView == null) return;
        pageAnimBusy = true;
        final int myGen = ++gen;
        cancelIdle();
        applyPlacement();
        int toX = pageTargetX();
        int fromX = toX + UiKit.dp(ctx, 60);
        pageLp.x = fromX;
        addWin(pageView, pageLp);
        pageView.setAlpha(0.3f);
        ValueAnimator va = ValueAnimator.ofInt(fromX, toX);
        va.setDuration(PAGE_OPEN_MS);
        va.setInterpolator(new OvershootInterpolator(1.08f));
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                pageLp.x = ((Integer) a.getAnimatedValue());
                try { wm.updateViewLayout(pageView, pageLp); } catch (Exception ignored) {}
            }
        });
        pageView.animate().alpha(1f).setDuration(PAGE_OPEN_MS).start();
        va.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                pageAnimBusy = false;
                if (myGen != gen) return;
                if (state == St.PAGE) armIdle(IDLE_PAGE_MS);
            }
        });
        va.start();
        UiKit.vibrate(ctx, 12);
    }

    private void closePageOnly() {
        if (state != St.PAGE || pageAnimBusy) return;
        final int myGen = ++gen;
        state = St.CHIPS;
        pageAnimBusy = true;
        cancelIdle();
        releaseConsoleFocus();
        final int from = pageLp.x;
        final int to = from + UiKit.dp(ctx, 90);
        ValueAnimator va = ValueAnimator.ofInt(from, to);
        va.setDuration(PAGE_CLOSE_MS);
        va.setInterpolator(new DecelerateInterpolator(1.3f));
        va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                pageLp.x = ((Integer) a.getAnimatedValue());
                try { wm.updateViewLayout(pageView, pageLp); } catch (Exception ignored) {}
            }
        });
        pageView.animate().alpha(0f).setDuration(PAGE_CLOSE_MS).start();
        va.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                pageAnimBusy = false;
                if (myGen != gen) return;
                removeWin(pageView);
                refreshChipStyles();
                armIdle(IDLE_CHIPS_MS);
            }
        });
        va.start();
    }

    /** بستن کامل → زبانه‌ها هم جمع می‌شوند؛ فقط نوار می‌ماند */
    public void collapseAll() {
        interact();
        if (pageAnimBusy) return;
        final int myGen = ++gen;
        cancelIdle();
        if (state == St.PAGE) {
            state = St.CHIPS;
            pageAnimBusy = true;
            releaseConsoleFocus();
            final int from = pageLp.x;
            final int to = from + UiKit.dp(ctx, 90);
            ValueAnimator va = ValueAnimator.ofInt(from, to);
            va.setDuration(PAGE_CLOSE_MS);
            va.setInterpolator(new DecelerateInterpolator(1.3f));
            va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) {
                    pageLp.x = ((Integer) a.getAnimatedValue());
                    try { wm.updateViewLayout(pageView, pageLp); } catch (Exception ignored) {}
                }
            });
            pageView.animate().alpha(0f).setDuration(PAGE_CLOSE_MS).start();
            va.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) {
                    pageAnimBusy = false;
                    if (myGen != gen) return;
                    removeWin(pageView);
                    hideChipsSequence(myGen);
                }
            });
            va.start();
        } else if (state == St.CHIPS) {
            hideChipsSequence(myGen);
        }
    }

    // =====================================================================
    // انیمیشن تعویض محتوا
    // =====================================================================

    private Animation pageAnim(final boolean in, final boolean forward) {
        android.view.animation.AnimationSet set = new android.view.animation.AnimationSet(true);
        float dir = forward ? 1f : -1f;
        if (in) {
            TranslateAnimation t = new TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, dir * 0.6f,
                    Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, 0f);
            t.setDuration(260);
            t.setInterpolator(new OvershootInterpolator(0.9f));
            AlphaAnimation a = new AlphaAnimation(0f, 1f);
            a.setDuration(260);
            ScaleAnimation s = new ScaleAnimation(0.94f, 1f, 0.94f, 1f,
                    Animation.RELATIVE_TO_SELF, 0.5f,
                    Animation.RELATIVE_TO_SELF, 0.5f);
            s.setDuration(260);
            s.setInterpolator(new OvershootInterpolator(0.8f));
            set.addAnimation(t);
            set.addAnimation(a);
            set.addAnimation(s);
        } else {
            TranslateAnimation t = new TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, -dir * 0.5f,
                    Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, 0f);
            t.setDuration(160);
            t.setInterpolator(new DecelerateInterpolator());
            AlphaAnimation a = new AlphaAnimation(1f, 0f);
            a.setDuration(160);
            set.addAnimation(t);
            set.addAnimation(a);
        }
        return set;
    }

    private void pulse(final View v) {
        if (v == null) return;
        v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(70)
                .withEndAction(new Runnable() {
                    @Override public void run() {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(140).start();
                    }
                }).start();
    }

    // =====================================================================
    // کالیبراسیون دکمهٔ چت — نشانگر را روی دکمهٔ چت بازی ببر و ذخیره کن
    // =====================================================================

    /** نمایش حالت کالیبراسیون (بازی در پس‌زمینه پیداست) */
    public void showChatCalibration() {
        if (calWin != null) return;
        if (state != St.NONE) {
            collapseAll();
            main.postDelayed(new Runnable() {
                @Override public void run() {
                    if (state == St.NONE) showCalibrationWindows();
                }
            }, 500);
            return;
        }
        showCalibrationWindows();
    }

    private void showCalibrationWindows() {
        if (calWin != null) return;

        // نشانگر
        TextView marker = new TextView(ctx);
        marker.setText("⌖");
        marker.setTextSize(26f);
        marker.setGravity(Gravity.CENTER);
        marker.setTextColor(Palette.ACCENT);
        GradientDrawable mBg = new GradientDrawable();
        mBg.setShape(GradientDrawable.OVAL);
        mBg.setStroke(UiKit.dp(ctx, 2), Palette.GOLD);
        mBg.setColor(0x33000000);
        marker.setBackground(mBg);
        calWin = marker;
        calLp = mkLp(UiKit.dp(ctx, 44), UiKit.dp(ctx, 44), Gravity.TOP | Gravity.START);
        int dx = prefs.getChatX();
        int dy = prefs.getChatY();
        if (dx < 0 || dy < 0) {
            dx = insLeft + UiKit.dp(ctx, 20);
            dy = Math.max(insTop, UiKit.dp(ctx, 10)) + UiKit.dp(ctx, 10);
        }
        calLp.x = dx - UiKit.dp(ctx, 22);
        calLp.y = dy - UiKit.dp(ctx, 22);
        addWin(calWin, calLp);

        // کارت کنترل
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        GradientDrawable cBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Palette.alphaColor(0x1C2431, 0.98f),
                        Palette.alphaColor(0x10151D, 0.98f)});
        cBg.setCornerRadius(UiKit.dp(ctx, 18));
        cBg.setStroke(1, 0x55FFFFFF);
        card.setBackground(cBg);
        int pad = UiKit.dp(ctx, 10);
        card.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(ctx);
        title.setText("🎯 کالیبرهٔ دکمهٔ چت");
        title.setTextSize(13f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Palette.GOLD);
        card.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView hint = new TextView(ctx);
        hint.setText("نشانگر ⌖ را با دکمه‌ها روی «آیکون چت» بازی ببر، بعد «ذخیره» را بزن.");
        hint.setTextSize(11f);
        hint.setTextColor(Palette.TEXT_SUB);
        card.addView(hint, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // دکمه‌های جهت
        final int step = UiKit.dp(ctx, 22);

        LinearLayout upRow = new LinearLayout(ctx);
        upRow.setGravity(Gravity.CENTER);
        upRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        TextView up = arrowKey("▲");
        up.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { moveCal(0, -step); }
        });
        upRow.addView(up, new LinearLayout.LayoutParams(
                UiKit.dp(ctx, 64), UiKit.dp(ctx, 40)));
        card.addView(upRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                UiKit.dp(ctx, 44)));

        LinearLayout midRow = new LinearLayout(ctx);
        midRow.setGravity(Gravity.CENTER);
        midRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        TextView left = arrowKey("◀");
        left.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { moveCal(-step, 0); }
        });
        TextView right = arrowKey("▶");
        right.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { moveCal(step, 0); }
        });
        midRow.addView(left, new LinearLayout.LayoutParams(
                UiKit.dp(ctx, 64), UiKit.dp(ctx, 40)));
        midRow.addView(right, new LinearLayout.LayoutParams(
                UiKit.dp(ctx, 64), UiKit.dp(ctx, 40)));
        card.addView(midRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                UiKit.dp(ctx, 44)));

        LinearLayout downRow = new LinearLayout(ctx);
        downRow.setGravity(Gravity.CENTER);
        downRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        TextView down = arrowKey("▼");
        down.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { moveCal(0, step); }
        });
        downRow.addView(down, new LinearLayout.LayoutParams(
                UiKit.dp(ctx, 64), UiKit.dp(ctx, 40)));
        card.addView(downRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                UiKit.dp(ctx, 44)));

        LinearLayout btnRow = new LinearLayout(ctx);
        btnRow.setGravity(Gravity.CENTER);
        btnRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        TextView cancel = new TextView(ctx);
        cancel.setText("انصراف");
        cancel.setTextSize(12f);
        cancel.setGravity(Gravity.CENTER);
        cancel.setTextColor(Palette.TEXT_SUB);
        GradientDrawable canBg = UiKit.roundedSolid(0x22FFFFFF, UiKit.dp(ctx, 12));
        cancel.setBackground(canBg);
        cancel.setClickable(true);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { hideCalibration(false); }
        });
        TextView save = new TextView(ctx);
        save.setText("ذخیره ✓");
        save.setTextSize(12.5f);
        save.setTypeface(Typeface.DEFAULT_BOLD);
        save.setGravity(Gravity.CENTER);
        save.setTextColor(Palette.ON_ACCENT);
        GradientDrawable savBg = new GradientDrawable();
        savBg.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        savBg.setOrientation(GradientDrawable.Orientation.TL_BR);
        savBg.setColors(new int[]{0xFF96D96A, Palette.ACCENT_DEEP});
        savBg.setCornerRadius(UiKit.dp(ctx, 12));
        save.setBackground(savBg);
        save.setClickable(true);
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.setChatPoint(calLp.x + UiKit.dp(ctx, 22), calLp.y + UiKit.dp(ctx, 22));
                hideCalibration(true);
                flash("✓ موقعیت چت ذخیره شد", "حالا یک دستور را تست کن");
            }
        });
        btnRow.addView(cancel, new LinearLayout.LayoutParams(
                UiKit.dp(ctx, 96), UiKit.dp(ctx, 40)));
        btnRow.addView(save, new LinearLayout.LayoutParams(
                UiKit.dp(ctx, 96), UiKit.dp(ctx, 40)));
        card.addView(btnRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                UiKit.dp(ctx, 46)));

        calCtrl = card;
        int cw = UiKit.dp(ctx, 240);
        calCtrlLp = mkLp(cw, WindowManager.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        calCtrlLp.x = screenW() - cw - UiKit.dp(ctx, 16);
        calCtrlLp.y = topPad() + UiKit.dp(ctx, 6);
        addWin(calCtrl, calCtrlLp);

        interact();
    }

    private TextView arrowKey(String label) {
        TextView tv = new TextView(ctx);
        tv.setText(label);
        tv.setTextSize(16f);
        tv.setGravity(Gravity.CENTER);
        tv.setClickable(true);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Palette.SURFACE_HI);
        g.setCornerRadius(UiKit.dp(ctx, 10));
        g.setStroke(1, 0x40FFFFFF);
        tv.setBackground(g);
        tv.setTextColor(Palette.TEXT_MAIN);
        return tv;
    }

    private void moveCal(int dx, int dy) {
        if (calLp == null) return;
        calLp.x = Math.max(0, Math.min(screenW() - calLp.width, calLp.x + dx));
        calLp.y = Math.max(0, Math.min(screenH() - calLp.height, calLp.y + dy));
        try {
            if (calWin != null && calWin.getParent() != null) {
                wm.updateViewLayout(calWin, calLp);
            }
        } catch (Exception ignored) {}
    }

    private void hideCalibration(boolean saved) {
        if (calWin != null) { removeWin(calWin); calWin = null; }
        if (calCtrl != null) { removeWin(calCtrl); calCtrl = null; }
        if (!saved) flash("کالیبره لغو شد");
    }

    public void hideCalibration() {
        hideCalibration(false);
    }

    // =====================================================================
    // تایمر بستن خودکار ۳۰ + ۳۰
    // =====================================================================

    private void armIdle(long delayMs) {
        cancelIdle();
        final int g = gen;
        idleTask = new Runnable() {
            @Override public void run() {
                if (g != gen) return;
                if (typing) {
                    armIdle(3000);
                    return;
                }
                if (state == St.PAGE) {
                    closePageOnly();
                } else if (state == St.CHIPS) {
                    closeChips();
                }
            }
        };
        main.postDelayed(idleTask, delayMs);
    }

    private void cancelIdle() {
        if (idleTask != null) main.removeCallbacks(idleTask);
        idleTask = null;
    }

    private void interact() {
        cancelIdle();
        if (state == St.PAGE) armIdle(IDLE_PAGE_MS);
        else if (state == St.CHIPS) armIdle(IDLE_CHIPS_MS);
    }

    private void touchInteract() {
        if (!pageAnimBusy) interact();
    }

    // =====================================================================
    // API عمومی
    // =====================================================================

    public boolean isPageOpen() { return state == St.PAGE; }
    public boolean isChipsOpen() { return state == St.CHIPS || state == St.PAGE; }
    public boolean isRconConnected() { return rcon != null && rcon.isOpen(); }
    public boolean isRconAttempting() { return rcon != null && !rcon.isOpen(); }

    public void switchTab(int index) { onChipTap(index); }

    public void showPage(int index) {
        if (state == St.NONE) {
            openChips();
            final int target = index;
            main.postDelayed(new Runnable() {
                @Override public void run() {
                    if (state == St.CHIPS) openPage(target);
                }
            }, 220);
        } else {
            onChipTap(index);
        }
    }

    public void closePage(boolean unused) { closePageOnly(); }

    public void setPanelAlpha(float alpha) {
        prefs.setOverlayAlpha(alpha);
        if (pageView != null) pageView.setAlpha(alpha);
    }

    public void setPanelWidthMode(AppPrefs.PanelWidthMode mode) {
        prefs.setPanelWidthMode(mode);
    }

    public void registerStateListener(StateListener l) { stateListeners.add(l); }
    public void registerConsoleListener(ConsoleListener l) { consoleListeners.add(l); }

    private void notifyState() {
        for (StateListener l : stateListeners) {
            try { l.onState(); } catch (Exception ignored) {}
        }
    }

    // =====================================================================
    // RCON — اجرای خودکار با یک کلیک
    // =====================================================================

    private boolean hasRconConfig() {
        String h = prefs.getRconHost();
        return h != null && !h.trim().isEmpty();
    }

    private void flushPending() {
        if (pendingCommands.isEmpty()) return;
        List<String> cmds = new ArrayList<String>(pendingCommands);
        pendingCommands.clear();
        if (rcon != null && rcon.isOpen()) {
            for (String c : cmds) {
                rcon.send(c);
                try { Thread.sleep(60); } catch (InterruptedException ignored) {}
            }
        }
    }

    public void quickCommand(String raw, boolean chatEligible) {
        if (raw == null || raw.trim().isEmpty()) return;
        raw = resolve(raw);   // تبدیل {p} به نام بازیکن ذخیره‌شده
        interact();
        RconConnection r = rcon;
        if (r != null && r.isOpen()) {
            boolean ok = r.send(raw);
            flash(ok ? "✓ اجرا شد" : "خطا در اجرا", raw);
        } else if (r != null || hasRconConfig()) {
            pendingCommands.add(raw);
            if (r == null) {
                flash("در حال اتصال خودکار به سرور…", "دستور پس از وصل اجرا می‌شود");
                rconConnect();
            } else {
                flash("در حال اتصال…", "دستور در صف است");
            }
        } else if (chatEligible || AutoAccessibilityService.isEnabled()) {
            // تک‌نفره: روش دوم — ارسال خودکار به چت بازی
            startChatAuto(raw);
        } else {
            flash("برای این دستور، اجرای خودکار یا سرور شخصی لازم است",
                    "زبانهٔ «تنظیمات» ← اجرای خودکار ← قدم ۱");
            if (state != St.PAGE) showPage(5);
        }
    }

    public void kit(final String[] cmds, final String kitName) {
        if (cmds == null || cmds.length == 0) return;
        interact();
        RconConnection r = rcon;
        if (r != null && r.isOpen()) {
            main.post(new Runnable() {
                @Override public void run() { runKitNow(cmds, kitName); }
            });
        } else if (r != null || hasRconConfig()) {
            for (String c : cmds) pendingCommands.add(c);
            if (r == null) {
                flash("در حال اتصال خودکار…", "کیت «" + kitName + "» پس از وصل داده می‌شود");
                rconConnect();
            } else {
                flash("در حال اتصال…", "کیت «" + kitName + "» در صف است");
            }
        } else if (AutoAccessibilityService.isEnabled()) {
            // تک‌نفره: کیت = چند دستور پشت‌سرهم در چت بازی (صف خودکار)
            for (String c : cmds) startChatAuto(c);
            flash("🎒 کیت «" + kitName + "»", cmds.length + " دستور پشت‌سرهم ارسال می‌شود");
        } else {
            flash("کیت نیاز به اجرای خودکار یا سرور شخصی دارد",
                    "زبانهٔ «تنظیمات» ← اجرای خودکار ← قدم ۱");
            if (state != St.PAGE) showPage(5);
        }
    }

    private void runKitNow(String[] cmds, String kitName) {
        for (String c : cmds) {
            RconConnection r = rcon;
            if (r == null || !r.isOpen()) break;
            r.send(c);
            try { Thread.sleep(160); } catch (InterruptedException ignored) {}
        }
        flash("✓ کیت «" + kitName + "» داده شد");
    }

    public boolean rconConnect() {
        interact();
        String host = prefs.getRconHost() == null ? "" : prefs.getRconHost().trim();
        if (host.isEmpty()) {
            flash("آدرس سرور وارد نشده", "در زبانهٔ «سرور» آدرس و رمز را بنویس");
            if (state != St.PAGE) showPage(4);
            return false;
        }
        RconConnection old = rcon;
        rcon = null;
        if (old != null) old.stop();
        rcon = new RconConnection(host, prefs.getRconPort(), prefs.getRconPassword(),
                new RconListener() {
                    @Override public void onConnecting() {
                        main.post(new Runnable() {
                            @Override public void run() { notifyState(); }
                        });
                    }
                    @Override public void onAuth(final boolean ok, final String message) {
                        main.post(new Runnable() {
                            @Override public void run() {
                                if (ok) {
                                    if (prefs.getVibrationOn()) UiKit.vibrate(ctx, 30);
                                    int n = pendingCommands.size();
                                    flushPending();
                                    if (n > 0) {
                                        flash("✓ وصل شد — " + n + " دستور اجرا شد");
                                    } else {
                                        flash("✓ به کنسول سرور وصل شدی");
                                    }
                                } else {
                                    pendingCommands.clear();
                                    flash("ورود رد شد: " + message, "رمز یا آدرس را بررسی کن");
                                }
                                notifyState();
                            }
                        });
                    }
                    @Override public void onOutput(final String text) {
                        main.post(new Runnable() {
                            @Override public void run() {
                                for (ConsoleListener l : consoleListeners) {
                                    try { l.onLine(text); } catch (Exception ignored) {}
                                }
                            }
                        });
                    }
                    @Override public void onClosed(final String reason) {
                        main.post(new Runnable() {
                            @Override public void run() {
                                for (ConsoleListener l : consoleListeners) {
                                    try { l.onLine("⛔ " + reason); } catch (Exception ignored) {}
                                }
                                notifyState();
                            }
                        });
                    }
                });
        rcon.start();
        return true;
    }

    public void rconDisconnect() {
        pendingCommands.clear();
        RconConnection old = rcon;
        rcon = null;
        if (old != null) old.stop();
        notifyState();
    }

    public void sendConsole(String command) {
        if (command == null || command.trim().isEmpty()) return;
        command = resolve(command);   // تبدیل {p}
        interact();
        RconConnection r = rcon;
        if (r != null && r.isOpen()) {
            if (!r.send(command)) {
                flash("ارسال ناموفق بود", "اتصال را دوباره برقرار کن");
            }
        } else {
            quickCommand(command, false);
        }
    }

    public void copyText(String raw) {
        String text = raw.startsWith("/") ? raw : "/" + raw;
        try {
            ClipboardManager cm = (ClipboardManager) ctx
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("atta", text));
        } catch (Exception ignored) {}
    }

    // =====================================================================
    // اتوماسیون تک‌نفره — روش دوم (صف ارسال + بازگشت دستی)
    // =====================================================================

    /** آیا سرویس دسترسی‌پذیری Atta روشن است؟ */
    public boolean isAutomationOn() {
        return AutoAccessibilityService.isEnabled();
    }

    /** آیا مختصات دکمهٔ چت کالیبره شده؟ */
    public boolean isChatCalibrated() {
        return prefs.getChatX() >= 0 && prefs.getChatY() >= 0;
    }

    /** ورودی عمومی: یک دستور برای اجرا در چت تک‌نفره */
    public void autoChat(String raw) {
        if (raw == null || raw.trim().isEmpty()) return;
        startChatAuto(raw);
    }

    /**
     * شروع/صف‌کردن ارسال خودکار:
     * ۱) لمس خودکار چت → ۲) کیبورد Atta تایپ و Enter → ۳) ادامهٔ صف
     */
    private void startChatAuto(final String raw) {
        if (!AutoAccessibilityService.isEnabled()) {
            flash("اجرای خودکار روشن نیست",
                    "زبانهٔ «تنظیمات» ← «اجرای خودکار در تک‌نفره» ← قدم ۱");
            if (state != St.PAGE) showPage(5);
            return;
        }
        final String cmd = raw.startsWith("/") ? raw : "/" + raw;
        if (chatBusy) {
            if (chatQueue.size() < 15) chatQueue.add(cmd);
            return;
        }
        if (!isChatCalibrated()) {
            flash("قدم ۲: کالیبرهٔ دکمهٔ چت",
                    "بعد از ذخیرهٔ موقعیت، دوباره روی همین دستور بزن");
            main.postDelayed(new Runnable() {
                @Override public void run() { showChatCalibration(); }
            }, 500);
            return;
        }
        chatBusy = true;
        chatManual = false;
        cancelChatManualTimeout();
        AutoAccessibilityService.arm(this, cmd);
    }

    /** کیبورد Atta ارسال را تمام کرد → دستور بعدی صف */
    public void notifyChatSent() {
        chatManual = false;
        cancelChatManualTimeout();
        chatBusy = false;
        if (!chatQueue.isEmpty()) {
            final String next = chatQueue.remove(0);
            main.postDelayed(new Runnable() {
                @Override public void run() {
                    if (AutoAccessibilityService.isEnabled() && isChatCalibrated()) {
                        chatBusy = true;
                        chatManual = false;
                        AutoAccessibilityService.arm(PanelHost.this, next);
                    } else {
                        chatBusy = false;
                    }
                }
            }, 1000);
        }
    }

    /** لمس خودکار جواب نداد → بازگشت دستی + انتخابگر کیبورد */
    public void onChatAutoMiss() {
        if (chatManual) return;
        chatManual = true;
        flash("کیبورد Atta را انتخاب کن",
                "حالا یک بار روی آیکون چت بازی بزن؛ دستور خودکار می‌رود");
        openImePicker();
        cancelChatManualTimeout();
        chatManualTimeout = new Runnable() {
            @Override public void run() {
                chatManual = false;
                chatBusy = false;
                AutoAccessibilityService.cancelArm();
                flash("ارسال خودکار لغو شد", "دوباره روی دستور بزن");
            }
        };
        main.postDelayed(chatManualTimeout, 35000);
    }

    private void cancelChatManualTimeout() {
        if (chatManualTimeout != null) {
            main.removeCallbacks(chatManualTimeout);
            chatManualTimeout = null;
        }
    }

    /** باز کردن انتخابگر کیبورد (برای انتخاب «Atta» در همین لحظه) */
    public void openImePicker() {
        try {
            InputMethodManager imm = (InputMethodManager) ctx
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        } catch (Exception ignored) {}
    }

    /** تنظیمات دسترسی‌پذیری اندروید (برای روشن‌کردن سرویس Atta) */
    public void openA11ySettings() {
        try {
            Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception e) {
            flash("باز نشد", "از تنظیمات گوشی → دسترسی‌پذیری، خودت وارد شو");
        }
    }

    /** تنظیمات کیبورد اندروید (فعال‌سازی و انتخاب کیبورد Atta) */
    public void openImeSettings() {
        try {
            Intent i = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception e) {
            flash("باز نشد", "از تنظیمات گوشی → زبان و ورودی، کیبورد Atta را فعال کن");
        }
    }

    public String resolve(String template) {
        String p = prefs.getPlayerName();
        return template.replace("{p}", p == null || p.trim().isEmpty() ? "@s" : p.trim());
    }

    public void vibrate() {
        if (prefs.getVibrationOn()) UiKit.vibrate(ctx, 25);
    }

    // ---------- پیام کوتاه ----------

    public void flash(String title) { flash(title, null); }

    public void flash(String title, String detail) {
        if (flashTv == null) {
            flashTv = new TextView(ctx);
            GradientDrawable bg = UiKit.roundedSolid(Palette.FLASH_BG, UiKit.dp(ctx, 16));
            bg.setStroke(1, Palette.FLASH_STROKE);
            flashTv.setBackground(bg);
            flashTv.setTextColor(Palette.TEXT_MAIN);
            flashTv.setTextSize(12.5f);
            flashTv.setLineSpacing(0f, 1.05f);
            flashTv.setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 10),
                    UiKit.dp(ctx, 14), UiKit.dp(ctx, 10));
            flashTv.setClickable(false);
            flashLp = mkLp(WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.START);
        }
        flashTv.setText(detail == null || detail.isEmpty() ? title : title + "\n" + detail);
        flashTv.setAlpha(0f);
        flashLp.x = screenW() - UiKit.dp(ctx, 350);
        flashLp.y = topPad() + UiKit.dp(ctx, 6);
        flashLp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        addWin(flashTv, flashLp);
        flashTv.animate().cancel();
        flashTv.animate().alpha(1f).setDuration(150).start();
        main.removeCallbacks(flashHide);
        main.postDelayed(flashHide, 2400);
    }

    private final Runnable flashHide = new Runnable() {
        @Override public void run() {
            if (flashTv != null) {
                flashTv.animate().alpha(0f).setDuration(350)
                        .withEndAction(new Runnable() {
                            @Override public void run() { removeWin(flashTv); }
                        }).start();
            }
        }
    };

    // ---------- فیلدهای ورودی ----------

    public EditText newInput(String hint, int inputType) {
        return newInput(hint, inputType, null);
    }

    public EditText newInput(final String hint, int inputType, String prefill) {
        final EditText et = new EditText(ctx);
        et.setHint(hint);
        et.setInputType(inputType);
        et.setSingleLine(true);
        et.setTextSize(13.5f);
        et.setTextColor(Palette.TEXT_MAIN);
        et.setHintTextColor(Palette.TEXT_DIM);
        float d = ctx.getResources().getDisplayMetrics().density;
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Palette.INPUT_BG);
        bg.setCornerRadius(12 * d);
        bg.setStroke(Math.round(1 * d), Palette.STROKE2);
        et.setBackground(bg);
        et.setPadding(UiKit.dp(ctx, 12), 0, UiKit.dp(ctx, 12), 0);
        if (prefill != null) et.setText(prefill);
        et.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent ev) {
                if (ev.getActionMasked() == MotionEvent.ACTION_UP) {
                    makePageEditable();
                    et.requestFocus();
                    typing = true;
                    showKeyboard(et);
                    armIdle(3000);
                }
                return false;
            }
        });
        et.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean has) {
                if (has) {
                    activeInput = et;
                    typing = true;
                } else if (activeInput == et) {
                    activeInput = null;
                    typing = false;
                    restorePageFocus();
                }
            }
        });
        return et;
    }

    private void showKeyboard(EditText et) {
        try {
            InputMethodManager imm = (InputMethodManager) ctx
                    .getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(et, InputMethodManager.SHOW_IMPLICIT);
        } catch (Exception ignored) {}
    }

    private void makePageEditable() {
        if (pageLp == null) return;
        pageLp.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        pageLp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        try { wm.updateViewLayout(pageView, pageLp); } catch (Exception ignored) {}
    }

    private void restorePageFocus() {
        if (pageLp == null) return;
        pageLp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        pageLp.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN;
        try { wm.updateViewLayout(pageView, pageLp); } catch (Exception ignored) {}
    }

    private void releaseConsoleFocus() {
        EditText et = activeInput;
        activeInput = null;
        typing = false;
        if (et != null) et.clearFocus();
        restorePageFocus();
    }

    // ---------- دیالوگ‌ها ----------

    public void showRconDialog(String title, final boolean autoConnect) {
        float d = ctx.getResources().getDisplayMetrics().density;
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Math.round(22 * d), Math.round(10 * d), Math.round(22 * d), 0);

        final EditText hostEt = dialogInput("مثلاً 192.168.1.20 یا localhost", prefs.getRconHost());
        final EditText portEt = dialogInput("پورت RCON", String.valueOf(prefs.getRconPort()));
        portEt.setInputType(InputType.TYPE_CLASS_NUMBER);
        final EditText passEt = dialogInput("رمز RCON (rcon.password)", prefs.getRconPassword());
        passEt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        box.addView(dialogLabel("آدرس سرور"));
        box.addView(hostEt, UiKit.wrapParams(hostEt, 2, 46));
        box.addView(dialogLabel("پورت"));
        box.addView(portEt, UiKit.wrapParams(portEt, 2, 46));
        box.addView(dialogLabel("رمز"));
        box.addView(passEt, UiKit.wrapParams(passEt, 2, 46));

        new AlertDialog.Builder(ctx)
                .setTitle(title)
                .setMessage("اتصال RCON به سرور شخصی — localhost برای سرور روی همین دستگاه")
                .setView(box)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("ذخیره", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface dg, int w) {
                        prefs.setRconHost(hostEt.getText().toString());
                        String pt = portEt.getText().toString().trim();
                        prefs.setRconPort(pt.isEmpty() ? 25575 : Integer.parseInt(pt));
                        prefs.setRconPassword(passEt.getText().toString());
                        if (autoConnect) rconConnect();
                    }
                })
                .show();
    }

    public void promptText(String title, String hint, String prefill,
                           final OnTextResult onResult) {
        float d = ctx.getResources().getDisplayMetrics().density;
        final EditText et = dialogInput(hint, prefill);
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Math.round(22 * d), Math.round(10 * d), Math.round(22 * d), 0);
        box.addView(et, UiKit.wrapParams(et, 2, 48));
        new AlertDialog.Builder(ctx)
                .setTitle(title)
                .setView(box)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("ادامه", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface dg, int w) {
                        String t = et.getText().toString().trim();
                        if (!t.isEmpty()) onResult.onResult(t);
                    }
                })
                .show();
    }

    public interface OnTextResult {
        void onResult(String text);
    }

    public void confirm(String title, String message, final Runnable onYes) {
        new AlertDialog.Builder(ctx)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton("نه، منصرف شدم", null)
                .setPositiveButton("بله، انجام بده",
                        new android.content.DialogInterface.OnClickListener() {
                            @Override public void onClick(android.content.DialogInterface d, int w) {
                                onYes.run();
                            }
                        })
                .show();
    }

    private EditText dialogInput(String hint, String prefill) {
        EditText et = new EditText(ctx);
        et.setHint(hint);
        et.setSingleLine(true);
        et.setTextSize(14f);
        et.setTextColor(Palette.TEXT_MAIN);
        et.setHintTextColor(Palette.TEXT_DIM);
        float d = ctx.getResources().getDisplayMetrics().density;
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Palette.INPUT_BG);
        bg.setCornerRadius(12 * d);
        bg.setStroke(Math.round(1 * d), Palette.STROKE2);
        et.setBackground(bg);
        et.setPadding(Math.round(12 * d), Math.round(4 * d),
                Math.round(12 * d), Math.round(4 * d));
        if (prefill != null) et.setText(prefill);
        return et;
    }

    private TextView dialogLabel(String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextSize(12f);
        tv.setTextColor(Palette.TEXT_SUB);
        tv.setPadding(0, UiKit.dp(ctx, 12), 0, UiKit.dp(ctx, 3));
        return tv;
    }

    private void setPageTitle() {
        if (pageTitle == null) return;
        pageTitle.setText(ICONS[currentTab] + "  " + TABS[currentTab]);
        applyTabTheme();
        // انیمیشن کوچک ورود عنوان در هر جابه‌جایی زبانه
        pageTitle.setAlpha(0f);
        pageTitle.setTranslationX(UiKit.dp(ctx, -14));
        pageTitle.animate().alpha(1f).translationX(0f)
                .setDuration(260).setInterpolator(new OvershootInterpolator(1.3f)).start();
    }

    /** هویت رنگی زبانهٔ بازشده: نوار بالا، نقطه و نوار پایین */
    private void applyTabTheme() {
        int tint = TAB_TINT[Math.max(0, Math.min(TABS.length - 1, currentTab))];
        if (headBarBg != null) {
            try {
                headBarBg.setColors(new int[]{tint, 0x00FFFFFF});
                headBarBg.setOrientation(GradientDrawable.Orientation.LEFT_RIGHT);
            } catch (Exception ignored) {}
        }
        if (themeDot != null) UiKit.dotColor(themeDot, tint);
        if (pageTitle != null) pageTitle.setTextColor(tint);
        if (themeStrip != null) themeStrip.setBackgroundColor(
                UiKit.alphaColor(tint, 0.92f));
    }
}
