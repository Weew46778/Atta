package com.atta.mcpanel;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.atta.mcpanel.ui.Pages;
import com.atta.mcpanel.voice.Homan;
import com.atta.mcpanel.voice.PersianTts;

/** صفحهٔ راهنما — هومن همهٔ بخش‌ها و رفع اشکال‌ها را توضیح می‌دهد */
public class HelpPage extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout col = Pages.root(this);
        col.addView(Pages.titleBar(this, "📖 راهنمای هومن", new Runnable() {
            @Override public void run() { Homan.intro(HelpPage.this, "help"); }
        }));

        Pages.action(this, "🔊 گفتن راهنمای کامل", 1, new Runnable() {
            @Override public void run() {
                PersianTts.shutUp();
                Homan.say(HelpPage.this, Homan.pageIntro("home"));
                Homan.say(HelpPage.this, Homan.pageIntro("aternos"));
                Homan.say(HelpPage.this, Homan.pageIntro("vps"));
                Homan.say(HelpPage.this, Homan.pageIntro("console"));
                Homan.say(HelpPage.this, Homan.pageIntro("overlay"));
            }
        });
        col.addView(Pages.action(this, "🔇 قطع گفتار", 0, new Runnable() {
            @Override public void run() { PersianTts.shutUp(); }
        }));

        String[] items = {
                "🌐 سرور Aternos — با حساب خودت وارد شو؛ استارت، توقف، ری‌استارت و وضعیت سرور از داخل اپ روی خود Aternos اجرا می‌شود. دکمهٔ Google داخل اپ کار نمی‌کند؛ با نام‌کاربری و رمز Aternos وارد شو.",
                "🛰 سرور VPS — مشخصات SSH را بده؛ اپ جاوا، Paper، Geyser و Floodgate را خودکار نصب می‌کند و استارت/توقف/کنسول دارد.",
                "🖥 کنسول — سرور باید روشن باشد؛ «اتصال کنسول» را بزن و دستور بفرست (مثل list یا op نام‌کاربری).",
                "🎮 اجرای بازی — ماینکرفت بدراک یا جاوا را اجرا کن؛ پنل اپراتور خودکار بالا می‌آید.",
                "🛠 پنل داخل بازی — اجازهٔ نمایش روی برنامه‌ها را بده و پنل را روشن کن.",
                "⚠ اگر صفحهٔ ورود باز نشد — نشست منقضی شده؛ اپ خودش فرم ورود را باز می‌کند. چند ثانیه صبر کن.",
                "⚠ اگر محافظ کلودفلر رد نشد — اینترنت یا آی‌پی را عوض کن (وی‌پی‌ان) و دوباره امتحان کن.",
                "⚠ اگر وصل نمی‌شد — لاگ را لمسِ طولانی بزن تا کپی شود و برای پشتیبانی بفرست."
        };
        for (String it : items) {
            TextView tv = new TextView(this);
            tv.setText(it);
            tv.setTextSize(14.5f);
            tv.setLineSpacing(6, 1f);
            tv.setTextColor(0xFFD5DCE8);
            android.graphics.drawable.GradientDrawable g =
                    com.atta.mcpanel.overlay.UiKit.roundedSolid(0xFF141B26, com.atta.mcpanel.overlay.UiKit.dp(this, 12));
            g.setStroke(1, 0x2A3547);
            tv.setBackground(g);
            int p = com.atta.mcpanel.overlay.UiKit.dp(this, 12);
            tv.setPadding(p, p, p, p);
            col.addView(tv, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }

        setContentView(col);
        Homan.intro(this, "help");
    }
}
