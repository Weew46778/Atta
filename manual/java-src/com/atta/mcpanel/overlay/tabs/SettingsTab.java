package com.atta.mcpanel.overlay.tabs;

import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.atta.mcpanel.core.AppPrefs;
import com.atta.mcpanel.core.Palette;
import com.atta.mcpanel.overlay.OverlayService;
import com.atta.mcpanel.overlay.PanelHost;
import com.atta.mcpanel.overlay.UiKit;
import com.atta.mcpanel.rcon.RconConnection;

/** زبانهٔ «تنظیمات» — ظاهر پنل و پروفایل سرور */
public final class SettingsTab {

    private SettingsTab() {}

    public static View build(final PanelHost host) {
        final Context ctx = host.getContext();
        final LinearLayout col = UiKit.vcol(ctx, 14);

        // ---- بازیکن هدف (برای سرور/RCON)
        col.addView(UiKit.sectionLabel(ctx, "بازیکن هدف (برای سرور/RCON)"));
        col.addView(UiKit.caption(ctx,
                "دستورهایی که روی «{p}» می‌نویسند به این نام اعمال می‌شوند. "
                + "وقتی به سرور وصل هستی، نام دقیق بازیکنت را این‌جا بگذار "
                + "(در تک‌نفرهٔ بدون سرور نیازی نیست).", false));
        final LinearLayout rowP = UiKit.hrow(ctx);
        final EditText etP = host.newInput("نام بازیکن (گیم‌تگ بدراک)", android.text.InputType.TYPE_CLASS_TEXT,
                host.getPrefs().getPlayerName().equals("@s")
                        ? "" : host.getPrefs().getPlayerName());
        rowP.addView(etP, new LinearLayout.LayoutParams(
                0, UiKit.dp(ctx, 46), 1f));
        final TextView btnSaveP = UiKit.chip(ctx, "ذخیره", UiKit.KIND_ACCENT, new Runnable() {
            @Override public void run() {
                String v = etP.getText().toString().trim();
                host.getPrefs().setPlayerName(v.isEmpty() ? "@s" : v);
                host.flash("✓ نام بازیکن ذخیره شد",
                        v.isEmpty() ? "@s (خودت)" : v);
            }
        });
        rowP.addView(btnSaveP, new LinearLayout.LayoutParams(
                UiKit.dp(ctx, 96), UiKit.dp(ctx, 46)));
        col.addView(rowP, UiKit.wrapParams(rowP, 4, 46));

        // ---- پروفایل سرور
        col.addView(UiKit.sectionLabel(ctx, "سرور شخصی (RCON)"));
        col.addView(UiKit.caption(ctx,
                "آدرس، پورت و رمز یک‌جا ذخیره می‌شود؛ در زبانهٔ «سرور» هم قابل ویرایش است", false));

        final LinearLayout row1 = UiKit.hrow(ctx);
        final TextView btnEdit = UiKit.chip(ctx, "✏️ ویرایش اطلاعات اتصال", UiKit.KIND_ACCENT,
                new Runnable() {
                    @Override public void run() {
                        host.showRconDialog("اطلاعات سرور شخصی", false);
                    }
                });
        row1.addView(btnEdit, new LinearLayout.LayoutParams(
                0, UiKit.dp(ctx, 46), 1f));
        final TextView btnTest = UiKit.chip(ctx, "🧪 تست اتصال", new Runnable() {
            @Override public void run() {
                String addr = host.getPrefs().getRconHost().trim();
                if (addr.isEmpty()) {
                    host.flash("آدرس سرور وارد نشده", "اول اطلاعات اتصال را کامل کن");
                } else {
                    RconConnection.testConnection(addr,
                            host.getPrefs().getRconPort(),
                            host.getPrefs().getRconPassword(),
                            new RconConnection.OnResult() {
                                @Override public void onResult(boolean ok, String message) {
                                    if (ok) host.vibrate();
                                    host.flash(ok ? "✓ " + message : "✗ " + message);
                                }
                            });
                }
            }
        });
        row1.addView(btnTest, new LinearLayout.LayoutParams(
                0, UiKit.dp(ctx, 46), 1f));
        col.addView(row1, UiKit.wrapParams(row1, 4, 46));

        final TextView btnHelp = UiKit.chip(ctx, "❓ راهنمای روشن‌کردن RCON در سرور",
                new Runnable() {
                    @Override public void run() {
                        new AlertDialog.Builder(ctx)
                                .setTitle("روشن‌کردن RCON روی سرور خودت")
                                .setMessage(
                                        "مخصوص سرورهایی که خودت مدیریت می‌کنی (جاوا یا بدراک):\n\n"
                                        + "۱) فایل server.properties را باز کن:\n"
                                        + "    enable-rcon=true\n"
                                        + "    rcon.port=25575\n"
                                        + "    rcon.password=یک_رمز_قوی\n"
                                        + "۲) سرور را ری‌استارت کن.\n\n"
                                        + "۳) آدرس را بزن:\n"
                                        + "   سرور روی همین دستگاه ← localhost\n"
                                        + "   سیستم دیگر در خانه ← IP آن سیستم\n"
                                        + "   سرور ابری (مثل Aternos) ← IP و پورت همان سرور\n\n"
                                        + "۴) برای اتصال از بیرون شبکه، پورت باید روی مودم "
                                        + "فوروارد شود (فقط روی شبکهٔ امن).\n\n"
                                        + "نکته: از طریق RCON دستورهای اپراتور مثل op/ban/kick "
                                        + "اجرا می‌شود و به اپ بودن تو در بازی نیاز نیست.")
                                .setPositiveButton("فهمیدم", null)
                                .show();
                    }
                });
        col.addView(btnHelp, UiKit.wrapParams(btnHelp, 4, 46));

        // ---- اتوماسیون تک‌نفره (اجرای یک‌کلیکی در خود بازی)
        col.addView(UiKit.sectionLabel(ctx, "اجرای خودکار در تک‌نفره (یک‌کلیک)"));
        col.addView(UiKit.caption(ctx,
                "وقتی این حالت روشن باشد، با هر کلیک روی دکمه‌ها، چت بازی خودکار باز می‌شود، "
                + "دستور تایپ و Enter زده می‌شود؛ دیگر خبری از کپی/پیست نیست. "
                + "سه قدم یک‌بار انجام می‌شود:", false));

        final TextView statusA = new TextView(ctx);
        statusA.setTextSize(11.5f);
        statusA.setPadding(UiKit.dp(ctx, 4), UiKit.dp(ctx, 4), UiKit.dp(ctx, 4), UiKit.dp(ctx, 2));
        col.addView(statusA, UiKit.wrapParams(statusA, 4, 0));

        LinearLayout rowA = UiKit.hrow(ctx);
        final TextView btnA11y = UiKit.chip(ctx, "۱) فعال‌سازی دسترسی‌پذیری", UiKit.KIND_ACCENT,
                new Runnable() {
                    @Override public void run() { host.openA11ySettings(); }
                });
        rowA.addView(btnA11y, new LinearLayout.LayoutParams(
                0, UiKit.dp(ctx, 44), 1f));
        final TextView btnCal = UiKit.chip(ctx, "۲) کالیبرهٔ دکمهٔ چت", new Runnable() {
            @Override public void run() {
                host.flash("نشانگر را روی آیکون چت بازی ببر", "بعد «ذخیره» را بزن");
                host.showChatCalibration();
            }
        });
        rowA.addView(btnCal, new LinearLayout.LayoutParams(
                0, UiKit.dp(ctx, 44), 1f));
        col.addView(rowA, UiKit.wrapParams(rowA, 4, 44));

        LinearLayout rowB = UiKit.hrow(ctx);
        final TextView btnIme = UiKit.chip(ctx, "۳) فعال‌سازی کیبورد Atta", new Runnable() {
            @Override public void run() { host.openImeSettings(); }
        });
        rowB.addView(btnIme, new LinearLayout.LayoutParams(
                0, UiKit.dp(ctx, 44), 1f));
        final TextView btnPick = UiKit.chip(ctx, "🎹 انتخاب‌گر کیبورد", new Runnable() {
            @Override public void run() { host.openImePicker(); }
        });
        rowB.addView(btnPick, new LinearLayout.LayoutParams(
                0, UiKit.dp(ctx, 44), 1f));
        col.addView(rowB, UiKit.wrapParams(rowB, 4, 44));

        LinearLayout rowC = UiKit.hrow(ctx);
        final TextView btnTestA = UiKit.chip(ctx, "🧪 تست خودکار", UiKit.KIND_ACCENT, new Runnable() {
            @Override public void run() {
                host.autoChat("/say تست پنل Atta از بازی 👋");
            }
        });
        rowC.addView(btnTestA, new LinearLayout.LayoutParams(
                0, UiKit.dp(ctx, 44), 1f));
        final TextView btnManual = UiKit.chip(ctx, "✋ تست با لمس خودم", new Runnable() {
            @Override public void run() {
                host.autoChat("/say تست دستی Atta 👋");
            }
        });
        rowC.addView(btnManual, new LinearLayout.LayoutParams(
                0, UiKit.dp(ctx, 44), 1f));
        col.addView(rowC, UiKit.wrapParams(rowC, 4, 44));

        col.addView(UiKit.caption(ctx,
                "قدم ۱: دسترسی‌پذیری ← «اجرای خودکار Atta» روشن.  "
                + "قدم ۲: دکمهٔ کالیبره را بزن و نشانگر را روی آیکون چت بازی ببر (یک‌بار).  "
                + "قدم ۳: از «زبان و ورودی» کیبورد Atta را فعال کن و اگر کیبورد دیگری هم داری، "
                + "کیبورد Atta را پیش‌فرض کن (یا در بازی از «🎹 انتخاب‌گر» او را انتخاب کن).\n"
                + "🔁 اگر لمس خودکار گاهی جواب ندهد، کافی است خودت یک بار آیکون چت را بزنی — "
                + "دستور در صف می‌ماند و خودکار ارسال می‌شود.\n"
                + "⚠️ دنیای تک‌نفرهٔ بدراک باید «Allow Cheats» روشن داشته باشد تا دستورها اجرا شوند.",
                true));

        final Runnable refreshStatus = new Runnable() {
            @Override public void run() {
                String a = host.isAutomationOn()
                        ? "✅ دسترسی‌پذیری: روشن"
                        : "❌ دسترسی‌پذیری: خاموش (قدم ۱)";
                String b = host.isChatCalibrated()
                        ? "  • موقعیت چت: کالیبره شده ✓"
                        : "  • موقعیت چت: نشده (قدم ۲)";
                statusA.setText(a + "\n" + b);
                statusA.setTextColor(host.isAutomationOn()
                        ? Palette.DOT_OK : Palette.TEXT_SUB);
            }
        };
        refreshStatus.run();
        final TextView btnRefresh = UiKit.chip(ctx, "🔄 بررسی وضعیت", new Runnable() {
            @Override public void run() { refreshStatus.run(); }
        });
        col.addView(btnRefresh, UiKit.wrapParams(btnRefresh, 4, 40));

        // ---- ظاهر
        col.addView(UiKit.sectionLabel(ctx, "ظاهر پنل"));

        final LinearLayout alphaRow = UiKit.hrow(ctx);
        final TextView tvAlpha = new TextView(ctx);
        tvAlpha.setText("شفافیت صفحه‌ها:");
        tvAlpha.setTextSize(13f);
        tvAlpha.setTextColor(Palette.TEXT_SUB);
        alphaRow.addView(tvAlpha);
        final SeekBar seek = new SeekBar(ctx);
        seek.setMax(100);
        seek.setProgress(Math.round(host.getPrefs().getOverlayAlpha() * 100));
        alphaRow.addView(seek, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        col.addView(alphaRow, UiKit.wrapParams(alphaRow, 4, 50));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) host.setPanelAlpha(progress / 100f);
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        final LinearLayout vibRow = UiKit.hrow(ctx);
        final TextView tvVib = new TextView(ctx);
        tvVib.setText("لرزش هنگام باز شدن و اجرای دستور");
        tvVib.setTextSize(13f);
        tvVib.setTextColor(Palette.TEXT_MAIN);
        vibRow.addView(tvVib, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        final Switch swVib = new Switch(ctx);
        swVib.setChecked(host.getPrefs().getVibrationOn());
        vibRow.addView(swVib);
        col.addView(vibRow, UiKit.wrapParams(vibRow, 4, 50));
        swVib.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                host.getPrefs().setVibrationOn(checked);
                if (checked) host.vibrate();
            }
        });

        // ---- درباره و راهنما
        col.addView(UiKit.sectionLabel(ctx, "راهنما", false));
        col.addView(UiKit.caption(ctx,
                "• دکمهٔ سبز کنار صفحه → باز شدن زبانه‌ها (یکی‌یکی با انیمیشن).\n"
                + "• لمس هر زبانه → باز شدن صفحهٔ همان بخش در کنارش.\n"
                + "• ✕ داخل صفحه → فقط صفحه بسته می‌شود. ✕ زیر زبانه‌ها → بستن همه.\n"
                + "• ۳۰ ثانیه بی‌کاری → صفحه بسته، ۳۰ ثانیهٔ بعد زبانه‌ها جمع می‌شوند.\n"
                + "• بقیهٔ صفحه هنگام باز بودن پنل کاملاً آزاد و قابل لمس است.",
                true));

        col.addView(UiKit.sectionLabel(ctx, "درباره", false));
        col.addView(UiKit.caption(ctx,
                "Atta · پنل کمکی اپراتور ماینکرفت.\n"
                + "• تک‌نفرهٔ بدراک: با اتوماسیون (دسترسی‌پذیری + کیبورد Atta) دستورها با یک کلیک در چت بازی اجرا می‌شوند.\n"
                + "• سرور شخصی: با RCON دستورها مستقیم روی سرور اجرا می‌شوند (op/ban/kick/…).\n"
                + "• این ابزار به موجانگ وابسته نیست؛ برای دنیا/سروری که خودت اپراتورش هستی است.",
                true));

        final TextView btnQuit = UiKit.chip(ctx, "⏹ توقف پنل", UiKit.KIND_DANGER,
                new Runnable() {
                    @Override public void run() { OverlayService.stop(ctx); }
                });
        LinearLayout.LayoutParams quitLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(ctx, 48));
        quitLp.setMargins(UiKit.dp(ctx, 4), UiKit.dp(ctx, 14), UiKit.dp(ctx, 4), 0);
        col.addView(btnQuit, quitLp);

        ScrollView sv = new ScrollView(ctx);
        sv.setFillViewport(true);
        sv.setOverScrollMode(android.view.View.OVER_SCROLL_NEVER);
        sv.addView(col);
        return sv;
    }
}
