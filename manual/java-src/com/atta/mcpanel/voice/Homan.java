package com.atta.mcpanel.voice;

import android.content.Context;

import com.atta.mcpanel.core.AppPrefs;

/**
 * هومن — دستیار صوتی فارسی اپ.
 * هر صفحه را توضیح می‌دهد، در هر مرحله می‌گوید بعد از این چه کنی،
 * و وقتی خطایی رخ دهد راهِ درست را می‌گوید.
 * صدای زنانهٔ نورال «دلارا» — مستقل از موتور گفتار گوشی.
 */
public final class Homan {

    private Homan() {}

    // ------------------------------------------------------------------
    // روشن/خاموش
    // ------------------------------------------------------------------

    public static boolean isEnabled(Context ctx) {
        return !"0".equals(new AppPrefs(ctx).getString("homan_on", "1"));
    }

    public static void setEnabled(Context ctx, boolean on) {
        new AppPrefs(ctx).setString("homan_on", on ? "1" : "0");
        if (!on) PersianTts.shutUp();
    }

    /** گفتن متن (اگر هومن روشن باشد) */
    public static void say(Context ctx, String text) {
        if (!isEnabled(ctx)) return;
        PersianTts.speak(ctx, text);
    }

    // ------------------------------------------------------------------
    // معرفی صفحه‌ها
    // ------------------------------------------------------------------

    public static String pageIntro(String page) {
        if ("home".equals(page)) {
            return "سلام. من هومن هستم، دستیار فارسی آتا پنل. از این صفحه، بخش مورد نظرت را انتخاب کن. "
                    + "سرور اترنوس، سرور وی پی اس، کنسول، اجرای بازی، پنل داخل بازی و راهنما. "
                    + "در هر صفحه دکمهٔ هومن را بزن تا راهنمای همان صفحه را بگویم.";
        }
        if ("aternos".equals(page)) {
            return "این صفحهٔ سرور اترنوس است. اول دکمهٔ ورود را بزن و با نام کاربری و رمز اترنوس وارد شو. "
                    + "بعد از وصل شدن، دکمهٔ استارت سرور را روشن می‌کند. "
                    + "اگر بخواهی دستور بفرستی، از صفحهٔ کنسول استفاده کن.";
        }
        if ("vps".equals(page)) {
            return "این صفحهٔ سرور وی پی اس است. اول دکمهٔ اتصال را بزن و آدرس، پورت، کاربر و رمز سرورت را وارد کن، "
                    + "بعد ذخیره و تست را بزن. اگر تست موفق بود، نصب خودکار و بعد استارت را بزن. "
                    + "برای پنل داخل بازی، دکمهٔ آرم کن هم لازم است.";
        }
        if ("console".equals(page)) {
            return "این کنسول سرور است. سرور باید روشن باشد. اول دکمهٔ اتصال کنسول را بزن، "
                    + "بعد هر دستوری مثل لیست را بنویس و ارسال کن. جواب سرور همین صفحه می‌آید.";
        }
        if ("game".equals(page)) {
            return "از این صفحه می‌توانی ماینکرفت را اجرا کنی. اگر بازی نصب باشد دکمهٔ اجرا را می‌بینی، "
                    + "وگرنه دکمهٔ نصب. هنگام اجرا، پنل اپراتور هم خودکار بالا می‌آید.";
        }
        if ("overlay".equals(page)) {
            return "این صفحهٔ پنل اپراتور داخل بازی است. اول اجازهٔ نمایش روی برنامه‌ها را بده، "
                    + "بعد پنل را روشن کن. با گزینهٔ اجرای خودکار، پنل هر بار با باز کردن بازی بالا می‌آید.";
        }
        if ("help".equals(page)) {
            return "من هومن هستم. در هر صفحه دکمهٔ هومن را بزن تا کار همان صفحه را توضیح بدهم. "
                    + "اگر جایی خطا دیدم، همان لحظه می‌گویم مشکل چیست و چطور درستش کنی.";
        }
        return "";
    }

    public static void intro(Context ctx, String page) {
        say(ctx, pageIntro(page));
    }

    // ------------------------------------------------------------------
    // رویدادها — «الان باید چیکار کنم؟»
    // ------------------------------------------------------------------

    public static void event(Context ctx, String key) {
        String t = null;
        if ("ready".equals(key)) t = "به اترنوس وصل شدی. حالا می‌توانی دکمهٔ استارت را بزنی.";
        else if ("expired".equals(key)) t = "نشست اترنوس منقضی شده است. دوباره دکمهٔ ورود را بزن.";
        else if ("cloudflare".equals(key)) t = "صفحهٔ محافظ کلادفلر باز شده. چند لحظه صبر کن. اگر رد نشد، با تغییر آی‌پی یا وی پی ان امتحان کن.";
        else if ("server_on".equals(key)) t = "سرور روشن شد. حالا می‌توانی وارد بازی شوی یا از کنسول دستور بفرستی.";
        else if ("server_starting".equals(key)) t = "سرور در حال روشن شدن است. یکی دو دقیقه صبر کن.";
        else if ("server_off".equals(key)) t = "سرور خاموش است. برای روشن کردن، دکمهٔ استارت را بزن.";
        else if ("vps_ok".equals(key)) t = "اتصال به وی پی اس موفق بود. حالا می‌توانی نصب خودکار را بزنی.";
        else if ("vps_fail".equals(key)) t = "اتصال به وی پی اس ناموفق بود. آدرس، پورت و رمز را بررسی کن.";
        else if ("install_done".equals(key)) t = "نصب سرور روی وی پی اس کامل شد. حالا دکمهٔ استارت را بزن.";
        else if ("login_open".equals(key)) t = "صفحهٔ ورود باز شد. با نام کاربری و رمز اترنوس وارد شو.";
        if (t != null) say(ctx, t);
    }

    // ------------------------------------------------------------------
    // عیب‌یابی از روی خط لاگ
    // ------------------------------------------------------------------

    /** خط لاگ را می‌بیند؛ اگر الگوی خطای شناخته‌شده بود، راهنمایی صوتی می‌دهد */
    public static void adviseOnLog(Context ctx, String line) {
        if (line == null || !isEnabled(ctx)) return;
        String l = line.toLowerCase();
        if (l.contains("منقضی")) event(ctx, "expired");
        else if (l.contains("کلودفلر") || l.contains("just a moment")) event(ctx, "cloudflare");
        else if (l.contains("اتصال کامل نشد")) say(ctx, "اتصال کامل نشد. یک بار دیگر دکمهٔ وضعیت را بزن و اگر باز نشد، لاگ را برای پشتیبانی کپی کن.");
        else if (l.contains("auth") || l.contains("authentication")) say(ctx, "سرور اجازهٔ ورود نداد. رمز یا نام کاربری اشتباه است.");
        else if (l.contains("timed out") || l.contains("timeout") || l.contains("connection refused")) say(ctx, "سرور پاسخ نداد. آدرس و پورت را بررسی کن و مطمئن شو سرور روشن است.");
        else if (l.contains("java not installed") || l.contains("openjdk")) say(ctx, "جاوا روی سرور نصب نیست. دکمهٔ نصب خودکار را بزن.");
        else if (l.contains("اتصال کنسول قطع شد")) say(ctx, "اتصال کنسول قطع شد. دوباره دکمهٔ اتصال کنسول را بزن.");
    }
}
