package ir.atta.console;

import android.animation.ObjectAnimator;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * اورلای واقعی اپراتور آتا — با مجوز SYSTEM_ALERT_WINDOW روی خود بازی ماینکرافت رسم می‌شود.
 * رفتاردقیقاً مثل طراحی: دستگیرهٔ لبهٔ چپ، ۹ زبانهٔ رنگی نیمه‌شفاف با انیمیشن،
 * پنل کشویی کنار زبانه، لمس بقیهٔ صفحه آزاد، بستن خودکار پس از ۳۰ ثانیه.
 * هر دکمه، دستور را با یک لمس به پل محلی (وب‌ویوی اپ) می‌فرستد تا به سرور واقعی برسد.
 */
public final class OverlayService extends Service {
    private static final int BRIDGE_PORT = 48273;
    private static final long AUTO_CLOSE_MS = 30000;

    private static final String[][] TABS = {
        // نام، رنگ، دستورها به شکل «برچسب|دستور» — دستورها واقعی و مخصوص بدراک‌اند؛
        // «;» چند دستور پشت‌سرهم، و «@power:» / «@backup» عمل‌های سطح پنل‌اند.
        {"سریع", "#f59e0b", "حالت بقا|gamemode survival,حالت خلاقانه|gamemode creative,حالت ماجراجویی|gamemode adventure,روز شود|time set day,شب شود|time set night,آسمان صاف|weather clear,باران|weather rain,طوفان و رعد|weather thunder,حفظ آیتم بعد مرگ|gamerule keepinventory true"},
        {"جهان", "#10b981", "طلوع|time set sunrise,ظهر|time set noon,غروب|time set sunset,نیمه‌شب|time set midnight,توقف زمان|gamerule doDaylightCycle false,جریان زمان|gamerule doDaylightCycle true,ماب طبیعی نیاید|gamerule doMobSpawning false,ماب طبیعی بیاید|gamerule doMobSpawning true,آتش گسترش نیابد|gamerule doFireTick false,سختی صلح‌آمیز|difficulty peaceful,سختی سخت|difficulty hard"},
        {"بازیکن", "#3b82f6", "ثبت اسپاون همین‌جا|spawnpoint @s,درمان کامل|effect @s instant_health 1 5 true,سیر شدن|effect @s saturation 10 5 true,پاک‌کردن کیف|clear @s,۱۰۰ سطح تجربه|xp 100 @s,پرواز روشن|ability @s mayfly true,پرواز خاموش|ability @s mayfly false,لیست بازیکن‌ها|list"},
        {"آیتم‌ها", "#a855f7", "شمشیر ندرایتی|give @s netherite_sword,کلنگ ندرایتی|give @s netherite_pickaxe,کمان و تیر|give @s bow;give @s arrow 64,زره کامل ندرایتی|give @s netherite_helmet;give @s netherite_chestplate;give @s netherite_leggings;give @s netherite_boots,الیترا (بال)|give @s elytra,سیب طلایی|give @s golden_apple 8,استیک ۶۴|give @s cooked_beef 64,مروارید اندر|give @s ender_pearl 16,توتم جاودانگی|give @s totem_of_undying,بیکون|give @s beacon"},
        {"توانایی‌ها", "#ec4899", "سرعت فوق‌العاده|effect @s speed 300 2 true,پرش بلند|effect @s jump_boost 300 2 true,قدرت ضربت|effect @s strength 300 2 true,بازیابی جان|effect @s regeneration 300 2 true,مقاومت کامل|effect @s resistance 300 2 true,ضد آتش|effect @s fire_resistance 300 2 true,تنفس زیر آب|effect @s water_breathing 300 2 true,دید در شب|effect @s night_vision 300 2 true,نامرئی شدن|effect @s invisibility 300 2 true,سقوط آرام|effect @s slow_falling 300 2 true,پاک‌کردن افکت‌ها|effect @s clear"},
        {"ماب‌ها", "#84cc16", "اسب|summon horse,گربه|summon cat,گرگ|summon wolf,روستایی|summon villager,غول آهنی|summon iron_golem,زامبی|summon zombie,کریپر|summon creeper,اندرمن|summon enderman,اژدهای اندر|summon ender_dragon,حذف همهٔ ماب‌ها|kill @e[type=!player]"},
        {"دستور", "#06b6d4", "راهنمای دستورها|help,بازیکن‌های آنلاین|list,سید جهان|seed,ذخیرهٔ جهان|save-all,اعلان در سرور|say سرور آتا فعال است,اخراج بازیکن|kick,بن‌کردن بازیکن|"},
        {"مدیریت", "#ef4444", "روشن‌کردن سرور|@power:start,خاموش‌کردن سرور|@power:stop,ری‌استارت سرور|@power:restart,بکاپ فوری|@backup,وایت‌لیست روشن|,وایت‌لیست خاموش|"},
        {"پشتیبانی", "#14b8a6", "راهنمای آتا|help,لیست بازیکن‌ها|list,ذخیرهٔ جهان|save-all"},
    };

    private WindowManager wm;
    private WindowManager.LayoutParams lp;
    private FrameLayout root;
    private LinearLayout dock, tabColumn;
    private TextView handle, countdown;
    private ScrollView panel;
    private LinearLayout panelInner;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private int selectedTab = -1;
    private final Runnable autoClose = new Runnable() { @Override public void run() { collapse(); } };

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        buildViews();
        lp = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
        wm.addView(root, lp);
        resetIdle();
    }

    private int dp(int v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }

    private void buildViews() {
        root = new FrameLayout(this);

        handle = new TextView(this);
        handle.setText("◀ آتا");
        handle.setTextSize(15f);
        handle.setTypeface(null, Typeface.BOLD);
        handle.setTextColor(Color.rgb(4, 41, 27));
        handle.setPadding(dp(6), dp(14), dp(6), dp(14));
        handle.setBackgroundColor(Color.argb(215, 16, 185, 129));
        handle.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { expand(); } });
        enableDrag(handle);
        root.addView(handle, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL));

        dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setVisibility(View.GONE);

        tabColumn = new LinearLayout(this);
        tabColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout closeCol = new LinearLayout(this);
        closeCol.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            int color = Color.parseColor(TABS[i][1]);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rp.bottomMargin = dp(6);
            Button b = new Button(this);
            b.setText(TABS[i][0]);
            b.setTextSize(13f);
            b.setTypeface(null, Typeface.BOLD);
            b.setTextColor(Color.WHITE);
            b.setPadding(dp(12), dp(9), dp(10), dp(9));
            b.setBackgroundColor(Color.argb(190, Color.red(color), Color.green(color), Color.blue(color)));
            b.setAllCaps(false);
            b.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { toggleTab(idx); } });
            Button x = new Button(this);
            x.setText("✕");
            x.setTextSize(9f);
            x.setTextColor(Color.WHITE);
            x.setPadding(dp(4), dp(2), dp(4), dp(2));
            x.setBackgroundColor(Color.argb(120, 0, 0, 0));
            x.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { collapse(); } });
            row.addView(b); row.addView(x);
            tabColumn.addView(row, rp);
        }
        dock.addView(tabColumn);

        panel = new ScrollView(this);
        panel.setVisibility(View.GONE);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(dp(240), dp(340));
        pp.leftMargin = dp(8);
        panelInner = new LinearLayout(this);
        panelInner.setOrientation(LinearLayout.VERTICAL);
        panel.addView(panelInner);
        dock.addView(panel, pp);
        root.addView(dock, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL));
    }

    private void enableDrag(View v) {
        v.setOnTouchListener(new View.OnTouchListener() {
            int startY, startParamY; boolean moved;
            @Override public boolean onTouch(View view, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: startY = (int) e.getRawY(); startParamY = lp.y; moved = false; return true;
                    case MotionEvent.ACTION_MOVE:
                        int dy = (int) (e.getRawY() - startY);
                        if (Math.abs(dy) > 8) moved = true;
                        lp.y = startParamY - dy;
                        try { wm.updateViewLayout(root, lp); } catch (Exception ignored) { }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) view.performClick();
                        return true;
                }
                return false;
            }
        });
    }

    private void resetIdle() {
        handler.removeCallbacks(autoClose);
        handler.postDelayed(autoClose, AUTO_CLOSE_MS);
    }

    private void expand() {
        handle.setVisibility(View.GONE);
        dock.setVisibility(View.VISIBLE);
        for (int i = 0; i < tabColumn.getChildCount(); i++) {
            View row = tabColumn.getChildAt(i);
            row.setTranslationX(-dp(60));
            row.setAlpha(0f);
            row.animate().translationX(0).alpha(1f).setDuration(320).setStartDelay(i * 55L).start();
        }
        resetIdle();
    }

    /** جمع‌شدن کامل به دستگیرهٔ لبه (بستن خودکار یا دکمهٔ ضربدر) */
    private void collapse() {
        selectedTab = -1;
        panel.setVisibility(View.GONE);
        dock.setVisibility(View.GONE);
        handle.setVisibility(View.VISIBLE);
        handler.removeCallbacks(autoClose);
        resetIdle();
    }

    private void toggleTab(int idx) {
        resetIdle();
        if (selectedTab == idx) { panel.setVisibility(View.GONE); selectedTab = -1; highlight(); return; }
        selectedTab = idx;
        highlight();
        renderPanel(idx);
    }

    private void highlight() {
        for (int i = 0; i < tabColumn.getChildCount(); i++) {
            LinearLayout row = (LinearLayout) tabColumn.getChildAt(i);
            Button b = (Button) row.getChildAt(0);
            int color = Color.parseColor(TABS[i][1]);
            boolean sel = i == selectedTab;
            b.setBackgroundColor(Color.argb(sel ? 255 : 190, Color.red(color), Color.green(color), Color.blue(color)));
            row.setScaleX(sel ? 1.06f : 1f);
            row.setScaleY(sel ? 1.06f : 1f);
        }
    }

    private void renderPanel(int idx) {
        final int color = Color.parseColor(TABS[idx][1]);
        panelInner.removeAllViews();
        panelInner.setBackgroundColor(Color.argb(215, Color.red(color), Color.green(color), Color.blue(color)));
        panelInner.setPadding(dp(8), dp(8), dp(8), dp(8));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = new TextView(this);
        title.setText(TABS[idx][0]);
        title.setTextColor(Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextSize(14f);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        head.addView(title, tp);
        countdown = new TextView(this);
        countdown.setTextColor(Color.WHITE);
        countdown.setTextSize(11f);
        head.addView(countdown);
        panelInner.addView(head);
        startCountdown();

        String[] actions = TABS[idx][2].split(",");
        for (String a : actions) {
            String[] parts = a.split("\\|", -1);
            final String label = parts[0];
            final String cmd = parts.length > 1 ? parts[1] : "";
            Button b = new Button(this);
            b.setText(label);
            b.setAllCaps(false);
            b.setTextSize(12f);
            b.setTextColor(Color.WHITE);
            b.setBackgroundColor(Color.argb(70, 255, 255, 255));
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            bp.topMargin = dp(5);
            b.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { resetIdle(); fire(label, cmd); } });
            panelInner.addView(b, bp);
        }
        panel.setVisibility(View.VISIBLE);
        panel.setTranslationX(-dp(70));
        panel.setAlpha(0f);
        panel.animate().translationX(0).alpha(1f).setDuration(330).start();
    }

    private void startCountdown() {
        handler.removeCallbacks(tick);
        secondsLeft = (int) (AUTO_CLOSE_MS / 1000);
        handler.post(tick);
    }
    private int secondsLeft = 30;
    private final Runnable tick = new Runnable() { @Override public void run() {
        secondsLeft -= 1;
        if (countdown != null && secondsLeft > 0) { countdown.setText(secondsLeft + "s"); handler.postDelayed(this, 1000); }
        else if (countdown != null) countdown.setText("");
    }};

    /** ارسال دستور به پل محلی اپ؛ وب‌ویو آن را به سرور واقعی می‌رساند. */
    private void fire(final String label, final String cmd) {
        new Thread(new Runnable() { @Override public void run() {
            try {
                URL url = new URL("http://127.0.0.1:" + BRIDGE_PORT + "/command");
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                c.setRequestMethod("POST");
                c.setRequestProperty("Content-Type", "application/json");
                c.setConnectTimeout(2500); c.setReadTimeout(2500);
                c.setDoOutput(true);
                String body = "{\"label\":\"" + label.replace("\"", "") + "\",\"cmd\":\"" + cmd.replace("\"", "") + "\"}";
                OutputStream os = c.getOutputStream();
                os.write(body.getBytes(StandardCharsets.UTF_8));
                os.flush(); os.close();
                final int code = c.getResponseCode();
                handler.post(new Runnable() { @Override public void run() {
                    Toast.makeText(OverlayService.this, code == 200 ? "✓ «" + label + "» به آتا سپرده شد" : "خطا در ارسال به اپ", Toast.LENGTH_SHORT).show();
                }});
            } catch (final Exception e) {
                handler.post(new Runnable() { @Override public void run() {
                    Toast.makeText(OverlayService.this, "اپ آتا در حال اجرا نیست؛ اول اپ را باز کن", Toast.LENGTH_SHORT).show();
                }});
            }
        }}).start();
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        try { wm.removeView(root); } catch (Exception ignored) { }
        super.onDestroy();
    }
}
