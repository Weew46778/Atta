package com.atta.mcpanel.overlay.tabs

import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.atta.mcpanel.R
import com.atta.mcpanel.overlay.PanelHost
import com.atta.mcpanel.overlay.UiKit
import com.google.android.material.button.MaterialButton

/**
 * زبانهٔ «سرور»: اتصال RCON به سرورهای شخصی (هم روی همین دستگاه، هم شبکهٔ داخلی)
 * و کنسول کامل دستورهای اپراتور.
 */
object ServerTab {

    private const val LOG_LIMIT = 120000

    fun build(host: PanelHost): View {
        val ctx = host.context
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(ctx, 14), UiKit.dp(ctx, 6), UiKit.dp(ctx, 14), UiKit.dp(ctx, 8))
        }

        // ---- اتصال --------------------------------------------------------
        col.addView(UiKit.sectionLabel(ctx, "اتصال RCON به سرور شخصی"))
        col.addView(
            UiKit.caption(
                ctx,
                "آدرس، پورت و رمز را بگذار؛ اگر سرور روی همین گوشی/کامپیوتر است: localhost و اگر روی سیستم دیگر است: IP همان سیستم."
            )
        )

        val hostEt = host.newInput("آدرس سرور", InputType.TYPE_CLASS_TEXT, host.prefs.rconHost)
        val portEt = host.newInput("پورت", InputType.TYPE_CLASS_NUMBER, host.prefs.rconPort.toString())
        val passEt = host.newInput(
            "رمز RCON",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD,
            host.prefs.rconPassword,
        )

        val topRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        topRow.addView(
            hostEt,
            LinearLayout.LayoutParams(0, UiKit.dp(ctx, 46), 1f).apply {
                setMargins(UiKit.dp(ctx, 4), UiKit.dp(ctx, 4), UiKit.dp(ctx, 2), 0)
            }
        )
        topRow.addView(
            portEt,
            LinearLayout.LayoutParams(UiKit.dp(ctx, 96), UiKit.dp(ctx, 46)).apply {
                setMargins(UiKit.dp(ctx, 2), UiKit.dp(ctx, 4), UiKit.dp(ctx, 4), 0)
            }
        )
        col.addView(topRow)
        col.addView(
            passEt,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(ctx, 46)
            ).apply {
                setMargins(UiKit.dp(ctx, 4), UiKit.dp(ctx, 4), UiKit.dp(ctx, 4), 0)
            }
        )

        fun saveFields() {
            host.prefs.rconHost = hostEt.text.toString()
            host.prefs.rconPort = portEt.text.toString().toIntOrNull() ?: host.prefs.rconPort
            host.prefs.rconPassword = passEt.text.toString()
        }

        // نوار وضعیت + دکمهٔ اتصال
        val statusRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(UiKit.dp(ctx, 4), UiKit.dp(ctx, 10), UiKit.dp(ctx, 4), UiKit.dp(ctx, 2))
        }
        val dot = UiKit.dot(ctx, ContextCompat.getColor(ctx, R.color.dotOff))
        statusRow.addView(
            dot,
            LinearLayout.LayoutParams(UiKit.dp(ctx, 10), UiKit.dp(ctx, 10)).apply {
                setMargins(0, 0, UiKit.dp(ctx, 8), 0)
            }
        )
        val tvState = TextView(ctx).apply {
            textSize = 13f
            setTextColor(ContextCompat.getColor(ctx, R.color.textSub))
        }
        statusRow.addView(tvState, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val btnConnect = MaterialButton(ctx).apply {
            text = "اتصال"
            isAllCaps = false
            textSize = 13f
            minHeight = 0
            insetTop = 0
            insetBottom = 0
        }
        btnConnect.layoutParams = LinearLayout.LayoutParams(UiKit.dp(ctx, 118), UiKit.dp(ctx, 42))
        statusRow.addView(btnConnect)
        col.addView(statusRow)

        // ---- کنسول --------------------------------------------------------
        col.addView(UiKit.sectionLabel(ctx, "کنسول سرور", accent = true))

        val logScroll = ScrollView(ctx).apply {
            setBackgroundResource(R.drawable.bg_log)
            isVerticalScrollBarEnabled = false
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val logTv = TextView(ctx).apply {
            textSize = 12f
            setTextColor(ContextCompat.getColor(ctx, R.color.textMain))
            setLineSpacing(0f, 1.15f)
            setPadding(UiKit.dp(ctx, 10), UiKit.dp(ctx, 8), UiKit.dp(ctx, 10), UiKit.dp(ctx, 8))
        }
        logScroll.addView(
            logTv,
            ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT
            )
        )
        col.addView(
            logScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ).apply {
                setMargins(UiKit.dp(ctx, 4), 0, UiKit.dp(ctx, 4), 0)
            }
        )

        fun append(line: String) {
            if (logTv.text.isNotEmpty()) logTv.append("\n")
            logTv.append(line)
            if (logTv.text.length > LOG_LIMIT) {
                logTv.text = logTv.text.substring(logTv.text.length - LOG_LIMIT)
            }
            logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
        }

        host.registerConsoleListener { line -> append(line) }

        // ---- ارسال دستور --------------------------------------------------
        val sendRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
        val inputEt: EditText = host.newInput(
            "دستور اپراتور — بدون / بنویس، مثلاً op Steve", InputType.TYPE_CLASS_TEXT
        )
        sendRow.addView(inputEt, LinearLayout.LayoutParams(0, UiKit.dp(ctx, 44), 1f))
        val btnSend = UiKit.chip(ctx, "ارسال", UiKit.ChipKind.ACCENT) {
            val cmd = inputEt.text.toString().trim()
            if (cmd.isNotEmpty()) {
                append("> $cmd")
                host.sendConsole(cmd)
            }
        }
        sendRow.addView(btnSend, LinearLayout.LayoutParams(UiKit.dp(ctx, 74), UiKit.dp(ctx, 44)))
        col.addView(sendRow)
        col.addView(UiKit.caption(ctx, "خروجی کنسول و پیام خطاها اینجا می‌آید.", dim = true))

        // ---- دستورهای پرکاربرد سرور -------------------------------------
        col.addView(UiKit.sectionLabel(ctx, "دستورهای سریع سرور", accent = false))
        UiKit.chipRow(
            ctx,
            listOf(
                UiKit.chip(ctx, "👥 فهرست بازیکن‌ها") { host.sendConsole("list") },
                UiKit.chip(ctx, "🌱 بذر دنیا") { host.sendConsole("seed") },
                UiKit.chip(ctx, "💾 ذخیرهٔ فوری") { host.sendConsole("save-all") },
                UiKit.chip(ctx, "❓ help") { host.sendConsole("help") },
            ),
            4, col,
        )

        val btnStop = UiKit.chip(ctx, "⛔ استاپ کامل سرور", UiKit.ChipKind.DANGER) {
            host.confirm(
                "استاپ سرور",
                "سرور برای همهٔ بازیکن‌ها بسته می‌شود و همه از بازی بیرون می‌افتند. مطمئنی؟",
            ) { host.sendConsole("stop") }
        }
        col.addView(
            btnStop,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, UiKit.dp(ctx, 46)
            ).apply {
                val m = UiKit.dp(ctx, 4)
                setMargins(m, UiKit.dp(ctx, 8), m, 0)
            }
        )

        // ---- به‌روزرسانی وضعیت ------------------------------------------
        fun updateStateUi() {
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
            tvState.text = when {
                connected -> "وصل است — می‌توانی دستور بدهی"
                attempting -> "در حال اتصال…"
                else -> "آفلاین — وصل شو تا دستورها اجرا شوند"
            }
            tvState.setTextColor(
                ContextCompat.getColor(
                    ctx,
                    when {
                        connected -> R.color.dotOk
                        attempting -> R.color.dotBusy
                        else -> R.color.textSub
                    }
                )
            )
            btnConnect.text = if (connected) "قطع اتصال" else "اتصال"
        }

        btnConnect.setOnClickListener {
            if (host.isRconConnected()) {
                host.rconDisconnect()
            } else {
                saveFields()
                if (host.rconConnect()) {
                    updateStateUi()
                    append("→ در حال اتصال به ${host.prefs.rconHost}:${host.prefs.rconPort} …")
                }
            }
        }
        host.registerStateListener { updateStateUi() }
        updateStateUi()
        return col
    }
}
