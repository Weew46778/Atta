package com.atta.mcpanel.overlay.tabs

import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import com.atta.mcpanel.overlay.PanelHost
import com.atta.mcpanel.overlay.UiKit

/**
 * زبانهٔ «ابزارها»: ابزارها و لوازم سریع برای اپراتور در حین بازی.
 * همهٔ دستورها با {p} به بازیکن هدف می‌روند؛ بدون اتصال RCON در کلیپ‌بورد کپی
 * می‌شوند تا در چت بازی Paste شوند (مخصوص دنیا/سرور خود اپراتور).
 */
object ControlsTab {

    fun build(host: PanelHost): android.view.View {
        val ctx = host.context
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 6), UiKit.dp(ctx, 14), UiKit.dp(ctx, 24))
        }

        // ---- بازیکن هدف -------------------------------------------------
        col.addView(UiKit.sectionLabel(ctx, "بازیکن هدف · {p}"))
        val targetEt = host.newInput(
            "نام بازیکن (در سرور خودت) یا @s",
            InputType.TYPE_CLASS_TEXT,
            host.prefs.playerName,
        )
        col.addView(
            targetEt,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(ctx, 46)
            ).apply {
                val m = UiKit.dp(ctx, 4)
                setMargins(m, 0, m, 0)
            }
        )
        col.addView(
            UiKit.caption(
                ctx,
                "دستورها روی این بازیکن اعمال می‌شود؛ اگر وصل به سرور باشی مستقیم اجرا می‌شود، وگرنه در چت بازی Paste می‌کنی."
            )
        )

        fun saveTarget() {
            host.prefs.playerName = targetEt.text.toString()
        }

        fun fire(raw: String, chatOk: Boolean) {
            saveTarget()
            host.quickCommand(host.resolve(raw), chatOk)
        }

        // ---- گیم‌مود -----------------------------------------------------
        col.addView(UiKit.sectionLabel(ctx, "حالت بازی (گیم‌مود)"))
        UiKit.chipRow(
            ctx,
            listOf(
                UiKit.chip(ctx, "خلاق", UiKit.ChipKind.ACCENT) { fire("/gamemode creative {p}", true) },
                UiKit.chip(ctx, "بقا") { fire("/gamemode survival {p}", true) },
                UiKit.chip(ctx, "ماجراجویی") { fire("/gamemode adventure {p}", true) },
                UiKit.chip(ctx, "ناظر") { fire("/gamemode spectator {p}", true) },
            ),
            4, col,
        )

        // ---- هوا ---------------------------------------------------------
        col.addView(UiKit.sectionLabel(ctx, "آب‌وهوا"))
        UiKit.chipRow(
            ctx,
            listOf(
                UiKit.chip(ctx, "☀️ صاف") { fire("/weather clear", true) },
                UiKit.chip(ctx, "🌧️ باران") { fire("/weather rain", true) },
                UiKit.chip(ctx, "⛈️ رعدوبرق") { fire("/weather thunder", true) },
            ),
            3, col,
        )

        // ---- زمان --------------------------------------------------------
        col.addView(UiKit.sectionLabel(ctx, "زمان روز"))
        UiKit.chipRow(
            ctx,
            listOf(
                UiKit.chip(ctx, "🌅 سپیده‌دم") { fire("/time set 0", true) },
                UiKit.chip(ctx, "☀️ ظهر") { fire("/time set 6000", true) },
                UiKit.chip(ctx, "🌇 غروب") { fire("/time set 13000", true) },
                UiKit.chip(ctx, "🌙 نیمه‌شب") { fire("/time set 18000", true) },
            ),
            4, col,
        )

        // ---- آیتم‌ها ------------------------------------------------------
        col.addView(UiKit.sectionLabel(ctx, "لوازم و آیتم‌ها (به بازیکن هدف)"))
        UiKit.chipRow(
            ctx,
            listOf(
                UiKit.chip(ctx, "الماس ×64") { fire("/give {p} diamond 64", true) },
                UiKit.chip(ctx, "ندرایت ×16") { fire("/give {p} netherite_ingot 16", true) },
                UiKit.chip(ctx, "زمرد ×32") { fire("/give {p} emerald_block 32", true) },
                UiKit.chip(ctx, "شمش طلا ×32") { fire("/give {p} gold_ingot 32", true) },
                UiKit.chip(ctx, "سیب طلایی ×16") { fire("/give {p} golden_apple 16", true) },
                UiKit.chip(ctx, "توتم ×8") { fire("/give {p} totem_of_undying 8", true) },
                UiKit.chip(ctx, "ایلیترا") { fire("/give {p} elytra 1", true) },
                UiKit.chip(ctx, "بیکن ×2") { fire("/give {p} beacon 2", true) },
            ),
            4, col,
        )

        // ---- افکت‌ها ------------------------------------------------------
        col.addView(UiKit.sectionLabel(ctx, "افکت‌ها (۶۰ ثانیه)"))
        UiKit.chipRow(
            ctx,
            listOf(
                UiKit.chip(ctx, "سرعت III") { fire("/effect {p} speed 60 2", true) },
                UiKit.chip(ctx, "شب‌بینی") { fire("/effect {p} night_vision 60 0", true) },
                UiKit.chip(ctx, "بازیابی II") { fire("/effect {p} regeneration 60 1", true) },
                UiKit.chip(ctx, "قدرت II") { fire("/effect {p} strength 60 1", true) },
                UiKit.chip(ctx, "مقاومت III") { fire("/effect {p} resistance 60 2", true) },
                UiKit.chip(ctx, "پرش III") { fire("/effect {p} jump_boost 60 2", true) },
                UiKit.chip(ctx, "تنفس زیر آب") { fire("/effect {p} water_breathing 60 0", true) },
                UiKit.chip(ctx, "نامرئی") { fire("/effect {p} invisibility 60 0", true) },
                UiKit.chip(ctx, "پاک‌کردن همه", UiKit.ChipKind.DANGER) {
                    fire("/effect {p} clear", true)
                },
            ),
            3, col,
        )

        // ---- دستور دلخواه ------------------------------------------------
        col.addView(UiKit.sectionLabel(ctx, "دستور دلخواه", accent = false))
        val sendRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val customEt: EditText = host.newInput("مثلاً /time set night", InputType.TYPE_CLASS_TEXT)
        sendRow.addView(
            customEt,
            LinearLayout.LayoutParams(0, UiKit.dp(ctx, 46), 1f).apply {
                val m = UiKit.dp(ctx, 4)
                setMargins(m, 0, m, 0)
            }
        )
        sendRow.addView(
            UiKit.chip(ctx, "ارسال", UiKit.ChipKind.ACCENT) {
                fire(customEt.text.toString(), true)
            },
            LinearLayout.LayoutParams(UiKit.dp(ctx, 78), UiKit.dp(ctx, 46)).apply {
                val m = UiKit.dp(ctx, 4)
                setMargins(0, 0, m, 0)
            }
        )
        col.addView(sendRow)

        return ScrollView(ctx).apply {
            isFillViewport = true
            addView(col)
        }
    }
}
