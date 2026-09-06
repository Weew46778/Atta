package com.arena.mineva

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.arena.mineva.assistant.TextToSpeechManager
import com.arena.mineva.system.Diagnostics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DiagnosticsActivity : AppCompatActivity() {

    private val tts by lazy { TextToSpeechManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts.init { tts.speak("در حال تست کامل سیستم هستم، چند لحظه صبر کن.") }
        val root = Ui.fill(this)
        root.addView(Ui.text(this, "🔬 تست کامل سیستم", 22f, 0xFFFF5A5A.toInt(), bold = true))
        root.addView(Ui.text(this, "تمام بخشهای زیرساخت اپ را بخشبهبخش بررسی میکند.", 13f, 0xFF9FB2C2.toInt()))

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this)
        scroll.addView(list)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0, 1f
        ))

        root.addView(
            Ui.button(this, "📋 کپی گزارش در کلیپبورد", 0xFF35D07F.toInt(), 48f) {
                val cm = getSystemService(ClipboardManager::class.java)
                cm.setPrimaryClip(ClipData.newPlainText("mineava_report", lastReport))
                tts.speak("گزارش کامل کپی شد.")
            }
        )
        setContentView(root)

        lifecycleScope.launch {
            delay(500)
            val checks = Diagnostics.run(this@DiagnosticsActivity)
            lastReport = Diagnostics.report(checks)
            list.removeAllViews()
            checks.forEach { c ->
                val color = if (c.ok) 0xFF35D07F.toInt() else 0xFFFF5A5A.toInt()
                list.addView(
                    Ui.text(
                        this@DiagnosticsActivity,
                        "${if (c.ok) "✓" else "✗"}  ${c.name}",
                        15f,
                        color,
                        bold = true
                    )
                )
                list.addView(Ui.text(this@DiagnosticsActivity, "      ${c.detail}", 12f, 0xFF9FB2C2.toInt()))
            }
            list.addView(Ui.text(this@DiagnosticsActivity, lastReport, 12f, 0xFFD8E3EC.toInt()))
            val failed = checks.count { !it.ok }
            tts.speak(
                if (failed == 0)
                    "تست کامل شد. همه چیز سالم است."
                else
                    "تست کامل شد. $failed مورد نیاز به توجه دارد. گزارش در کلیپبورد است."
            )
        }
    }

    private var lastReport = ""

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) tts.shutdown()
    }
}
