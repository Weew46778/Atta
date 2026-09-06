package com.arena.mineva

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.arena.mineva.assistant.TextToSpeechManager
import com.arena.mineva.knowledge.KnowledgeRepository

/**
 * Encyclopedia screen. Lists every category/topic (Minecraft + default general topics +
 * user-added topics), supports local search, and lets the user add any new topic with a
 * custom category. This is the same framework that supports up to five (and more)
 * non-Minecraft topics.
 */
class EncyclopediaActivity : AppCompatActivity() {

    private val tts by lazy { TextToSpeechManager(this) }
    private val repo by lazy { KnowledgeRepository(this) }
    private lateinit var list: LinearLayout
    private lateinit var searchField: EditText
    private lateinit var titleField: EditText
    private lateinit var textField: EditText
    private lateinit var categoryField: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts.init { tts.speak("دایرة‌المعارف. جستجو کن یا موضوع جدید اضافه کن.") }

        val scroll = ScrollView(this)
        val root = Ui.fill(this)
        scroll.addView(root)
        setContentView(scroll)

        root.addView(Ui.text(this, "📚 دایرة‌المعارف آوا", 22f, 0xFF7D4DB1.toInt(), bold = true))
        root.addView(Ui.text(this, "ماینکرافت + دانش عمومی + موضوعات خودت. هر مطلبی که اضافه کنی همین‌جا ذخیره و از این‌به‌بعد در پاسخ استفاده می‌شود.", 12f, 0xFF9FB2C2.toInt()))

        searchField = EditText(this).apply {
            hint = "جستجو: مثلاً فارم آهن یا آب بدن"
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF9FB2C2.toInt())
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            background = Ui.card(this@EncyclopediaActivity).background
            setPadding(Ui.dp(this@EncyclopediaActivity, 12f), Ui.dp(this@EncyclopediaActivity, 10f), Ui.dp(this@EncyclopediaActivity, 12f), Ui.dp(this@EncyclopediaActivity, 10f))
        }
        root.addView(searchField)
        root.addView(Ui.button(this, "🔎 جستجو در دایرة‌المعارف", 0xFF2E9BFF.toInt(), 46f) {
            search()
        })

        root.addView(Ui.text(this, "➕ افزودن موضوع جدید", 16f, 0xFFF1F5F9.toInt(), bold = true))
        titleField = field("عنوان موضوع")
        categoryField = field("دسته (مثلاً دانش عمومی یا ماینکرافت)")
        textField = EditText(this).apply {
            hint = "متن/پاسخ کامل"
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF9FB2C2.toInt())
            minLines = 4
            maxLines = 8
            gravity = android.view.Gravity.START or android.view.Gravity.TOP
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            background = Ui.card(this@EncyclopediaActivity).background
            setPadding(Ui.dp(this@EncyclopediaActivity, 12f), Ui.dp(this@EncyclopediaActivity, 10f), Ui.dp(this@EncyclopediaActivity, 12f), Ui.dp(this@EncyclopediaActivity, 10f))
        }
        root.addView(titleField)
        root.addView(categoryField)
        root.addView(textField)
        root.addView(Ui.button(this, "💾 ذخیره موضوع در دایرة‌المعارف", 0xFF35D07F.toInt(), 46f) {
            addTopic()
        })

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(Ui.text(this, "همهٔ موضوعات:", 16f, 0xFFF1F5F9.toInt(), bold = true))
        root.addView(list)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        list.removeAllViews()
        val summary = repo.categorySummary()
        list.addView(Ui.text(this, summary, 13f, 0xFF35D07F.toInt()))

        repo.categories().sorted().forEach { category ->
            list.addView(Ui.text(this, "◼ $category", 15f, 0xFF2E9BFF.toInt(), bold = true))
            repo.topicsIn(category).forEach { topic ->
                val card = Ui.card(this)
                card.addView(Ui.text(this, topic.title, 14f, 0xFFF1F5F9.toInt(), bold = true))
                card.addView(Ui.text(this, topic.text.take(240), 12f, 0xFFD8E3EC.toInt()))
                card.addView(Ui.button(this, "📋 کپی کامل", 0xFF1F8F8F.toInt(), 38f) {
                    val cm = getSystemService(ClipboardManager::class.java)
                    cm.setPrimaryClip(ClipData.newPlainText("mineava_knowledge", "${topic.title}\n\n${topic.text}"))
                    tts.speak("کپی شد.")
                })
                list.addView(card)
            }
        }
    }

    private fun search() {
        val q = searchField.text.toString().trim()
        if (q.isBlank()) {
            render()
            return
        }
        val result = repo.search(q)
        list.removeAllViews()
        list.addView(
            Ui.text(this, "نتیجه جستجو برای «$q»:", 14f, 0xFF2E9BFF.toInt(), bold = true)
        )
        list.addView(
            Ui.text(this, result ?: "در دایرة‌المعارف فعلی پیدا نشد.", 13f, if (result == null) 0xFFC97C22.toInt() else 0xFFD8E3EC.toInt())
        )
        list.addView(Ui.button(this, "⬅ بازگشت به فهرست", 0xFF2E70B8.toInt(), 42f) {
            render()
        })
    }

    private fun addTopic() {
        val title = titleField.text.toString().trim()
        val text = textField.text.toString().trim()
        if (title.isBlank() || text.isBlank()) {
            tts.speak("عنوان و متن موضوع را کامل کن.")
            return
        }
        repo.addUserTopic(title, text, categoryField.text.toString().trim())
        titleField.setText("")
        textField.setText("")
        categoryField.setText("")
        tts.speak("موضوع «$title» به دایرة‌المعارف اضافه شد.")
        render()
    }

    private fun field(hint: String): EditText = EditText(this).apply {
        this.hint = hint
        setTextColor(Color.WHITE)
        setHintTextColor(0xFF9FB2C2.toInt())
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_TEXT
        background = Ui.card(this@EncyclopediaActivity).background
        setPadding(Ui.dp(this@EncyclopediaActivity, 12f), Ui.dp(this@EncyclopediaActivity, 10f), Ui.dp(this@EncyclopediaActivity, 12f), Ui.dp(this@EncyclopediaActivity, 10f))
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) tts.shutdown()
    }
}
