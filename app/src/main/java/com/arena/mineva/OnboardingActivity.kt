package com.arena.mineva

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.arena.mineva.assistant.TextToSpeechManager
import com.arena.mineva.system.Diagnostics

class OnboardingActivity : AppCompatActivity() {

    private lateinit var tts: TextToSpeechManager
    private lateinit var list: LinearLayout
    private lateinit var continueBtn: android.widget.TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeechManager(this)
        tts.init {
            tts.speak(
                "سلام! من آوا هستم. اول چند دسترسی لازم دارم و یک تست کوتاه میگیرم تا مطمئن شویم همه چیز آماده است."
            )
        }

        val root = Ui.fill(this)
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams = lp
        }
        root.addView(Ui.text(this, " خوش آمدی", 26f, 0xFF35D07F.toInt(), bold = true))
        root.addView(Ui.text(this, "آوا آماده است نصب زیرساخت را انجام دهد.", 14f, 0xFF9FB2C2.toInt()))
        root.addView(list)

        continueBtn = Ui.button(this, "ادامه →", 0xFF35D07F.toInt()) {
            requestCorePermissionsAndFinish()
        }
        root.addView(continueBtn)
        setContentView(root)

        renderChecks()
    }

    private fun renderChecks() {
        list.removeAllViews()
        val checks = Diagnostics.run(this)
        checks.forEach { c ->
            val symbol = if (c.ok) "✓" else "✗"
            val color = if (c.ok) 0xFF35D07F.toInt() else 0xFFFF5A5A.toInt()
            list.addView(Ui.text(this, "$symbol  ${c.name}", 15f, color, bold = true))
            list.addView(Ui.text(this, "     ${c.detail}", 12f, 0xFF9FB2C2.toInt()))
        }
    }

    private fun requestCorePermissionsAndFinish() {
        val permissions = mutableListOf<String>()
        if (hasMic()) permissions += Manifest.permission.RECORD_AUDIO
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 42)
        } else {
            requestOverlayThenFinish()
        }
    }

    private fun requestOverlayThenFinish() {
        if (!Settings.canDrawOverlays(this)) {
            tts.speak("یک دسترسی آخر: نمایش روی برنامههای دیگر. این را برای پنل اورلای فعال کن.")
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } else {
            finishOnboard()
        }
    }

    private fun finishOnboard() {
        AppPrefs.onboarded = true
        tts.speak("همه چیز آماده است. اینترنت و کلید هوش مصنوعی را از تنظیمات میتوانی اضافه کنی. حالا برو به منوی اصلی.")
        finish()
    }

    private fun hasMic(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        renderChecks()
        requestOverlayThenFinish()
    }

    override fun onResume() {
        super.onResume()
        renderChecks()
        val micOk =
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        val notificationOk =
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!AppPrefs.onboarded && Settings.canDrawOverlays(this) && micOk && notificationOk) {
            finishOnboard()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) tts.shutdown()
    }
}
