package com.atta.mcpanel;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.atta.mcpanel.core.AppPrefs;
import com.atta.mcpanel.core.Palette;
import com.atta.mcpanel.overlay.OverlayService;
import com.atta.mcpanel.overlay.UiKit;
import com.atta.mcpanel.ui.Pages;
import com.atta.mcpanel.voice.Homan;

import java.util.ArrayList;

/** صفحهٔ اجرای بازی — هر بازی یک کارت درشت */
public class GamePage extends Activity {

    private static final class GameInfo {
        final String pkg;
        final String title;
        final String note;
        final String webUrl; // null → گوگل‌پلی
        GameInfo(String pkg, String title, String note, String webUrl) {
            this.pkg = pkg; this.title = title; this.note = note; this.webUrl = webUrl;
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
    private LinearLayout rows;
    private final ArrayList<Object[]> rowRefs = new ArrayList<Object[]>(); // {tvState, btn, game}

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new AppPrefs(this);

        LinearLayout col = Pages.root(this);
        col.addView(Pages.titleBar(this, "🎮 اجرای بازی", new Runnable() {
            @Override public void run() { Homan.intro(GamePage.this, "game"); }
        }));

        rows = UiKit.vcol(this, 0);
        col.addView(rows, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView note = UiKit.caption(this,
                "با هر «اجرا»، اگر مجوز داده باشی پنل اپراتور خودکار بالا می‌آید.", true);
        col.addView(note);

        setContentView(col);
        buildRows();
        Homan.intro(this, "game");
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRows();
    }

    private boolean isInstalled(String pkg) {
        try {
            return getPackageManager().getLaunchIntentForPackage(pkg) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private void buildRows() {
        rows.removeAllViews();
        rowRefs.clear();
        for (final GameInfo game : GAMES) {
            LinearLayout card = UiKit.vcol(this, 12);
            GradientDrawable bg = UiKit.roundedSolid(Palette.SURFACE, UiKit.dp(this, 18));
            bg.setStroke(1, Palette.STROKE);
            card.setBackground(bg);

            TextView name = new TextView(this);
            name.setText(game.title);
            name.setTextSize(16f);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setTextColor(Palette.TEXT_MAIN);
            card.addView(name, UiKit.wrapParams(name, 2, 0));

            final TextView state = new TextView(this);
            state.setTextSize(13f);
            card.addView(state, UiKit.wrapParams(state, 2, 0));

            TextView note = new TextView(this);
            note.setText(game.note);
            note.setTextSize(12f);
            note.setTextColor(Palette.TEXT_DIM);
            card.addView(note, UiKit.wrapParams(note, 2, 0));

            final TextView btn = Pages.action(this, "اجرا", 1, new Runnable() {
                @Override public void run() { onGameAction(game); }
            });
            card.addView(btn);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, UiKit.dp(this, 8), 0, 0);
            rows.addView(card, lp);
            rowRefs.add(new Object[]{state, btn, game});
        }
        refreshRows();
    }

    private void refreshRows() {
        for (Object[] r : rowRefs) {
            TextView state = (TextView) r[0];
            TextView btn = (TextView) r[1];
            GameInfo game = (GameInfo) r[2];
            boolean installed = isInstalled(game.pkg);
            state.setText(installed ? "✓ نصب است" : "روی این دستگاه نیست");
            state.setTextColor(installed ? Palette.DOT_OK : Palette.GOLD);
            btn.setText(installed ? "▶ اجرا" : "⬇ نصب");
        }
    }

    private void onGameAction(GameInfo game) {
        if (isInstalled(game.pkg)) launchGame(game.pkg);
        else if (game.webUrl != null) openUrl(game.webUrl);
        else openPlay(game.pkg);
    }

    private void launchGame(String pkg) {
        Intent launch = getPackageManager().getLaunchIntentForPackage(pkg);
        if (launch == null) return;
        if (prefs.getAutoPanelOnLaunch()) {
            if (Settings.canDrawOverlays(this)) {
                if (!OverlayService.isRunning()) OverlayService.start(this);
            } else {
                Toast.makeText(this, "پنل روشن نشد: اول اجازهٔ نمایش روی برنامه‌ها را بده", Toast.LENGTH_SHORT).show();
            }
        }
        try {
            startActivity(launch);
        } catch (Exception e) {
            Toast.makeText(this, "بازی قابل اجرا نیست: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "مرورگری پیدا نشد", Toast.LENGTH_SHORT).show();
        }
    }

    private void openPlay(String pkg) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)));
        } catch (Exception e) {
            openUrl("https://play.google.com/store/apps/details?id=" + pkg);
        }
    }
}
