package com.arena.mineva

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
import com.arena.mineva.server.ServerConfig
import com.arena.mineva.server.ServerProfileStore
import com.arena.mineva.server.ServerTarget
import com.arena.mineva.server.ServerWatchdog
import com.arena.mineva.service.ServerWatchdogService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Watchdog screen: periodically checks the active server and auto-restarts it when it
 * crashes or goes down. Works for local Bedrock/Java and for VPS via SSH.
 */
class ServerWatchdogActivity : AppCompatActivity() {

    private val tts by lazy { TextToSpeechManager(this) }
    private val watchdog by lazy { ServerWatchdog(this) }
    private lateinit var output: LinearLayout
    private lateinit var autoRestart: CheckBox
    private lateinit var backgroundCheck: CheckBox
    private lateinit var intervalInput: EditText
    private lateinit var sshPassword: EditText
    private lateinit var sshKeyPass: EditText
    private var job: Job? = null
    private var restartCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts.init { tts.speak("پایش و ریاستارت خودکار سرور.") }

        val config = ServerProfileStore.activeConfig() ?: ServerConfig()

        val scroll = ScrollView(this)
        val root = Ui.fill(this)
        scroll.addView(root)
        setContentView(scroll)

        root.addView(Ui.text(this, "🛡 پایش و ریاستارت خودکار", 22f, 0xFF35D07F.toInt(), bold = true))
        root.addView(
            Ui.text(
                this,
                "${config.target} | ${config.edition} ${config.version} | پورت ${config.port}" +
                    (if (config.target == ServerTarget.VPS && config.host.isNotBlank()) " | ${config.host}" else ""),
                12f,
                0xFF9FB2C2.toInt()
            )
        )

        autoRestart = CheckBox(this).apply {
            text = "🔄 ریاستارت خودکار هنگام کرش/رفت بالا"
            isChecked = AppPrefs.autoRestartEnabled
            setTextColor(Color.WHITE)
            setOnCheckedChangeListener { _, checked -> AppPrefs.autoRestartEnabled = checked }
        }
        root.addView(autoRestart)

        intervalInput = EditText(this).apply {
            hint = "فاصله بررسی (ثانیه، پیش‌فرض 8)"
            setText("${AppPrefs.watchdogIntervalSec}")
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF9FB2C2.toInt())
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_NUMBER
            background = Ui.card(this@ServerWatchdogActivity).background
            setPadding(Ui.dp(this@ServerWatchdogActivity, 12f), Ui.dp(this@ServerWatchdogActivity, 10f), Ui.dp(this@ServerWatchdogActivity, 12f), Ui.dp(this@ServerWatchdogActivity, 10f))
        }
        root.addView(intervalInput)

        backgroundCheck = CheckBox(this).apply {
            text = "📲 اجرا در پس‌زمینه (حتی وقتی اپ بسته است)"
            isChecked = AppPrefs.watchdogServiceEnabled
            setTextColor(Color.WHITE)
            setOnCheckedChangeListener { _, checked ->
                AppPrefs.watchdogServiceEnabled = checked
                if (checked) {
                    ServerWatchdogService.start(this@ServerWatchdogActivity)
                    output.addView(Ui.text(this@ServerWatchdogActivity, "پایش پس‌زمینه شروع شد.", 13f, 0xFF35D07F.toInt()))
                    tts.speak("پایش پس‌زمینه فعال شد.")
                } else {
                    ServerWatchdogService.stop(this@ServerWatchdogActivity)
                    output.addView(Ui.text(this@ServerWatchdogActivity, "پایش پس‌زمینه متوقف شد.", 13f, 0xFFC97C22.toInt()))
                    tts.speak("پایش پس‌زمینه غیرفعال شد.")
                }
            }
        }
        root.addView(backgroundCheck)

        if (config.target == ServerTarget.VPS && config.host.isNotBlank()) {
            sshPassword = passwordField("رمز SSH (اختیاری)")
            sshKeyPass = passwordField("رمز کلید SSH (اختیاری)")
            root.addView(sshPassword)
            root.addView(sshKeyPass)
        }

        root.addView(Ui.button(this, "▶ شروع پایش", 0xFF35D07F.toInt(), 48f) {
            startWatch()
        })
        root.addView(Ui.button(this, "⏹ توقف پایش", 0xFFFF5A5A.toInt(), 48f) {
            stopWatch()
        })
        root.addView(Ui.button(this, "🔍 بررسی یک‌بار", 0xFF2E70B8.toInt(), 48f) {
            checkOnce()
        })
        root.addView(Ui.button(this, "🧾 لاگ رویدادها", 0xFF1F8F8F.toInt(), 48f) {
            showLog()
        })
        root.addView(Ui.button(this, "🧺 پاک کردن لاگ", 0xFFC97C22.toInt(), 48f) {
            watchdog.clearLog()
            output.addView(Ui.text(this, "لاگ پاک شد.", 13f, 0xFF35D07F.toInt()))
        })

        output = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(Ui.text(this, "رویدادها:", 15f, 0xFFF1F5F9.toInt(), bold = true))
        root.addView(output)

        showLog()
    }

    override fun onDestroy() {
        super.onDestroy()
        job?.cancel()
        if (isFinishing) tts.shutdown()
    }

    private fun startWatch() {
        val seconds = intervalInput.text.toString().toIntOrNull()?.coerceIn(3, 300) ?: 8
        AppPrefs.watchdogIntervalSec = seconds
        stopWatch()
        output.addView(Ui.text(this, "پایش شروع شد: هر ${seconds} ثانیه.", 13f, 0xFF35D07F.toInt()))
        tts.speak("پایش خودکار سرور شروع شد.")
        val pw = if (this::sshPassword.isInitialized) sshPassword.text.toString().ifBlank { null } else null
        val keyPass = if (this::sshKeyPass.isInitialized) sshKeyPass.text.toString().ifBlank { null } else null
        val auto = autoRestart.isChecked
        val cfg = ServerProfileStore.activeConfig()
        job = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                val result = runCheck(cfg, auto, pw, keyPass)
                if (result.restarted) {
                    restartCount++
                    withContext(Dispatchers.Main) {
                        append("🛡 ریاستارت #$restartCount: ${result.message.take(180)}", 0xFFFF5A5A.toInt())
                        tts.speak("سرور پایین بود و دوباره روشن کردم.")
                    }
                } else if (result.crashed) {
                    withContext(Dispatchers.Main) {
                        append("⚠ کرش تشخیص داده شد: ${result.status}", 0xFFC97C22.toInt())
                    }
                }
                delay(seconds * 1000L)
            }
        }
    }

    private fun stopWatch() {
        val wasRunning = job != null
        job?.cancel()
        job = null
        if (wasRunning) {
            output.addView(Ui.text(this, "پایش متوقف شد.", 13f, 0xFFC97C22.toInt()))
            tts.speak("پایش متوقف شد.")
        }
    }

    private fun checkOnce() {
        val pw = if (this::sshPassword.isInitialized) sshPassword.text.toString().ifBlank { null } else null
        val keyPass = if (this::sshKeyPass.isInitialized) sshKeyPass.text.toString().ifBlank { null } else null
        val auto = autoRestart.isChecked
        val cfg = ServerProfileStore.activeConfig()
        lifecycleScope.launch(Dispatchers.IO) {
            val result = runCheck(cfg, auto, pw, keyPass)
            withContext(Dispatchers.Main) {
                append(
                    "یک‌بار بررسی: ${result.status}\n${result.message.take(240)}",
                    if (result.restarted) 0xFFFF5A5A.toInt() else 0xFF35D07F.toInt()
                )
                tts.speak(if (result.restarted) "سرور ریاستارت شد." else "بررسی انجام شد.")
            }
        }
    }

    /** Runs on IO. Call from IO scope with values captured on the main thread. */
    private fun runCheck(
        config: ServerConfig?,
        auto: Boolean,
        pw: String?,
        keyPass: String?
    ): ServerWatchdog.WatchResult {
        return watchdog.checkOnce(
            config = config ?: ServerConfig(),
            autoRestart = auto,
            sshPassword = pw,
            sshKeyPass = keyPass
        )
    }

    private fun showLog() {
        output.addView(Ui.text(this, "آخرین لاگ:", 14f, 0xFF2E9BFF.toInt(), bold = true))
        val log = watchdog.logs(60)
        output.addView(Ui.text(this, log, 12f, 0xFFD8E3EC.toInt()))
    }

    private fun append(text: String, color: Int) {
        output.addView(Ui.text(this, text, 13f, color))
    }

    private fun passwordField(hint: String): EditText = EditText(this).apply {
        this.hint = hint
        setTextColor(Color.WHITE)
        setHintTextColor(0xFF9FB2C2.toInt())
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        background = Ui.card(this@ServerWatchdogActivity).background
        setPadding(Ui.dp(this@ServerWatchdogActivity, 12f), Ui.dp(this@ServerWatchdogActivity, 10f), Ui.dp(this@ServerWatchdogActivity, 12f), Ui.dp(this@ServerWatchdogActivity, 10f))
    }
}
