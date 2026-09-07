package com.arena.mineva

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import com.arena.mineva.assistant.TextToSpeechManager
import com.arena.mineva.server.ServerConfig
import com.arena.mineva.server.ServerProfileStore
import com.arena.mineva.server.ServerTarget

/**
 * Multi-server dashboard ("پیشخوان"). Stores many server profiles and lets the user
 * activate one, open its professional panel / cloud log, rename it, or delete it.
 */
class ServerDashboardActivity : AppCompatActivity() {

    private val tts by lazy { TextToSpeechManager(this) }
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts.init { tts.speak("پیشخوان سرورها باز شد.") }

        val root = Ui.fill(this)
        val scroll = Ui.scrollable(this, root)
        setContentView(scroll)

        root.addView(Ui.text(this, "📋 پیشخوان سرورها", 23f, 0xFF2E70B8.toInt(), bold = true))
        root.addView(
            Ui.text(
                this,
                "چند سرور (محلی/VPS، Java/Bedrock/Hybrid) را نگه می‌دارم؛ یکی را «فعال» می‌کنی و پنل/لاگ همان را باز می‌شود.",
                12f,
                0xFF9FB2C2.toInt()
            )
        )

        root.addView(
            Ui.button(this, "➕ ساخت سرور جدید / ویرایش فعال", 0xFF35D07F.toInt(), 50f) {
                startActivity(Intent(this, ServerWizardActivity::class.java))
            }
        )

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(list)
        root.addView(Ui.text(this, "", 8f, Color.TRANSPARENT))
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        list.removeAllViews()
        val profiles = ServerProfileStore.all()
        val activeId = ServerProfileStore.activeProfile()?.id ?: ""

        if (profiles.isEmpty()) {
            list.addView(
                Ui.text(
                    this,
                    "هنوز سروری ذخیره نشده. دکمه «ساخت سرور جدید» را بزن؛ هر ساخته‌شده به این پیشخوان اضافه می‌شود.",
                    14f,
                    0xFFC97C22.toInt()
                )
            )
            return
        }

        profiles.forEach { profile ->
            val config = ServerConfig.fromJson(profile.configJson) ?: ServerConfig()
            val card = Ui.card(this)
            card.addView(
                Ui.text(
                    this,
                    (if (profile.id == activeId) "⭐ " else "") + profile.name,
                    17f,
                    if (profile.id == activeId) 0xFF35D07F.toInt() else 0xFFF1F5F9.toInt(),
                    bold = true
                )
            )
            card.addView(
                Ui.text(
                    this,
                    "${config.target} | ${config.edition} ${config.version} | پورت ${config.port}" +
                        (if (config.target == ServerTarget.VPS && config.host.isNotBlank()) " | ${config.host}" else ""),
                    12f,
                    0xFF9FB2C2.toInt()
                )
            )
            card.addView(
                Ui.text(
                    this,
                    "بازیکن ${config.maxPlayers} | رم ${config.memoryMb}MB | ${config.allowJavaClients} Java / ${config.allowBedrockClients} Bedrock",
                    11f,
                    0xFF9FB2C2.toInt()
                )
            )

            val row = Ui.horizontal(this)
            val weight = LinearLayout.LayoutParams(0, Ui.dp(this, 42f), 1f)
            row.addView(Ui.button(this, "⭐ فعال", if (profile.id == activeId) 0xFF35D07F.toInt() else 0xFF1F8F8F.toInt(), 42f) {
                ServerProfileStore.setActive(profile.id)
                tts.speak("پروفایل «${profile.name}» فعال شد.")
                render()
            }, weight)
            row.addView(Ui.button(this, "🛠 پنل", 0xFF2E70B8.toInt(), 42f) {
                ServerProfileStore.setActive(profile.id)
                startActivity(Intent(this, ServerPanelActivity::class.java))
            }, weight)
            row.addView(Ui.button(this, "🌐 لاگ", 0xFF2E9BFF.toInt(), 42f) {
                ServerProfileStore.setActive(profile.id)
                startActivity(Intent(this, CloudLogActivity::class.java))
            }, weight)
            row.addView(Ui.button(this, "✏️ نام", 0xFFC97C22.toInt(), 42f) {
                rename(profile.id, profile.name)
            }, weight)
            card.addView(row)
            card.addView(
                Ui.button(this, "🗑 حذف این پروفایل", 0xFFFF5A5A.toInt(), 40f) {
                    ServerProfileStore.remove(profile.id)
                    tts.speak("پروفایل «${profile.name}» حذف شد.")
                    render()
                }
            )
            list.addView(card)
        }
    }

    private fun rename(id: String, current: String) {
        val field = EditText(this).apply {
            setText(current)
            setTextColor(Color.WHITE)
            setSingleLine(true)
        }
        list.addView(field)
        list.addView(
            Ui.button(this, "✅ ذخیره نام", 0xFF35D07F.toInt(), 44f) {
                val name = field.text.toString().trim()
                if (name.isNotBlank()) {
                    ServerProfileStore.updateName(id, name)
                    tts.speak("نام پروفایل تغییر کرد.")
                }
                render()
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) tts.shutdown()
    }
}
