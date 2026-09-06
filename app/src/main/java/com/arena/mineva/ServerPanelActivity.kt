package com.arena.mineva

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.arena.mineva.assistant.TextToSpeechManager
import com.arena.mineva.server.OnDeviceServerManager
import com.arena.mineva.server.RconClient
import com.arena.mineva.server.ServerConfig
import com.arena.mineva.server.ServerTarget
import com.arena.mineva.server.SshClient
import com.arena.mineva.system.DeviceMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Professional central server panel. It works both for:
 *  - VPS: through SSH (tmux console, status, resources, backup)
 *  - On-device: through OnDeviceServerManager (start/stop/logs)
 */
class ServerPanelActivity : AppCompatActivity() {

    private val tts by lazy { TextToSpeechManager(this) }
    private lateinit var config: ServerConfig
    private lateinit var output: LinearLayout
    private lateinit var consoleInput: EditText
    private lateinit var remotePassword: EditText
    private lateinit var remoteKeyPass: EditText
    private lateinit var rconPortInput: EditText
    private lateinit var rconPasswordInput: EditText
    private lateinit var rconEnabled: CheckBox
    private var rcon: RconClient? = null
    private val onDevice = OnDeviceServerManager(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts.init { tts.speak("پنل حرفه‌ای سرور باز شد.") }

        config = ServerConfig.fromJson(AppPrefs.lastServerConfigJson) ?: ServerConfig()

        val scroll = ScrollView(this)
        val root = Ui.fill(this)
        scroll.addView(root)
        setContentView(scroll)

        root.addView(Ui.text(this, "🛠 پنل حرفه‌ای سرور", 24f, 0xFF2E70B8.toInt(), bold = true))
        root.addView(
            Ui.text(
                this,
                "${config.target} | ${config.edition} ${config.version} | پورت ${config.port}",
                13f,
                0xFF9FB2C2.toInt()
            )
        )

        // -------------------- status & remote credentials
        if (config.target == ServerTarget.VPS && config.host.isNotBlank()) {
            root.addView(section("اتصال SSH"))
            remotePassword = field("رمز SSH (برای sudo کنسول)")
            remoteKeyPass = field("رمز کلید SSH")
            root.addView(remotePassword)
            root.addView(remoteKeyPass)
        }

        root.addView(section("وضعیت و کنترل"))
        val statusRow = Ui.horizontal(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        statusRow.addView(Ui.button(this, "🔍 وضعیت", 0xFF2E70B8.toInt(), 44f) {
            refreshStatus()
        }, weightParams())
        statusRow.addView(Ui.button(this, "▶ شروع", 0xFF35D07F.toInt(), 44f) {
            startServer()
        }, weightParams())
        statusRow.addView(Ui.button(this, "⏹ توقف", 0xFFFF5A5A.toInt(), 44f) {
            stopServer()
        }, weightParams())
        root.addView(statusRow)

        root.addView(section("ارسال واقعی فرمان (RCON)"))
        root.addView(Ui.text(this, "وقتی سرور RCON فعال باشد، فرمان‌ها مستقیم به موتور سرور ارسال می‌شوند (برای Bedrock و Java).", 12f, 0xFF9FB2C2.toInt()))
        rconEnabled = CheckBox(this).apply {
            text = "فعال‌کردن ارسال با RCON"
            isChecked = AppPrefs.rconEnabled
            setTextColor(Color.WHITE)
            setOnCheckedChangeListener { _, checked -> AppPrefs.rconEnabled = checked }
        }
        root.addView(rconEnabled)
        rconPortInput = field("پورت RCON (پیش‌فرض ${AppPrefs.rconPort})")
        rconPortInput.setInputType(InputType.TYPE_CLASS_NUMBER)
        root.addView(rconPortInput)
        rconPasswordInput = field("رمز RCON")
        rconPasswordInput.setText(AppPrefs.rconPassword)
        root.addView(rconPasswordInput)
        root.addView(
            Ui.button(this, "🔌 تست/اتصال RCON", 0xFF7D4DB1.toInt(), 44f) {
                testRcon()
            }
        )

        root.addView(section("کنسول سرور"))
        consoleInput = field("فرمان مثل: say سلام  یا  /tp  یا  stop")
        root.addView(consoleInput)
        root.addView(
            Ui.button(this, "📨 ارسال به کنسول", 0xFF35D07F.toInt(), 46f) {
                sendConsole()
            }
        )
        root.addView(
            Ui.button(this, "📺 نمایش خروجی/لاگ", 0xFF2E9BFF.toInt(), 46f) {
                readConsole()
            }
        )

        root.addView(section("منابع و مدیریت"))
        root.addView(
            Ui.button(this, "💾 منابع دستگاه", 0xFF1F8F8F.toInt(), 46f) {
                showResources()
            }
        )
        root.addView(
            Ui.button(this, "📦 بکاپ دنیا (VPS)", 0xFFC97C22.toInt(), 46f) {
                backupWorld()
            }
        )
        root.addView(
            Ui.button(this, "🌐 لاگ و کنسول ابری", 0xFF2E9BFF.toInt(), 46f) {
                startActivity(Intent(this, CloudLogActivity::class.java))
            }
        )
        root.addView(
            Ui.button(this, "📦 مدیریت پکیج‌ها", 0xFF1F8F8F.toInt(), 46f) {
                startActivity(Intent(this, PackageManagerActivity::class.java))
            }
        )
        root.addView(
            Ui.button(this, "🗂 مدیریت سرور (قبل)", 0xFF2E70B8.toInt(), 46f) {
                startActivity(Intent(this, ServerManagerActivity::class.java))
            }
        )

        root.addView(section("خروجی"))
        output = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(output)

        showServerInfo()
        refreshStatus()
    }

    // -------------------------------- sections / helpers
    private fun section(text: String) =
        Ui.text(this, text, 17f, 0xFFF1F5F9.toInt(), bold = true)

    private fun weightParams() =
        LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f)

    private fun field(hint: String) = EditText(this).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        this.hint = hint
        setTextColor(Color.WHITE)
        setHintTextColor(0xFF9FB2C2.toInt())
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_TEXT
        background = Ui.card(this@ServerPanelActivity).background
        setPadding(Ui.dp(this@ServerPanelActivity, 12f), Ui.dp(this@ServerPanelActivity, 10f), Ui.dp(this@ServerPanelActivity, 12f), Ui.dp(this@ServerPanelActivity, 10f))
    }

    private fun showServerInfo() {
        output.addView(Ui.text(this, "رم سرور: ${config.memoryMb}MB | بازیکن: ${config.maxPlayers}", 13f, 0xFFD8E3EC.toInt()))
    }

    private fun refreshStatus() {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = when (config.target) {
                ServerTarget.VPS -> vpsCommand("systemctl is-active MineAva-server 2>/dev/null; tmux has-session -t MineAvaServer 2>/dev/null && echo TMUX-RUNNING || echo TMUX-STOPPED")
                ServerTarget.LOCAL -> onDevice.status()
            }
            show("📊 وضعیت", result)
        }
    }

    private fun startServer() {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = when (config.target) {
                ServerTarget.VPS -> vpsCommand("systemctl start MineAva-server 2>/dev/null; tmux has-session -t MineAvaServer || (cd \$HOME/minecraft-server && tmux new-session -d -s MineAvaServer ./run.sh); echo STARTED")
                ServerTarget.LOCAL -> onDevice.start(config)
            }
            show("▶ شروع", result)
            tts.speak(if (!result.contains("خطا")) "سرور روشن شد." else "سرور روشن نشد.")
        }
    }

    private fun stopServer() {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = when (config.target) {
                ServerTarget.VPS -> vpsCommand("tmux send-keys -t MineAvaServer 'stop' Enter 2>/dev/null; sleep 5; tmux kill-session -t MineAvaServer 2>/dev/null; echo STOPPED")
                ServerTarget.LOCAL -> onDevice.stop()
            }
            show("⏹ توقف", result)
            tts.speak(if (!result.contains("خطا")) "خاموش شد." else "خاموش نشد.")
        }
    }

    private fun sendConsole() {
        val cmd = consoleInput.text.toString().trim()
        if (cmd.isBlank()) {
            tts.speak("فرمان خالی است.")
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val result = if (AppPrefs.rconEnabled) {
                sendViaRcon(cmd)
            } else {
                val quoted = "'" + cmd.replace("'", "'\\''") + "'"
                when (config.target) {
                    ServerTarget.VPS -> vpsCommand("tmux send-keys -t MineAvaServer $quoted Enter && echo SENT")
                    ServerTarget.LOCAL -> {
                        // In on-device mode commands can be appended to the process stdin in a later
                        // build; for now record them in the log.
                        "ON-DEVICE: command $cmd queued; direct stdin attached in future build."
                    }
                }
            }
            show("📨", result)
            tts.speak(if (!result.contains("خطا")) "ارسال شد." else "ارسال نشد.")
        }
    }

    private fun testRcon() {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = sendViaRcon("list")
            show("🔌 RCON", result)
            tts.speak(if (!result.contains("خطا")) "اتصال RCON برقرار شد." else "اتصال RCON برقرار نشد.")
        }
    }

    /** Real server-side command execution through the RCON protocol. */
    private fun sendViaRcon(command: String): String {
        return runCatching {
            val conn = rcon ?: RconClient().also { rcon = it }
            val port = rconPortInput.text.toString().toIntOrNull() ?: AppPrefs.rconPort
            val password = rconPasswordInput.text.toString().ifBlank { AppPrefs.rconPassword }
            if (!conn.isConnected()) {
                val host = if (config.target == ServerTarget.VPS) config.host else "127.0.0.1"
                val auth = conn.connect(host, port, password)
                if (!auth.success) return "خطا: ${auth.message}"
            }
            val res = conn.command(command)
            if (res.success) "RCON OK: ${res.message.ifBlank { "پاسخ خالی" }}" else "خطا: ${res.message}"
        }.getOrElse { e -> "خطا: ${e.message}" }
    }

    private fun readConsole() {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = when (config.target) {
                ServerTarget.VPS -> vpsCommand("tmux capture-pane -t MineAvaServer -p | tail -n 50")
                ServerTarget.LOCAL -> onDevice.logs(50)
            }
            show("📺 لاگ", result)
        }
    }

    private fun showResources() {
        lifecycleScope.launch(Dispatchers.IO) {
            val local = DeviceMonitor.snapshot(this@ServerPanelActivity)
            val vps = if (config.target == ServerTarget.VPS) vpsCommand("free -m | head -2; df -h | head -5; uptime") else ""
            withContext(Dispatchers.Main) {
                output.removeAllViews()
                output.addView(Ui.text(this@ServerPanelActivity, "دستگاه: رم آزاد ${local.freeRamMb}/${local.totalRamMb}MB | حافظه ${local.freeStorageMb}MB | دما ${if (local.temperatureC > 0) "${local.temperatureC}°C" else "؟"} | CPU ${local.cpuLoadPercent}%", 12f, 0xFF35D07F.toInt()))
                if (vps.isNotBlank() && config.target == ServerTarget.VPS) {
                    output.addView(Ui.text(this@ServerPanelActivity, "سرور VPS:\n$vps", 12f, 0xFFD8E3EC.toInt()))
                }
                tts.speak("منابع آماده است.")
            }
        }
    }

    private fun backupWorld() {
        if (config.target != ServerTarget.VPS) {
            show("📦", "بکاپ دنیا فقط برای VPS قابل اجراست؛ روی گوشی بعداً به مدیر محلی متصل میشود.")
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val result = vpsCommand(
                "mkdir -p ~/minecraft-server/backups && tar -czf ~/minecraft-server/backups/world-backup.tar.gz -C ~/minecraft-server world && ls -lh ~/minecraft-server/backups"
            )
            show("📦 بکاپ", result)
            tts.speak(if (!result.contains("خطا")) "بکاپ آماده شد." else "بکاپ موفق نبود.")
        }
    }

    private fun vpsCommand(command: String): String = runCatching {
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

    private fun show(label: String, text: String) {
        runOnUiThread {
            output.removeAllViews()
            output.addView(Ui.text(this, "$label", 14f, 0xFF35D07F.toInt(), bold = true))
            output.addView(Ui.text(this, text.take(1800), 12f, if (text.startsWith("خطا")) 0xFFFF5A5A.toInt() else 0xFFD8E3EC.toInt()))
            val cm = getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("mineava_server_output", text))
            tts.speak("خروجی آماده است.")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        rcon?.disconnect()
        rcon = null
        if (isFinishing) tts.shutdown()
    }
}
