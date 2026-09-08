package com.arena.mineva.service

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.arena.mineva.AppPrefs
import com.arena.mineva.MainActivity
import com.arena.mineva.Ui
import com.arena.mineva.assistant.BedrockGameDetector
import com.arena.mineva.assistant.TextToSpeechManager
import com.arena.mineva.server.BedrockCommandBuilder
import com.arena.mineva.server.InWorldAvaBuilder
import com.arena.mineva.server.RconClient
import com.arena.mineva.server.ServerConfig
import com.arena.mineva.server.ServerTarget
import com.arena.mineva.server.SshClient
import com.arena.mineva.system.DeviceMonitor
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * In-game assistant overlay.
 *
 * It only runs while Minecraft (or MineAva itself) is actually in the foreground, shows a
 * small round floating button that never covers more than a tiny corner, and always has a
 * visible "✕ بستن اورلای" button that fully stops the service and removes every view.
 */
class OverlayService : Service() {

    companion object {
        private val GAME_PACKAGES = arrayOf(
            "com.mojang.minecraftpe",
            "com.mojang.minecraft",
            "net.kdt.pojavlaunch"
        )
        private const val CHECK_MS = 1800L
    }

    private lateinit var windowManager: WindowManager
    private lateinit var tts: TextToSpeechManager
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Job() + Dispatchers.Default)
    private val autoHide = Runnable { if (opened) collapsePanel() }

    private var opened = false
    private var selectedTab = -1

    private lateinit var handleView: TextView
    private lateinit var panelView: FrameLayout
    private lateinit var tabsColumn: LinearLayout
    private lateinit var pageContainer: FrameLayout
    private lateinit var tabButtons: MutableList<TextView>
    private lateinit var pages: MutableList<ScrollView>

    private var handleParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var handleAdded = false
    private var panelAdded = false

    private data class TabSpec(val title: String, val color: Int, val build: (ViewGroup) -> Unit)

    private val tabs = listOf(
        TabSpec("اپراتور", 0xFFE0483C.toInt()) { c -> buildOperatorTab(c) },
        TabSpec("ابزار", 0xFF2E9B5B.toInt()) { c -> buildToolsTab(c) },
        TabSpec("کد/سازه", 0xFF2E70B8.toInt()) { c -> buildStructuresTab(c) },
        TabSpec("سلاح", 0xFFC97C22.toInt()) { c -> buildWeaponsTab(c) },
        TabSpec("آوا", 0xFF7D4DB1.toInt()) { c -> buildAssistantTab(c) },
        TabSpec("سلامت", 0xFF1F8F8F.toInt()) { c -> buildHealthTab(c) }
    )

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        tts = TextToSpeechManager(this)
        tts.init {
            // Do not speak when the service starts in the background.
        }
        createNotificationChannel()
        startForeground(1, buildNotification())
        setupOverlay()
        startForegroundChecker()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------------------------------------------------------------- lifecycle

    private fun startForegroundChecker() {
        scope.launch {
            while (isActive) {
                if (shouldStayRunning()) {
                    runOnMain { addHandle() }
                } else {
                    runOnMain { closeOverlay() }
                    break
                }
                delay(CHECK_MS)
            }
        }
    }

    private fun shouldStayRunning(): Boolean {
        return runCatching {
            val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
            val processes = am.runningAppProcesses.orEmpty()
            val anyForeground = processes.any { p ->
                p.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
                    p.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
            }
            if (!anyForeground) return false
            processes.any { p ->
                val name = p.processName ?: return@any false
                val important = p.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
                    p.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
                important && (
                    name == packageName || name == "${packageName}:overlay" ||
                        GAME_PACKAGES.any { name.startsWith(it, ignoreCase = true) } ||
                        name.contains("minecraft", ignoreCase = true)
                    )
            }
        }.getOrDefault(false)
    }

    private fun runOnMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else handler.post(action)
    }

    // ---------------------------------------------------------------- overlay

    private fun setupOverlay() {
        buildHandle()
        buildPanel()
        showHandle()
    }

    private fun addHandle() {
        if (handleAdded) return
        handleView = TextView(this).apply {
            text = "◉"
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
            setBackgroundColor(0xE035D07F.toInt())
        }
        handleParams = WindowManager.LayoutParams(
            Ui.dp(this, 52f),
            Ui.dp(this, 52f),
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            x = Ui.dp(this@OverlayService, 6f)
            y = 0
        }
        windowManager.addView(handleView, handleParams)
        handleAdded = true
        handleView.setOnClickListener {
            vibrate(30)
            openPanel()
        }
    }

    private fun removeHandle() {
        runCatching { if (handleAdded) windowManager.removeView(handleView) }
        handleAdded = false
    }

    private fun buildHandle() {
        addHandle()
    }

    private fun showHandle() {
        addHandle()
        runCatching { panelView.visibility = View.GONE }
        opened = false
    }

    private fun closeOverlay() {
        removePanel()
        removeHandle()
        stopForeground(true)
        stopSelf()
    }

    // ---------------------------------------------------------------- panel

    private fun buildPanel() {
        panelView = FrameLayout(this)
        panelView.setBackgroundColor(0xF20C1722.toInt())
        panelView.visibility = View.GONE

        val close = TextView(this).apply {
            text = "✕ بستن اورلای"
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            setBackgroundColor(0xFFE0483C.toInt())
            setTypeface(typeface, Typeface.BOLD)
        }
        panelView.addView(
            close,
            FrameLayout.LayoutParams(Ui.dp(this, 132f), Ui.dp(this, 42f)).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = Ui.dp(this@OverlayService, 8f)
                rightMargin = Ui.dp(this@OverlayService, 8f)
            }
        )
        close.setOnClickListener {
            openCollapseOrStop()
        }

        val header = Ui.text(this, "آوا — پنل ماینکرافت", 17f, Color.WHITE, bold = true)
        panelView.addView(
            header,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.START
                topMargin = Ui.dp(this@OverlayService, 10f)
                leftMargin = Ui.dp(this@OverlayService, 14f)
                rightMargin = Ui.dp(this@OverlayService, 148f)
            }
        )

        val body = FrameLayout(this)
        panelView.addView(
            body,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                topMargin = Ui.dp(this@OverlayService, 58f)
                bottomMargin = Ui.dp(this@OverlayService, 12f)
                leftMargin = Ui.dp(this@OverlayService, 12f)
                rightMargin = Ui.dp(this@OverlayService, 12f)
            }
        )

        tabsColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF121D29.toInt())
        }
        body.addView(tabsColumn, FrameLayout.LayoutParams(Ui.dp(this, 54f), ViewGroup.LayoutParams.MATCH_PARENT))

        pageContainer = FrameLayout(this)
        body.addView(pageContainer, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))

        tabButtons = mutableListOf()
        pages = mutableListOf()
        tabs.forEachIndexed { index, spec ->
            val btn = tabButton(spec.title, spec.color)
            btn.tag = index
            btn.setOnClickListener {
                vibrate(30)
                tts.speak(spec.title)
                selectTab(index)
                scheduleAutoHide()
            }
            tabButtons.add(btn)
            tabsColumn.addView(
                btn,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 56f))
            )

            val page = ScrollView(this).apply {
                val inner = Ui.fill(this@OverlayService)
                spec.build(inner)
                addView(
                    inner,
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                )
            }
            pages.add(page)
            pageContainer.addView(
                page,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }
    }

    private fun addPanel() {
        if (panelAdded) return
        val w = (resources.displayMetrics.widthPixels * 0.91f).toInt()
        val h = (resources.displayMetrics.heightPixels * 0.86f).toInt()
        panelParams = WindowManager.LayoutParams(
            w,
            h,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
        windowManager.addView(panelView, panelParams)
        panelAdded = true
    }

    private fun removePanel() {
        runCatching { if (panelAdded) windowManager.removeView(panelView) }
        panelAdded = false
    }

    private fun openPanel() {
        addPanel()
        opened = true
        removeHandle()
        panelView.visibility = View.VISIBLE
        if (selectedTab < 0) selectTab(0)
        scheduleAutoHide()
    }

    private fun collapsePanel() {
        opened = false
        removePanel()
        addHandle()
    }

    private fun openCollapseOrStop() {
        // First tap hides the panel, second tap fully closes the overlay.
        if (opened) {
            collapsePanel()
        } else {
            closeOverlay()
        }
    }

    private fun scheduleAutoHide() {
        handler.removeCallbacks(autoHide)
        handler.postDelayed(autoHide, 12_000L)
    }

    private fun selectTab(index: Int) {
        selectedTab = index
        pages.forEachIndexed { i, p ->
            p.visibility = if (i == index) View.VISIBLE else View.GONE
        }
        tabButtons.forEachIndexed { i, b ->
            b.alpha = if (i == index) 1f else 0.6f
        }
    }

    private fun tabButton(title: String, color: Int): TextView = TextView(this).apply {
        text = title
        setTextColor(Color.WHITE)
        textSize = 11f
        gravity = Gravity.CENTER
        setTypeface(typeface, Typeface.BOLD)
        background = GradientDrawable().apply {
            setColor(color)
            cornerRadius = Ui.dp(this@OverlayService, 10f).toFloat()
        }
    }

    // ---------------------------------------------------------------- operator

    private fun buildOperatorTab(c: ViewGroup) {
        c.addView(tabHeader("🎮 اپراتور"))
        c.addView(Ui.text(
            this,
            "دستورها از RCON یا کنسول tmux به سرور میرسند و در دنیا اعمال میشوند.",
            11f, 0xFFD8E3EC.toInt()
        ))
        val commandBox = EditText(this).apply {
            hint = "/weather rain  یا هر دستور دلخواه"
            setTextColor(Color.WHITE)
            setHintTextColor(0xFF9FB2C2.toInt())
            setSingleLine(true)
            textSize = 13f
            background = Ui.card(this@OverlayService).background
        }
        c.addView(commandBox)
        c.addView(Ui.button(this, "⚡ ارسال به کنسول سرور", 0xFFE0483C.toInt(), 46f) {
            val raw = commandBox.text.toString().trim()
            if (raw.isNotBlank()) {
                sendConsoleAction(raw)
                commandBox.setText("")
            }
        })
        c.addView(Ui.text(this, "دستورهای سریع", 15f, Color.WHITE, bold = true))
        listOf(
            "☔ باران" to "/weather rain",
            "☀️ صاف" to "/weather clear",
            "🕐 صبح" to "/time set day",
            "💎 ۶۴ الماس" to "/give @p diamond 64",
            "🥇 اپراتور من" to "/op @p",
            "🎮 خلاق" to "/gamemode creative",
            "🛠 بقا" to "/gamemode survival",
            "🩹 سلامتی" to "/effect @p minecraft:instant_health 1 10"
        ).forEach { (label, cmd) -> c.addView(commandRow(label, cmd)) }
    }

    private fun sendConsoleAction(command: String) {
        val normalized = if (command.startsWith("/")) command else "/$command"
        vibrate(25)
        copyToClipboard(normalized)
        tts.speak(
            if (trySendToServerConsole(normalized))
                "دستور به کنسول سرور ارسال شد."
            else
                "دستور در کلیپبورد کپی شد. سرور باید به RCON یا SSH وصل باشد."
        )
        scheduleAutoHide()
    }

    // ---------------------------------------------------------------- pages

    private fun buildToolsTab(c: ViewGroup) {
        c.addView(tabHeader("ابزارهای سریع"))
        c.addView(commandRow("⚔ شمشیر نتریت", BedrockCommandBuilder.giveNetheriteSword(BedrockCommandBuilder.Edition.BEDROCK)))
        c.addView(commandRow("🛡 سپر نتریت", BedrockCommandBuilder.giveProtectedChestplate(BedrockCommandBuilder.Edition.BEDROCK)))
        c.addView(commandRow("🥚 اژدها", BedrockCommandBuilder.giveSpawnEgg("ender_dragon", BedrockCommandBuilder.Edition.BEDROCK)))
        c.addView(Ui.text(this, "دستور برای سرور بدراک است؛ از RCON یا کنسول سرور اجرا میشود.", 11f, 0xFF9FB2C2.toInt()))
    }

    private fun buildStructuresTab(c: ViewGroup) {
        c.addView(tabHeader("سازه و ردستون"))
        c.addView(commandRow("کشاورزی خودکار", buildWheatFarm()))
        c.addView(commandRow("آهنخودکار", buildIronFarm()))
        c.addView(commandRow("فارم تجربه", buildXpFarm()))
        c.addView(Ui.text(this, "طرح سازه در کلیپبورد کپی میشود.", 12f, 0xFF9FB2C2.toInt()))
    }

    private fun buildWeaponsTab(c: ViewGroup) {
        c.addView(tabHeader("سفارش وسایل ویژه"))
        c.addView(commandRow("شمشیر نتریت + تیز", BedrockCommandBuilder.giveNetheriteSword(
            BedrockCommandBuilder.Edition.BEDROCK,
            enchants = listOf("sharpness", "unbreaking", "looting")
        )))
        c.addView(commandRow("کمان بیپایان", "give @p bow 1 0 {\"ench\":[{\"id\":\"infinity\",\"lvl\":1},{\"id\":\"power\",\"lvl\":10}]}"))
        c.addView(commandRow("زره محافظ", BedrockCommandBuilder.giveProtectedChestplate(BedrockCommandBuilder.Edition.BEDROCK)))
        c.addView(Ui.text(this, "برای اجرا باید اپراتور روی سرور باشی.", 12f, 0xFF9FB2C2.toInt()))
    }

    private fun buildAssistantTab(c: ViewGroup) {
        c.addView(tabHeader("دستیار آوا"))
        val snap = DeviceMonitor.snapshot(this)
        c.addView(Ui.text(this, "سلام! رم آزاد: ${snap.freeRamMb}MB. چه کاری انجام بدهم؟", 14f))
        c.addView(Ui.button(this, "🎤 گفتگوی صوتی", 0xFF7D4DB1.toInt(), 46f) {
            val i = Intent(this, com.arena.mineva.VoiceAssistantActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        })
        c.addView(Ui.button(this, "🏠 آوردن آوا داخل دنیا (NPC)", 0xFF7D4DB1.toInt(), 46f) {
            val plan = InWorldAvaBuilder.build(this)
            val result = InWorldAvaBuilder.deliverWithAppConsole(this)
            copyToClipboard(plan.commands.joinToString("\n"))
            tts.speak(if (result.contains("RCON failed") || result.contains("SSH error")) "دستورها آماده شد؛ برای ارسال مستقیم RCON یا SSH لازم است." else "دستورها ارسال شد.")
            c.addView(Ui.text(this, result.take(500), 11f, 0xFFD8E3EC.toInt()))
        })
    }

    private fun buildHealthTab(c: ViewGroup) {
        c.removeAllViews()
        c.addView(tabHeader("سلامت دستگاه"))
        val snap = DeviceMonitor.snapshot(this)
        c.addView(Ui.text(this, "رم: ${snap.freeRamMb}/${snap.totalRamMb} MB آزاد", 14f))
        c.addView(Ui.text(this, "حافظه: ${snap.freeStorageMb}/${snap.totalStorageMb} MB", 14f))
        c.addView(Ui.text(this, "دما: ${if (snap.temperatureC > 0) "${snap.temperatureC}°C" else "نامشخص"}", 14f))
        c.addView(Ui.text(this, "CPU: ${snap.cpuLoadPercent}%", 14f))
        c.addView(Ui.button(this, "🧹 پاکسازی کش", 0xFF1F8F8F.toInt(), 46f) {
            DeviceMonitor.clearAppCache(this)
            tts.speak("کش پاک شد.")
            buildHealthTab(c)
        })
    }

    private fun tabHeader(title: String): TextView = Ui.text(this, title, 18f, Color.WHITE, bold = true)

    private fun commandRow(title: String, command: String): TextView =
        Ui.text(this, "$title:\n$command", 12f, 0xFFD8E3EC.toInt()).apply {
            setOnClickListener {
                vibrate(20)
                copyToClipboard(command)
                val sent = trySendToServerConsole(command)
                tts.speak(
                    if (sent) "$title به کنسول سرور ارسال شد."
                    else "$title در کلیپبورد کپی شد."
                )
            }
        }

    private fun copyToClipboard(command: String) {
        val cm = getSystemService(android.content.ClipboardManager::class.java)
        cm.setPrimaryClip(android.content.ClipData.newPlainText("mineava", command))
    }

    private fun trySendToServerConsole(command: String): Boolean {
        val clean = if (command.startsWith("/")) command else "/$command"
        val config = ServerConfig.fromJson(AppPrefs.lastServerConfigJson)
        val rconReady = AppPrefs.rconEnabled
        val sshReady = config != null && config.target == ServerTarget.VPS &&
            config.host.isNotBlank() && config.user.isNotBlank() &&
            (config.sshKeyPath.isNotBlank() || config.sshPassword.isNotBlank())
        if (!rconReady && !sshReady) return false

        scope.launch {
            if (rconReady) {
                val host = config?.host?.ifBlank { "127.0.0.1" } ?: "127.0.0.1"
                runCatching {
                    val rcon = RconClient()
                    val auth = rcon.connect(host, AppPrefs.rconPort, AppPrefs.rconPassword, 6000)
                    if (auth.success) {
                        rcon.command(clean)
                        rcon.disconnect()
                        return@launch
                    }
                }
            }
            if (sshReady && config != null) {
                runCatching {
                    val ssh = SshClient()
                    val session = ssh.connect(
                        host = config.host,
                        user = config.user,
                        password = config.sshPassword.ifBlank { null },
                        keyPath = config.sshKeyPath.ifBlank { null },
                        keyPassphrase = config.sshKeyPassphrase.ifBlank { null },
                        port = config.sshPort
                    )
                    val quoted = "'" + clean.replace("'", "'\\''") + "'"
                    ssh.exec(session, "tmux send-keys -t MineAvaServer $quoted Enter")
                    session.disconnect()
                }
            }
        }
        return true
    }

    private fun buildWheatFarm(): String = """
        ساخت فارم گندم ساده:
        1) گودال 1x1 و آب وسط.
        2) دورش ردیف خاک و گندم.
        3) پایین/بالا دکمه + ردیف آب.
    """.trimIndent()

    private fun buildIronFarm(): String = """
        فارم آهن خودکار (ساده):
        1) نی رو 3 پلاک بالای زمین.
        2) با فاصله 3 بلاک، 3 گلمب.
        3) زیرش هاپر + چست.
    """.trimIndent()

    private fun buildXpFarm(): String = """
        فارم تجربه:
        1) اتاق 8x8 با اسپانر.
        2) رسیدن موجود به قلب 1 ضربه.
        3) هاپر و آب برای رساندن به نقطه صفر.
    """.trimIndent()

    // ---------------------------------------------------------------- util

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun vibrate(ms: Long) {
        val v = getSystemService(Vibrator::class.java)
        if (v.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(ms)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                "mineava_overlay",
                "دستیار ماینکرافت",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, "mineava_overlay")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("آوا")
            .setContentText("پنل ماینکرافت فقط هنگام بازی فعال است.")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        handler.removeCallbacksAndMessages(null)
        removePanel()
        removeHandle()
        tts.shutdown()
        super.onDestroy()
    }
}
