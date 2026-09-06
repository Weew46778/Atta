package com.arena.mineva

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.arena.mineva.assistant.GeminiAssistantEngine
import com.arena.mineva.assistant.TextToSpeechManager
import com.arena.mineva.knowledge.KnowledgeRepository
import com.arena.mineva.knowledge.KnowledgeResearchWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {

    private val tts by lazy { TextToSpeechManager(this) }
    private lateinit var keyField: EditText
    private lateinit var result: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts.init { tts.speak("تنظیمات هوش مصنوعی. کلید را وارد کن تا آوا بتواند سوالهای خارج از حافظه محلی را هم جواب دهد.") }

        val root = Ui.fill(this)
        root.addView(Ui.text(this, "⚙️ تنظیمات", 22f, 0xFF2E9BFF.toInt(), bold = true))
        root.addView(Ui.text(this, "کلید Gemini را در تنظیمات Google AI Studio بسازید. بدون کلید، آوا فقط از دانش آفلاین جواب میدهد.", 13f, 0xFF9FB2C2.toInt()))

        keyField = EditText(this).apply {
            hint = "AIza... (کلید Gemini)"
            setText(AppPrefs.geminiApiKey)
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF9FB2C2.toInt())
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            background = Ui.card(this@SettingsActivity).background
            setPadding(
                Ui.dp(this@SettingsActivity, 12f),
                Ui.dp(this@SettingsActivity, 10f),
                Ui.dp(this@SettingsActivity, 12f),
                Ui.dp(this@SettingsActivity, 10f)
            )
        }
        root.addView(keyField)

        root.addView(
            Ui.button(this, "💾 ذخیره کلید", 0xFF35D07F.toInt(), 48f) {
                AppPrefs.geminiApiKey = keyField.text.toString().trim()
                tts.speak("کلید ذخیره شد.")
            }
        )
        root.addView(
            Ui.button(this, "🧪 تست اتصال هوش مصنوعی", 0xFF2E9BFF.toInt(), 48f) {
                testConnection()
            }
        )
        root.addView(
            Ui.button(this, "📚 مطالب یادگرفته (دایرةالمعارف شخصی)", 0xFF7D4DB1.toInt(), 48f) {
                val count = KnowledgeRepository(this).learnedCount()
                tts.speak("تا حالا $count مطلب را یاد گرفتهام. هر بار که پاسخ آنلاین بدهی، همینجا ذخیره میشود.")
                result.removeAllViews()
                result.addView(
                    Ui.text(this, "یادگیری پویا فعال است. محتوای شخصی ذخیرهشده: $count", 14f, 0xFF35D07F.toInt())
                )
                result.addView(
                    Ui.text(this, "مسیر: filesDir/knowledge_extra.json — با جستجوی بعدی به همین مقادیر میرسد.", 12f, 0xFF9FB2C2.toInt())
                )
            }
        )
        root.addView(
            Ui.button(this, "🧠 تحقیق پویا و تکمیل دانش (۵ موضوع)", 0xFF7D4DB1.toInt(), 48f) {
                startResearch()
            }
        )

        val scroll = ScrollView(this)
        result = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(result)
        root.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun startResearch() {
        val key = AppPrefs.geminiApiKey
        if (key.isBlank()) {
            tts.speak("برای تحقیق آنلاین اول کلید هوش مصنوعی را بگذار.")
            result.addView(Ui.text(this, "کلید Gemini خالی است.", 14f, 0xFFFF5A5A.toInt()))
            return
        }
        result.removeAllViews()
        result.addView(Ui.text(this, "آوا در حال تحقیق و تکمیل دانش است...", 14f, 0xFF7D4DB1.toInt()))
        tts.speak("در حال تحقیق و ذخیره دانش هستم.")
        lifecycleScope.launch(Dispatchers.IO) {
            val res = KnowledgeResearchWorker.run(
                context = this@SettingsActivity,
                limit = 5,
                onProgress = { step -> withContext(Dispatchers.Main) {
                    result.addView(Ui.text(this@SettingsActivity, "• $step", 13f, 0xFFD8E3EC.toInt()))
                } }
            )
            withContext(Dispatchers.Main) {
                result.addView(Ui.text(this@SettingsActivity, res.output, 14f, 0xFF35D07F.toInt()))
                tts.speak("کار تحقیق تمام شد. ${res.saved} مطلب جدید را یاد گرفتم.")
            }
        }
    }

    private fun testConnection() {
        val key = keyField.text.toString().trim()
        if (key.isBlank()) {
            tts.speak("اول کلید را وارد کن و درست نگاه کن.")
            result.addView(Ui.text(this, "کلید خالی است.", 14f, 0xFFFF5A5A.toInt()))
            return
        }
        result.removeAllViews()
        result.addView(Ui.text(this, "در حال تست...", 14f, 0xFF9FB2C2.toInt()))
        tts.speak("در حال تست اتصال هوش مصنوعی هستم.")
        lifecycleScope.launch(Dispatchers.IO) {
            val answer = runCatching {
                GeminiAssistantEngine.ask(
                    apiKey = key,
                    system = "تو آوا هستی. فقط یک جمله کوتاه بگو که اتصال سالم است.",
                    question = "سلام، اتصال تست است."
                )
            }.getOrElse { e -> "خطا: ${e.message ?: "نامشخص"}" }
            withContext(Dispatchers.Main) {
                result.removeAllViews()
                result.addView(
                    Ui.text(
                        this@SettingsActivity,
                        if (answer.startsWith("خطا")) answer else "✓ $answer",
                        14f,
                        if (answer.startsWith("خطا")) 0xFFFF5A5A.toInt() else 0xFF35D07F.toInt()
                    )
                )
                tts.speak(if (answer.startsWith("خطا")) "اتصال برقرار نشد. کلید و اینترنت را چک کن." else "اتصال برقرار شد.")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) tts.shutdown()
    }
}
