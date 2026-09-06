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
import com.arena.mineva.server.OnDeviceJavaServerManager
import com.arena.mineva.server.OnDeviceJavaServerProvisioner
import com.arena.mineva.server.OnDeviceServerManager
import com.arena.mineva.server.OnDeviceServerProvisioner
import com.arena.mineva.server.ServerConfig
import com.arena.mineva.server.ServerProfileStore
import com.arena.mineva.server.ServerEdition
import com.arena.mineva.server.ServerRecipeGenerator
import com.arena.mineva.server.ServerTarget
import com.arena.mineva.server.SshClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ServerManagerActivity : AppCompatActivity() {

    private val tts by lazy { TextToSpeechManager(this) }
    private val onDevice = OnDeviceServerManager(this)
    private val onDeviceJava = OnDeviceJavaServerManager(this)
    private lateinit var remotePassword: EditText
    private lateinit var remoteKeyPass: EditText
    private lateinit var output: LinearLayout
    private lateinit var consoleInput: EditText
    private lateinit var consoleOutput: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts.init { tts.speak("مدیریت سرور. وضعیت فعلی را نشان میدهم.") }

        val root = Ui.fill(this)
        root.addView(Ui.text(this, "⚙️ مدیریت سرور", 22f, 0xFF2E70B8.toInt(), bold = true))

        val current = ServerProfileStore.activeConfig()
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
        output = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
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
            // Local target: show Bedrock and/or Java controls depending on the saved edition.
            val edition = current?.edition ?: ServerEdition.BEDROCK
            val wantsJava = edition == ServerEdition.JAVA || edition == ServerEdition.HYBRID
            val wantsBedrock = edition == ServerEdition.BEDROCK || edition == ServerEdition.HYBRID

            if (wantsJava) {
                root.addView(Ui.text(this, "☕ سرور Java روی گوشی", 17f, 0xFFF1F5F9.toInt(), bold = true))
                root.addView(
                    Ui.button(this, "▶ شروع سرور Java", 0xFF35D07F.toInt(), 48f) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            val r = onDeviceJava.start(current ?: ServerConfig(target = ServerTarget.LOCAL, edition = ServerEdition.JAVA))
                            withContext(Dispatchers.Main) { output.addView(Ui.text(this@ServerManagerActivity, r, 12f, if (r.startsWith("خطا")) 0xFFFF5A5A.toInt() else 0xFF35D07F.toInt())) }
                            tts.speak(if (r.startsWith("خطا")) "سرور Java روی گوشی اجرا نشد." else "سرور Java روشن شد.")
                        }
                    }
                )
                root.addView(
                    Ui.button(this, "⏹ توقف سرور Java", 0xFFFF5A5A.toInt(), 48f) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            val r = onDeviceJava.stop()
                            withContext(Dispatchers.Main) { output.addView(Ui.text(this@ServerManagerActivity, r, 12f, 0xFFC97C22.toInt())) }
                            tts.speak("سرور Java متوقف شد.")
                        }
                    }
                )
                root.addView(
                    Ui.button(this, "🔍 وضعیت + پیش‌نیازها", 0xFF2E70B8.toInt(), 48f) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            val r = onDeviceJava.status()
                            withContext(Dispatchers.Main) { output.addView(Ui.text(this@ServerManagerActivity, r, 12f, 0xFFD8E3EC.toInt())) }
                        }
                    }
                )
                root.addView(
                    Ui.button(this, "📜 لاگ سرور Java", 0xFF1F8F8F.toInt(), 48f) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            val r = onDeviceJava.logs()
                            withContext(Dispatchers.Main) { output.addView(Ui.text(this@ServerManagerActivity, r, 12f, 0xFFD8E3EC.toInt())) }
                            tts.speak("لاگ سرور Java آماده است.")
                        }
                    }
                )
                root.addView(
                    Ui.button(this, "📂 نصب JRE موبایل (ZIP)", 0xFFC97C22.toInt(), 48f) {
                        pickFile(8003)
                    }
                )
                root.addView(
                    Ui.button(this, "📦 نصب server.jar سرور", 0xFF7D4DB1.toInt(), 48f) {
                        pickFile(8004)
                    }
                )
                root.addView(Ui.text(this, "JRE باید یک runtime سازگار با Android/ARM باشد و داخل ZIP فایل bin/java داشته باشد. server.jar می‌تواند vanilla/Paper/Purpur باشد.", 11f, 0xFF9FB2C2.toInt()))
            }

            if (wantsBedrock) {
                root.addView(Ui.text(this, "🧱 سرور Bedrock روی گوشی", 17f, 0xFFF1F5F9.toInt(), bold = true))
                root.addView(
                    Ui.button(this, "▶ شروع سرور Bedrock", 0xFF35D07F.toInt(), 48f) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            val r = onDevice.start(current ?: ServerConfig(target = ServerTarget.LOCAL, edition = ServerEdition.BEDROCK))
                            withContext(Dispatchers.Main) { output.addView(Ui.text(this@ServerManagerActivity, r, 12f, if (r.startsWith("خطا")) 0xFFFF5A5A.toInt() else 0xFF35D07F.toInt())) }
                            tts.speak("سرور Bedrock روی گوشی روشن شد.")
                        }
                    }
                )
                root.addView(
                    Ui.button(this, "⏹ توقف سرور Bedrock", 0xFFFF5A5A.toInt(), 48f) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            val r = onDevice.stop()
                            withContext(Dispatchers.Main) { output.addView(Ui.text(this@ServerManagerActivity, r, 12f, 0xFFC97C22.toInt())) }
                            tts.speak("سرور Bedrock متوقف شد.")
                        }
                    }
                )
                root.addView(
                    Ui.button(this, "📜 لاگ سرور Bedrock", 0xFF1F8F8F.toInt(), 48f) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            val r = onDevice.logs()
                            withContext(Dispatchers.Main) { output.addView(Ui.text(this@ServerManagerActivity, r, 12f, 0xFFD8E3EC.toInt())) }
                            tts.speak("لاگ سرور Bedrock آماده است.")
                        }
                    }
                )
                root.addView(
                    Ui.button(this, "📂 نصب باینری سرور (ZIP)", 0xFFC97C22.toInt(), 48f) {
                        pickFile(8002)
                    }
                )
            }

            root.addView(Ui.text(this, "⚠ اگر سرور LOCAL است و نسخهٔ ذخیره‌شده BEDROCK باشد، از بخش Bedrock استفاده می‌کنم؛ اگر JAVA/HYBRID باشد، بخش Java هم اضافه می‌شود.", 11f, 0xFFC97C22.toInt()))
        }
        root.addView(output)

        if (current?.target == ServerTarget.VPS && current.host.isNotBlank()) {
            root.addView(Ui.text(this, "🎮 کنسول واقعی سرور (tmux)", 17f, 0xFFF1F5F9.toInt(), bold = true))
            root.addView(Ui.text(this, "سرور باید روی سشن tmux با نام MineAvaServer اجرا شود. دستورها واقعاً به STDIN سرور ارسال میشوند.", 12f, 0xFF9FB2C2.toInt()))

            consoleInput = EditText(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                hint = "فرمان سرور، مثلاً: say hello  یا  /tp  یا  stop"
                setTextColor(Color.WHITE)
                setHintTextColor(0xFF9FB2C2.toInt())
                setSingleLine(true)
                inputType = InputType.TYPE_CLASS_TEXT
                background = Ui.card(this@ServerManagerActivity).background
                setPadding(Ui.dp(this@ServerManagerActivity, 12f), Ui.dp(this@ServerManagerActivity, 10f), Ui.dp(this@ServerManagerActivity, 12f), Ui.dp(this@ServerManagerActivity, 10f))
            }
            root.addView(consoleInput)

            root.addView(
                Ui.button(this, "📨 ارسال فرمان به کنسول", 0xFF35D07F.toInt(), 48f) {
                    val cmd = consoleInput.text.toString().trim()
                    if (cmd.isBlank()) {
                        tts.speak("فرمانی برای ارسال ننوشته‌ای.")
                        return@Ui.button
                    }
                    val quoted = shQuote(cmd)
                    vpsCommand(current, "tmux send-keys -t MineAvaServer $quoted Enter", consoleOutput, "ارسال", clear = true)
                }
            )
            root.addView(
                Ui.button(this, "📺 نمایش خروجی کنسول (آخرین ۵۰ خط)", 0xFF2E9BFF.toInt(), 48f) {
                    vpsCommand(current, "tmux capture-pane -t MineAvaServer -p | tail -n 50", consoleOutput, "خروجی کنسول", clear = true)
                }
            )
            root.addView(
                Ui.button(this, "▶ شروع سرور در tmux", 0xFF35D07F.toInt(), 48f) {
                    vpsCommand(current, "systemctl restart MineAva-server && sleep 2 && tmux has-session -t MineAvaServer && echo RUNNING", consoleOutput, "شروع سرویس", clear = true)
                }
            )
            root.addView(
                Ui.button(this, "⏹ توقف سرور", 0xFFFF5A5A.toInt(), 48f) {
                    vpsCommand(current, "tmux send-keys -t MineAvaServer 'stop' Enter 2>/dev/null || true", consoleOutput, "توقف", clear = true)
                }
            )
            root.addView(
                Ui.button(this, "🔎 وضعیت کنسول", 0xFF1F8F8F.toInt(), 48f) {
                    vpsCommand(current, "tmux has-session -t MineAvaServer 2>/dev/null && echo 'RUNNING' || echo 'STOPPED'", consoleOutput, "وضعیت", clear = true)
                }
            )
            consoleOutput = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            root.addView(consoleOutput)
            root.addView(Ui.text(this, "🔹 اگر سرور قبلاً بدون tmux نصب شده، دوباره از ویزارد ساخت سرور استفاده کن تا اسکریپت‌های tmux و سرویس به‌روز شوند.", 11f, 0xFFC97C22.toInt()))
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
                val uidResult = ssh.exec(session, "id -u").output.trim()
                val pw = remotePassword.text.toString()
                val finalCommand = when {
                    uidResult == "0" -> command
                    pw.isNotBlank() -> {
                        val esc = pw.replace("'", "'\\''")
                        val escCmd = command.replace("\"", "\\\"")
                        "printf '%s\\n' '$esc' | sudo -S -p '' bash -c \"$escCmd\" 2>&1"
                    }
                    else -> command
                }
                val out = ssh.exec(session, finalCommand).output
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

    private fun shQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun pickFile(requestCode: Int) {
        val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(intent, requestCode)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        when (requestCode) {
            8002 -> lifecycleScope.launch(Dispatchers.IO) {
                val result = OnDeviceServerProvisioner.installZip(this@ServerManagerActivity, uri)
                withContext(Dispatchers.Main) {
                    output.addView(Ui.text(this@ServerManagerActivity, result.detail, 13f, if (result.success) 0xFF35D07F.toInt() else 0xFFFF5A5A.toInt()))
                    tts.speak(if (result.success) "باینری سرور روی گوشی نصب شد." else "نصب باینری ناموفق بود.")
                }
            }
            8003 -> lifecycleScope.launch(Dispatchers.IO) {
                val result = OnDeviceJavaServerProvisioner.installJreZip(this@ServerManagerActivity, uri)
                withContext(Dispatchers.Main) {
                    output.addView(Ui.text(this@ServerManagerActivity, result.detail, 13f, if (result.success) 0xFF35D07F.toInt() else 0xFFFF5A5A.toInt()))
                    tts.speak(if (result.success) "JRE موبایل نصب شد." else "نصب JRE ناموفق بود.")
                }
            }
            8004 -> lifecycleScope.launch(Dispatchers.IO) {
                val result = OnDeviceJavaServerProvisioner.installServerJar(this@ServerManagerActivity, uri)
                withContext(Dispatchers.Main) {
                    output.addView(Ui.text(this@ServerManagerActivity, result.detail, 13f, if (result.success) 0xFF35D07F.toInt() else 0xFFFF5A5A.toInt()))
                    tts.speak(if (result.success) "server.jar نصب شد." else "نصب JAR ناموفق بود.")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) tts.shutdown()
    }
}
