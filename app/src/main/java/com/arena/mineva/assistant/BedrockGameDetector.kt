package com.arena.mineva.assistant

import android.app.ActivityManager
import android.content.Context
import com.arena.mineva.AppPrefs
import com.arena.mineva.knowledge.KnowledgeRepository
import java.io.File

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

    fun markSeen(context: Context, game: String) {
        AppPrefs.lastSeenGame = game
        AppPrefs.suggestionsSeen = game
    }

    fun suggestions(context: Context): List<Suggestion> {
        val game = detect(context)
        val repo = KnowledgeRepository(context)
        val output = mutableListOf<Suggestion>()
        if (game.isNotBlank()) {
            output += Suggestion(
                "🎮 ${if (game.contains("pe")) "Bedrock" else "Minecraft"} در حال اجراست",
                "لبه چپ صفحه را به راست بکش تا پنل آوا باز شود."
            )
        }
        // Commands that work inside Bedrock dedicated servers / enabled cheats.
        output += Suggestion(
            "⚔ آیتم سریع",
            "/give @p netherite_sword 1 0 {\"ench\":[{\"id\":\"sharpness\",\"lvl\":10}]}"
        )
        output += Suggestion(
            "🏰 ایمنی پایگاه",
            "/gamerule mobGriefing false"
        )
        // Related encyclopedia tips if available.
        searchTitles(repo, "فارم آهن")?.let {
            output += Suggestion("⚙ نکته آهن", it.take(220))
        }
        searchTitles(repo, "ارتفاع الماس")?.let {
            output += Suggestion("💎 الماس", it.take(220))
        }
        searchTitles(repo, "انچنت")?.let {
            output += Suggestion("✨ انچنت", it.take(220))
        }
        return output
    }

    private fun searchTitles(repo: KnowledgeRepository, q: String): String? = repo.search(q)
}
