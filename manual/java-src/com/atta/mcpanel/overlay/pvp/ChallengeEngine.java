package com.atta.mcpanel.overlay.pvp;

import java.util.ArrayList;
import java.util.List;

/**
 * موتور مسابقه/داوری درون‌برنامه:
 * رقابت ۱×۱ (یا تیمی) در چند راند؛ داور (شما) بعد از هر راند به یکی از دو طرف
 * یا به «تساوی» رأی می‌دهد. اگر در پایان راندهای برنامه‌ریزی‌شده امتیازها برابر
 * بود، راند طلایی خودکار اضافه می‌شود تا برنده مشخص شود.
 * برنده اعلام شده و «جایزه» به شکل متن/فرمان آمادهٔ کپی یا ارسال به کنسول است.
 *
 * وضعیت این موتور «ایستا» است تا بازسازی صفحه‌های زبانه‌ها چیزی را پاک نکند.
 */
public final class ChallengeEngine {

    public static final String[][] TEMPLATES = {
            {"⚔️ دوئل PvP",
                    "دو بازیکن در میدان بسته؛ هر راند تا آخرین نفر زنده ادامه دارد. "
                            + "کشتن در بازی ممنوع نیست اما بعد از مرگ، بازیکن از راند حذف می‌شود."},
            {"🏹 مسابقهٔ تیراندازی",
                    "۶۰ ثانیه زمان؛ هر گل دقیق به هدف = امتیاز راند برای آن بازیکن (نفر برتر راند را داور ثبت می‌کند)."},
            {"🧱 مسابقهٔ ساخت (Build-Off)",
                    "یک موضوع اعلام می‌شود (مثلاً «خانهٔ روی آب»)؛ ۵ دقیقه ساخت؛ داور با دیدن هر دو اثر امتیاز راند را می‌دهد."},
            {"🐎 مسابقهٔ سرعت (دوی/اسب)",
                    "پیست ۱۰۰ بلوکی با مانع؛ نخستین نفری که به خط پایان برسد برندهٔ راند است."},
            {"⛏️ شکار الماس",
                    "هر دو به عمق بروند؛ اولی که ۸ الماس بیاورد برندهٔ راند است (با اعلام در چت)."},
            {"👾 شکار موب (Mob Hunt)",
                    "۹۰ ثانیه؛ هر بازیکن در ناحیهٔ خودش موب می‌زند؛ بیشترین کشتار = برندهٔ راند."},
    };

    // ---- وضعیت سری ----
    public static int selected = 0;            // ایندکس قالب انتخاب‌شده
    public static String[] names = {"", ""};   // نام دو طرف
    public static int roundsTotal = 3;         // تعداد راندهای برنامه‌ریزی‌شده (فرد)
    public static int roundIdx = 0;            // راندهای برگزارشده
    public static int[] pts = {0, 0};
    public static boolean running = false;
    public static boolean finished = false;
    public static boolean golden = false;      // راند طلایی فعال شد
    public static int winner = -1;             // 0 یا 1؛ -1 = هنوز/تساوی
    public static String lastResult = "";

    private static final List<String> log = new ArrayList<String>();
    /** نتیجهٔ هر راند برگزارشده: 0/1 = طرف برنده، 2 = تساوی */
    public static final List<Integer> roundResults = new ArrayList<Integer>();

    private ChallengeEngine() {}

    public static void reset() {
        names[0] = "";
        names[1] = "";
        roundIdx = 0;
        pts[0] = 0;
        pts[1] = 0;
        running = false;
        finished = false;
        golden = false;
        winner = -1;
        lastResult = "";
        log.clear();
        roundResults.clear();
    }

    /** شروع سری جدید. total باید فرد باشد. */
    public static void start(int template, String n1, String n2, int total) {
        reset();
        selected = Math.max(0, Math.min(TEMPLATES.length - 1, template));
        names[0] = n1 == null || n1.trim().isEmpty() ? "بازیکن ۱" : n1.trim();
        names[1] = n2 == null || n2.trim().isEmpty() ? "بازیکن ۲" : n2.trim();
        int t = total;
        if (t < 1 || t > 9) t = 3;
        if (t % 2 == 0) t += 1; // رقابت عادلانه: فرد
        roundsTotal = t;
        running = true;
        addLog("🚩 شروع مسابقه: " + TEMPLATES[selected][0]);
        addLog("   " + names[0] + "  در برابر  " + names[1]
                + "   •   بهترین از " + roundsTotal + " راند");
    }

    /** ثبت نتیجهٔ یک راند توسط داور. who: 0 یا 1 = امتیاز به همان طرف؛ 2 = تساوی (بدون امتیاز). */
    public static String award(int who) {
        if (!running || finished) return lastResult;
        if (who == 0 || who == 1) {
            pts[who]++;
        }
        roundIdx++;
        roundResults.add(who);
        String r = "راند " + roundIdx + ": ";
        if (who == 2) r += "🤝 تساوی (بدون امتیاز)";
        else r += "🏅 امتیاز برای " + names[who];
        addLog(r);
        addLog("   📊 نتیجه فعلی: " + names[0] + " " + pts[0]
                + " — " + pts[1] + " " + names[1]);

        if (pts[0] != pts[1] && roundIdx >= roundsTotal) {
            winner = pts[0] > pts[1] ? 0 : 1;
            finished = true;
            running = false;
            lastResult = "🏆 " + names[winner] + " برنده شد "
                    + pts[winner] + " به " + pts[1 - winner] + "!";
            addLog("🎉 " + lastResult);
        } else if (pts[0] == pts[1] && roundIdx >= roundsTotal) {
            if (!golden) {
                golden = true;
                addLog("⚡ امتیازها برابر شد؛ یک «راند طلایی» اضافه شد!");
            } else {
                addLog("⚡ باز هم تساوی؛ راند طلایی دیگر اضافه شد.");
            }
            roundsTotal++; // رقابت ادامه دارد تا برنده مشخص شود
        }
        return lastResult;
    }

    /** آیا راند بعدی همان «راند طلایی» است؟ */
    public static boolean nextIsGolden() {
        return running && roundIdx >= roundsTotal - 1
                && pts[0] == pts[1] && golden;
    }

    /** خط‌های گزارش (برای نمایش کوتاه). */
    public static List<String> logLines() {
        return log;
    }

    /** متن کامل گزارش برای کپی/ارسال. */
    public static String transcript() {
        StringBuilder sb = new StringBuilder();
        sb.append(TEMPLATES[selected][0]).append("\n");
        sb.append("🎮 ").append(names[0]).append("  در برابر  ").append(names[1]).append("\n");
        for (String l : log) sb.append(l).append("\n");
        return sb.toString();
    }

    public static String templateRules() {
        return TEMPLATES[selected][0] + "\n" + TEMPLATES[selected][1];
    }

    /** متن اعلام برنده برای چت بازی/کنسول سرور. */
    public static String announceText() {
        if (!finished || winner < 0) return "";
        String t = TEMPLATES[selected][0];
        int sp = t.indexOf(' ');
        String title = sp > 0 ? t.substring(sp + 1) : t;
        return "🏆 برندهٔ مسابقهٔ «" + title + "»: " + names[winner]
                + " با نتیجهٔ " + pts[winner] + " به " + pts[1 - winner]
                + " — تبریک! 🎉";
    }

    /** چند پیشنهاد «جایزه» برای برنده؛ {نام، شرح، فرمان اپراتور برای بدراک/جاوا} */
    public static final String[][] PRIZES = {
            {"🥇 تاج قهرمانی", "یک تاج طلایی + اعلام قهرمانی در چت", "give"},
            {"🪙 گنجینهٔ طلا", "۲۴ طلا + ۸ زمرد (مثل جایزهٔ رسمی)", "give"},
            {"⚔️ شمشیر قهرمان", "شمشیر الماس با افسون‌های قدرت (آتش ۲ + غنیمت ۳)", "give"},
            {"📜 افتخار در چت", "یک پیام رسمی «تبریک به قهرمان» از سمت اپراتور", "say"},
            {"🎁 سورپرایز صندوق", "برنده یک صندوق جایزه (Trophy) کنار تختش می‌سازد", "say"},
    };

    private static void addLog(String line) {
        log.add(line);
        if (log.size() > 60) log.remove(0);
    }
}
