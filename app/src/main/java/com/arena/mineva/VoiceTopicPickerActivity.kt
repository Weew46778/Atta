package com.arena.mineva

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.arena.mineva.assistant.SpeechRecognitionManager
import com.arena.mineva.assistant.TextToSpeechManager
import com.arena.mineva.knowledge.UserTopicStore
import kotlinx.coroutines.launch

/**
 * Voice-driven encyclopedia topic picker.
 *
 * The assistant asks for "5 non-Minecraft topics you want me to learn", listens to the
 * user, saves each one, then asks a short clarifier ("tell me more" / "what to cover") if
 * the sentence is long. The stored topics go straight into KnowledgeRepository and the
 * normal question-answering search.
 */
class VoiceTopicPickerActivity : AppCompatActivity() {

    private lateinit var tts: TextToSpeechManager
    private val stt = SpeechRecognitionManager(this)
    private lateinit var list: LinearLayout
    private lateinit var titleInput: EditText
    private lateinit var detailInput: EditText
    private var phase = "new" // new | detail

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeechManager(this)
        tts.init {
            tts.speak("خوب. پنج موضوع غیر ماینکرافتی را بگو که دوست داری یاد بگیرم. یکی یکی بگو.")
        }

        val scroll = ScrollView(this)
        val root = Ui.fill(this)
        scroll.addView(root)
        setContentView(scroll)

        root.addView(Ui.text(this, "🎙 انتخاب ۵ موضوع دایرةالمعارف", 21f, 0xFF35D07F.toInt(), bold = true))
        root.addView(Ui.text(this, "به آوا صوتی بگو یا اینجا متن بنویس؛ هر موضوع در دسته دلخواه ذخیره میشود.", 12f, 0xFF9FB2C2.toInt()))

        root.addView(Ui.text(this, "موضوعات انتخابی (${UserTopicStore.count()}/۵):", 15f, 0xFFF1F5F9.toInt(), bold = true))
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)

        root.addView(Ui.text(this, "➕ افزودن دستی", 15f, 0xFFF1F5F9.toInt(), bold = true))
        titleInput = field("عنوان موضوع")
        detailInput = EditText(this).apply {
            hint = "جزئیات (اختیاری)"
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF9FB2C2.toInt())
            minLines = 3
            maxLines = 6
            gravity = android.view.Gravity.START or android.view.Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            background = Ui.card(this@VoiceTopicPickerActivity).background
            setPadding(Ui.dp(this@VoiceTopicPickerActivity, 12f), Ui.dp(this@VoiceTopicPickerActivity, 10f), Ui.dp(this@VoiceTopicPickerActivity, 12f), Ui.dp(this@VoiceTopicPickerActivity, 10f))
        }
        root.addView(titleInput)
        root.addView(detailInput)
        root.addView(Ui.button(this, "💾 ذخیره موضوع", 0xFF35D07F.toInt(), 46f) {
            val title = titleInput.text.toString().trim()
            if (title.isBlank()) { tts.speak("عنوان را بنویس."); return@button }
            val detail = detailInput.text.toString().trim()
            UserTopicStore.add(title, "دایرةالمعارف کاربر", detail)
            titleInput.setText(""); detailInput.setText("")
            tts.speak("«$title» یاد گرفتم.")
            render()
        })
        root.addView(Ui.button(this, "🎤 گوش دادن به موضوع جدید", 0xFF2E9BFF.toInt(), 48f) {
            if (UserTopicStore.remainingCapacity() <= 0) {
                tts.speak("هر پنج موضوع را داری. برای تغییر یکی را با لمس حذف کن.")
                return@button
            }
            listen("new")
        })

        render()
        requestMicOnce()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 60 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            listen("new")
        }
    }

    private fun requestMicOnce() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 60)
        }
    }

    private fun listen(nextPhase: String) {
        phase = nextPhase
        stt.startListening(
            onPartial = {},
            onResult = { result ->
                if (phase == "new") {
                    if (result.isNotBlank()) {
                        UserTopicStore.add(result, "دایرةالمعارف کاربر", "")
                        tts.speak("«$result» اضافه شد. اگر موضوع بعدی داری بگو.")
                        render()
                    } else {
                        tts.speak("متوجه نشدم. دوباره بگو.")
                    }
                }
            },
            onError = { e -> tts.speak(e) }
        )
    }

    private fun render() {
        list.removeAllViews()
        val topics = UserTopicStore.all()
        if (topics.isEmpty()) {
            list.addView(Ui.text(this, "هنوز موضوعی انتخاب نشده.", 13f, 0xFF9FB2C2.toInt()))
        } else {
            topics.forEach { t ->
                val card = Ui.card(this)
                card.addView(Ui.text(this, "• ${t.title} (${t.category})", 14f, 0xFF35D07F.toInt(), bold = true))
                card.addView(
                    Ui.button(this, "🗑 حذف", 0xFFFF5A5A.toInt(), 36f) {
                        UserTopicStore.remove(t.title)
                        tts.speak("«${t.title}» حذف شد.")
                        render()
                    }
                )
                list.addView(card)
            }
        }
    }

    private fun field(hint: String): EditText = EditText(this).apply {
        this.hint = hint
        setTextColor(Color.WHITE)
        setHintTextColor(0xFF9FB2C2.toInt())
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_TEXT
        background = Ui.card(this@VoiceTopicPickerActivity).background
        setPadding(Ui.dp(this@VoiceTopicPickerActivity, 12f), Ui.dp(this@VoiceTopicPickerActivity, 10f), Ui.dp(this@VoiceTopicPickerActivity, 12f), Ui.dp(this@VoiceTopicPickerActivity, 10f))
    }

    override fun onDestroy() {
        stt.destroy()
        if (isFinishing) tts.shutdown()
        super.onDestroy()
    }
}
