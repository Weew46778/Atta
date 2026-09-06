package com.arena.mineva

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.arena.mineva.assistant.TextToSpeechManager
import com.arena.mineva.server.ServerConfig
import com.arena.mineva.server.ServerEdition
import com.arena.mineva.server.ServerProfileStore
import com.arena.mineva.server.ServerTarget
import com.arena.mineva.server.SshClient
import com.arena.mineva.server.VpsProvisioner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Guided first-time VPS setup. Walks the user through:
 *   1. SSH credentials + connection test
 *   2. server type/version/resource choices
 *   3. full automatic deploy (VpsProvisioner)
 *   4. final verification (systemd + tmux)
 * The successful result is stored as the active multi-server profile.
 */
class VpsGuideActivity : AppCompatActivity() {

    private val tts by lazy { TextToSpeechManager(this) }
    private lateinit var hostField: EditText
    private lateinit var userField: EditText
    private lateinit var passwordField: EditText
    private lateinit var keyPassField: EditText
    private lateinit var sshPortField: EditText
    private lateinit var versionField: EditText
    private lateinit var memoryField: EditText
    private lateinit var playersField: EditText
    private lateinit var output: LinearLayout
    private var edition = ServerEdition.JAVA

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts.init { tts.speak("راهنمای راه‌اندازی سرور مجازی شروع شد.") }

        val scroll = ScrollView(this)
        val root = Ui.fill(this)
        scroll.addView(root)
        setContentView(scroll)

        root.addView(Ui.text(this, "🎓 راهنمای گام‌به‌گام VPS", 22f, 0xFFC97C22.toInt(), bold = true))
        root.addView(
            Ui.text(
                this,
                "مرحله ۱: اطلاعات SSH را وارد کن. مرحله ۲: نوع سرور. مرحله ۳: تست اتصال و ساخت خودکار. مرحله ۴: بررسی نهایی.",
                12f,
                0xFF9FB2C2.toInt()
            )
        )

        root.addView(section("مرحله ۱ — اتصال SSH"))
        hostField = field("آدرس VPS (ip یا domain)")
        userField = field("نام کاربری SSH (مثلاً root یا ubuntu)")
        passwordField = passwordField("رمز عبور SSH (اختیاری اگر کلید داری)")
        keyPassField = passwordField("رمز کلید SSH (اختیاری)")
        sshPortField = field("پورت SSH")
        sshPortField.setText("22")
        root.addView(hostField)
        root.addView(userField)
        root.addView(passwordField)
        root.addView(keyPassField)
        root.addView(sshPortField)

        root.addView(section("مرحله ۲ — نوع سرور و منابع"))
        val row = Ui.horizontal(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        val javaBtn = Ui.button(this, "Java", 0xFF1C2836.toInt(), 42f)
        val bedrockBtn = Ui.button(this, "Bedrock", 0xFF1C2836.toInt(), 42f)
        val hybridBtn = Ui.button(this, "Hybrid", 0xFF1C2836.toInt(), 42f)
        row.addView(javaBtn, weightParams())
        row.addView(bedrockBtn, weightParams())
        row.addView(hybridBtn, weightParams())
        root.addView(row)
        _editionButtons = listOf(javaBtn, bedrockBtn, hybridBtn)
        javaBtn.setOnClickListener { setEdition(ServerEdition.JAVA, javaBtn) }
        bedrockBtn.setOnClickListener { setEdition(ServerEdition.BEDROCK, bedrockBtn) }
        hybridBtn.setOnClickListener { setEdition(ServerEdition.HYBRID, hybridBtn) }
        setEdition(ServerEdition.JAVA, javaBtn)

        versionField = field("نسخه (مثلاً 1.21)")
        versionField.setText("1.21")
        memoryField = field("رم سرور MB")
        memoryField.setText("2048")
        playersField = field("حداکثر بازیکن")
        playersField.setText("10")
        root.addView(versionField)
        root.addView(memoryField)
        root.addView(playersField)

        root.addView(
            Ui.button(this, "🔎 تست اتصال SSH", 0xFF2E70B8.toInt(), 48f) {
                testSsh()
            }
        )
        root.addView(
            Ui.button(this, "⚙️ ساخت و استقرار خودکار", 0xFF35D07F.toInt(), 52f) {
                deploy()
            }
        )

        output = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(section("خروجی / مراحل"))
        root.addView(output)
    }

    private var _editionButtons: List<android.widget.TextView> = emptyList()

    private fun setEdition(e: ServerEdition, btn: android.widget.TextView) {
        edition = e
        _editionButtons.forEach {
            val active = it == btn
            it.setBackgroundColor(if (active) 0xFF2E70B8.toInt() else 0xFF1C2836.toInt())
            it.setTextColor(if (active) Color.WHITE else 0xFF9FB2C2.toInt())
        }
        tts.speak("نسخه $e انتخاب شد.")
    }

    private fun config(): ServerConfig = ServerConfig(
        target = ServerTarget.VPS,
        edition = edition,
        version = versionField.text.toString().ifBlank { "1.21" },
        host = hostField.text.toString().trim(),
        user = userField.text.toString().trim(),
        sshPassword = passwordField.text.toString(),
        sshKeyPassphrase = keyPassField.text.toString(),
        sshPort = sshPortField.text.toString().toIntOrNull() ?: 22,
        port = if (edition == ServerEdition.JAVA) 25565 else 19132,
        maxPlayers = playersField.text.toString().toIntOrNull() ?: 10,
        memoryMb = memoryField.text.toString().toIntOrNull() ?: 2048,
        allowJavaClients = edition != ServerEdition.BEDROCK,
        allowBedrockClients = edition != ServerEdition.JAVA
    )

    private fun testSsh() {
        val cfg = config()
        output.removeAllViews()
        if (cfg.host.isBlank() || cfg.user.isBlank()) {
            output.addView(Ui.text(this, "آدرس VPS و نام کاربری را کامل کن.", 14f, 0xFFFF5A5A.toInt()))
            tts.speak("اول آدرس و نام کاربری SSH را بده.")
            return
        }
        output.addView(Ui.text(this, "در حال تست اتصال به ${cfg.host}...", 13f, 0xFF9FB2C2.toInt()))
        tts.speak("در حال اتصال به سرور مجازی.")
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val ssh = SshClient()
                val session = ssh.connect(
                    host = cfg.host,
                    user = cfg.user,
                    password = cfg.sshPassword.ifBlank { null },
                    keyPath = cfg.sshKeyPath.ifBlank { null },
                    keyPassphrase = cfg.sshKeyPassphrase.ifBlank { null },
                    port = cfg.sshPort
                )
                val who = ssh.exec(session, "whoami && uname -m && free -m | head -2 && df -h | head -3").output
                session.disconnect()
                who
            }.getOrElse { e -> "خطا: ${e.message}" }
            withContext(Dispatchers.Main) {
                output.addView(Ui.text(this@VpsGuideActivity, result.take(600), 12f, if (result.startsWith("خطا")) 0xFFFF5A5A.toInt() else 0xFF35D07F.toInt()))
                tts.speak(if (result.startsWith("خطا")) "اتصال برقرار نشد." else "اتصال SSH برقرار شد.")
            }
        }
    }

    private fun deploy() {
        val cfg = config()
        if (cfg.host.isBlank() || cfg.user.isBlank()) {
            tts.speak("اول آدرس و نام کاربری را کامل کن.")
            output.addView(Ui.text(this, "آدرس VPS و نام کاربری را کامل کن.", 13f, 0xFFFF5A5A.toInt()))
            return
        }
        output.removeAllViews()
        output.addView(Ui.text(this, "مرحله ۳: در حال ساخت و استقرار روی ${cfg.host}...", 13f, 0xFF2E70B8.toInt()))
        tts.speak("شروع استقرار خودکار روی سرور مجازی.")
        lifecycleScope.launch(Dispatchers.IO) {
            val outcome = VpsProvisioner().provision(this@VpsGuideActivity, cfg) { stage ->
                withContext(Dispatchers.Main) {
                    output.addView(Ui.text(this@VpsGuideActivity, "• $stage", 12f, 0xFFD8E3EC.toInt()))
                }
            }
            withContext(Dispatchers.Main) {
                showResult(outcome, cfg)
            }
        }
    }

    private fun showResult(outcome: VpsProvisioner.ProvisionResult, cfg: ServerConfig) {
        output.addView(
            Ui.text(
                this,
                outcome.output.take(1200),
                12f,
                if (outcome.success) 0xFF35D07F.toInt() else 0xFFFF5A5A.toInt()
            )
        )
        if (outcome.success) {
            val stream = ServerProfileStore.add(cfg, "سرور VPS (راهنما)")
            ServerProfileStore.setActive(stream)
            AppPrefs.lastServerConfigJson = cfg.toJson()
            output.addView(Ui.text(this, "مرحله ۴: پروفایل ذخیره شد؛ می‌توانی از «پنل حرفه‌ای» و «پیشخوان» استفاده کنی.", 13f, 0xFF35D07F.toInt()))
            tts.speak("سرور مجازی ساخته شد و به پیشخوان اضافه شد.")
        } else {
            tts.speak("استقرار کامل نشد. خروجی را بررسی کن.")
        }
    }

    private fun section(text: String) =
        Ui.text(this, text, 16f, 0xFFF1F5F9.toInt(), bold = true)

    private fun weightParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, Ui.dp(this, 42f), 1f)

    private fun field(hint: String): EditText = EditText(this).apply {
        this.hint = hint
        setTextColor(Color.WHITE)
        setHintTextColor(0xFF9FB2C2.toInt())
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_TEXT
        background = Ui.card(this@VpsGuideActivity).background
        setPadding(Ui.dp(this@VpsGuideActivity, 12f), Ui.dp(this@VpsGuideActivity, 10f), Ui.dp(this@VpsGuideActivity, 12f), Ui.dp(this@VpsGuideActivity, 10f))
    }

    private fun passwordField(hint: String): EditText = EditText(this).apply {
        this.hint = hint
        setTextColor(Color.WHITE)
        setHintTextColor(0xFF9FB2C2.toInt())
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        background = Ui.card(this@VpsGuideActivity).background
        setPadding(Ui.dp(this@VpsGuideActivity, 12f), Ui.dp(this@VpsGuideActivity, 10f), Ui.dp(this@VpsGuideActivity, 12f), Ui.dp(this@VpsGuideActivity, 10f))
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) tts.shutdown()
    }
}
