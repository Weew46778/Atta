package com.arena.mineva

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.arena.mineva.assistant.TextToSpeechManager
import com.arena.mineva.service.OverlayService

class MainActivity : AppCompatActivity() {

    private lateinit var tts: TextToSpeechManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeechManager(this)
        tts.init { tts.speak("سلام! من آوا هستم، دستیار ماینکرافت تو. منوی اصلی آماده است.") }

        if (!AppPrefs.onboarded) {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }

        setContentView(buildHome())
    }

    private fun buildHome(): View {
        val scroll = ScrollView(this)
        val root = Ui.fill(this)
        scroll.addView(root)

        root.addView(title("MineAva", 28f))
        root.addView(Ui.text(
            this,
            "دستیار هوشمند و لانچر ماینکرافت بدراک\n${
                getString(R.string.app_version)
            }",
            14f,
            0xFF9FB2C2.toInt()
        ))

        root.addView(assistantHeader())

        root.addView(sectionTitle("لانچر"))
        root.addView(
            Ui.button(this, "▶  باز کردن Minecraft Bedrock", 0xFF35D07F.toInt()) {
                launchMinecraft()
            }
        )
        root.addView(
            Ui.button(this, "🔊  گفتگو با دستیار آوا (صوتی)", 0xFF2E9BFF.toInt()) {
                startActivity(Intent(this, VoiceAssistantActivity::class.java))
            }
        )
        root.addView(
            Ui.button(this, "🪟  فعالکردن پنل اورلای داخل بازی", 0xFF7D4DB1.toInt()) {
                startOverlay()
            }
        )

        root.addView(sectionTitle("سرور و مدیریت"))
        root.addView(
            Ui.button(this, "🛠  ساخت خودکار سرور", 0xFF2E70B8.toInt()) {
                startActivity(Intent(this, ServerWizardActivity::class.java))
            }
        )
        root.addView(
            Ui.button(this, "⚙️  مدیریت سرور", 0xFF2E70B8.toInt()) {
                startActivity(Intent(this, ServerManagerActivity::class.java))
            }
        )
        root.addView(
            Ui.button(this, "🛠  پنل حرفهای سرور", 0xFF2E70B8.toInt()) {
                startActivity(Intent(this, ServerPanelActivity::class.java))
            }
        )
        root.addView(
            Ui.button(this, "🌐  لاگ و کنسول ابری سرور", 0xFF2E9BFF.toInt()) {
                startActivity(Intent(this, CloudLogActivity::class.java))
            }
        )
        root.addView(
            Ui.button(this, "📋  پیشخوان چندسروره", 0xFF2E70B8.toInt()) {
                startActivity(Intent(this, ServerDashboardActivity::class.java))
            }
        )
        root.addView(
            Ui.button(this, "📦  مدیریت پلاگین/مود/پک", 0xFF1F8F8F.toInt()) {
                startActivity(Intent(this, PackageManagerActivity::class.java))
            }
        )

        root.addView(sectionTitle("سلامت و عیبیابی"))
        root.addView(
            Ui.button(this, "💾  سلامت دستگاه (رم، حافظه، دما)", 0xFFC97C22.toInt()) {
                startActivity(Intent(this, DeviceHealthActivity::class.java))
            }
        )
        root.addView(
            Ui.button(this, "🔬  تست کامل سیستم و گزارش خطا", 0xFFFF5A5A.toInt()) {
                startActivity(Intent(this, DiagnosticsActivity::class.java))
            }
        )
        root.addView(
            Ui.button(this, "⚙️  تنظیمات هوش مصنوعی", 0xFF2E9BFF.toInt()) {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        )

        root.addView(Ui.text(
            this,
            "\nنسخه اولیه: لانچر، پنل اورلای، دستیار صوتی، تولید دستور سرور، سلامت دستگاه و تست سیستم.\nقابلیتهای واقعی VPS/تزریق داخل بدراک در نسخه بعدی به همین ساختار متصل میشود.",
            12f,
            0xFF9FB2C2.toInt()
        ))

        return scroll
    }

    private fun assistantHeader(): View {
        val row = Ui.horizontal(this)
        val pad = Ui.dp(this, 12f)
        row.setPadding(pad, pad, pad, pad)
        row.background = Ui.card(this).background
        row.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = Ui.dp(this@MainActivity, 8f)
            bottomMargin = Ui.dp(this@MainActivity, 8f)
        }
        val avatar = Ui.text(this, "🤖", 36f)
        val status = Ui.vertical(this).apply {
            addView(Ui.text(this@MainActivity, "آوا", 18f, 0xFF35D07F.toInt(), bold = true))
            addView(Ui.text(this@MainActivity, "پاسخگوی صوتی و متنی، مدیر سیستم", 12f, 0xFF9FB2C2.toInt()))
        }
        row.addView(avatar)
        row.addView(status)
        return row
    }

    private fun sectionTitle(text: String): View =
        Ui.text(this, text, 17f, 0xFFF1F5F9.toInt(), bold = true)

    private fun title(text: String, size: Float): View =
        Ui.text(this, text, size, 0xFF35D07F.toInt(), bold = true)

    private fun launchMinecraft() {
        val candidates = listOf("com.mojang.minecraftpe", "com.mojang.minecraft")
        val launch: Intent? = candidates.firstNotNullOfOrNull { pkg ->
            runCatching { packageManager.getLaunchIntentForPackage(pkg) }.getOrNull()
        }
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launch)
            tts.speak("ماینکرافت در حال باز شدن است. برای پنل اورلای از لبه چپ صفحه بکش.")
        } else {
            tts.speak("ماینکرافت بدراک را پیدا نکردم. لطفاً آن را نصب کن.")
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.mojang.minecraftpe")))
            }.onFailure {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.mojang.minecraftpe")))
            }
        }
    }

    private fun startOverlay() {
        if (Settings.canDrawOverlays(this)) {
            val intent = Intent(this, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            tts.speak("پنل اورلای فعال شد. داخل بازی از لبه چپ صفحه به راست بکش.")
            return
        }
        // First request runtime + settings permission.
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 10)
        }
        tts.speak("برای پنل اورلای باید دسترسی نمایش روی برنامههای دیگر را بدهی.")
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
        )
    }

    override fun onResume() {
        super.onResume()
        if (AppPrefs.onboarded && Settings.canDrawOverlays(this)) {
            // Everything ready; keep silent if user already opened once.
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) tts.shutdown()
    }
}
