package com.atta.mcpanel;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.atta.mcpanel.core.AppPrefs;
import com.atta.mcpanel.core.Palette;
import com.atta.mcpanel.overlay.OverlayService;
import com.atta.mcpanel.overlay.UiKit;

/**
 * لانچر Atta — اجرای ماینکرفت + روشن/خاموش‌کردن پنل اپراتور
 */
public class MainActivity extends Activity {

    private static final class GameInfo {
        final String pkg;
        final String title;
        final String note;
        final String webUrl;   // null → گوگل‌پلی
        GameInfo(String pkg, String title, String note, String webUrl) {
            this.pkg = pkg;
            this.title = title;
            this.note = note;
            this.webUrl = webUrl;
        }
    }

    private static final GameInfo[] GAMES = {
            new GameInfo("com.mojang.minecraftpe",
                    "ماینکرفت — نسخهٔ موبایل (بدراک)",
                    "نسخهٔ رسمی اندروید از گوگل‌پلی", null),
            new GameInfo("net.kdt.pojavlaunch",
                    "ماینکرفت جاوا — PojavLauncher",
                    "نسخهٔ جاوا روی اندروید (متن‌باز و رایگان)",
                    "https://github.com/PojavLauncherTeam/PojavLauncher/releases"),
    };

    private AppPrefs prefs;
    private TextView tvOverlayStatus;
    private TextView tvPermission;
    private LinearLayout overlayActions;
    private LinearLayout gameRowsContainer;
    private Switch swOverlay;
    private Switch swAutoPanel;
    private boolean suppressSwitch = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = new AppPrefs(this);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshOverlayState();
        refreshGameRows();
    }

    // =====================================================================
    // UI
    // =====================================================================

    private View buildUi() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        // پس‌زمینهٔ گرادیانی مدرن
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0E141D, 0xFF13201A, 0xFF0C1017});
        sv.setBackground(bg);
        final LinearLayout col = UiKit.vcol(this, 16);
        sv.addView(col);

        // ---- سربرگ
        final LinearLayout header = UiKit.hrow(this);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView name = new TextView(this);
        name.setText("AttaPanel");
        name.setTextSize(32f);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(Palette.ACCENT);
        titles.addView(name);
        TextView sub = new TextView(this);
        sub.setText("Remote server manager • In-game overlay");
        sub.setTextSize(13f);
        sub.setTextColor(Palette.TEXT_SUB);
        titles.addView(sub);
        header.addView(titles, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        tvOverlayStatus = new TextView(this);
        tvOverlayStatus.setTextSize(12.5f);
        tvOverlayStatus.setTextColor(Palette.TEXT_DIM);
        tvOverlayStatus.setGravity(Gravity.CENTER);
        GradientDrawable stBg = UiKit.roundedSolid(Palette.SURFACE, UiKit.dp(this, 14));
        tvOverlayStatus.setBackground(stBg);
        tvOverlayStatus.setPadding(UiKit.dp(this, 12), UiKit.dp(this, 8),
                UiKit.dp(this, 12), UiKit.dp(this, 8));
        header.addView(tvOverlayStatus);
        col.addView(header, UiKit.wrapParams(header, 2, 70));
        col.addView(UiKit.space(this, 4));

        // ======================================================
        // کارت اصلی (Hero): مرکز سرور — VPS + Aternos
        // ======================================================
        final FrameLayout hero = new FrameLayout(this);
        {
            GradientDrawable hbg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFF1C3B2A, 0xFF0E1A24, 0xFF14241B});
            hbg.setCornerRadius(UiKit.dp(this, 24));
            hbg.setStroke(1, 0x66A9E37B);
            hero.setBackground(hbg);
            hero.setClickable(true);
            hero.setForeground(UiKit.roundedSolid(0x14FFFFFF, UiKit.dp(this, 24)));
            hero.setPadding(UiKit.dp(this, 18), UiKit.dp(this, 14),
                    UiKit.dp(this, 18), UiKit.dp(this, 12));

            LinearLayout inner = UiKit.vcol(this, 0);
            LinearLayout hi = UiKit.hrow(this);
            TextView big = new TextView(this);
            big.setText("🛰");
            big.setTextSize(34f);
            hi.addView(big, new LinearLayout.LayoutParams(
                    UiKit.dp(this, 52), UiKit.dp(this, 52)));

            LinearLayout ht = new LinearLayout(this);
            ht.setOrientation(LinearLayout.VERTICAL);
            TextView h1 = new TextView(this);
            h1.setText("مرکز سرور — VPS و Aternos");
            h1.setTextSize(18f);
            h1.setTypeface(Typeface.DEFAULT_BOLD);
            h1.setTextColor(0xFFB6EF9C);
            ht.addView(h1);
            TextView h2 = new TextView(this);
            h2.setText("ساخت، راه‌اندازی و مدیریت سرور ابری از داخل همین اپ");
            h2.setTextSize(12f);
            h2.setTextColor(Palette.TEXT_SUB);
            ht.addView(h2);
            hi.addView(ht, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            inner.addView(hi);

            LinearLayout hb = UiKit.hrow(this);
            TextView pill = new TextView(this);
            pill.setText("VPS  •  کنسول  •  Aternos  •  RCON  —  لمس کن و برو ▶");
            pill.setTextSize(11.5f);
            pill.setTypeface(Typeface.DEFAULT_BOLD);
            pill.setTextColor(0xFF20362B);
            GradientDrawable pbg = UiKit.roundedSolid(0xFFA9E37B, UiKit.dp(this, 999));
            pill.setBackground(pbg);
            pill.setPadding(UiKit.dp(this, 14), UiKit.dp(this, 6),
                    UiKit.dp(this, 14), UiKit.dp(this, 6));
            hb.addView(pill);
            inner.addView(hb);
            inner.setPadding(0, UiKit.dp(this, 2), 0, UiKit.dp(this, 2));
            hero.addView(inner);

            // درخشش متحرک روی کارت اصلی
            View shine = new View(this);
            shine.setBackground(UiKit.roundedSolid(0x1AFFFFFF, UiKit.dp(this, 999)));
            shine.setAlpha(0.7f);
            FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(
                    UiKit.dp(this, 60), UiKit.dp(this, 46), Gravity.CENTER_VERTICAL);
            shine.setLayoutParams(slp);
            shine.setVisibility(View.INVISIBLE);
            hero.addView(shine);
            ValueAnimator sweep = ValueAnimator.ofFloat(-1f, 1.25f);
            sweep.setDuration(3400);
            sweep.setRepeatCount(ValueAnimator.INFINITE);
            sweep.setStartDelay(900);
            sweep.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) {
                    float p = (Float) a.getAnimatedValue();
                    shine.setVisibility(View.VISIBLE);
                    shine.setAlpha(0.10f + 0.55f * (1f - Math.abs(p)));
                    shine.setTranslationX(p * (hero.getWidth() + UiKit.dp(MainActivity.this, 120)));
                }
            });
            sweep.start();
        }
        hero.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).withEndAction(new Runnable() {
                    @Override public void run() {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(160).start();
                    }
                }).start();
                try {
                    startActivity(new Intent(MainActivity.this,
                            com.atta.mcpanel.server.ServerHubActivity.class));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "مرکز سرور باز نشد", Toast.LENGTH_SHORT).show();
                }
            }
        });
        col.addView(hero, UiKit.wrapParams(hero, 2, 0));
        col.addView(UiKit.space(this, 10));

        // ======================================================
        // کارت: اجرای بازی
        // ======================================================
        LinearLayout cardGame = newCard();
        addCardHeader(cardGame, "🎮", "اجرای ماینکرفت", 0xFF7CCB58);
        col.addView(cardGame, UiKit.wrapParams(cardGame, 2, 0));
        gameRowsContainer = new LinearLayout(this);
        gameRowsContainer.setOrientation(LinearLayout.VERTICAL);
        gameRowsContainer.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        cardGame.addView(gameRowsContainer, UiKit.wrapParams(gameRowsContainer, 2, 0));
        buildGameRows();
        TextView gameNote = UiKit.caption(this,
                "با هر «اجرا»، اگر مجوز داده باشی پنل اپراتور خودکار بالا می‌آید.", true);
        cardGame.addView(gameNote);
        col.addView(UiKit.space(this, 10));

        // ======================================================
        // کارت: پنل اپراتور (Overlay)
        // ======================================================
        final LinearLayout cardOv = newCard();
        addCardHeader(cardOv, "🛠", "پنل اپراتور — داخل بازی", 0xFF5BD6E8);
        col.addView(cardOv, UiKit.wrapParams(cardOv, 2, 0));

        final LinearLayout swRow = UiKit.hrow(this);
        TextView tvSw = new TextView(this);
        tvSw.setText("نمایش پنل روی بازی");
        tvSw.setTextSize(15f);
        tvSw.setTextColor(Palette.TEXT_MAIN);
        swRow.addView(tvSw, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        swOverlay = new Switch(this);
        swOverlay.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                if (suppressSwitch) return;
                if (checked) requestStartOverlay();
                else OverlayService.stop(MainActivity.this);
            }
        });
        swRow.addView(swOverlay);
        cardOv.addView(swRow, UiKit.wrapParams(swRow, 4, 54));

        tvPermission = new TextView(this);
        tvPermission.setTextSize(12.5f);
        cardOv.addView(tvPermission, UiKit.wrapParams(tvPermission, 4, 40));

        overlayActions = new LinearLayout(this);
        overlayActions.setOrientation(LinearLayout.HORIZONTAL);
        overlayActions.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        cardOv.addView(overlayActions, UiKit.wrapParams(overlayActions, 4, 50));

        final LinearLayout autoRow = UiKit.hrow(this);
        TextView tvAuto = new TextView(this);
        tvAuto.setText("اجرای خودکار پنل هنگام باز کردن بازی");
        tvAuto.setTextSize(13f);
        tvAuto.setTextColor(Palette.TEXT_SUB);
        autoRow.addView(tvAuto, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        swAutoPanel = new Switch(this);
        swAutoPanel.setChecked(prefs.getAutoPanelOnLaunch());
        swAutoPanel.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                prefs.setAutoPanelOnLaunch(checked);
            }
        });
        autoRow.addView(swAutoPanel);
        cardOv.addView(autoRow, UiKit.wrapParams(autoRow, 4, 54));
        col.addView(UiKit.space(this, 10));

        // ======================================================
        // پانوشت
        // ======================================================
        TextView footer = new TextView(this);
        footer.setText("AttaPanel · ابزار کمکی اپراتور — ساخت و مدیریت سرور ابری، کنسول زنده و پنل درون‌بازی. وابسته به موجانگ نیست.");
        footer.setTextSize(11.5f);
        footer.setTextColor(Palette.TEXT_DIM);
        footer.setGravity(Gravity.CENTER);
        col.addView(footer, UiKit.wrapParams(footer, 4, 40));

        animateIn(col);
        return sv;
    }

    /** کارت بخش با پس‌زمینهٔ سطحی و گوشه‌های نرم */
    private LinearLayout newCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{UiKit.alphaColor(Palette.SURFACE_HI, 0.72f),
                        UiKit.alphaColor(Palette.SURFACE, 0.9f)});
        bg.setCornerRadius(UiKit.dp(this, 22));
        bg.setStroke(1, 0x2EFFFFFF);
        card.setBackground(bg);
        card.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 8),
                UiKit.dp(this, 16), UiKit.dp(this, 12));
        return card;
    }

    /** سربرگ رنگی کارت: آیکون در قاب رنگی + عنوان */
    private void addCardHeader(LinearLayout card, String icon, String title, int tint) {
        LinearLayout h = UiKit.hrow(this);
        TextView ic = new TextView(this);
        ic.setText(icon);
        ic.setTextSize(16f);
        ic.setGravity(Gravity.CENTER);
        GradientDrawable ibg = UiKit.roundedSolid(UiKit.alphaColor(tint, 0.18f),
                UiKit.dp(this, 12));
        ibg.setStroke(1, UiKit.alphaColor(tint, 0.55f));
        ic.setBackground(ibg);
        h.addView(ic, new LinearLayout.LayoutParams(
                UiKit.dp(this, 40), UiKit.dp(this, 40)));
        TextView ti = new TextView(this);
        ti.setText(title);
        ti.setTextSize(16f);
        ti.setTypeface(Typeface.DEFAULT_BOLD);
        ti.setTextColor(tint);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tlp.setMarginStart(UiKit.dp(this, 10));
        h.addView(ti, tlp);
        card.addView(h, UiKit.wrapParams(h, 2, 0));
        card.addView(UiKit.space(this, 4));
    }

    // انیمیشن ورود پلکانی (fade + slide) برای چشم‌نوازی
    private void animateIn(final LinearLayout col) {
        for (int i = 0; i < col.getChildCount(); i++) {
            final View v = col.getChildAt(i);
            v.setAlpha(0f);
            v.setTranslationY(dpA(16));
        }
        for (int i = 0; i < col.getChildCount(); i++) {
            final int idx = i;
            final View v = col.getChildAt(i);
            v.postDelayed(new Runnable() {
                @Override public void run() {
                    v.animate().alpha(1f).translationY(0).setDuration(420).start();
                }
            }, 60L + idx * 55L);
        }
        // پالس آرام روی نام
        try {
            ObjectAnimator pulse = ObjectAnimator.ofFloat(col, "alpha", 0.86f, 1f);
            pulse.setDuration(2600);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.setRepeatMode(ValueAnimator.REVERSE);
        } catch (Exception ignored) {}
    }

    private int dpA(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // =====================================================================
    // ردیف‌های بازی
    // =====================================================================

    private boolean isInstalled(String pkg) {
        try {
            return getPackageManager().getLaunchIntentForPackage(pkg) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private void buildGameRows() {
        gameRowsContainer.removeAllViews();
        for (final GameInfo game : GAMES) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            GradientDrawable bg = UiKit.roundedSolid(Palette.SURFACE, UiKit.dp(this, 18));
            bg.setStroke(1, Palette.STROKE);
            card.setBackground(bg);
            card.setPadding(UiKit.dp(this, 14), UiKit.dp(this, 10),
                    UiKit.dp(this, 14), UiKit.dp(this, 10));

            LinearLayout row = UiKit.hrow(this);
            LinearLayout info = new LinearLayout(this);
            info.setOrientation(LinearLayout.VERTICAL);
            TextView tvName = new TextView(this);
            tvName.setText(game.title);
            tvName.setTextSize(14.5f);
            tvName.setTypeface(Typeface.DEFAULT_BOLD);
            tvName.setTextColor(Palette.TEXT_MAIN);
            info.addView(tvName);
            TextView tvState = new TextView(this);
            tvState.setTextSize(12f);
            info.addView(tvState);
            TextView tvNote = new TextView(this);
            tvNote.setText(game.note);
            tvNote.setTextSize(11f);
            tvNote.setTextColor(Palette.TEXT_DIM);
            info.addView(tvNote);
            row.addView(info, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            final TextView btn = UiKit.chip(this, "اجرا", UiKit.KIND_ACCENT,
                    new Runnable() {
                        @Override public void run() { onGameAction(game); }
                    });
            row.addView(btn, new LinearLayout.LayoutParams(
                    UiKit.dp(this, 96), UiKit.dp(this, 44)));
            card.addView(row);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, UiKit.dp(this, 8), 0, 0);
            gameRowsContainer.addView(card, lp);
            gameRowViews.add(tvState);
            gameRowViews.add(btn);
        }
        refreshGameRows();
    }

    private final java.util.ArrayList<View> gameRowViews = new java.util.ArrayList<View>();

    private void refreshGameRows() {
        int i = 0;
        for (GameInfo game : GAMES) {
            if (i + 1 >= gameRowViews.size()) break;
            TextView state = (TextView) gameRowViews.get(i);
            TextView btn = (TextView) gameRowViews.get(i + 1);
            i += 2;
            boolean installed = isInstalled(game.pkg);
            state.setText(installed ? "✓ نصب است" : "روی این دستگاه نیست");
            state.setTextColor(installed ? Palette.DOT_OK : Palette.GOLD);
            btn.setText(installed ? "اجرا" : "نصب");
        }
    }

    private void onGameAction(GameInfo game) {
        if (isInstalled(game.pkg)) {
            launchGame(game.pkg);
        } else if (game.webUrl != null) {
            openUrl(game.webUrl);
        } else {
            openPlay(game.pkg);
        }
    }

    private void launchGame(String pkg) {
        Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) return;
        if (prefs.getAutoPanelOnLaunch()) {
            if (Settings.canDrawOverlays(this)) {
                if (!OverlayService.isRunning()) OverlayService.start(this);
            } else {
                toast("پنل روشن نشد: اول «اجازهٔ نمایش روی برنامه‌ها» را بده");
            }
        }
        try {
            startActivity(launch);
        } catch (Exception e) {
            toast("بازی قابل اجرا نیست: " + e.getMessage());
        }
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast("مرورگری برای باز کردن لینک پیدا نشد");
        }
    }

    private void openPlay(String pkg) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("market://details?id=" + pkg)));
        } catch (Exception e) {
            openUrl("https://play.google.com/store/apps/details?id=" + pkg);
        }
    }

    // =====================================================================
    // وضعیت پنل
    // =====================================================================

    private boolean canOverlay() {
        return Settings.canDrawOverlays(this);
    }

    private void refreshOverlayState() {
        suppressSwitch = true;
        boolean running = OverlayService.isRunning();
        swOverlay.setChecked(running);
        tvOverlayStatus.setText(running ? "پنل: روشن" : "پنل: خاموش");
        tvOverlayStatus.setTextColor(running ? Palette.DOT_OK : Palette.TEXT_DIM);

        if (canOverlay()) {
            tvPermission.setText("✓ اجازهٔ نمایش روی سایر برنامه‌ها داده شده است");
            tvPermission.setTextColor(Palette.DOT_OK);
        } else {
            tvPermission.setText("اجازهٔ نمایش روی سایر برنامه‌ها داده نشده — برای پنل لازم است");
            tvPermission.setTextColor(Palette.DANGER);
        }

        overlayActions.removeAllViews();
        if (!canOverlay()) {
            TextView btn = UiKit.chip(this, "اعطای اجازهٔ نمایش روی برنامه‌ها",
                    UiKit.KIND_ACCENT, new Runnable() {
                        @Override public void run() { goManageOverlayPermission(); }
                    });
            overlayActions.addView(btn, new LinearLayout.LayoutParams(
                    0, UiKit.dp(this, 46), 1f));
        } else {
            TextView btnState = UiKit.chip(this,
                    OverlayService.isRunning() ? "خاموش کردن پنل" : "روشن کردن پنل",
                    UiKit.KIND_ACCENT, new Runnable() {
                        @Override public void run() {
                            if (OverlayService.isRunning()) {
                                OverlayService.stop(MainActivity.this);
                                refreshOverlayState();
                            } else {
                                requestStartOverlay();
                            }
                        }
                    });
            overlayActions.addView(btnState, new LinearLayout.LayoutParams(
                    0, UiKit.dp(this, 46), 1f));
            TextView btnTile = UiKit.chip(this, "➕ کاشی تنظیمات سریع",
                    new Runnable() {
                        @Override public void run() {
                            toast("دو بار از بالای صفحه پایین بکش → ویرایش کاشی‌ها → "
                                    + "«پنل اپراتور» را بکش و رها کن");
                        }
                    });
            LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(
                    0, UiKit.dp(this, 46), 1f);
            lp2.setMargins(UiKit.dp(this, 8), 0, 0, 0);
            overlayActions.addView(btnTile, lp2);
        }
        suppressSwitch = false;
    }

    private void requestStartOverlay() {
        if (!canOverlay()) {
            swOverlay.setChecked(false);
            goManageOverlayPermission();
            return;
        }
        OverlayService.start(this);
        refreshOverlayState();
    }

    private void goManageOverlayPermission() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            } catch (Exception ignored) {}
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
