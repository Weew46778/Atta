package com.atta.mcpanel.overlay.tabs;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.atta.mcpanel.core.Palette;
import com.atta.mcpanel.overlay.PanelHost;
import com.atta.mcpanel.overlay.UiKit;
import com.atta.mcpanel.overlay.pvp.ChallengeEngine;

/**
 * زبانهٔ «چالش» — مسابقه‌های دونفره با داوری درون‌برنامه:
 * انتخاب قالب مسابقه، ثبت نام دو طرف، رأی داور بعد از هر راند،
 * راند طلایی هنگام تساوی، اعلام برنده و جایزه (کپی یا ارسال به چت/کنسول).
 */
public final class ChallengesTab {

    private ChallengesTab() {}

    private static LinearLayout area;
    private static PanelHost hostRef;

    private static final int COL_A = 0xFFFFD54F; // طلایی
    private static final int COL_B = 0xFF5BD6E8; // فیروزه‌ای

    public static View build(final PanelHost host) {
        hostRef = host;
        final Context ctx = host.getContext();

        LinearLayout col = UiKit.vcol(ctx, 10);
        col.addView(UiKit.caption(ctx,
                "🎮 مسابقهٔ دونفره با داوری تو: بعد از هر راند، رأی بده — امتیازها، "
                        + "راند طلایی، برنده و جایزه خودکار ثبت می‌شود.", false));

        area = new LinearLayout(ctx);
        area.setOrientation(LinearLayout.VERTICAL);
        area.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        col.addView(area);

        refreshArea(ctx);

        ScrollView sv = new ScrollView(ctx);
        sv.setFillViewport(true);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(col);
        return sv;
    }

    private static void refreshArea(Context ctx) {
        if (area == null || hostRef == null) return;
        area.removeAllViews();
        if (!ChallengeEngine.running && !ChallengeEngine.finished) {
            buildIdle(ctx);
        } else if (!ChallengeEngine.finished) {
            buildLive(ctx);
        } else {
            buildResult(ctx);
        }
    }

    // ---------------- وضعیت بی‌مسابقه ----------------

    private static void buildIdle(final Context ctx) {
        area.addView(UiKit.sectionLabel(ctx, "قالب مسابقه را انتخاب کن"));
        final TextView[] tpl = new TextView[ChallengeEngine.TEMPLATES.length];
        for (int i = 0; i < tpl.length; i++) {
            final int idx = i;
            tpl[i] = UiKit.chip(ctx, ChallengeEngine.TEMPLATES[i][0], new Runnable() {
                @Override public void run() {
                    ChallengeEngine.selected = idx;
                    if (hostRef != null) {
                        hostRef.flash("قالب انتخاب شد", ChallengeEngine.TEMPLATES[idx][1]);
                    }
                    refreshArea(ctx);
                }
            });
        }
        UiKit.chipRow(ctx, tpl, 2, area);

        TextView rules = UiKit.caption(ctx, ChallengeEngine.TEMPLATES[ChallengeEngine.selected][1], true);
        area.addView(rules);

        TextView start = UiKit.chip(ctx,
                "🚩 شروع مسابقهٔ جدید — " + ChallengeEngine.TEMPLATES[ChallengeEngine.selected][0],
                UiKit.KIND_ACCENT, new Runnable() {
                    @Override public void run() {
                        promptName(ctx, 0);
                    }
                });
        area.addView(start, UiKit.wrapParams(start, 4, 44));

        area.addView(UiKit.caption(ctx,
                "راهنما: نام هر دو طرف را بده و تعداد راند را انتخاب کن. بعد از هر راند، "
                        + "دکمهٔ امتیاز را برای برندهٔ همان راند بزن (یا «تساوی»).", false));
    }

    private static void promptName(final Context ctx, final int slot) {
        if (hostRef == null) return;
        hostRef.promptText(slot == 0 ? "نام طرف اول" : "نام طرف دوم",
                "مثلاً " + (slot == 0 ? "آرش" : "سارا"),
                ChallengeEngine.names[slot],
                new PanelHost.OnTextResult() {
                    @Override public void onResult(String text) {
                        ChallengeEngine.names[slot] = text;
                        if (slot == 0) promptName(ctx, 1);
                        else promptRounds(ctx);
                    }
                });
    }

    private static void promptRounds(final Context ctx) {
        if (hostRef == null) return;
        hostRef.promptText("تعداد راندها (عدد فرد)", "مثلاً 3 یا 5", "3",
                new PanelHost.OnTextResult() {
                    @Override public void onResult(String text) {
                        int total = 3;
                        try {
                            total = Integer.parseInt(text.trim());
                        } catch (Exception ignored) {}
                        ChallengeEngine.start(ChallengeEngine.selected,
                                ChallengeEngine.names[0], ChallengeEngine.names[1], total);
                        if (hostRef != null) {
                            hostRef.flash("🚩 مسابقه شروع شد",
                                    ChallengeEngine.TEMPLATES[ChallengeEngine.selected][0]);
                            hostRef.vibrate();
                        }
                        refreshArea(ctx);
                    }
                });
    }

    // ---------------- مسابقه در جریان ----------------

    private static void buildLive(final Context ctx) {
        // عنوان + وضعیت راند
        LinearLayout bar = UiKit.hrow(ctx);
        TextView t = new TextView(ctx);
        t.setText(ChallengeEngine.TEMPLATES[ChallengeEngine.selected][0]);
        t.setTextSize(13f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Palette.GOLD);
        bar.addView(t);
        TextView rr = new TextView(ctx);
        int plan = ChallengeEngine.roundsTotal;
        String roundLabel;
        if (ChallengeEngine.nextIsGolden()) {
            roundLabel = "⚡ راند طلایی!";
        } else if (ChallengeEngine.roundIdx >= plan) {
            roundLabel = "راند پایانی";
        } else {
            roundLabel = "راند " + (ChallengeEngine.roundIdx + 1) + " از " + plan;
        }
        rr.setText(roundLabel);
        rr.setTextSize(11.5f);
        rr.setTypeface(Typeface.DEFAULT_BOLD);
        rr.setTextColor(ChallengeEngine.nextIsGolden() ? Palette.DANGER_TEXT : Palette.TEXT_DIM);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.setMarginStart(UiKit.dp(ctx, 8));
        bar.addView(rr, rlp);
        area.addView(bar, UiKit.wrapParams(bar, 4, 0));

        // کارت دو بازیکن + امتیاز
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        for (int p = 0; p < 2; p++) {
            final int side = p;
            final int colr = side == 0 ? COL_A : COL_B;
            LinearLayout card = new LinearLayout(ctx);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setBackground(UiKit.roundedSolid(
                    UiKit.alphaColor(colr, 0.16f), UiKit.dp(ctx, 14)));
            card.setPadding(0, UiKit.dp(ctx, 8), 0, UiKit.dp(ctx, 8));

            TextView nm = new TextView(ctx);
            nm.setText(ChallengeEngine.names[side]);
            nm.setTextSize(12.5f);
            nm.setTypeface(Typeface.DEFAULT_BOLD);
            nm.setTextColor(Palette.TEXT_MAIN);
            nm.setGravity(Gravity.CENTER);
            nm.setMaxLines(1);
            card.addView(nm);

            TextView sc = new TextView(ctx);
            sc.setText(String.valueOf(ChallengeEngine.pts[side]));
            sc.setTextSize(30f);
            sc.setTypeface(Typeface.DEFAULT_BOLD);
            sc.setTextColor(colr);
            sc.setGravity(Gravity.CENTER);
            card.addView(sc);

            TextView who = new TextView(ctx);
            who.setText(side == 0 ? "طرف اول" : "طرف دوم");
            who.setTextSize(10f);
            who.setTextColor(Palette.TEXT_DIM);
            who.setGravity(Gravity.CENTER);
            card.addView(who);

            TextView b = UiKit.chip(ctx, "🏅 رأی داور",
                    new Runnable() {
                        @Override public void run() {
                            judge(ctx, side);
                        }
                    });
            auto(ctx, b);
            card.addView(b, UiKit.wrapParams(b, 6, 40));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            int m2 = UiKit.dp(ctx, 3);
            if (side == 0) lp.setMarginEnd(m2); else lp.setMarginStart(m2);
            row.addView(card, lp);
        }
        area.addView(row);

        TextView tie = UiKit.chip(ctx, "🤝 تساوی این راند (بدون امتیاز)",
                new Runnable() {
                    @Override public void run() { judge(ctx, 2); }
                });
        auto(ctx, tie);
        area.addView(tie, UiKit.wrapParams(tie, 4, 40));

        // نقطه‌های راندها
        if (!ChallengeEngine.roundResults.isEmpty()) {
            LinearLayout dots = UiKit.hrow(ctx);
            dots.setGravity(Gravity.CENTER);
            for (int i = 0; i < ChallengeEngine.roundResults.size(); i++) {
                int res = ChallengeEngine.roundResults.get(i);
                View dot = UiKit.dot(ctx,
                        res == 2 ? 0xFF9AA5B1 : (res == 0 ? COL_A : COL_B), 12);
                LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                        UiKit.dp(ctx, 12), UiKit.dp(ctx, 12));
                dlp.setMargins(UiKit.dp(ctx, 3), 0, UiKit.dp(ctx, 3), 0);
                dots.addView(dot, dlp);
            }
            area.addView(dots, UiKit.wrapParams(dots, 4, 0));
        }

        // خلاصهٔ گزارش
        java.util.List<String> log = ChallengeEngine.logLines();
        int from = Math.max(0, log.size() - 3);
        for (int i = from; i < log.size(); i++) {
            TextView lv = UiKit.caption(ctx, log.get(i), i < log.size() - 1);
            area.addView(lv);
        }

        TextView stop = UiKit.chip(ctx, "🚫 لغو مسابقهٔ جاری", UiKit.KIND_DANGER, new Runnable() {
            @Override public void run() {
                if (hostRef != null) {
                    hostRef.confirm("لغو مسابقه", "گزارش و امتیازهای این مسابقه پاک می‌شود. ادامه بدهی؟",
                            new Runnable() {
                                @Override public void run() {
                                    ChallengeEngine.reset();
                                    refreshArea(ctx);
                                }
                            });
                }
            }
        });
        area.addView(stop, UiKit.wrapParams(stop, 4, 40));
    }

    /** ثبت رأی داور و بازسازی صفحه؛ در پایان، اعلام برنده + باز شدن بخش جایزه. */
    private static void judge(final Context ctx, int who) {
        if (hostRef == null) return;
        boolean wasGolden = ChallengeEngine.golden;
        String res = ChallengeEngine.award(who);
        hostRef.vibrate();
        if (ChallengeEngine.finished) {
            hostRef.flash("🏆 برنده مشخص شد!", ChallengeEngine.announceText());
        } else if (!wasGolden && ChallengeEngine.golden) {
            hostRef.flash("⚡ راند طلایی!", "امتیازها برابر شد؛ یک راند اضافه برای تعیین برنده.");
        } else if (ChallengeEngine.nextIsGolden()) {
            hostRef.flash("راند بعد", "⚡ راند طلایی — برنده هر طور شد، قهرمان مسابقه است!");
        }
        refreshArea(ctx);
    }

    // ---------------- پایان مسابقه ----------------

    private static void buildResult(final Context ctx) {
        if (ChallengeEngine.winner < 0 || hostRef == null) {
            return;
        }
        final int w = ChallengeEngine.winner;
        final int colr = w == 0 ? COL_A : COL_B;

        LinearLayout banner = new LinearLayout(ctx);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setGravity(Gravity.CENTER);
        banner.setBackground(UiKit.roundedSolid(UiKit.alphaColor(colr, 0.22f), UiKit.dp(ctx, 16)));
        banner.setPadding(UiKit.dp(ctx, 10), UiKit.dp(ctx, 12), UiKit.dp(ctx, 10), UiKit.dp(ctx, 12));
        banner.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        TextView tro = new TextView(ctx);
        tro.setText("🏆👑🏆");
        tro.setTextSize(26f);
        tro.setGravity(Gravity.CENTER);
        banner.addView(tro);

        TextView win = new TextView(ctx);
        win.setText(ChallengeEngine.names[w]);
        win.setTextSize(19f);
        win.setTypeface(Typeface.DEFAULT_BOLD);
        win.setTextColor(colr);
        win.setGravity(Gravity.CENTER);
        banner.addView(win);

        TextView det = new TextView(ctx);
        det.setText("قهرمان مسابقه با نتیجهٔ " + ChallengeEngine.pts[w]
                + " به " + ChallengeEngine.pts[1 - w]);
        det.setTextSize(12f);
        det.setTextColor(Palette.TEXT_MAIN);
        det.setGravity(Gravity.CENTER);
        banner.addView(det);
        area.addView(banner, UiKit.wrapParams(banner, 2, 0));

        // دکمه‌های اعلام
        TextView ann = UiKit.chip(ctx, "🔊 اعلام برنده در چت بازی (اتوماسیون)", UiKit.KIND_ACCENT,
                new Runnable() {
                    @Override public void run() {
                        String txt = ChallengeEngine.announceText();
                        if (txt.isEmpty() || hostRef == null) return;
                        if (hostRef.isAutomationOn() && hostRef.isChatCalibrated()) {
                            hostRef.autoChat(txt);
                        } else {
                            hostRef.copyText(txt);
                            hostRef.flash("اعلام برنده", "کپی شد؛ حالت اتوماسیون چت را روشن کن یا متن را در چت بگذار.");
                        }
                    }
                });
        area.addView(ann, UiKit.wrapParams(ann, 4, 42));

        TextView rcon = UiKit.chip(ctx, "🖥 اعلام در کنسول سرور (RCON: say)",
                new Runnable() {
                    @Override public void run() {
                        String txt = ChallengeEngine.announceText();
                        if (txt.isEmpty() || hostRef == null) return;
                        hostRef.sendConsole("say " + txt);
                    }
                });
        area.addView(rcon, UiKit.wrapParams(rcon, 4, 42));

        // جایزه برای برنده
        area.addView(UiKit.sectionLabel(ctx, "🎁 جایزهٔ قهرمان", false));
        final String winnerName = ChallengeEngine.names[w];
        for (final String[] pr : ChallengeEngine.PRIZES) {
            LinearLayout c2 = UiKit.vcol(ctx, 8);
            c2.setBackground(UiKit.roundedSolid(0x14FFD54F, UiKit.dp(ctx, 10)));
            TextView t1 = UiKit.sectionLabel(ctx, pr[0], false);
            c2.addView(t1);
            TextView t2 = UiKit.caption(ctx, pr[1] + "\nبرای " + winnerName, true);
            c2.addView(t2);
            TextView bc = UiKit.chip(ctx, "📋 کپی متن جایزه", new Runnable() {
                @Override public void run() {
                    copy(ctx, "🎁 جایزهٔ قهرمان «" + winnerName + "» — "
                            + pr[0] + "\n" + pr[1]
                            + "\nمتن فرمان اپراتور (بدراک/جاوا):");
                }
            });
            c2.addView(bc, UiKit.wrapParams(bc, 6, 36));
            area.addView(c2, UiKit.wrapParams(c2, 2, 0));
        }

        // مسابقهٔ تازه
        TextView again = UiKit.chip(ctx, "🔄 مسابقهٔ جدید با همین قالب", new Runnable() {
            @Override public void run() {
                ChallengeEngine.reset();
                refreshArea(ctx);
            }
        });
        area.addView(again, UiKit.wrapParams(again, 4, 42));

        TextView full = UiKit.chip(ctx, "📄 کپی گزارش کامل مسابقه", new Runnable() {
            @Override public void run() {
                copy(ctx, ChallengeEngine.transcript());
            }
        });
        area.addView(full, UiKit.wrapParams(full, 4, 42));
    }

    private static void auto(Context ctx, TextView tv) {
        if (Build.VERSION.SDK_INT >= 26) {
            tv.setAutoSizeTextTypeUniformWithConfiguration(
                    8, 12, 1, TypedValue.COMPLEX_UNIT_SP);
        }
    }

    private static void copy(Context ctx, String s) {
        ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("atta", s));
        UiKit.vibrate(ctx, 25);
    }
}
