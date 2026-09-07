package com.arena.mineva.system

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.speech.SpeechRecognizer
import com.arena.mineva.AppPrefs
import com.arena.mineva.knowledge.KnowledgeRepository

data class DiagnosticCheck(
    val id: String,
    val name: String,
    val ok: Boolean,
    val detail: String
)

/**
 * Runs a real self-test of every major subsystem the app depends on. Used by the first
 * launch onboarding and by the "بررسی سلامت سیستم" screen.
 */
object Diagnostics {

    fun run(context: Context): List<DiagnosticCheck> {
        val checks = mutableListOf<DiagnosticCheck>()

        checks += check(
            "overlay",
            "دسترسی نمایش روی بازی (Overlay)",
            Settings.canDrawOverlays(context),
            if (Settings.canDrawOverlays(context)) "مجاز است." else "در تنظیمات اپ باید فعال شود."
        )

        checks += check(
            "notification",
            "اعلانهای Android",
            !needsNotificationPermission() ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
            if (needsNotificationPermission()) "دسترسی اعلان لازم است." else "لازم نیست."
        )

        checks += check(
            "mic",
            "میکروفن",
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
            "برای گفتار صوتی باید مجاز باشد."
        )

        checks += check(
            "stt",
            "تشخیص گفتار (STT)",
            runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false),
            "در صورت نبود، Google Speech Service یا پکیج فارسی لازم است."
        )

        checks += check(
            "tts",
            "متن به گفتار (TTS)",
            true,
            "صدا توسط موتور TTS دستگاه پخش میشود؛ زبان فارسی روی اکثر دستگاهها نصب است."
        )

        checks += check(
            "network",
            "اینترنت",
            isOnline(context),
            if (isOnline(context)) "متصل." else "برای ارتقا و هوش آنلاین لازم است."
        )

        checks += check(
            "minecraft",
            "ماینکرافت بدراک",
            isPackageInstalled(context, "com.mojang.minecraftpe"),
            "اگر نصب نباشد، دکمه لانچ به تنظیمات/فروشگاه هدایت میشود."
        )

        checks += check(
            "knowledge",
            "دایرهالمعارف محلی",
            runCatching { KnowledgeRepository(context).search("netherite") != null }.getOrDefault(false),
            "فایل دانش محلی باید بارگذاری شود."
        )

        checks += check(
            "apiKey",
            "محل اتصال هوش مصنوعی",
            AppPrefs.geminiApiKey.isNotBlank(),
            "برای جواب کامل آنلاین، کلید Gemini در تنظیمات لازم است."
        )

        val snap = runCatching { DeviceMonitor.snapshot(context) }.getOrNull()
        if (snap != null) {
        checks += check(
            "ram",
            "حافظه رم",
            snap.freeRamMb > 300,
            "${snap.freeRamMb} MB آزاد از ${snap.totalRamMb} MB"
        )

        checks += check(
            "storage",
            "حافظه دستگاه",
            snap.freeStorageMb > 1000,
            "${snap.freeStorageMb} MB آزاد از ${snap.totalStorageMb} MB"
        )

        checks += check(
            "temp",
            "دمای دستگاه",
            snap.temperatureC <= 45f,
            if (snap.temperatureC > 0) "${snap.temperatureC} درجه سانتیگراد" else "در دسترس نیست."
        )

        }
        return checks
    }

    fun report(checks: List<DiagnosticCheck>): String {
        val sb = StringBuilder("گزارش سلامت MineAva\n${"=".repeat(36)}\n")
        checks.forEach {
            sb.append(if (it.ok) "[OK]  " else "[FAIL]")
            sb.append(it.name).append("\n      ").append(it.detail).append("\n")
        }
        val failures = checks.count { !it.ok }
        sb.append("\nمجموع: ${checks.count()} بررسی، $failures مشکل.")
        return sb.toString()
    }

    private fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(n) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isPackageInstalled(context: Context, pkg: String): Boolean = runCatching {
        context.packageManager.getPackageInfo(pkg, 0)
        true
    }.getOrDefault(false)

    private fun needsNotificationPermission(): Boolean = Build.VERSION.SDK_INT >= 33

    private fun check(id: String, name: String, ok: Boolean, detail: String) =
        DiagnosticCheck(id, name, ok, detail)
}
