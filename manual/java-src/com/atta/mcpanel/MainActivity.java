package com.atta.mcpanel;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.atta.mcpanel.core.Palette;
import com.atta.mcpanel.overlay.UiKit;
import com.atta.mcpanel.server.AternosPage;
import com.atta.mcpanel.server.ConsolePage;
import com.atta.mcpanel.server.VpsPage;
import com.atta.mcpanel.ui.Pages;
import com.atta.mcpanel.voice.Homan;
import com.atta.mcpanel.voice.PersianTts;

/**
 * خانهٔ اپ — فقط منوی درشت و خوانا؛ هر بخش، صفحهٔ خودش را دارد.
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PersianTts.init(this);
        setContentView(buildUi());
        Homan.intro(this, "home");
    }

    private View buildUi() {
        LinearLayout col = Pages.root(this);

        // ---------- سربرگ ----------
        LinearLayout header = UiKit.hrow(this);
        TextView name = new TextView(this);
        name.setText("AttaPanel");
        name.setTextSize(26f);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(0xFFB6EF9C);
        header.addView(name, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView ver = new TextView(this);
        ver.setText("2.3.0");
        ver.setTextSize(12f);
        ver.setTextColor(Palette.TEXT_DIM);
        header.addView(ver);
        col.addView(header, UiKit.wrapParams(header, 2, 60));

        // ---------- هومن ----------
        LinearLayout homanRow = UiKit.vcol(this, 12);
        GradientDrawable hg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF23324A, 0xFF1A2233});
        hg.setCornerRadius(UiKit.dp(this, 16));
        hg.setStroke(1, 0x407FB3FF);
        homanRow.setBackground(hg);
        LinearLayout hr = UiKit.hrow(this);
        TextView hl = new TextView(this);
        hl.setText("🗣 دستیار صوتی هومن (فارسی)");
        hl.setTextSize(16f);
        hl.setTypeface(Typeface.DEFAULT_BOLD);
        hl.setTextColor(0xFF9FC9FF);
        hr.addView(hl, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Switch sw = new Switch(this);
        sw.setChecked(Homan.isEnabled(this));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                Homan.setEnabled(MainActivity.this, checked);
                if (checked) Homan.say(MainActivity.this, "من هومن هستم. روشن شدم. در هر صفحه دکمهٔ هومن را بزن تا راهنمایی کنم.");
            }
        });
        hr.addView(sw);
        homanRow.addView(hr);
        TextView hc = new TextView(this);
        hc.setText("هر صفحه را توضیح می‌دهد، می‌گوید الان چه کنی و اگر خطا شد راه درست را می‌گوید.");
        hc.setTextSize(12.5f);
        hc.setTextColor(Palette.TEXT_SUB);
        homanRow.addView(hc);
        col.addView(homanRow, UiKit.wrapParams(homanRow, 2, 0));
        col.addView(UiKit.space(this, 8));

        // ---------- منوی صفحات — دکمه‌های درشت دو ستونه ----------
        LinearLayout r1 = UiKit.hrow(this);
        col.addView(r1, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(this, 78)));
        nav(r1, "🌐", "سرور Aternos", 0xFF2E5233, 0xFFB9F0A8, AternosPage.class);
        nav(r1, "🛰", "سرور VPS", 0xFF33415C, 0xFFB7D4FF, VpsPage.class);
        LinearLayout r2 = UiKit.hrow(this);
        col.addView(r2, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(this, 78)));
        nav(r2, "🖥", "کنسول سرور", 0xFF4A3A2E, 0xFFFFD9B0, ConsolePage.class);
        nav(r2, "🎮", "اجرای بازی", 0xFF2E5233, 0xFFB9F0A8, GamePage.class);
        LinearLayout r3 = UiKit.hrow(this);
        col.addView(r3, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(this, 78)));
        nav(r3, "🛠", "پنل داخل بازی", 0xFF23424A, 0xFFA8E8F0, OverlayPage.class);
        nav(r3, "📖", "راهنمای هومن", 0xFF44335A, 0xFFDCC2FF, HelpPage.class);

        // ---------- پانوشت ----------
        TextView footer = new TextView(this);
        footer.setText("AttaPanel · ابزار کمکی اپراتور — وابسته به موجانگ نیست.");
        footer.setTextSize(11.5f);
        footer.setTextColor(Palette.TEXT_DIM);
        footer.setGravity(Gravity.CENTER);
        col.addView(footer, UiKit.wrapParams(footer, 4, 40));

        return col;
    }

    /** یک دکمهٔ ناوبری درشت داخل ردیف داده‌شده */
    private void nav(LinearLayout row, String icon, String title, int bg, int fg, final Class<?> cls) {
        TextView b = new TextView(this);
        b.setText(icon + "  " + title);
        b.setTextSize(17.5f);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(fg);
        b.setGravity(Gravity.CENTER);
        b.setClickable(true);
        GradientDrawable g = UiKit.roundedSolid(bg, UiKit.dp(this, 16));
        g.setStroke(1, 0x30FFFFFF);
        b.setBackground(g);
        b.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    startActivity(new Intent(MainActivity.this, cls));
                } catch (Throwable t) {
                    android.widget.Toast.makeText(MainActivity.this,
                            "باز نشد: " + t.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                }
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        lp.setMargins(UiKit.dp(this, 3), UiKit.dp(this, 3), UiKit.dp(this, 3), UiKit.dp(this, 3));
        row.addView(b, lp);
    }
}
