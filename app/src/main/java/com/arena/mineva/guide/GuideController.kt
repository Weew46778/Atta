package com.arena.mineva.guide

import android.app.Activity
import android.content.Context
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.arena.mineva.AppPrefs
import com.arena.mineva.Ui
import com.arena.mineva.assistant.TextToSpeechManager

/**
 * Voice-guided, step-by-step assistant tour.
 *
 * The steps are persisted so the app can resume from where the user left. It is not a
 * one-time "onboarding only": it can be restarted from Settings and used as a shared
 * training/mission system by other screens (each step can launch an Activity or just
 * explain what to do).
 */
data class GuideStep(
    val key: String,
    val title: String,
    val speech: String,
    val description: String,
    val actionLabel: String? = null,
    val targetClass: Class<out Activity>? = null
)

object GuideController {

    val steps = listOf(
        GuideStep(
            key = "launch",
            title = "۱. باز کردن ماین‌کرافت",
            speech = "اول بازی ماین کرافت بدراک را باز کن. اگر نصب نیست من به فروشگاه می روم.",
            description = "از منوی اصلی دکمه «باز کردن Minecraft Bedrock» را بزن.",
            actionLabel = "باز کردن بازی",
            targetClass = com.arena.mineva.MainActivity::class.java
        ),
        GuideStep(
            key = "overlay",
            title = "۲. پنل اورلای",
            speech = "حالا پنل اورلای را فعال کن. از لبه چپ صفحه داخل بازی به سمت راست بکش.",
            description = "دکمه «فعال‌کردن پنل اورلای داخل بازی» را بزن، سپس داخل بازی از لبه چپ بکش.",
            actionLabel = "فعال‌کردن پنل",
            targetClass = com.arena.mineva.MainActivity::class.java
        ),
        GuideStep(
            key = "voice",
            title = "۳. صدا و تنظیمات",
            speech = "واجبی صدای فارسی و زن را چک کن. می توانی بسته صوتی را هم نصب کنی.",
            description = "از منوی اصلی → تنظیمات هوش مصنوعی → تنظیمات صوتی.",
            actionLabel = "تنظیمات صوتی",
            targetClass = com.arena.mineva.VoiceSettingsActivity::class.java
        ),
        GuideStep(
            key = "server",
            title = "۴. ساخت سرور",
            speech = "حالا بیا یک سرور بسازیم. روی گوشی یا سرور مجازی. تو فقط جواب بده.",
            description = "از منوی اصلی «ساخت خودکار سرور» را بزن؛ نوع و نسخه را انتخاب کن.",
            actionLabel = "ساخت سرور",
            targetClass = com.arena.mineva.ServerWizardActivity::class.java
        ),
        GuideStep(
            key = "panel",
            title = "۵. پنل و پایش",
            speech = "سرور که ساخت، از «پنل حرفه‌ای» او را روشن کن و «پایش خودکار» را فعال کن.",
            description = "از پیشخوان یا منوی اصلی: پنل حرفه‌ای → پایش و ریاستارت خودکار.",
            actionLabel = "پنل حرفه‌ای",
            targetClass = com.arena.mineva.ServerPanelActivity::class.java
        ),
        GuideStep(
            key = "encyclopedia",
            title = "۶. اصطلاحات و دائرةالمعارف",
            speech = "هرچه نمی دانی از «آوا» بپرس. من جواب را در دائرةالمعارف خودم ذخیره می کنم و دفعه بعد آفلاین می گویم.",
            description = "از «دائرةالمعارف آوا» می‌توانی موضوعات را ببینی و اضافه کنی.",
            actionLabel = "دائرةالمعارف",
            targetClass = com.arena.mineva.EncyclopediaActivity::class.java
        )
    )

    fun currentIndex(): Int = AppPrefs.guideIndex.coerceIn(0, steps.size)

    fun current(): GuideStep = steps[currentIndex().coerceIn(0, steps.size - 1)]

    fun complete(context: Context, tts: TextToSpeechManager?) {
        AppPrefs.guideIndex = steps.size
        runCatching { tts?.speak("شروع کامل شد. من همیشه همین‌جا کنار تو هستم.") }
    }

    /**
     * Called by real screens when a step is actually completed (not just "next").
     * Advances the tour only forward; jumping ahead is allowed.
     */
    fun markStepDone(key: String, context: Context, tts: TextToSpeechManager?) {
        val idx = steps.indexOfFirst { it.key == key }
        if (idx < 0) return
        if (AppPrefs.guideIndex <= idx) {
            AppPrefs.guideIndex = (idx + 1).coerceAtMost(steps.size)
            if (AppPrefs.guideIndex <= steps.size - 1) {
                runCatching { tts?.speak("این مرحله انجام شد. " + current().speech) }
            } else {
                runCatching { tts?.speak("این مرحله انجام شد. شروع کامل شد.") }
            }
        }
    }

    fun advance(context: Context, tts: TextToSpeechManager?): GuideStep {
        AppPrefs.guideIndex = (AppPrefs.guideIndex + 1).coerceIn(0, steps.size)
        return current()
    }

    /** Renders a single guide card inside a container. */
    fun renderCard(
        activity: Activity,
        tts: TextToSpeechManager,
        container: LinearLayout,
        onChanged: () -> Unit
    ) {
        container.removeAllViews()
        val idx = currentIndex().coerceAtMost(steps.size - 1)
        val step = steps[idx]

        val header = Ui.horizontal(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        header.addView(Ui.text(activity, "🎓 راهنمای گام‌به‌گام", 19f, 0xFF35D07F.toInt(), bold = true))
        header.addView(
            Ui.text(activity, "$idx/${steps.size - 1}", 14f, 0xFF9FB2C2.toInt(), bold = true).apply {
                gravity = android.view.Gravity.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
        )
        container.addView(header)

        container.addView(Ui.text(activity, step.title, 18f, 0xFFF1F5F9.toInt(), bold = true))
        container.addView(Ui.text(activity, step.description, 13f, 0xFFD8E3EC.toInt()))
        container.addView(
            Ui.button(activity, "🔊 تکرار راهنما", 0xFF7D4DB1.toInt(), 44f) {
                tts.speak(step.speech)
            }
        )
        if (step.actionLabel != null) {
            container.addView(
                Ui.button(activity, step.actionLabel, 0xFF2E9BFF.toInt(), 46f) {
                    step.targetClass?.let { activity.startActivity(android.content.Intent(activity, it)) }
                }
            )
        }
        val row = Ui.horizontal(activity)
        row.addView(Ui.button(activity, "⬅ قبلی", 0xFF1F8F8F.toInt(), 44f) {
            AppPrefs.guideIndex = (AppPrefs.guideIndex - 1).coerceAtLeast(0)
            onChanged()
        }, LinearLayout.LayoutParams(0, Ui.dp(activity, 44f), 1f))
        row.addView(Ui.button(activity, "بعدی ➡", 0xFF35D07F.toInt(), 44f) {
            advance(activity, tts)
            onChanged()
        }, LinearLayout.LayoutParams(0, Ui.dp(activity, 44f), 1f))
        container.addView(row)
        if (idx == steps.size - 1) {
            container.addView(
                Ui.button(activity, "✅ پایان شروع", 0xFFFF5A5A.toInt(), 46f) {
                    complete(activity, tts)
                    onChanged()
                }
            )
        }
    }
}

/** Small re-usable GuideActivity so anything can open the tour. */
class GuideActivity : AppCompatActivity() {
    private lateinit var tts: TextToSpeechManager
    private lateinit var box: LinearLayout

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeechManager(this)
        tts.init { tts.speak(GuideController.current().speech) }
        val scroll = ScrollView(this)
        val root = Ui.fill(this)
        scroll.addView(root)
        setContentView(scroll)
        box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(box)
        render()

        root.addView(
            Ui.button(this, "🔁 شروع مجدد راهنما از اول", 0xFFC97C22.toInt(), 46f) {
                AppPrefs.guideIndex = 0
                render()
                tts.speak(GuideController.current().speech)
            }
        )
    }

    private fun render() {
        GuideController.renderCard(this, tts, box) {
            render()
            tts.speak(GuideController.current().speech)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) tts.shutdown()
    }
}
