package com.arena.mineva

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.arena.mineva.assistant.TextToSpeechManager

class VoiceSettingsActivity : AppCompatActivity() {

    private var manager: TextToSpeechManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = Ui.fill(this)
        root.addView(Ui.text(this, "🎙 تنظیمات صوتی آوا", 22f, 0xFF35D07F.toInt(), bold = true))
        root.addView(Ui.text(this, "صدای فارسی، انتخاب صدای زن و سرعت صحبت.", 13f, 0xFF9FB2C2.toInt()))

        root.addView(
            Ui.button(this, "📥 نصب/بروزرسانی داده صوتی فارسی", 0xFF2E9BFF.toInt(), 48f) {
                openSystemVoiceDataInstall()
            }
        )
        root.addView(
            Ui.button(this, "⚙️ موتور متن به گفتار (تنظیمات سیستم)", 0xFFC97C22.toInt(), 48f) {
                try {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } catch (_: Exception) {
                }
            }
        )
        root.addView(
            Ui.button(this, "🎙 تست جمله فارسی", 0xFF7D4DB1.toInt(), 48f) {
                manager?.speak("سلام! من آوا هستم. صدای من آماده است.")
            }
        )

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)

        val speedLabel = Ui.text(this, "سرعت: 0.90", 14f, 0xFFF1F5F9.toInt())
        root.addView(speedLabel)
        val seek = SeekBar(this).apply {
            max = 50
            progress = ((AppPrefs.speechRate - 0.5f) * 100).toInt().coerceIn(0, 50)
        }
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val rate = 0.5f + progress / 100f
                AppPrefs.speechRate = rate
                speedLabel.text = "سرعت: %.2f".format(rate)
                manager?.speechRate = rate
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
        root.addView(seek)

        setContentView(root)

        val m = TextToSpeechManager(this)
        manager = m
        m.init {
            m.speak("تنظیمات صوتی آماده است.")
            runOnUiThread { renderVoices(list) }
        }
    }

    private fun openSystemVoiceDataInstall() {
        runCatching {
            val intent = Intent("com.android.settings.TTS_SETTINGS")
            startActivity(intent)
        }.onFailure {
            runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
        manager?.speak("به تنظیمات صدا رفتم. گزینه نصب داده صوتی را انتخاب کن.")
    }

    private fun renderVoices(list: LinearLayout) {
        list.removeAllViews()
        val all = manager?.persianVoices().orEmpty()
        list.addView(Ui.text(this, "صدای فارسی نصبشده: ${all.size}", 15f, 0xFFF1F5F9.toInt(), bold = true))
        if (all.isEmpty()) {
            list.addView(
                Ui.text(
                    this,
                    "هیچ صدای فارسی نصب نیست. روی دکمه «نصب/بروزرسانی داده صوتی» بزن و زبان فارسی را انتخاب کن.",
                    13f,
                    0xFFFF5A5A.toInt()
                )
            )
            return
        }
        all.forEach { voice ->
            val installed = !voice.isNetworkConnectionRequired
            val female = (voice.name ?: "").lowercase().contains("female") ||
                (voice.name ?: "").contains("زهرا")
            val saved = AppPrefs.selectedPersianVoice == voice.name
            val label = "${voice.name} — ${if (installed) "آفلاین" else "نیاز به اینترنت"}${if (female) " — زنانه" else ""}"
            val btn = Ui.button(this, if (saved) "✓ $label" else label, if (saved) 0xFF35D07F.toInt() else 0xFF1C2836.toInt(), 46f)
            btn.setOnClickListener {
                AppPrefs.selectedPersianVoice = voice.name
                manager?.setVoice(voice)
                manager?.speak("صدای جدید انتخاب شد. سلام، من آوا هستم.")
                renderVoices(list)
            }
            list.addView(btn)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) manager?.shutdown()
    }
}
