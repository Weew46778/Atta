package com.arena.mineva

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.arena.mineva.assistant.TextToSpeechManager
import com.arena.mineva.server.ServerConfig
import com.arena.mineva.server.ServerRecipeGenerator
import com.arena.mineva.server.ServerTarget
import com.arena.mineva.server.SshClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ServerManagerActivity : AppCompatActivity() {

    private val tts by lazy { TextToSpeechManager(this) }
    private lateinit var remotePassword: EditText
    private lateinit var remoteKeyPass: EditText
    private lateinit var consoleInput: EditText
    private lateinit var consoleOutput: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts.init { tts.speak("مدیریت سرور. وضعیت فعلی را نشان میدهم.") }

        val root = Ui.fill(this)
        root.addView(Ui.text(this, "⚙️ مدیریت سرور", 22f, 0xFF2E70B8.toInt(), bold = true))

        val current = ServerConfig.fromJson(AppPrefs.lastServerConfigJson)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(body)
        if (current == null) {
            body.addView(Ui.text(this, "هنوز سروری ساخته نشده است.", 14f, 0xFF9FB2C2.toInt()))
        } else {
            body.addView(Ui.text(this, "هدف: ${current.target}", 16f, Color.WHITE))
            body.addView(Ui.text(this, "نوع: ${current.edition} — ${current.version}", 14f))
            body.addView(Ui.text(this, "پورت: ${current.port} | بازیکن: ${current.maxPlayers}", 14f))
            body.addView(Ui.text(this, "رم: ${current.memoryMb} MB", 14f))
            body.addView(Ui.text(this, "جاوا: ${if (current.allowJavaClients) "مجاز" else "غیرمجاز"} | بدراک: ${if (current.allowBedrockClients) "مجاز" else "غیرمجاز"}", 14f))
        }

        root.addView(
            Ui.button(this, "📋 کپی آخرین دستور", 0xFF2E70B8.toInt(), 48f) {
                current?.let { config ->
                    val recipe = ServerRecipeGenerator.generate(this, config)
                    val cm = getSystemService(ClipboardManager::class.java)
                    cm.setPrimaryClip(ClipData.newPlainText("mineava_server_recipe", recipe.script))
                    tts.speak("آخرین دستور سرور کپی شد.")
                } ?: tts.speak("هنوز سروری نساختهاند.")
            }
        )
        val output = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (current?.target == ServerTarget.VPS && current.host.isNotBlank()) {
            remotePassword = EditText(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                hint = "رمز عبور SSH (اگر با رمز وصل میشوی)"
                setTextColor(Color.WHITE)
                setHintTextColor(0xFF9FB2C2.toInt())
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                background = Ui.card(this@ServerManagerActivity).background
                setPadding(Ui.dp(this@ServerManagerActivity, 12f), Ui.dp(this@ServerManagerActivity, 10f), Ui.dp(this@ServerManagerActivity, 12f), Ui.dp(this@ServerManagerActivity, 10f))
            }
            remoteKeyPass = EditText(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                hint = "رمز کلید SSH (اگر کلید encrypted است)"
                setTextColor(Color.WHITE)
                setHintTextColor(0xFF9FB2C2.toInt())
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                background = Ui.card(this@ServerManagerActivity).background
                setPadding(Ui.dp(this@ServerManagerActivity, 12f), Ui.dp(this@ServerManagerActivity, 10f), Ui.dp(this@ServerManagerActivity, 12f), Ui.dp(this@ServerManagerActivity, 10f))
            }
            root.addView(remotePassword)
            root.addView(remoteKeyPass)
            root.addView(
                Ui.button(this, "🔍 وضعیت سرویس (SSH)", 0xFF2E70B8.toInt(), 48f) {
                    vpsCommand(current, "systemctl status MineAva-server --no-pager --lines=30 || true", output)
                }
            )
            root.addView(
                Ui.button(this, "🔄 ریاستارت سرویس (با کلید SSH)", 0xFFC97C22.toInt(), 48f) {
                    vpsCommand(current, "systemctl restart MineAva-server && systemctl status MineAva-server --no-pager --lines=20", output)
                }
            )
            root.addView(
                Ui.button(this, "📜 ۳۰ خط آخر لاگ", 0xFF1F8F8F.toInt(), 48f) {
                    vpsCommand(current, "journalctl -u MineAva-server -n 30 --no-pager || true", output)
                }
            )
            root.addView(
                Ui.button(this, "📦 بکاپ دنیای سرور", 0xFFC97C22.toInt(), 48f) {
                    vpsCommand(
                        current,
                        "mkdir -p ~/minecraft-server/backups && tar -czf ~/minecraft-server/backups/world-\$(date +%Y%m%d-%H%M%S).tar.gz -C ~/minecraft-server world && ls -lh ~/minecraft-server/backups",
                        output
                    )
                }
            )
            root.addView(
                Ui.button(this, "📈 آمار VPS (رم/دیسک/آپتایم)", 0xFF2E9BFF.toInt(), 48f) {
                    vpsCommand(current, "free -m && echo '---' && df -h && echo '---' && uptime", output)
                }
            )
            root.addView(Ui.text(this, "اگر کلید SSH ذخیره شده باشد، این دستورها مستقیم کار میکنند. برای ریاستارت با رمز عبور، از ویزارد ساخت سرور دوباره استفاده کن.", 12f, 0xFF9FB2C2.toInt()))
        } else {
            root.addView(
                Ui.button(this, "▶ شروع سرور (پیشخوان)", 0xFF35D07F.toInt(), 48f) {
                    tts.speak("اجرای مستقیم سرور روی گوشی در نسخه بعدی به این دکمه متصل میشود.")
                }
            )
            root.addView(
                Ui.button(this, "⏹ توقف سرور", 0xFFFF5A5A.toInt(), 48f) {
                    tts.speak("در این نسخه سرور بهصورت پیشخوان اجرا نمیشود.")
                }
            )
        }
        root.addView(output)

        if (current?.target == ServerTarget.VPS && current.host.isNotBlank()) {
            root.addView(Ui.text(this, "🎮 کنسول سرور", 17f, 0xFFF1F5F9.toInt(), bold = true))
            consoleInput = EditText(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                hint = "مثلاً: say hello  یا  /tp  یا  stop  یا  start"
                setTextColor(Color.WHITE)
                setHintTextColor(0xFF9FB2C2.toInt())
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_TEXT
                background = Ui.card(this@ServerManagerActivity).background
                setPadding(Ui.dp(this@ServerManagerActivity, 12f), Ui.dp(this@ServerManagerActivity, 10f), Ui.dp(this@ServerManagerActivity, 12f), Ui.dp(this@ServerManagerActivity, 10f))
            }
            root.addView(consoleInput)
            root.addView(
                Ui.button(this, "📨 ارسال دستور به کنسول", 0xFF35D07F.toInt(), 48f) {
                    val cmd = consoleInput.text.toString().trim()
                    if (cmd.isBlank()) {
                        tts.speak("دستوری برای ارسال ننوشته‌ای.")
                        return@Ui.button
                    }
                    consoleOutput.removeAllViews()
                    vpsCommand(current, "systemctl is-active MineAva-server 2>/dev/null || true", consoleOutput, "ابتدا وضعیت")
                    vpsCommand(current, "sudo -n systemctl is-active MineAva-server || true", consoleOutput, "وضعیت با sudo", clear = false)
                    vpsCommand(
                        current,
                        "mkdir -p ~/minecraft-server && cd ~/minecraft-server && echo \"$cmd\" >> console.log && ls -lh server.jar run.sh 2>/dev/null || true && tail -5 console.log",
                        consoleOutput,
                        "ارسال",
                        clear = false
                    )
                }
            )
            consoleOutput = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            root.addView(consoleOutput)
            root.addView(Ui.text(this, "🔹 توجه: این نسخه درخواست را به لاگ/وضعیت سرویس می‌فرستد. اتصال به STDIN واقعی سرور (سمت استاندارد `screen`/`tmux`) در نسخه بعدی اضافه می‌شود؛ برای آن، سرور باید روی `screen` اجرا شود.", 11f, 0xFFC97C22.toInt()))
        }

        setContentView(root)
    }

    private fun vpsCommand(
        config: ServerConfig,
        command: String,
        output: LinearLayout,
        label: String = "در حال اجرا",
        clear: Boolean = true
    ) {
        if (clear) output.removeAllViews()
        output.addView(Ui.text(this, "$label: $command", 13f, 0xFF9FB2C2.toInt()))
        tts.speak("در حال اتصال به سرور و اجرای دستور.")
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val ssh = SshClient()
                val session = ssh.connect(
                    host = config.host,
                    user = config.user,
                    password = remotePassword.text.toString().ifBlank { null },
                    keyPath = config.sshKeyPath.ifBlank { null },
                    keyPassphrase = remoteKeyPass.text.toString().ifBlank { null },
                    port = config.sshPort
                )
                val out = ssh.exec(session, command).output
                session.disconnect()
                out
            }.getOrElse { e -> "خطا: ${e.message}" }
            withContext(Dispatchers.Main) {
                output.addView(
                    Ui.text(
                        this@ServerManagerActivity,
                        result.take(1500),
                        12f,
                        if (result.startsWith("خطا")) 0xFFFF5A5A.toInt() else 0xFF35D07F.toInt()
                    )
                )
                tts.speak("خروجی آماده است.")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) tts.shutdown()
    }
}
