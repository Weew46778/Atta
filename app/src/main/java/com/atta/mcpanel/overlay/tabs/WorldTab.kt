package com.atta.mcpanel.overlay.tabs

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.atta.mcpanel.R
import com.atta.mcpanel.overlay.PanelHost
import com.atta.mcpanel.overlay.UiKit

/**
 * زبانهٔ «دنیا»: مدیریت دنیا، گیمرول‌ها، سختی و مدیریت بازیکن‌ها
 * برای اپراتور سرور شخصی (از طریق RCON یا کپی در چت).
 */
object WorldTab {

    fun build(host: PanelHost): View {
        val ctx = host.context
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 6), UiKit.dp(ctx, 14), UiKit.dp(ctx, 24))
        }

        // ---- نوار وضعیت جهان ---------------------------------------------
        val statusRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(UiKit.dp(ctx, 4), UiKit.dp(ctx, 2), UiKit.dp(ctx, 4), UiKit.dp(ctx, 4))
        }
        val dot = UiKit.dot(ctx, ContextCompat.getColor(ctx, R.color.dotOff))
        statusRow.addView(
            dot,
            LinearLayout.LayoutParams(UiKit.dp(ctx, 10), UiKit.dp(ctx, 10)).apply {
                setMargins(0, 0, UiKit.dp(ctx, 8), 0)
            }
        )
        val tvWorld = TextView(ctx).apply {
            textSize = 12.5f
            setTextColor(ContextCompat.getColor(ctx, R.color.textSub))
        }
        statusRow.addView(tvWorld, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val btnConn = UiKit.chip(ctx, "اتصال", UiKit.ChipKind.ACCENT) { host.rconConnect() }
        statusRow.addView(
            btnConn,
            LinearLayout.LayoutParams(UiKit.dp(ctx, 96), UiKit.dp(ctx, 40))
        )
        col.addView(statusRow)
        col.addView(UiKit.caption(ctx, "برای دستورهای مدیریتیِ سرور، اتصال RCON لازم است.", dim = true))

        // ---- سختی ----------------------------------------------------------
        col.addView(UiKit.sectionLabel(ctx, "سختی دنیا"))
        UiKit.chipRow(
            ctx,
            listOf(
                UiKit.chip(ctx, "🕊 صلح‌آمیز") { host.quickCommand("/difficulty peaceful", true) },
                UiKit.chip(ctx, "😊 آسان") { host.quickCommand("/difficulty easy", true) },
                UiKit.chip(ctx, "🙂 عادی") { host.quickCommand("/difficulty normal", true) },
                UiKit.chip(ctx, "😈 سخت") { host.quickCommand("/difficulty hard", true) },
            ),
            4, col,
        )

        // ---- گیمرول‌ها ------------------------------------------------------
        col.addView(UiKit.sectionLabel(ctx, "قانون‌های دنیا (Gamerule)"))
        col.addView(
            UiKit.caption(ctx, "لمس کوتاه = روشن  ·  لمس بلند = خاموش", dim = true)
        )
        val rules = listOf(
            "حفظ آیتم‌ها پس از مرگ" to "keepInventory",
            "چرخهٔ شبانه‌روز" to "doDaylightCycle",
            "تغییر آب‌وهوا" to "doWeatherCycle",
            "گسترش آتش" to "doFireTick",
            "تخریب توسط موب‌ها" to "mobGriefing",
            "زاییده‌شدن موب‌ها" to "doMobSpawning",
            "افتادن بلوک‌ها" to "doTileDrops",
            "افتادن آیتم هنگام مرگ موب" to "doMobLoot",
        )
        val ruleChips = rules.map { (label, rule) ->
            UiKit.chipToggle(
                ctx, label,
                hint = rule,
                onClick = { host.quickCommand("/gamerule $rule true", true) },
                onLongClick = { host.quickCommand("/gamerule $rule false", true) },
            )
        }
        UiKit.chipRow(ctx, ruleChips, 2, col)

        // ---- دنیا -----------------------------------------------------------
        col.addView(UiKit.sectionLabel(ctx, "دنیا", accent = false))
        UiKit.chipRow(
            ctx,
            listOf(
                UiKit.chip(ctx, "🌱 بذر (Seed)") { host.quickCommand("/seed", true) },
                UiKit.chip(ctx, "💾 ذخیرهٔ فوری") { host.sendConsole("save-all") },
                UiKit.chip(ctx, "🌦 تغییر هوا") { host.quickCommand("/toggledownfall", true) },
                UiKit.chip(ctx, "🗑 پاک‌کردن آیتم‌ها") { host.quickCommand("/kill @e[type=item]", true) },
            ),
            2, col,
        )

        // ---- مدیریت بازیکن (سرور شخصی) -------------------------------------
        col.addView(UiKit.sectionLabel(ctx, "مدیریت بازیکن‌ها — اپراتور سرور"))
        col.addView(
            UiKit.caption(
                ctx,
                "نام دقیق بازیکن را بده؛ این دستورها فقط روی سرور شخصیِ خودت (از طریق RCON) کار می‌کند.",
                dim = true,
            )
        )
        val suggested = host.prefs.playerName.takeIf { it.isNotBlank() && it != "@s" }

        val manageChips = listOf(
            Triple("👑 اپ کردن", "op") { name: String -> host.quickCommand("/op $name", false) },
            Triple("🚫 اپ برداشتن", "deop") { name: String -> host.quickCommand("/deop $name", false) },
            Triple("🥾 بیرون کردن", "kick") { name: String -> host.quickCommand("/kick $name", false) },
            Triple("🔨 بن کردن", "ban") { name: String -> host.quickCommand("/ban $name", false) },
            Triple("🕊 بخشیدن (پاردون)", "pardon") { name: String -> host.quickCommand("/pardon $name", false) },
            Triple("📋 وایت‌لیست", "whitelist") { name: String -> host.quickCommand("/whitelist add $name", false) },
        )
        val manageViews = manageChips.map { (label, tag, act) ->
            UiKit.chip(ctx, label, hint = tag) {
                host.promptText("نام بازیکن", "نام دقیق داخل بازی (مثل Steve)", suggested ?: "", act)
            }
        }
        UiKit.chipRow(ctx, manageViews, 2, col)

        // ---- به‌روزرسانی نوار وضعیت ----------------------------------------
        fun updateUi() {
            val connected = host.isRconConnected()
            val attempting = host.rconAttempting
            val color = ContextCompat.getColor(
                ctx,
                when {
                    connected -> R.color.dotOk
                    attempting -> R.color.dotBusy
                    else -> R.color.dotBad
                }
            )
            (dot.background.mutate() as android.graphics.drawable.GradientDrawable)
                .setColor(color)
            tvWorld.text = when {
                connected -> "به کنسول سرور وصل است — همهٔ دستورها اجرا می‌شود"
                attempting -> "در حال اتصال به کنسول سرور…"
                else -> "کنسول آفلاین است — دستورهای چت‌پذیر کپی می‌شوند"
            }
            btnConn.setTextColor(
                ContextCompat.getColor(ctx, if (connected) R.color.dotBad else R.color.dotOk)
            )
            btnConn.text = if (connected) "قطع" else "اتصال"
        }

        btnConn.setOnClickListener {
            if (host.isRconConnected()) host.rconDisconnect() else host.rconConnect()
        }
        host.registerStateListener { updateUi() }
        updateUi()
        return ScrollView(ctx).apply {
            isFillViewport = true
            addView(col)
        }
    }
}
