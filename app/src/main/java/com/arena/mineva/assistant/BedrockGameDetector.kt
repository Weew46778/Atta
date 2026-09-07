package com.arena.mineva.assistant

import android.app.ActivityManager
import android.content.Context
import com.arena.mineva.knowledge.KnowledgeRepository
import com.arena.mineva.server.OnDeviceJavaServerManager
import com.arena.mineva.server.OnDeviceServerManager
import com.arena.mineva.server.ServerConfig
import com.arena.mineva.server.ServerEdition
import com.arena.mineva.server.ServerProfileStore
import com.arena.mineva.server.ServerTarget
import com.arena.mineva.system.DeviceMonitor
import java.util.Calendar

/**
 * Detects when a Bedrock/Java game process is in the foreground and produces smart,
 * context-aware suggestions for the overlay/assistant.
 *
 * Android does not expose a safe way to inject items inside another app's process; this
 * detector is the correct pattern: notice the game, keep the overlay available, and show
 * suggestions that the user can use (commands, encyclopedia tips, server quick actions).
 */
object BedrockGameDetector {

    data class Suggestion(val title: String, val text: String)

    fun detect(context: Context): String = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val running = am.runningAppProcesses?.mapNotNull { it.processName } ?: emptyList()
        running.firstOrNull { pkg ->
            pkg.equals("com.mojang.minecraftpe", true) ||
                pkg.equals("com.mojang.minecraft", true) ||
                pkg.contains("minecraft", true)
        } ?: ""
    }.getOrDefault("")

    fun isGameRunning(context: Context): Boolean = detect(context).isNotBlank()

    fun suggestions(context: Context): List<Suggestion> {
        val game = detect(context)
        val repo = KnowledgeRepository(context)
        val output = mutableListOf<Suggestion>()

        // 1) game status
        if (game.isNotBlank()) {
            output += Suggestion(
                "🎮 ${if (game.contains("pe")) "Bedrock" else "Minecraft"} در حال اجراست",
                "لبه چپ صفحه را به راست بکش تا پنل آوا باز شود."
            )
        }

        // 2) time-aware suggestions
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when {
            hour in 18..23 || hour in 0..5 -> output += Suggestion(
                "🌙 شب است",
                "برای دید بهتر: /time set night — ساخت نورپردازی: /give @p glowing_berries 8"
            )
            hour in 9..17 -> output += Suggestion(
                "☀️ روز است",
                "برای شروع پروژه روزانه: /time set day — دیدن مختصات: /gamerule showcoordinates true"
            )
            else -> output += Suggestion(
                "⏰ میان‌روز",
                "برای تمیز کردن مصنوعات: /gamerule doDaylightCycle true"
            )
        }

        // 3) server-aware suggestions
        val config = ServerProfileStore.activeConfig()
        when {
            config != null && config.target == ServerTarget.LOCAL && config.edition != ServerEdition.JAVA -> {
                val running = runCatching { OnDeviceServerManager(context).statusFromDisk().contains("RUNNING") }.getOrDefault(false)
                if (running) output += Suggestion("🖥 سرور گوشی روشن است", "از پنل حرفه‌ای می‌توانی دستور را مستقیم به کنسول بفرستی.")
                else output += Suggestion("🖥 سرور گوشی خاموش است", "از «مدیریت سرور» یا «پنل حرفه‌ای» آن را شروع کن.")
            }
            config != null && config.target == ServerTarget.LOCAL -> {
                val running = runCatching { OnDeviceJavaServerManager(context).statusFromDisk().contains("RUNNING") }.getOrDefault(false)
                if (running) output += Suggestion("☕ سرور Java گوشی روشن است", "پورت ${config.port} فعال است؛ از پنل حرفه‌ای کنسول را باز کن.")
                else output += Suggestion("☕ سرور Java خاموش است", "JRE و server.jar را در «مدیریت سرور» نصب و سپس شروع کن.")
            }
            config != null && config.target == ServerTarget.VPS && config.host.isNotBlank() -> output += Suggestion(
                "🖥 سرور VPS پیکربندی شده",
                "${config.host} — از «پنل حرفه‌ای» وضعیت، لاگ و کنسول را بررسی کن."
            )
            config == null -> output += Suggestion("🛠 هنوز سروری نداری", "از «ساخت خودکار سرور» یا «راهنمای گام‌به‌گام VPS» شروع کن.")
        }

        // 4) resource-aware suggestions
        val snap = DeviceMonitor.snapshot(context)
        if (snap.freeRamMb < 400) output += Suggestion("⚠ رم کم است", "برنامه‌های سنگین را ببند یا رم سرور روی گوشی را کم کن (${snap.freeRamMb}MB آزاد).")
        if (snap.temperatureC > 43) output += Suggestion("🌡 گوشی داغ شده", "نور/گرافیک بازی را کم کن و چند دقیقه استراحت بده (${snap.temperatureC}°C).")

        // 5) fixed quick commands
        output += Suggestion("⚔ آیتم سریع", "/give @p netherite_sword 1 0 {\"ench\":[{\"id\":\"sharpness\",\"lvl\":10}]}")
        output += Suggestion("🏰 ایمنی پایگاه", "/gamerule mobGriefing false")

        // 6) encyclopedia tips
        searchTitles(repo, "فارم آهن")?.let { output += Suggestion("⚙ نکته آهن", it.take(220)) }
        searchTitles(repo, "ارتفاع الماس")?.let { output += Suggestion("💎 الماس", it.take(220)) }
        searchTitles(repo, "انچنت")?.let { output += Suggestion("✨ انچنت", it.take(220)) }

        return output.distinctBy { it.title }
    }

    private fun searchTitles(repo: KnowledgeRepository, q: String): String? = repo.search(q)
}
