package com.arena.mineva.system

import android.content.Context
import com.arena.mineva.AppPrefs
import com.arena.mineva.assistant.BedrockGameDetector
import com.arena.mineva.assistant.TextToSpeechManager
import com.arena.mineva.knowledge.KnowledgeRepository
import com.arena.mineva.knowledge.UserTopicStore
import com.arena.mineva.server.OnDeviceJavaServerManager
import com.arena.mineva.server.OnDeviceServerManager
import com.arena.mineva.server.ServerConfig
import com.arena.mineva.server.ServerProfileStore
import com.arena.mineva.server.ServerTarget
import java.io.File

/**
 * Builds a full, human-readable status report of the whole app:
 * device health, server status, watchdog, voice, encyclopedia, guide, and app version.
 * Used by the diagnostics screen and copied to clipboard.
 */
object AppStatusReport {

    fun build(context: Context): String {
        val sb = StringBuilder()
        sb.appendLine("📊 گزارش وضعیت کامل MineAva")
        sb.appendLine("تاریخ: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
        sb.appendLine()

        // App
        val version = runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            (info.versionName ?: "?") + " (code " + (if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()) + ")"
        }.getOrDefault("نامشخص")
        sb.appendLine("✅ نسخه اپ: $version")
        sb.appendLine("📶 اتصال اینترنت: ${AppPrefs.geminiApiKey.isNotBlank()}")
        sb.appendLine()

        // Device
        val snap = DeviceMonitor.snapshot(context)
        sb.appendLine("💾 دستگاه:")
        sb.appendLine("  رم: ${snap.freeRamMb}/${snap.totalRamMb} MB آزاد")
        sb.appendLine("  حافظه: ${snap.freeStorageMb}/${snap.totalStorageMb} MB")
        sb.appendLine("  دما: ${if (snap.temperatureC > 0) "${snap.temperatureC}°C" else "نامشخص"}")
        sb.appendLine("  CPU: ${snap.cpuLoadPercent}%")
        sb.appendLine("  کش: ${snap.cacheMb} MB")
        sb.appendLine()

        // Game
        val game = BedrockGameDetector.detect(context)
        sb.appendLine("🎮 بازی: ${if (game.isNotBlank()) "$game در حال اجراست" else "در حال اجرا نیست"}")
        sb.appendLine()

        // Server
        val cfg = ServerProfileStore.activeConfig()
        sb.appendLine("🖥 سرور:")
        sb.appendLine("  پروفر: ${if (cfg != null) "${cfg.target} / ${cfg.edition} ${cfg.version} / پورت ${cfg.port}" else "هنوز ساخته نشده"}")
        if (cfg != null) {
            val status = when (cfg.target) {
                ServerTarget.LOCAL -> {
                    if (cfg.edition == com.arena.mineva.server.ServerEdition.JAVA || cfg.edition == com.arena.mineva.server.ServerEdition.HYBRID) {
                        OnDeviceJavaServerManager(context).statusFromDisk()
                    } else {
                        OnDeviceServerManager(context).statusFromDisk()
                    }
                }
                ServerTarget.VPS -> "VPS (از طریق SSH قابل بررسی است)"
            }
            sb.appendLine("  وضعیت: $status")
        }
        sb.appendLine("  پروفایل‌ها: ${ServerProfileStore.all().size}")
        sb.appendLine("  ریاستارت خودکار: ${if (AppPrefs.autoRestartEnabled) "روشن" else "خاموش"}")
        sb.appendLine("  پایش پس‌زمینه: ${if (AppPrefs.watchdogServiceEnabled) "روشن" else "خاموش"}")
        sb.appendLine()

        // Voice / TTS
        val tts = TextToSpeechManager(context)
        val voiceReady = tts.ready
        val bundled = tts.bundledVoiceStatus()
        tts.shutdown()
        sb.appendLine("🎙 صدا:")
        sb.appendLine("  TTS آماده: $voiceReady")
        sb.appendLine("  صدای داخل اپ: ${if (AppPrefs.useBundledVoice) "روشن" else "خاموش"} — $bundled")
        sb.appendLine()

        // Knowledge
        val repo = KnowledgeRepository(context)
        sb.appendLine("📚 دانش:")
        sb.appendLine("  دسته‌ها: ${repo.categories().joinToString("، ")}")
        sb.appendLine("  یادگیری پویا: ${repo.learnedCount()} مورد")
        sb.appendLine("  موضوعات کاربر: ${UserTopicStore.all().size} (${UserTopicStore.remainingCapacity()} ظرفیت باقی)")
        sb.appendLine()

        // Guide
        val guideIdx = AppPrefs.guideIndex.coerceIn(0, com.arena.mineva.guide.GuideController.steps.size)
        sb.appendLine("🎓 راهنما: مرحله $guideIdx/${com.arena.mineva.guide.GuideController.steps.size}")
        sb.appendLine()

        return sb.toString()
    }
}
