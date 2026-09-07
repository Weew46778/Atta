package com.arena.mineva

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.arena.mineva.assistant.AssistantEngine
import com.arena.mineva.assistant.SpeechRecognitionManager
import com.arena.mineva.assistant.TextToSpeechManager
import com.arena.mineva.knowledge.KnowledgeRepository
import kotlinx.coroutines.launch

class VoiceAssistantActivity : AppCompatActivity() {

    private lateinit var tts: TextToSpeechManager
    private val stt by lazy { SpeechRecognitionManager(this) }
    private lateinit var messages: LinearLayout
    private lateinit var input: EditText
    private lateinit var scroll: ScrollView

    private val engine by lazy { AssistantEngine(KnowledgeRepository(this)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeechManager(this)
        tts.init { tts.speak("سلام! من آوا هستم. سوال ماینکرافتی یا درخواستت را بگو.") }

        val root = Ui.fill(this)
        root.addView(Ui.text(this, " 🤖 گفتگو با آوا", 22f, 0xFF35D07F.toInt(), bold = true))
        root.addView(
            Ui.text(this, "صوتی یا متنی سوال بپرس؛ پاسخ متنی و در صورت امکان صوتی میآید.", 12f, 0xFF9FB2C2.toInt())
        )

        scroll = ScrollView(this)
        messages = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(messages)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0, 1f
        ))

        val controls = Ui.horizontal(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        input = EditText(this).apply {
            hint = "مثلاً: در ماینکرافت چطور نتریت بسازم؟"
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF9FB2C2.toInt())
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            background = Ui.card(this@VoiceAssistantActivity).background
            textSize = 14f
            setPadding(Ui.dp(this@VoiceAssistantActivity, 12f), Ui.dp(this@VoiceAssistantActivity, 8f), Ui.dp(this@VoiceAssistantActivity, 12f), Ui.dp(this@VoiceAssistantActivity, 8f))
        }
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        input.layoutParams = lp
        val mic = Ui.button(this, "🎤", 0xFF2E9BFF.toInt(), 48f).apply {
            layoutParams = LinearLayout.LayoutParams(Ui.dp(this@VoiceAssistantActivity, 52f), Ui.dp(this@VoiceAssistantActivity, 52f))
        }
        val send = Ui.button(this, "➤", 0xFF35D07F.toInt(), 48f).apply {
            layoutParams = LinearLayout.LayoutParams(Ui.dp(this@VoiceAssistantActivity, 52f), Ui.dp(this@VoiceAssistantActivity, 52f))
        }
        mic.setOnClickListener { startVoiceInput() }
        send.setOnClickListener {
            val q = input.text.toString().trim()
            if (q.isNotBlank()) ask(q)
        }
        controls.addView(input)
        controls.addView(mic)
        controls.addView(send)
        root.addView(controls)

        addBubble("🤖", "آوا", "سلام! سوال یا درخواستت را بگو. من متخصص ماینکرافت هستم و در حال یادگیری کاملتر هستم.", true)
        setContentView(root)
    }

    private fun startVoiceInput() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 50)
            return
        }
        input.setText("")
        input.hint = "در حال گوش دادن... 🎙"
        addBubble("🎙", "من", "در حال شنیدن...", user = true)
        stt.startListening(
            onPartial = { p ->
                input.setText(p)
                input.setSelection(p.length)
            },
            onResult = { r ->
                if (r.isNotBlank()) ask(r) else tts.speak("متوجه نشدم. دوباره بگو.")
            },
            onError = { e ->
                input.hint = "message"
                tts.speak(e)
            }
        )
    }

    private fun ask(question: String) {
        input.setText("")
        addBubble("👤", "من", question, user = true)
        lifecycleScope.launch {
            val answer = if (isLearnRequest(question)) engine.learn(question) else engine.respond(question)
            addBubble("🤖", "آوا", answer, user = false)
            tts.speak(answer)
            scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun isLearnRequest(q: String): Boolean {
        val text = q.trim().lowercase()
        return listOf(
            "یاد بگیر", "یاد بده", "این را یاد بگیر", "به خاطر بسپار",
            "اضافه کن به دانش", "اضافه کن به دایرةالمعارف", "یادم بده", "ذخیره کن"
        ).any { text.startsWith(it) }
    }

    private fun addBubble(avatar: String, name: String, text: String, user: Boolean) {
        val row = Ui.horizontal(this)
        val bubble = Ui.card(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            addView(Ui.text(this@VoiceAssistantActivity, "$avatar $name", 13f, if (user) 0xFF2E9BFF.toInt() else 0xFF35D07F.toInt(), bold = true))
            addView(Ui.text(this@VoiceAssistantActivity, text, 15f))
        }
        if (user) row.gravity = Gravity.END else row.gravity = Gravity.START
        row.addView(bubble)
        messages.addView(row)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 50 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startVoiceInput()
        }
    }

    override fun onDestroy() {
        stt.destroy()
        if (isFinishing) tts.shutdown()
        super.onDestroy()
    }
}
