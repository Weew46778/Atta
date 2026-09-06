package com.arena.mineva

import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.arena.mineva.assistant.TextToSpeechManager
import com.arena.mineva.server.OnDeviceServerManager
import com.arena.mineva.server.ServerConfig
import com.arena.mineva.server.ServerProfileStore
import com.arena.mineva.server.ServerTarget
import com.arena.mineva.server.SshClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Advanced server log / cloud console screen.
 *
 * For a VPS it uses SSH + systemd journal + the tmux session + the raw log file.
 * For the on-device server it uses OnDeviceServerManager while the process is alive.
 * Supports filtering, copying, exporting to device storage and a lightweight
 * auto-refresh poll.
 */
class CloudLogActivity : AppCompatActivity() {

    private val tts by lazy { TextToSpeechManager(this) }
    private val onDevice = OnDeviceServerManager(this)
    private lateinit var config: ServerConfig
    private lateinit var output: LinearLayout
    private lateinit var filterInput: EditText
    private lateinit var linesInput: EditText
    private lateinit var remotePassword: EditText
    private lateinit var remoteKeyPass: EditText
    private lateinit var autoRefresh: CheckBox
    private var refreshJob: Job? = null
    private var lastRaw = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts.init { tts.speak("صفحهٔ لاگ پیشرفتهٔ سرور باز شد.") }

        config = ServerProfileStore.activeConfig() ?: ServerConfig()

        val root = Ui.fill(this)
        root.addView(Ui.text(this, "🌐 لاگ و کنسول ابری سرور", 22f, 0xFF2E9BFF.toInt(), bold = true))
        root.addView(
            Ui.text(
                this,
                "${config.target} | ${config.edition} ${config.version} | پورت ${config.port}",
                13f,
                0xFF9FB2C2.toInt()
            )
        )

        if (config.target == ServerTarget.VPS && config.host.isNotBlank()) {
            root.addView(section("اتصال SSH"))
            remotePassword = field("رمز SSH (اختیاری اگر کلید دارید)")
            remoteKeyPass = field("رمز کلید SSH")
            root.addView(remotePassword)
            root.addView(remoteKeyPass)
        }

        root.addView(section("منبع لاگ"))
        if (config.target == ServerTarget.VPS) {
            root.addView(
                Ui.button(this, "📜 systemd journal", 0xFF2E70B8.toInt(), 46f) {
                    readVps("journalctl -u MineAva-server -n ${lineCount()} --no-pager 2>/dev/null || tail -n ${lineCount()} ~/minecraft-server/server.log 2>/dev/null")
                }
            )
            root.addView(
                Ui.button(this, "🛠 لاگ فایل run.sh", 0xFF1F8F8F.toInt(), 46f) {
                    readVps("ls -lh ~/minecraft-server/*.log ~/minecraft-server/logs/**/*.log 2>/dev/null; tail -n ${lineCount()} ~/minecraft-server/server.log ~/minecraft-server/nohup.out 2>/dev/null")
                }
            )
            root.addView(
                Ui.button(this, "🖥 خروجی کنسول tmux", 0xFF7D4DB1.toInt(), 46f) {
                    readVps("tmux capture-pane -t MineAvaServer -p | tail -n ${lineCount()}")
                }
            )
            root.addView(
                Ui.button(this, "📊 وضعیت + دمای دیسک", 0xFFC97C22.toInt(), 46f) {
                    readVps("systemctl is-active MineAva-server; free -m | head -2; df -h | head -5; uptime")
                }
            )
        } else {
            root.addView(
                Ui.button(this, "📜 لاگ سرور روی گوشی", 0xFF1F8F8F.toInt(), 46f) {
                    readLocal("${lineCount()}")
                }
            )
            root.addView(
                Ui.button(this, "📊 وضعیت فرایند", 0xFF2E9BFF.toInt(), 46f) {
                    readLocal("status")
                }
            )
        }

        root.addView(section("فیلتر و تعداد خط"))
        filterInput = field("کلمهٔ فیلتر، مثل ERROR یا started یا تلاش برای اتصال")
        root.addView(filterInput)
        linesInput = field("تعداد خط (مثلاً 80)")
        root.addView(linesInput)

        val options = Ui.horizontal(this)
        options.addView(
            Ui.button(this, "🔄 به‌روزرسانی", 0xFF35D07F.toInt(), 44f) {
                if (config.target == ServerTarget.VPS) readVps(currentTailCommand(), refresh = true)
                else readLocal(refresh = true)
            }, weightParams()
        )
        options.addView(
            Ui.button(this, "🧺 پاک کردن صفحه", 0xFFC97C22.toInt(), 44f) {
                output.removeAllViews()
                lastRaw = ""
                tts.speak("پاک شد.")
            }, weightParams()
        )
        options.addView(
            Ui.button(this, "📋 کپی", 0xFF2E9BFF.toInt(), 44f) {
                copy(lastRaw.ifBlank { "خروجی خالی است" })
            }, weightParams()
        )
        options.addView(
            Ui.button(this, "📤 ذخیره", 0xFF7D4DB1.toInt(), 44f) {
                saveLog(lastRaw)
            }, weightParams()
        )
        root.addView(options)

        autoRefresh = CheckBox(this).apply {
            text = "🔄 به‌روزرسانی خودکار هر ۳ ثانیه"
            setTextColor(Color.WHITE)
            setOnCheckedChangeListener { _, checked -> if (checked) startAutoRefresh() else stopAutoRefresh() }
        }
        root.addView(autoRefresh)

        root.addView(section("خروجی"))
        output = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(output)

        refreshOnce()
        setContentView(root)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoRefresh()
        if (isFinishing) tts.shutdown()
    }

    // -------------------------------- core readers
    private fun refreshOnce() {
        if (config.target == ServerTarget.VPS) {
            readVps(currentTailCommand(), refresh = false)
        } else {
            readLocal(refresh = false)
        }
    }

    private fun currentTailCommand(): String =
        if (config.target == ServerTarget.VPS) {
            "journalctl -u MineAva-server -n ${lineCount()} --no-pager 2>/dev/null || tail -n ${lineCount()} ~/minecraft-server/server.log 2>/dev/null || tmux capture-pane -t MineAvaServer -p | tail -n ${lineCount()}"
        } else {
            lineCount()
        }

    private fun readVps(command: String, refresh: Boolean = false) {
        if (config.target != ServerTarget.VPS) return
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
            renderResult(result, "VPS", refresh)
        }
    }

    private fun readLocal(kind: String, refresh: Boolean = false) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = when (kind) {
                "status" -> onDevice.status()
                else -> onDevice.logs(kind.toIntOrNull() ?: 50)
            }
            renderResult(result, "روی گوشی", refresh)
        }
    }

    private fun startAutoRefresh() {
        stopAutoRefresh()
        refreshJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(3000)
                val cmd = currentTailCommand()
                val result = if (config.target == ServerTarget.VPS) {
                    runCatching {
                        val ssh = SshClient()
                        val session = ssh.connect(
                            host = config.host,
                            user = config.user,
                            password = remotePassword.text.toString().ifBlank { null },
                            keyPath = config.sshKeyPath.ifBlank { null },
                            keyPassphrase = remoteKeyPass.text.toString().ifBlank { null },
                            port = config.sshPort
                        )
                        val out = ssh.exec(session, cmd).output
                        session.disconnect()
                        out
                    }.getOrElse { e -> "خطا: ${e.message}" }
                } else {
                    onDevice.logs(cmd.toIntOrNull() ?: 50)
                }
                renderResult(result, "خودکار", refresh = true)
            }
        }
    }

    private fun stopAutoRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }

    // -------------------------------- helpers
    private fun lineCount(): Int = linesInput.text.toString().toIntOrNull()?.coerceIn(5, 500) ?: 80

    private fun applyFilter(raw: String): String {
        val needle = filterInput.text.toString().trim()
        if (needle.isBlank()) return raw
        val lines = raw.lines()
        val hits = lines.filter { it.contains(needle, ignoreCase = true) }
        return when {
            hits.isNotEmpty() -> hits.joinToString("\n")
            else -> lines.takeLast((lineCount() * 0.35).toInt().coerceAtLeast(5)).joinToString("\n")
        }
    }

    private fun renderResult(raw: String, label: String, refresh: Boolean) {
        val filtered = applyFilter(raw)
        withContext(Dispatchers.Main) {
            lastRaw = filtered
            // On manual refresh replace; on auto-refresh append but keep a bounded view.
            if (!refresh || output.childCount == 0 || output.childCount > 24) output.removeAllViews()
            output.addView(
                Ui.text(this@CloudLogActivity, "$label — ${System.currentTimeMillis()}", 12f, 0xFF35D07F.toInt())
            )
            output.addView(
                Ui.text(
                    this@CloudLogActivity,
                    filtered.take(6000),
                    11f,
                    if (filtered.startsWith("خطا")) 0xFFFF5A5A.toInt() else 0xFFD8E3EC.toInt()
                )
            )
        }
    }

    private fun copy(text: String) {
        val cm = getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("mineava_server_log", text))
        tts.speak("لاگ کپی شد.")
    }

    private fun saveLog(text: String) {
        runCatching {
            val dir = File(getExternalFilesDir(null) ?: filesDir, "logs")
            dir.mkdirs()
            val file = File(dir, "mineava-server-log-${System.currentTimeMillis()}.txt")
            file.writeText(text)
            output.addView(Ui.text(this, "ذخیره شد: ${file.absolutePath}", 12f, 0xFF35D07F.toInt()))
            tts.speak("لاگ ذخیره شد.")
        }.onFailure {
            output.addView(Ui.text(this, "خطا در ذخیره: ${it.message}", 12f, 0xFFFF5A5A.toInt()))
        }
    }

    private fun section(text: String) =
        Ui.text(this, text, 17f, 0xFFF1F5F9.toInt(), bold = true)

    private fun weightParams() =
        LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f)

    private fun field(hint: String) = EditText(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        this.hint = hint
        setTextColor(Color.WHITE)
        setHintTextColor(0xFF9FB2C2.toInt())
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_TEXT
        background = Ui.card(this@CloudLogActivity).background
        setPadding(
            Ui.dp(this@CloudLogActivity, 12f),
            Ui.dp(this@CloudLogActivity, 10f),
            Ui.dp(this@CloudLogActivity, 12f),
            Ui.dp(this@CloudLogActivity, 10f)
        )
    }
}
