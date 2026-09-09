package com.atta.mcpanel.server;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.atta.mcpanel.core.AppPrefs;
import com.atta.mcpanel.core.Palette;
import com.atta.mcpanel.rcon.RconConnection;
import com.atta.mcpanel.rcon.RconListener;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * مرکز سرور Atta — همه‌چیز داخل همین اپ، بدون Termux و بدون تایپ کد:
 * نصب خودکار PocketMine-MP (PHP مخصوص اندروید + هسته + پلاگین Atta)،
 * اجرا در پس‌زمینه، کنسول، مدیریت بازیکن/اپراتور، افزونه‌ها، Aternos و سلامت.
 */
public class ServerCenterActivity extends android.app.Activity {

    private enum Sec { DASH, MANAGE, PLUGINS, ATTERNOS, HEALTH }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private AppPrefs prefs;
    private LinearLayout content;

    // dash
    private TextView dashState, dashPhase, dashInstalled;
    private ProgressBar dashProgress;
    private TextView logTv;
    private String logAll = "";
    private final List<String> stepsDone = new ArrayList<String>();

    private TextView statusTv;
    private TextView playersTv, pluginsTv;
    private EditText opEt;
    private String report = "";
    private EmbeddedServer srv;
    // موتور دوم: Paper + Geyser
    private PaperServer psrv;
    private TextView paperState, paperPhase, paperInstalled, paperLogTv;
    private ProgressBar paperProgress;
    private String paperLogAll = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new AppPrefs(this);
        srv = EmbeddedServer.init(this);
        psrv = PaperServer.init(this);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        srv.addListener(emb);
        psrv.addListener(paperL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        srv.removeListener(emb);
        psrv.removeListener(paperL);
    }

    // شنوندهٔ سرور جاسازی‌شده
    private final EmbeddedServer.Listener emb = new EmbeddedServer.Listener() {
        @Override public void onLog(final String line) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    appendLog(line);
                    if (line.contains("Done") && line.contains("help")
                            && dashState != null) {
                        dashState.setText("✅ سرور روشن و آماده است — از بازی وارد 127.0.0.1 شو");
                        dashState.setTextColor(Palette.DOT_OK);
                    }
                }
            });
        }

        @Override public void onInstall(final int percent, final String phase) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    if (dashProgress != null) dashProgress.setProgress(percent);
                    if (dashPhase != null) dashPhase.setText("نصب: " + percent + "% — " + phase);
                    if (dashInstalled != null) refreshDash();
                }
            });
        }

        @Override public void onState(final boolean running) {
            runOnUiThread(new Runnable() {
                @Override public void run() { refreshDash(); }
            });
        }

        @Override public void onReady(boolean ready) {
            runOnUiThread(new Runnable() {
                @Override public void run() { refreshDash(); }
            });
        }
    };

    // شنوندهٔ موتور Paper + Geyser
    private final PaperServer.Listener paperL = new PaperServer.Listener() {
        @Override public void onLog(final String line) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    paperAppendLog(line);
                    if (line.contains("For help, type") && paperState != null) {
                        paperState.setText("✅ Paper آماده است — از بازی وارد 127.0.0.1:19132 شو (Geyser)");
                        paperState.setTextColor(Palette.DOT_OK);
                    }
                }
            });
        }
        @Override public void onInstall(final int percent, final String phase) {
            runOnUiThread(new Runnable() {
                @Override public void run() {
                    if (paperProgress != null) paperProgress.setProgress(percent);
                    if (paperPhase != null) paperPhase.setText("نصب Paper: " + percent + "% — " + phase);
                    if (paperInstalled != null) refreshPaperInfo();
                }
            });
        }
        @Override public void onState(final boolean running) {
            runOnUiThread(new Runnable() {
                @Override public void run() { refreshPaperInfo(); }
            });
        }
        @Override public void onReady(boolean ready) {
            runOnUiThread(new Runnable() {
                @Override public void run() { refreshPaperInfo(); }
            });
        }
    };

    // =====================================================================
    // رابط پایه
    // =====================================================================

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0C1119);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int p = dp(14);
        col.setPadding(p, dp(10), p, dp(20));

        TextView title = new TextView(this);
        title.setText("🖥 مرکز سرور Atta");
        title.setTextSize(20f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(Palette.TEXT_MAIN);
        title.setGravity(Gravity.CENTER);
        col.addView(title, new LinearLayout.LayoutParams(-1, dp(44)));

        TextView sub = new TextView(this);
        sub.setText("سرور PocketMine روی همین گوشی — نصب و اجرا کاملاً خودکار داخل اپ (بدون Termux)");
        sub.setTextSize(11.5f);
        sub.setTextColor(Palette.TEXT_SUB);
        sub.setGravity(Gravity.CENTER);
        col.addView(sub, new LinearLayout.LayoutParams(-1, dp(28)));

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"🚀 داشبورد", "🎛 مدیریت", "🧩 افزونه‌ها", "🌐 Aternos", "🩺 سلامت"};
        final Sec[] secs = {Sec.DASH, Sec.MANAGE, Sec.PLUGINS, Sec.ATTERNOS, Sec.HEALTH};
        for (int i = 0; i < names.length; i++) {
            final Sec s = secs[i];
            TextView b = navBtn(names[i]);
            b.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { showSection(s); }
            });
            nav.addView(b, new LinearLayout.LayoutParams(0, dp(38), 1f));
        }
        col.addView(nav, new LinearLayout.LayoutParams(-1, dp(44)));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        col.addView(content, new LinearLayout.LayoutParams(-1, -2));

        sv.addView(col, new ScrollView.LayoutParams(-1, -2));
        root.addView(sv, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        showSection(Sec.DASH);
    }

    private void showSection(Sec s) {
        if (content == null) return;
        content.removeAllViews();
        switch (s) {
            case DASH: buildDash(); break;
            case MANAGE: buildManage(); break;
            case PLUGINS: buildPlugins(); break;
            case ATTERNOS: buildAternos(); break;
            default: buildHealth();
        }
    }

    private TextView navBtn(String label) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(10.5f);
        tv.setGravity(Gravity.CENTER);
        tv.setTextColor(Palette.CHIP_TEXT);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Palette.SURFACE_HI);
        g.setCornerRadius(dp(10));
        g.setStroke(1, 0x33FFFFFF);
        tv.setBackground(g);
        tv.setClickable(true);
        return tv;
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        GradientDrawable g = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF1C2431, 0xFF121823});
        g.setCornerRadius(dp(16));
        g.setStroke(1, 0x40FFFFFF);
        c.setBackground(g);
        c.setPadding(dp(12), dp(8), dp(12), dp(10));
        return c;
    }

    private LinearLayout.LayoutParams cardLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(10);
        return lp;
    }

    private void cardTitle(LinearLayout c, String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(14f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(Palette.GOLD);
        c.addView(tv, new LinearLayout.LayoutParams(-1, dp(32)));
    }

    private TextView btn(String label, int kind) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(12.5f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setGravity(Gravity.CENTER);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(dp(11));
        if (kind == 1) {
            g.setGradientType(GradientDrawable.LINEAR_GRADIENT);
            g.setOrientation(GradientDrawable.Orientation.TL_BR);
            g.setColors(new int[]{0xFF96D96A, Palette.ACCENT_DEEP});
            tv.setTextColor(Palette.ON_ACCENT);
        } else if (kind == 2) {
            g.setColor(Palette.DANGER_BG);
            tv.setTextColor(Palette.DANGER_TEXT);
            g.setStroke(1, 0x44FFB3A6);
        } else {
            g.setColor(Palette.SURFACE_HI);
            tv.setTextColor(Palette.CHIP_TEXT);
            g.setStroke(1, 0x33FFFFFF);
        }
        tv.setBackground(g);
        tv.setClickable(true);
        return tv;
    }

    private EditText input(String hint, String text) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setSingleLine(true);
        et.setTextSize(13f);
        et.setTextColor(Palette.TEXT_MAIN);
        et.setHintTextColor(Palette.TEXT_DIM);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Palette.INPUT_BG);
        g.setCornerRadius(dp(10));
        g.setStroke(1, Palette.STROKE2);
        et.setBackground(g);
        et.setPadding(dp(10), 0, dp(10), 0);
        if (text != null) et.setText(text);
        return et;
    }

    private TextView label(String t, int color, float size) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(size);
        tv.setTextColor(color);
        tv.setPadding(0, dp(4), 0, dp(2));
        return tv;
    }

    private TextView monoText(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(11f);
        tv.setTypeface(Typeface.MONOSPACE);
        tv.setTextColor(0xFFD6E0EA);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xFF0A0E13);
        g.setCornerRadius(dp(8));
        g.setStroke(1, 0x33FFFFFF);
        tv.setBackground(g);
        tv.setPadding(dp(8), dp(8), dp(8), dp(8));
        return tv;
    }

    private LinearLayout hrow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    // =====================================================================
    // داشبورد (نصب + اجرا + لاگ)
    // =====================================================================

    private void buildDash() {
        LinearLayout c = card();
        cardTitle(c, "🚀 سرور PocketMine — نصب و اجرای خودکار");
        dashState = label("…", Palette.GOLD, 13f);
        c.addView(dashState);
        dashInstalled = label("", Palette.TEXT_SUB, 12f);
        c.addView(dashInstalled);

        dashProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        dashProgress.setMax(100);
        dashProgress.setProgress(0);
        c.addView(dashProgress, new LinearLayout.LayoutParams(-1, dp(14)));

        dashPhase = label("", Palette.TEXT_SUB, 11.5f);
        c.addView(dashPhase);

        // ===== انتخاب نسخهٔ موتور (باید با پروتکلِ نسخهٔ بازی گوشی هماهنگ باشد) =====
        c.addView(label("🎮 پروتکل نسخهٔ سرور — طبق نسخهٔ بازی خودت (در پایین صفحهٔ اول بازی ببین):",
                Palette.TEXT_SUB, 11.5f));
        LinearLayout rMode = hrow();
        final TextView m20 = btn("۱.۲۶.۲۰", 0);
        final TextView m30 = btn("۱.۲۶.۳۰", 0);
        final TextView mAuto = btn("🆕 خودکار", 0);
        refreshModeBtns(m20, m30, mAuto);
        View.OnClickListener mclick = new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (srv == null) return;
                if (srv.isRunning()) { toast("اول سرور را متوقف کن"); return; }
                String m = EmbeddedServer.MODE_2620;
                if (v == m30) m = EmbeddedServer.MODE_2630;
                else if (v == mAuto) m = EmbeddedServer.MODE_AUTO;
                srv.setMode(m);
                refreshModeBtns(m20, m30, mAuto);
                logAll = "";
                updateLog();
                dashProgress.setProgress(0);
                toast("حالت عوض شد — دکمهٔ «نصب کامل خودکار» را بزن");
            }
        };
        m20.setOnClickListener(mclick);
        m30.setOnClickListener(mclick);
        mAuto.setOnClickListener(mclick);
        rMode.addView(m20, new LinearLayout.LayoutParams(0, dp(40), 1f));
        rMode.addView(m30, new LinearLayout.LayoutParams(0, dp(40), 1f));
        rMode.addView(mAuto, new LinearLayout.LayoutParams(0, dp(40), 1f));
        c.addView(rMode, new LinearLayout.LayoutParams(-1, dp(44)));
        // توضیح کوتاه زیر دکمه‌ها
        c.addView(label("۱.۲۶.۲۰ ← PMMP 5.43.2 | ۱.۲۶.۳۰ ← PMMP 5.44.3 | 🆕 خودکار ← آخرین ریلیز PMMP "
                + "(بدراک ۱.۲۶.۴۵ هنوز هیچ PMMP رسمی ندارد؛ «خودکار» به محض انتشارش می‌گیردش)",
                Palette.TEXT_DIM, 10f));

        LinearLayout r1 = hrow();
        TextView bInstall = btn("⬇ نصب کامل خودکار", 1);
        bInstall.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                logAll = "";
                updateLog();
                dashProgress.setProgress(0);
                ServerHostService.startInstall(ServerCenterActivity.this);
            }
        });
        r1.addView(bInstall, new LinearLayout.LayoutParams(0, dp(46), 1f));
        TextView bStart = btn("▶ استارت", 1);
        bStart.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { ServerHostService.startServer(ServerCenterActivity.this); }
        });
        r1.addView(bStart, new LinearLayout.LayoutParams(0, dp(46), 1f));
        TextView bStop = btn("⏹ توقف", 2);
        bStop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (srv != null && srv.isRunning()) srv.stopAsync();
                else toast("سرور روشن نیست");
            }
        });
        r1.addView(bStop, new LinearLayout.LayoutParams(0, dp(46), 1f));
        c.addView(r1, new LinearLayout.LayoutParams(-1, dp(50)));

        LinearLayout r2 = hrow();
        TextView bRestart = btn("🔄 ری‌استارت", 0);
        bRestart.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (srv != null) srv.restartAsync(); }
        });
        r2.addView(bRestart, new LinearLayout.LayoutParams(0, dp(42), 1f));
        TextView bCopy = btn("📋 کپی لاگ", 0);
        bCopy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { copy(logAll); }
        });
        r2.addView(bCopy, new LinearLayout.LayoutParams(0, dp(42), 1f));
        TextView bClear = btn("🧹 پاک‌کردن لاگ", 0);
        bClear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { logAll = ""; updateLog(); }
        });
        r2.addView(bClear, new LinearLayout.LayoutParams(0, dp(42), 1f));
        c.addView(r2, new LinearLayout.LayoutParams(-1, dp(46)));

        logTv = monoText("");
        logTv.setMinHeight(dp(160));
        c.addView(logTv, new LinearLayout.LayoutParams(-1, -2));
        content.addView(c, cardLp());
        // لاگ‌های قبلی را دوباره نشان بده (اگر داشبورد دوباره ساخته شد)
        updateLog();
        refreshDash();

        content.addView(label("مراحل (اولین نصب): دانلود PHP مخصوص اندروید (~۲۷MB) ← دانلود PocketMine-MP "
                + "(~۴MB) ← کپی خودکار پلاگین Atta از داخل اپ ← استارت. "
                + "همه‌چیز داخل همین اپ است؛ هیچ برنامهٔ دیگری لازم نیست.", Palette.TEXT_DIM, 11.5f));
        content.addView(label("بعد از «Done» در لاگ، از خود بازی بدراک به 127.0.0.1:19132 وصل شو؛ "
                + "سپس در بخش «مدیریت» نامت (gaser) را اپراتور کن.", Palette.TEXT_DIM, 11.5f));
        buildPaperCard();
        refreshDash();
    }

    private void buildPaperCard() {
        LinearLayout c = card();
        cardTitle(c, "☕ موتور دوم: Paper + Geyser — برای نسخه‌های تازهٔ بدراک (مثل ۱.۲۶.۴۵)");
        paperState = label("…", Palette.GOLD, 12.5f);
        c.addView(paperState);
        paperInstalled = label("", Palette.TEXT_SUB, 11f);
        c.addView(paperInstalled);
        paperProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        paperProgress.setMax(100);
        paperProgress.setProgress(0);
        c.addView(paperProgress, new LinearLayout.LayoutParams(-1, dp(12)));
        paperPhase = label("", Palette.TEXT_SUB, 11f);
        c.addView(paperPhase);

        LinearLayout r1 = hrow();
        TextView bInst = btn("⬇ نصب Paper", 1);
        bInst.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                paperLogAll = "";
                paperUpdateLog();
                if (paperProgress != null) paperProgress.setProgress(0);
                ServerHostService.paperInstall(ServerCenterActivity.this);
            }
        });
        r1.addView(bInst, new LinearLayout.LayoutParams(0, dp(44), 1f));
        TextView bStart = btn("▶ استارت", 1);
        bStart.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { ServerHostService.paperStart(ServerCenterActivity.this); }
        });
        r1.addView(bStart, new LinearLayout.LayoutParams(0, dp(44), 1f));
        TextView bStop = btn("⏹ توقف", 2);
        bStop.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (psrv != null && psrv.isRunning()) ServerHostService.paperStop(ServerCenterActivity.this);
                else toast("Paper روشن نیست");
            }
        });
        r1.addView(bStop, new LinearLayout.LayoutParams(0, dp(44), 1f));
        TextView bRestart = btn("🔄", 0);
        bRestart.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (psrv != null) psrv.restartAsync(); }
        });
        r1.addView(bRestart, new LinearLayout.LayoutParams(0, dp(44), 0.7f));
        c.addView(r1, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout r2 = hrow();
        TextView bCopy = btn("📋 کپی لاگ Paper", 0);
        bCopy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { copy(paperLogAll); }
        });
        r2.addView(bCopy, new LinearLayout.LayoutParams(0, dp(40), 1f));
        TextView bClear = btn("🧹 پاک کردن", 0);
        bClear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { paperLogAll = ""; paperUpdateLog(); }
        });
        r2.addView(bClear, new LinearLayout.LayoutParams(0, dp(40), 1f));
        c.addView(r2, new LinearLayout.LayoutParams(-1, dp(44)));

        paperLogTv = monoText("");
        paperLogTv.setMinHeight(dp(140));
        c.addView(paperLogTv, new LinearLayout.LayoutParams(-1, -2));

        content.addView(label("اولین اجرا: JRE جاوا (~۱۵۰MB) + Paper + Geyser + ViaVersion دانلود می‌شود؛ "
                + "بعد از نصب، «▶ استارت» — ورود بازی از همان 127.0.0.1:19132 (Geyser خودش پل می‌زند). "
                + "نکته: گوشی باید قوی باشد (~۶GB+ رم)؛ حافظهٔ جاوا ۱۵۳۶MB است.",
                Palette.TEXT_DIM, 10.5f));
        content.addView(c, cardLp());
        refreshPaperInfo();
    }

    private void refreshPaperInfo() {
        if (paperState == null || psrv == null) return;
        if (psrv.isRunning()) {
            paperState.setText("🟢 Paper در حال اجراست — از بازی وارد 127.0.0.1:19132 شو");
            paperState.setTextColor(Palette.DOT_OK);
        } else if (psrv.isBusy()) {
            paperState.setText("🟡 در حال کار…");
            paperState.setTextColor(Palette.GOLD);
        } else {
            paperState.setText("⚪ متوقف");
            paperState.setTextColor(Palette.TEXT_SUB);
        }
        if (paperInstalled != null) paperInstalled.setText(psrv.infoLine());
    }

    private void paperAppendLog(String s) {
        if (s == null || s.isEmpty()) return;
        paperLogAll += s + "\n";
        if (paperLogAll.length() > 30000) {
            paperLogAll = paperLogAll.substring(paperLogAll.length() - 30000);
        }
        paperUpdateLog();
    }

    private void paperUpdateLog() {
        if (paperLogTv == null) return;
        final String t = paperLogAll;
        paperLogTv.post(new Runnable() {
            @Override public void run() {
                if (paperLogTv != null) paperLogTv.setText(t);
            }
        });
    }

    private void refreshModeBtns(TextView m20, TextView m30, TextView mAuto) {
        String m = srv != null ? srv.currentMode() : EmbeddedServer.MODE_2630;
        m20.setText(m.equals(EmbeddedServer.MODE_2620) ? "✓ ۱.۲۶.۲۰" : "۱.۲۶.۲۰");
        m30.setText(m.equals(EmbeddedServer.MODE_2630) ? "✓ ۱.۲۶.۳۰" : "۱.۲۶.۳۰");
        mAuto.setText(m.equals(EmbeddedServer.MODE_AUTO) ? "✓ 🆕 خودکار" : "🆕 خودکار");
        m20.setAlpha(m.equals(EmbeddedServer.MODE_2620) ? 1f : 0.55f);
        m30.setAlpha(m.equals(EmbeddedServer.MODE_2630) ? 1f : 0.55f);
        mAuto.setAlpha(m.equals(EmbeddedServer.MODE_AUTO) ? 1f : 0.55f);
    }

    private void refreshDash() {
        if (dashState == null || srv == null) return;
        boolean running = srv.isRunning();
        boolean installed = srv.installed();
        if (running) {
            dashState.setText("🟢 سرور در حال اجراست — از بازی وصل شو: 127.0.0.1 : 19132");
            dashState.setTextColor(Palette.DOT_OK);
        } else if (installed) {
            dashState.setText("🟡 نصب کامل است — دکمهٔ «▶ استارت» را بزن");
            dashState.setTextColor(Palette.GOLD);
        } else {
            dashState.setText("🔴 هنوز نصب نشده — دکمهٔ «⬇ نصب کامل خودکار» را بزن (یک‌بار، ~۳۰MB)");
            dashState.setTextColor(Palette.DOT_BAD);
        }
        if (dashInstalled != null) {
            dashInstalled.setText(installed
                    ? "وضعیت فایل‌ها: PHP ✓  PocketMine-MP ✓  پلاگین Atta ✓"
                    : "وضعیت فایل‌ها: ناقص است — نصب کن");
        }
    }

    // =====================================================================
    // مدیریت
    // =====================================================================

    private void buildManage() {
        LinearLayout c = card();
        cardTitle(c, "🎛 مدیریت سرور");
        statusTv = label("وضعیت: —", Palette.GOLD, 13f);
        c.addView(statusTv);
        refreshStatus();

        LinearLayout r0 = hrow();
        TextView st = btn("▶ استارت", 1);
        st.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { ServerHostService.startServer(ServerCenterActivity.this); }
        });
        r0.addView(st, new LinearLayout.LayoutParams(0, dp(42), 1f));
        TextView sp = btn("⏹ توقف", 2);
        sp.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (srv != null && srv.isRunning()) srv.stopAsync(); }
        });
        r0.addView(sp, new LinearLayout.LayoutParams(0, dp(42), 1f));
        TextView rs = btn("🔄 ری‌استارت", 0);
        rs.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (srv != null) srv.restartAsync(); }
        });
        r0.addView(rs, new LinearLayout.LayoutParams(0, dp(42), 1f));
        c.addView(r0, new LinearLayout.LayoutParams(-1, dp(46)));

        // اتصال خارجی (RCON) — برای Aternos یا سرورهای دیگر
        c.addView(label("اتصال خارجی (اختیاری — برای Aternos/سرور دیگر؛ سرور گوشی از کنسول داخلی استفاده می‌کند):",
                Palette.TEXT_DIM, 11f));
        LinearLayout r1 = hrow();
        final EditText host = input("آدرس", prefs.getRconHost().isEmpty() ? "localhost" : prefs.getRconHost());
        final EditText port = input("پورت", String.valueOf(prefs.getRconPort()));
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        r1.addView(host, new LinearLayout.LayoutParams(0, dp(42), 1f));
        r1.addView(port, new LinearLayout.LayoutParams(0, dp(42), 1f));
        c.addView(r1, new LinearLayout.LayoutParams(-1, dp(46)));
        final EditText pass = input("رمز RCON", prefs.getRconPassword());
        c.addView(pass, new LinearLayout.LayoutParams(-1, dp(42)));
        LinearLayout r2 = hrow();
        TextView save = btn("💾 ذخیره و تست (خارجی)", 1);
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.setRconHost(host.getText().toString().trim());
                String pt = port.getText().toString().trim();
                prefs.setRconPort(pt.isEmpty() ? 25575 : Integer.parseInt(pt));
                prefs.setRconPassword(pass.getText().toString());
                testExternal();
            }
        });
        r2.addView(save, new LinearLayout.LayoutParams(0, dp(44), 1f));
        c.addView(r2, new LinearLayout.LayoutParams(-1, dp(48)));

        // بازیکن و اپراتور
        c.addView(label("👥 بازیکن آنلاین و اپراتور:", Palette.CHIP_TEXT, 13f));
        playersTv = label("…", Palette.TEXT_SUB, 12f);
        c.addView(playersTv);
        LinearLayout r4 = hrow();
        opEt = input("نام برای اپراتور (پیش‌فرض: gaser)",
                prefs.getPlayerName().equals("@s") ? "gaser" : prefs.getPlayerName());
        r4.addView(opEt, new LinearLayout.LayoutParams(0, dp(42), 1f));
        TextView opb = btn("⭐ اپراتور کن", 1);
        opb.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String n = opEt.getText().toString().trim();
                if (!n.isEmpty()) {
                    prefs.setPlayerName(n);
                    sendOp(n);
                }
            }
        });
        r4.addView(opb, new LinearLayout.LayoutParams(0, dp(42), 1f));
        c.addView(r4, new LinearLayout.LayoutParams(-1, dp(46)));
        TextView listb = btn("🔄 فهرست بازیکنان", 0);
        listb.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendManage("list"); }
        });
        c.addView(listb, new LinearLayout.LayoutParams(-1, dp(40)));
        content.addView(c, cardLp());

        LinearLayout c4 = card();
        cardTitle(c4, "⌨ کنسول سرور");
        final EditText con = input("مثل: atta fly / gamemode creative gaser / say سلام / ban …", "");
        c4.addView(con, new LinearLayout.LayoutParams(-1, dp(46)));
        TextView run = btn("ارسال", 1);
        run.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String cmd = con.getText().toString().trim();
                if (!cmd.isEmpty()) sendManage(cmd);
            }
        });
        c4.addView(run, new LinearLayout.LayoutParams(-1, dp(44)));
        content.addView(c4, cardLp());
    }

    private void refreshStatus() {
        if (statusTv == null) return;
        if (srv != null && srv.isRunning()) {
            statusTv.setText("🟢 سرور گوشی روشن است (کنسول داخلی)");
            statusTv.setTextColor(Palette.DOT_OK);
        } else {
            statusTv.setText("⚪ سرور گوشی روشن نیست — یا اتصال خارجی را «تست» کن");
            statusTv.setTextColor(Palette.TEXT_SUB);
        }
    }

    private void sendManage(final String cmd) {
        if (srv != null && srv.isRunning()) {
            if (cmd.trim().equals("list")) {
                appendLog("> list  (برای دیدن نتیجه، خروجی کنسول را در لاگ ببین)");
            }
            srv.console(cmd);
        } else {
            rconNow(cmd);
        }
    }

    private void sendOp(String name) {
        if (srv != null && srv.isRunning()) {
            srv.console("op " + name);
            appendLog("دستور ارسال شد: op " + name);
        } else {
            rconNow("op " + name);
        }
    }

    private void testExternal() {
        setStatus("در حال تست اتصال خارجی…", Palette.GOLD);
        new Thread(new Runnable() {
            @Override public void run() {
                RconSync rc = new RconSync(host(), port(), pass());
                final boolean ok = rc.test();
                ui.post(new Runnable() {
                    @Override public void run() {
                        setStatus(ok ? "✅ اتصال خارجی برقرار شد" : "❌ وصل نشد (برای سرور گوشی نیازی نیست)",
                                ok ? Palette.DOT_OK : Palette.DOT_BAD);
                    }
                });
            }
        }).start();
    }

    private void setStatus(String s, int color) {
        if (statusTv != null) {
            statusTv.setText(s);
            statusTv.setTextColor(color);
        }
    }

    private String host() {
        String h = prefs.getRconHost();
        return (h == null || h.trim().isEmpty()) ? "localhost" : h.trim();
    }

    private int port() { return prefs.getRconPort(); }

    private String pass() {
        String p = prefs.getRconPassword();
        return p == null ? "" : p;
    }

    private void rconNow(final String cmd) {
        new Thread(new Runnable() {
            @Override public void run() {
                RconSync rc = new RconSync(host(), port(), pass());
                final String out = rc.command(cmd);
                ui.post(new Runnable() {
                    @Override public void run() {
                        appendLog("> " + cmd + "\n" + (out == null ? "(وصل نشد)" : out));
                    }
                });
            }
        }).start();
    }

    // =====================================================================
    // افزونه‌ها
    // =====================================================================

    private File pluginsDir() {
        return srv != null ? new File(srv.serverDir(), "plugins") : null;
    }

    private void buildPlugins() {
        LinearLayout c = card();
        cardTitle(c, "🧩 افزونه‌های سرور گوشی");
        c.addView(label("پلاگین Atta خودکار نصب شده است. بقیه را با یک کلیک از Poggit (منبع رسمی پلاگین‌های "
                + "PocketMine) نصب کن. بعد از نصب، «ری‌استارت» بزن.", Palette.TEXT_SUB, 11.5f));

        pluginsTv = label("…", Palette.TEXT_SUB, 12f);
        c.addView(pluginsTv);
        LinearLayout r0 = hrow();
        TextView refresh = btn("🔄 تازه‌سازی فهرست", 0);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { refreshPluginsList(); }
        });
        r0.addView(refresh, new LinearLayout.LayoutParams(0, dp(40), 1f));
        TextView rm = btn("🗑 حذف پلاگین", 2);
        rm.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { removePluginDialog(); }
        });
        r0.addView(rm, new LinearLayout.LayoutParams(0, dp(40), 1f));
        c.addView(r0, new LinearLayout.LayoutParams(-1, dp(44)));
        content.addView(c, cardLp());
        refreshPluginsList();

        content.addView(label("🔥 پلاگین‌های پیشنهادی (محبوب و سبک):", Palette.GOLD, 13f));
        String[][] items = {
                {"EconomyAPI", "اقتصاد و سکه"},
                {"PurePerms", "مدیریت گروه‌ها و دسترسی‌ها"},
                {"WorldProtect", "محافظت از مناطق/دنیا"},
                {"ClearLagg", "کاهش لگ — پاک‌سازی موجودات"},
                {"SimpleAuth", "رمز عبور برای ورود"},
                {"CustomAlerts", "پیام‌های اعلانی"},
                {"Kits", "کیت‌های آماده"},
                {"EssentialsPE", "دستورهای پایه (تله‌پورت/هوم)"},
        };
        for (final String[] it : items) {
            LinearLayout row = card();
            row.setPadding(dp(10), dp(4), dp(10), dp(6));
            TextView t1 = new TextView(this);
            t1.setText("🔥 " + it[0]);
            t1.setTextSize(13f);
            t1.setTypeface(Typeface.DEFAULT_BOLD);
            t1.setTextColor(Palette.CHIP_TEXT);
            row.addView(t1, new LinearLayout.LayoutParams(-1, dp(22)));
            TextView t2 = new TextView(this);
            t2.setText(it[1]);
            t2.setTextSize(11f);
            t2.setTextColor(Palette.TEXT_SUB);
            row.addView(t2, new LinearLayout.LayoutParams(-1, dp(18)));
            TextView inst = btn("⬇ نصب خودکار (از Poggit)", 1);
            inst.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { installPoggit(it[0]); }
            });
            row.addView(inst, new LinearLayout.LayoutParams(-1, dp(36)));
            content.addView(row, cardLp());
        }

        content.addView(label("توجه: نسخهٔ بازی تو (۱٫۲۶٫۴۵٫۱) باید با نسخهٔ PocketMine-MP سازگار باشد؛ "
                + "اگر هنگام وصل «Protocol mismatch» دیدی، دکمهٔ «نصب کامل خودکار» را دوباره بزن تا "
                + "نسخهٔ تازهٔ PocketMine دانلود شود.", Palette.TEXT_DIM, 11f));
    }

    private void refreshPluginsList() {
        if (pluginsTv == null) return;
        File d = pluginsDir();
        StringBuilder sb = new StringBuilder("فهرست پوشهٔ plugins:\n");
        if (d != null && d.exists()) {
            File[] fs = d.listFiles();
            if (fs == null || fs.length == 0) sb.append("(خالی)");
            else for (File f : fs) sb.append("• ").append(f.getName()).append("\n");
        } else {
            sb.append("(پوشه هنوز ساخته نشده — اول نصب کن)");
        }
        pluginsTv.setText(sb.toString());
    }

    private void installPoggit(final String name) {
        appendLog("⬇ جستجوی «" + name + "» در Poggit…");
        new Thread(new Runnable() {
            @Override public void run() {
                final String url = Poggit.findPhar(name);
                ui.post(new Runnable() {
                    @Override public void run() {
                        if (url == null) {
                            appendLog("✗ «" + name + "» در Poggit پیدا نشد.");
                        } else {
                            appendLog("✓ پیدا شد — دانلود مستقیم…");
                            downloadPlugin(name, url);
                        }
                    }
                });
            }
        }).start();
    }

    private void downloadPlugin(final String name, final String url) {
        new Thread(new Runnable() {
            @Override public void run() {
                File d = pluginsDir();
                if (d == null) { appendLog("ابتدا سرور را نصب کن."); return; }
                d.mkdirs();
                File target = new File(d, name + ".phar");
                boolean ok = EmbeddedServer.downloadStatic(url, target);
                ui.post(new Runnable() {
                    @Override public void run() {
                        appendLog(ok ? "✅ " + name + ".phar نصب شد — ری‌استارت بزن"
                                : "✗ دانلود نشد (اینترنت/منبع را بررسی کن)");
                        refreshPluginsList();
                    }
                });
            }
        }).start();
    }

    private void removePluginDialog() {
        final EditText et = input("نام دقیق فایل (مثل EconomyAPI.phar)", "");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(22), dp(6), dp(22), 0);
        box.addView(et, new LinearLayout.LayoutParams(-1, dp(48)));
        new android.app.AlertDialog.Builder(this)
                .setTitle("🗑 حذف پلاگین")
                .setMessage("نام فایل را از فهرست بالا کپی کن.")
                .setView(box)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("حذف", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) {
                        String n = et.getText().toString().trim();
                        File p = new File(pluginsDir(), n);
                        if (p.exists()) p.delete();
                        refreshPluginsList();
                        appendLog("حذف شد: " + n + " (ری‌استارت بزن)");
                    }
                })
                .show();
    }

    // =====================================================================
    // Aternos
    // =====================================================================

    private void buildAternos() {
        // ===== ۱) توضیح و دکمه‌های اصلی =====
        LinearLayout c = card();
        cardTitle(c, "🌐 حالت Aternos — سرور ابری رایگان (Paper + Geyser)");
        c.addView(label("Aternos یک سرور «جاوا» (Paper) در ابر به تو می‌دهد. با افزودن Geyser، "
                + "بدراکِ تازه (مثل ۱.۲۶.۴۵) هم وارد همان سرور می‌شود. تو با اکانت خودت اپراتور آن هستی.",
                Palette.TEXT_SUB, 12f));
        LinearLayout row = hrow();
        TextView bPanel = btn("🔌 پنل", 0);
        bPanel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openUrl("https://aternos.org/go/"); }
        });
        row.addView(bPanel, new LinearLayout.LayoutParams(0, dp(44), 1f));
        TextView bCons = btn("🎛 کنسول", 0);
        bCons.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openUrl("https://aternos.org/server/"); }
        });
        row.addView(bCons, new LinearLayout.LayoutParams(0, dp(44), 1f));
        TextView bPlug = btn("🧩 پلاگین‌ها", 0);
        bPlug.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openUrl("https://aternos.org/plugins/"); }
        });
        row.addView(bPlug, new LinearLayout.LayoutParams(0, dp(44), 1f));
        c.addView(row, new LinearLayout.LayoutParams(-1, dp(48)));
        content.addView(c, cardLp());

        // ===== ۲) راهنمای قدم‌به‌قدم Paper + Geyser =====
        LinearLayout c2 = card();
        cardTitle(c2, "☕ راه‌اندازی Paper + Geyser روی Aternos (یک بار انجام بده)");
        c2.addView(monoText("1) در پنل: «نرم‌افزار» ← گزینهٔ Paper را انتخاب و ذخیره کن\n"
                + "2) «پلاگین‌ها» ← جستجو و نصب این سه:\n"
                + "     • GeyserMC      (پل بدراک ← جاوا)\n"
                + "     • Floodgate     (ورود بدراک بدون اتصال Xbox)\n"
                + "     • ViaVersion    (نسخه‌های مختلف کلاینت جاوا)\n"
                + "3) «تنظیمات» ← RAM را تا حد ممکن بالا ببر (مثلاً ۳ تا ۴ گیگ)\n"
                + "4) استارت بزن و صبر کن کنار سرور بنویسد Online\n"
                + "5) در بازی: افزودن سرور ← آدرس aternos شما ← پورت 19132 (بدراک)\n"
                + "     (اگر نشد پورت را از روی اطلاعات سرور Aternos ببین)\n"
                + "6) یوزرنیم خودت در بازی: gaser  ← تا اپراتور بمانی"));
        content.addView(c2, cardLp());

        // ===== ۳) اتصال (اطلاعات آدرس Aternos + RCON اختیاری) =====
        LinearLayout c3 = card();
        cardTitle(c3, "🔗 اطلاعات اتصال به Aternos");
        final EditText host = input("آدرس سرور (you.aternos.me)", prefs.getRconHost());
        final EditText port = input("پورت", String.valueOf(prefs.getRconPort()));
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        c3.addView(host, new LinearLayout.LayoutParams(-1, dp(42)));
        c3.addView(port, new LinearLayout.LayoutParams(-1, dp(42)));
        final EditText pass = input("رمز RCON (فقط اگر Aternos پورت RCON بدهد)", prefs.getRconPassword());
        c3.addView(pass, new LinearLayout.LayoutParams(-1, dp(42)));
        c3.addView(label("نکته: Aternos معمولاً RCON بیرونی نمی‌دهد؛ کنسولش را از دکمهٔ «🎛 کنسول» باز کن. "
                + "این فیلدها برای سرورهای دیگر (یا اگر پورت RCON گرفتی) است.",
                Palette.TEXT_DIM, 10.5f));
        TextView save = btn("💾 ذخیره و تست اتصال", 1);
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.setRconHost(host.getText().toString().trim());
                String pt = port.getText().toString().trim();
                prefs.setRconPort(pt.isEmpty() ? 25575 : Integer.parseInt(pt));
                prefs.setRconPassword(pass.getText().toString());
                testExternal();
            }
        });
        c3.addView(save, new LinearLayout.LayoutParams(-1, dp(44)));
        TextView backLocal = btn("↩️ بازگشت به حالت سرور گوشی", 0);
        backLocal.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                prefs.setRconHost("localhost");
                prefs.setRconPort(25575);
                prefs.setRconPassword("attapass");
                toast("تنظیمات به سرور گوشی برگشت");
            }
        });
        c3.addView(backLocal, new LinearLayout.LayoutParams(-1, dp(42)));
        content.addView(c3, cardLp());

        // ===== ۴) یادآوری مقایسه =====
        LinearLayout c4 = card();
        cardTitle(c4, "ℹ کدام را کی استفاده کنم؟");
        c4.addView(monoText("• PocketMine گوشی ← بدراک ۱.۲۶.۳۰ و پایین‌تر (سبک)\n"
                + "• Paper گوشی ← بدراک تازه مثل ۱.۲۶.۴۵ (سنگین)\n"
                + "• Aternos ← هر نسخهٔ جاوا/بدراک؛ رایگان در ابر، اما باید\n"
                + "    سرور را از پنل Aternos روشن کنی و ممکن است صف انتظار داشته باشد"));
        content.addView(c4, cardLp());
    }

    // =====================================================================
    // سلامت
    // =====================================================================

    private void buildHealth() {
        LinearLayout c = card();
        cardTitle(c, "🩺 تست سلامت و سازگاری");
        final LinearLayout out = new LinearLayout(this);
        out.setOrientation(LinearLayout.VERTICAL);
        c.addView(out);
        TextView go = btn("▶ اجرای تست کامل", 1);
        go.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                out.removeAllViews();
                report = "گزارش سلامت Atta — "
                        + new java.text.SimpleDateFormat("yyyy/MM/dd HH:mm").format(new java.util.Date()) + "\n";
                runHealth(out);
            }
        });
        c.addView(go, new LinearLayout.LayoutParams(-1, dp(46)));
        LinearLayout r = hrow();
        TextView cp = btn("📋 کپی گزارش", 0);
        cp.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { copy(report); }
        });
        r.addView(cp, new LinearLayout.LayoutParams(0, dp(40), 1f));
        c.addView(r, new LinearLayout.LayoutParams(-1, dp(44)));
        content.addView(c, cardLp());
    }

    private void runHealth(final LinearLayout out) {
        addHealth(out, "برنامه Atta", true, "در حال اجراست");
        boolean installed = srv != null && srv.installed();
        addHealth(out, "فایل‌های سرور (PHP + PocketMine)", installed,
                installed ? "نصب شده" : "نصب نشده — از داشبورد نصب کن");
        boolean running = srv != null && srv.isRunning();
        addHealth(out, "سرور روشن است؟", running,
                running ? "بله — در پس‌زمینه در حال اجراست" : "خیر — دکمهٔ استارت را بزن");
        addHealth(out, "نسخهٔ بازی بدراک", true,
                "۱٫۲۶٫۴۵٫۱ — وصل‌شدن نیاز به نسخهٔ سازگار PocketMine دارد (در صورت خطا، نصب تازه کن)");
        addHealth(out, "بدون نیاز به Termux", true, "همه‌چیز داخل خود اپ انجام می‌شود");
        if (running) {
            addHealth(out, "پورت بازی 19132", true, "سرور در حال گوش‌دادن است");
        }
    }

    private void addHealth(LinearLayout out, String name, boolean ok, String detail) {
        TextView tv = new TextView(this);
        tv.setText((ok ? "✅ " : "❌ ") + name + " — " + detail);
        tv.setTextSize(12.5f);
        tv.setTextColor(ok ? Palette.DOT_OK : Palette.DOT_BAD);
        tv.setPadding(0, dp(3), 0, dp(2));
        out.addView(tv, new LinearLayout.LayoutParams(-1, -2));
        report += (ok ? "✅ " : "❌ ") + name + " — " + detail + "\n";
    }

    // =====================================================================
    // RCON خارجی / Poggit
    // =====================================================================

    private static class RconSync {
        private final String host, pass;
        private final int port;

        RconSync(String host, int port, String pass) {
            this.host = host;
            this.port = port;
            this.pass = pass == null ? "" : pass;
        }

        boolean test() {
            final CountDownLatch done = new CountDownLatch(1);
            final boolean[] ok = {false};
            RconConnection c = new RconConnection(host, port, pass, new RconListener() {
                @Override public void onAuth(boolean a, String message) { ok[0] = a; done.countDown(); }
                @Override public void onClosed(String reason) { done.countDown(); }
            }, 5000);
            c.start();
            try { done.await(7, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            c.stop();
            return ok[0];
        }

        String command(final String cmd) {
            final CountDownLatch auth = new CountDownLatch(1);
            final boolean[] ok = {false};
            final StringBuilder sb = new StringBuilder();
            final RconConnection c = new RconConnection(host, port, pass, new RconListener() {
                @Override public void onAuth(boolean a, String message) { ok[0] = a; auth.countDown(); }
                @Override public void onOutput(String text) { sb.append(text).append("\n"); }
                @Override public void onClosed(String reason) { auth.countDown(); }
            }, 5000);
            c.start();
            try { auth.await(6, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
            if (!ok[0]) { c.stop(); return null; }
            c.send(cmd);
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            c.stop();
            return sb.toString().trim();
        }
    }

    private static class Poggit {
        static String findPhar(String name) {
            try {
                URL u = new URL("https://poggit.pmmp.io/releases.json?name="
                        + java.net.URLEncoder.encode(name, "UTF-8"));
                HttpURLConnection c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(10000);
                c.setRequestProperty("User-Agent", "Atta/1.7");
                BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String l;
                while ((l = r.readLine()) != null) sb.append(l);
                r.close();
                String body = sb.toString();
                String key = "\"artifact_url\":\"";
                int i = body.indexOf(key);
                if (i < 0) { key = "\"artifact_url\": \""; i = body.indexOf(key); }
                if (i < 0) return null;
                i += key.length();
                int j = body.indexOf('"', i);
                if (j < 0) return null;
                String url = body.substring(i, j);
                return url.startsWith("http") ? url : "https://poggit.pmmp.io" + url;
            } catch (Exception e) {
                return null;
            }
        }
    }

    // =====================================================================
    // ابزارهای کوچک
    // =====================================================================

    private void appendLog(String s) {
        if (s == null || s.isEmpty()) return;
        logAll += s + "\n";
        if (logAll.length() > 30000) {
            logAll = logAll.substring(logAll.length() - 30000);
        }
        updateLog();
    }

    private void updateLog() {
        if (logTv == null) return;
        final String s = logAll;
        // متن را فقط روی نخ اصلی به‌روز کن (بدون scrollTo که متن را بیرون کادر می‌فرستاد)
        logTv.post(new Runnable() {
            @Override public void run() {
                if (logTv != null) logTv.setText(s);
            }
        });
    }

    private void copy(String s) {
        if (s == null || s.isEmpty()) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("atta", s));
        toast("کپی شد");
    }

    private void toast(String m) {
        android.widget.Toast.makeText(this, m, android.widget.Toast.LENGTH_SHORT).show();
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            toast("باز نشد");
        }
    }

    private int dp(float v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    @Override
    protected void onDestroy() {
        srv.removeListener(emb);
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
