package com.atta.mcpanel.overlay.tabs

import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.atta.mcpanel.R
import com.atta.mcpanel.core.PanelWidthMode
import com.atta.mcpanel.overlay.OverlayService
import com.atta.mcpanel.overlay.PanelHost
import com.atta.mcpanel.overlay.UiKit
import com.atta.mcpanel.rcon.RconConnection
import com.google.android.material.switchmaterial.SwitchMaterial

/** زبانهٔ «تنظیمات»: ظاهر پنل + پروفایل اتصال سرور. */
object SettingsTab {

    fun build(host: PanelHost): View {
        val ctx = host.context
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 6), UiKit.dp(ctx, 14), UiKit.dp(ctx, 24))
        }

        // ---- پروفایل سرور ------------------------------------------------
        col.addView(UiKit.sectionLabel(ctx, "سرور شخصی (RCON)"))
        col.addView(
            UiKit.caption(
                ctx,
                "آدرس، پورت و رمز یک‌جا ذخیره می‌شود؛ در زبانهٔ «سرور» هم می‌توانی همان‌ها را عوض کنی."
            )
        )
        val btnEdit = UiKit.chip(ctx, "✏️ ویرایش اطلاعات اتصال", UiKit.ChipKind.ACCENT) {
            host.showRconDialog("اطلاعات سرور شخصی", autoConnectAfterSave = false)
        }
        val btnTest = UiKit.chip(ctx, "🧪 تست اتصال") {
            val hostAddr = host.prefs.rconHost.trim()
            if (hostAddr.isEmpty()) {
                host.flash("آدرس سرور وارد نشده", "اول اطلاعات اتصال را کامل کن")
            } else {
                RconConnection.testConnection(
                    hostAddr, host.prefs.rconPort, host.prefs.rconPassword,
                ) { ok, msg ->
                    if (ok) host.vibrate()
                    host.flash(if (ok) "✓ $msg" else "✗ $msg")
                }
            }
        }
        col.addView(
            LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(btnEdit, LinearLayout.LayoutParams(0, UiKit.dp(ctx, 44), 1f))
                addView(
                    btnTest,
                    LinearLayout.LayoutParams(0, UiKit.dp(ctx, 44), 1f).apply {
                        setMargins(UiKit.dp(ctx, 8), 0, 0, 0)
                    }
                )
            }
        )
        val btnHelp = UiKit.chip(ctx, "❓ راهنمای روشن‌کردن RCON در سرور") {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                .setTitle("روشن‌کردن RCON روی سرور")
                .setMessage(
                    "مخصوص سرورهایی که خودت مدیریت می‌کنی (جاوا یا بدراک):\n\n" +
                        "۱) فایل server.properties را باز کن و این سه خط را بگذار:\n" +
                        "    enable-rcon=true\n" +
                        "    rcon.port=25575\n" +
                        "    rcon.password=یک_رمز_قوی_بگذار\n" +
                        "۲) سرور را دوباره (ری‌استارت) روشن کن.\n\n" +
                        "۳) در پنل: آدرس را بزن:\n" +
                        "   • سرور روی همین گوشی/کامپیوتر  ←  localhost\n" +
                        "   • سرور روی سیستم دیگر در خانه  ←  IP آن سیستم\n" +
                        "   • سرور ابری (مثل Aternos)  ←  IP و پورت همان سرور\n\n" +
                        "۴) برای اتصال از بیرونِ شبکه باید پورت روی مودم فوروارد شود (با احتیاط و فقط روی شبکهٔ امن).\n\n" +
                        "نکته: از طریق RCON دستورهای اپراتور مثل op/ban/kick اجرا می‌شود و به «اپ بودن» تو در بازی هم نیازی نیست."
                )
                .setPositiveButton("فهمیدم", null)
                .show()
        }
        col.addView(btnHelp, LinearLayout.LayoutParams(0, UiKit.dp(ctx, 44), 1f))

        // ---- ظاهر پنل ----------------------------------------------------
        col.addView(UiKit.sectionLabel(ctx, "ظاهر پنل"))

        val alphaRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val tvAlpha = TextView(ctx).apply {
            text = "شفافیت:"
            textSize = 12.5f
            setTextColor(ContextCompat.getColor(ctx, R.color.textSub))
        }
        alphaRow.addView(
            tvAlpha,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )
        val seek = SeekBar(ctx).apply {
            max = 100
            progress = (host.prefs.overlayAlpha * 100).toInt().coerceIn(0, 100)
        }
        seek.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        alphaRow.addView(seek)
        col.addView(alphaRow)
        col.addView(UiKit.caption(ctx, "کم‌تر = بازی بیشتر دیده می‌شود", dim = true))
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) host.setPanelAlpha(progress / 100f)
            }

            override fun onStartTrackingTouch(sb: SeekBar?) = Unit
            override fun onStopTrackingTouch(sb: SeekBar?) = Unit
        })

        col.addView(UiKit.caption(ctx, "عرض پنل:", dim = true))
        val modeChips = listOf(
            Triple("باریک", PanelWidthMode.NARROW),
            Triple("عادی", PanelWidthMode.NORMAL),
            Triple("تمام‌صفحه", PanelWidthMode.WIDE),
        ).map { (label, mode) ->
            UiKit.chip(ctx, label) { host.setPanelWidthMode(mode) }
        }
        UiKit.chipRow(ctx, modeChips, 3, col)

        val vibRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val tvVib = TextView(ctx).apply {
            text = "لرزش هنگام باز شدن و اجرای دستور"
            textSize = 13f
            setTextColor(ContextCompat.getColor(ctx, R.color.textMain))
        }
        vibRow.addView(tvVib, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val swVib = SwitchMaterial(ctx).apply { isChecked = host.prefs.vibrationOn }
        vibRow.addView(swVib)
        swVib.setOnCheckedChangeListener { _, checked ->
            host.prefs.vibrationOn = checked
            if (checked) host.vibrate()
        }
        col.addView(vibRow)

        // ---- درباره -------------------------------------------------------
        col.addView(UiKit.sectionLabel(ctx, "درباره", accent = false))
        col.addView(
            UiKit.caption(
                ctx,
                "Atta · پنل کمکی اپراتور ماینکرفت.\n" +
                    "• دستورهای چت‌پذیر بدون اتصال، در کلیپ‌بورد کپی می‌شوند تا در چت بازی بچسبانی.\n" +
                    "• دستورهای سرور (op/ban/kick/…) نیاز به اتصال RCON در زبانهٔ «سرور» دارد.\n" +
                    "• این ابزار به موجانگ وابسته نیست؛ روی هر نسخهٔ ماینکرفتی که خودت اپراتورش هستی کار می‌کند."
            )
        )

        val btnQuit = UiKit.chip(ctx, "⏹ توقف پنل", UiKit.ChipKind.DANGER) {
            OverlayService.stop(ctx)
        }
        col.addView(
            btnQuit,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(ctx, 46)
            ).apply {
                val m = UiKit.dp(ctx, 4)
                setMargins(m, UiKit.dp(ctx, 14), m, 0)
            }
        )

        return ScrollView(ctx).apply {
            isFillViewport = true
            addView(col)
        }
    }
}
