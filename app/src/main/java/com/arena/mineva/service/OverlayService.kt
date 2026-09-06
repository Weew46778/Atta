package com.arena.mineva.service

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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.arena.mineva.MainActivity
import com.arena.mineva.Ui
import com.arena.mineva.assistant.TextToSpeechManager
import com.arena.mineva.system.DeviceMonitor
import kotlin.math.abs

/**
 * In-game assistant overlay. Slide the thin left-edge line to the right to reveal vertical
 * tabs. Tap a tab to slide its page in, tap it again (or wait ~3s) to auto-hide.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var tts: TextToSpeechManager
    private val handler = Handler(Looper.getMainLooper())
    private val autoHide = Runnable { collapsePanel() }

    private var activityPaused = false
    private var opened = false
    private var selectedTab = -1

    private lateinit var handleView: TextView
    private lateinit var panelRoot: LinearLayout
    private lateinit var tabsColumn: LinearLayout
    private lateinit var tabButtons: MutableList<TextView>
    private lateinit var pageContainer: FrameLayout
    private lateinit var pages: MutableList<ScrollView>

    private var handleParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private data class TabSpec(val title: String, val color: Int, val build: (ViewGroup) -> Unit)

    private val tabs = listOf(
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
            tts.speak("پنل اورلای فعال شد. داخل بازی از لبه چپ صفحه به سمت راست بکش.")
        }
        createNotificationChannel()
        startForeground(1, buildNotification())
        setupOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    private fun setupOverlay() {
        buildHandle()
        buildPanel()
    }

    // ------------------------------------------------------------------ handle

    private fun buildHandle() {
        handleView = TextView(this).apply {
            text = "▮"
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF35D07F.toInt())
        }
        handleParams = WindowManager.LayoutParams(
            Ui.dp(this, 14f),
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            x = 0
            y = 0
        }
        windowManager.addView(handleView, handleParams)

        handleView.setOnTouchListener(object : View.OnTouchListener {
            var initialX = 0
            var downRaw = 0f
            override fun onTouch(v: View?, event: android.view.MotionEvent?): Boolean {
                val e = event ?: return false
                when (e.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        initialX = handleParams?.x ?: 0
                        downRaw = e.rawX
                        handler.removeCallbacks(autoHide)
                    }
                    android.view.MotionEvent.ACTION_MOVE -> {
                        val delta = e.rawX - downRaw
                        if (delta > 10) openPanel()
                    }
                    android.view.MotionEvent.ACTION_UP -> {
                        if (abs(e.rawX - downRaw) < 10) {
                            if (opened) collapsePanel() else openPanel()
                        } else {
                            scheduleAutoHide()
                        }
                    }
                }
                return true
            }
        })
    }

    // ------------------------------------------------------------------ panel + tabs

    private fun buildPanel() {
        panelRoot = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(0xE01C2836.toInt())
        }
        panelParams = WindowManager.LayoutParams(
            Ui.dp(this, 340f),
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
            x = 0
            y = 0
            translationX = -Ui.dp(this@OverlayService, 340f).toFloat()
        }
        windowManager.addView(panelRoot, panelParams)
        panelRoot.alpha = 0f

        tabsColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF121D29.toInt())
        }
        tabsColumn.layoutParams = LinearLayout.LayoutParams(Ui.dp(this, 52f), ViewGroup.LayoutParams.MATCH_PARENT)
        panelRoot.addView(tabsColumn)

        pageContainer = FrameLayout(this)
        pageContainer.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        panelRoot.addView(pageContainer)

        tabButtons = mutableListOf()
        pages = mutableListOf()
        tabs.forEachIndexed { index, spec ->
            val btn = tabButton(spec.title, spec.color)
            btn.tag = index
            btn.setOnClickListener {
                vibrate(40)
                tts.speak(spec.title)
                if (selectedTab == index && pages[index].translationX == 0f) {
                    collapsePanel()
                } else {
                    selectTab(index)
                }
                scheduleAutoHide()
            }
            tabButtons.add(btn)
            tabsColumn.addView(
                btn,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 60f))
            )

            val page = ScrollView(this).apply {
                val inner = Ui.fill(this@OverlayService)
                spec.build(inner)
                addView(
                    inner,
                    ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                )
                translationX = Ui.dp(this@OverlayService, 300f).toFloat()
                alpha = 0f
            }
            pages.add(page)
            pageContainer.addView(
                page,
                FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            )
        }
    }

    private fun tabButton(title: String, color: Int): TextView = TextView(this).apply {
        text = title
        setTextColor(Color.WHITE)
        textSize = 12f
        gravity = Gravity.CENTER
        setTypeface(typeface, Typeface.BOLD)
        setPadding(
            Ui.dp(this@OverlayService, 4f),
            Ui.dp(this@OverlayService, 4f),
            Ui.dp(this@OverlayService, 4f),
            Ui.dp(this@OverlayService, 4f)
        )
        background = GradientDrawable().apply {
            setColor(color)
            cornerRadius = Ui.dp(this@OverlayService, 10f).toFloat()
        }
    }

    private fun selectTab(index: Int) {
        if (selectedTab == index) {
            collapsePanel()
            return
        }
        val newPage = pages[index]
        if (selectedTab >= 0 && selectedTab < pages.size) {
            val old = pages[selectedTab]
            old.animate().translationX(-Ui.dp(this, 300f).toFloat()).alpha(0f).setDuration(260).start()
        }
        newPage.translationX = Ui.dp(this, 300f).toFloat()
        newPage.alpha = 0f
        newPage.animate().translationX(0f).alpha(1f).setDuration(340).start()
        selectedTab = index
        tabButtons.forEachIndexed { i, b ->
            b.alpha = if (i == index) 1f else 0.65f
        }
    }

    private fun openPanel() {
        if (opened) return
        opened = true
        panelRoot.animate().translationX(0f).alpha(1f).setDuration(360).start()
        vibrate(30)
        if (selectedTab < 0) selectTab(0)
        scheduleAutoHide()
    }

    private fun collapsePanel() {
        if (!opened) return
        opened = false
        panelRoot.animate().translationX(-Ui.dp(this, 340f).toFloat()).alpha(0f).setDuration(320).start()
        vibrate(25)
    }

    private fun scheduleAutoHide() {
        handler.removeCallbacks(autoHide)
        handler.postDelayed(autoHide, 3000L)
    }

    // ------------------------------------------------------------------ pages

    private fun buildToolsTab(c: ViewGroup) {
        c.addView(tabHeader("ابزارهای سریع"))
        c.addView(actionRow("⚔ اسلحه", "🛡 سپر", "🥄 خوراک"))
        c.addView(actionRow("🔥 مشعل/دکل", "💎 یاقوت", "🌟 enchant"))
        c.addView(Ui.text(this, "در نسخه بعدی این دکمهها به API/دستوری به سرور یا بازی واقعی متصل میشوند.", 12f, 0xFF9FB2C2.toInt()))
    }

    private fun buildStructuresTab(c: ViewGroup) {
        c.addView(tabHeader("سازه و ردستون"))
        c.addView(copyRow("کشاورزی خودکار", buildWheatFarm()))
        c.addView(copyRow("آهنخودکار", buildIronFarm()))
        c.addView(copyRow("فارم تجربه", buildXpFarm()))
        c.addView(Ui.text(this, "با زدن دکمه، طرح سادهٔ سازه به بخش کپی میشود تا بعداً به ماکرو/دستیار بدهم.", 12f, 0xFF9FB2C2.toInt()))
    }

    private fun buildWeaponsTab(c: ViewGroup) {
        c.addView(tabHeader("سفارش وسایل ویژه"))
        c.addView(copyRow("شمشیر نتریت + قلاب", "give @p netherite_sword 1 0 {\"Enchantments\":[{\"id\":\"sharpness\",\"lvl\":10}]}"))
        c.addView(copyRow("کمان بیپایان", "give @p bow 1 0 {\"Enchantments\":[{\"id\":\"infinity\",\"lvl\":1},{\"id\":\"power\",\"lvl\":10}]}"))
        c.addView(copyRow("زره محافظ", "give @p diamond_chestplate 1 0 {\"Enchantments\":[{\"id\":\"protection\",\"lvl\":10}]}"))
    }

    private fun buildAssistantTab(c: ViewGroup) {
        c.addView(tabHeader("دستیار آوا"))
        c.addView(Ui.text(this, "آوا:", 13f, 0xFF35D07F.toInt(), bold = true))
        val snap = DeviceMonitor.snapshot(this)
        c.addView(Ui.text(
            this,
            "سلام! اینجایم. رم آزاد: ${snap.freeRamMb}MB. میگم چه کاری بکنم؟",
            14f
        ))
        c.addView(
            Ui.button(this, "🎤 گفتگوی صوتی", 0xFF7D4DB1.toInt(), 46f) {
                val i = Intent(this, com.arena.mineva.VoiceAssistantActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
            }
        )
        c.addView(Ui.text(this, "دستور و ماموریت واقعی به سرور/بازی در نسخه بعدی اضافه میشود.", 12f, 0xFF9FB2C2.toInt()))
    }

    private fun buildHealthTab(c: ViewGroup) {
        c.removeAllViews()
        c.addView(tabHeader("سلامت دستگاه"))
        val snap = DeviceMonitor.snapshot(this)
        c.addView(Ui.text(this, "رم: ${snap.freeRamMb}/${snap.totalRamMb} MB آزاد", 14f))
        c.addView(Ui.text(this, "حافظه: ${snap.freeStorageMb}/${snap.totalStorageMb} MB", 14f))
        c.addView(Ui.text(this, "دما: ${if (snap.temperatureC > 0) "${snap.temperatureC}°C" else "نامشخص"}", 14f))
        c.addView(Ui.text(this, "CPU: ${snap.cpuLoadPercent}%", 14f))
        c.addView(
            Ui.button(this, "🧹 پاکسازی کش", 0xFF1F8F8F.toInt(), 46f) {
                DeviceMonitor.clearAppCache(this)
                tts.speak("کش پاک شد.")
                buildHealthTab(c)
            }
        )
    }

    private fun tabHeader(title: String): TextView = Ui.text(this, title, 18f, Color.WHITE, bold = true)

    private fun actionRow(a: String, b: String, d: String): TextView {
        return Ui.text(
            this,
            "🔹 $a   •   $b   •   $d\n   (پیشنمایش — اتصال واقعی در نسخه بعد)",
            13f,
            0xFF9FB2C2.toInt()
        ).apply {
            setOnClickListener {
                vibrate(20)
                tts.speak("ابزار $a. اتصال واقعی در نسخه بعدی فعال میشود.")
            }
        }
    }

    private fun copyRow(title: String, command: String): TextView =
        Ui.text(this, "$title:\n$command", 12f, 0xFFD8E3EC.toInt()).apply {
            setOnClickListener {
                vibrate(20)
                val cm = getSystemService(android.content.ClipboardManager::class.java)
                cm.setPrimaryClip(android.content.ClipData.newPlainText("mineava", command))
                tts.speak("$title در کلیپبورد کپی شد.")
            }
        }

    private fun buildWheatFarm(): String = """
        ساخت فارم گندم ساده (1x1 کارت):
        1) گودال 1x1 و آب وسط.
        2) دورش ردیف خاک و گندم.
        3) پایین/بالا دکمه + ردیف آب برای برداشت.
        دستور کمک: /structure save mineava:wheat_farm 10 60 10 20 70 20
    """.trimIndent()

    private fun buildIronFarm(): String = """
        فارم آهن خودکار (نسخه ساده):
        1) نی رو 3 پلاک بالای زمین.
        2) با فاصله 3 بلاک، 3 گلمب تعویضهای ساده.
        3) زیرش هاپر + چست.
        4) تذکر: از نسخه جاوا 1.19.2 رفتار کوتاه تغییر کرده است.
    """.trimIndent()

    private fun buildXpFarm(): String = """
        فارم تجربه:
        1) یک اتاق 8x8 با اسپانر.
        2) رسیدن موجود به قلب 1 ضربه.
        3) هاپر و آب برای آوردن به نقطه صفر.
        4) در بدراک نسخههای 1.21 نیاز به تایمر ندارد.
    """.trimIndent()

    // ------------------------------------------------------------------ utility

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
            .setContentTitle("آوا فعال است")
            .setContentText("پنل اورلای داخل بازی در حال اجراست.")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        panelParams?.let { windowManager.removeView(panelRoot) }
        handleParams?.let { windowManager.removeView(handleView) }
        tts.shutdown()
        super.onDestroy()
    }
}
