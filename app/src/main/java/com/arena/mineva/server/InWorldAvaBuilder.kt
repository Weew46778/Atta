package com.arena.mineva.server

import android.content.Context
import com.arena.mineva.AppPrefs
import com.arena.mineva.assistant.TextToSpeechManager

/**
 * Brings "Ava" into the Minecraft world.
 *
 * This is not a fake injection into another app's process. It builds real, safe Bedrock /
 * Java server commands that create an in-world marker for Ava:
 *  - Bedrock: an invisible Armor Stand with a named Allay/Villager, a glow item,
 *    a "leader" name, and ambient messages broadcast by the server.
 *  - Java: an entity with a custom name + glowing effect + a `tellraw`/`say` welcome.
 *
 * If a server console is reachable (on-device process, RCON via AppPrefs, or tmux on VPS),
 * the app can send these commands directly. Otherwise they are copied for the player.
 */
object InWorldAvaBuilder {

    data class AvaPlan(
        val edition: ServerEdition,
        val name: String,
        val commands: List<String>,
        val tips: String
    )

    fun build(context: Context, config: ServerConfig = ServerProfileStore.activeConfig() ?: ServerConfig()): AvaPlan {
        return if (config.edition == ServerEdition.JAVA) {
            javaPlan(config)
        } else {
            bedrockPlan(config)
        }
    }

    fun bedrockPlan(config: ServerConfig): AvaPlan {
        val commands = listOf(
            // Teleport-convenient spawn marker (works when cheats/ops are enabled).
            "/summon armor_stand \"آوا\" 0 64 0 {CustomNameVisible:1,Invisible:1,NoGravity:1}",
            "/summon allay \"آوای دستیار\" 0 64 0 {CustomNameVisible:1}",
            "/effect @e[name=\"آوای دستیار\"] instant_health 9999 1 true",
            "/say این منم! آوا. دستورها را در پنل اورلای ببین.",
            "/tp @p 0 65 0"
        )
        return AvaPlan(
            edition = ServerEdition.BEDROCK,
            name = "آوا",
            commands = commands,
            tips = "بدراک روی سرور با cheats فعال پشتیبانی میکند؛ اگر اپراتور نباشی، اپتوریت لازم است."
        )
    }

    fun javaPlan(config: ServerConfig): AvaPlan {
        val commands = listOf(
            "/summon minecraft:armor_stand 0 64 0 {CustomName:'{\"text\":\"آوا\"}',CustomNameVisible:1b,Invisible:1b,NoGravity:1b,Glowing:1b}",
            "/summon minecraft:allay 0 64 0 {CustomName:'{\"text\":\"آوای دستیار\"}',CustomNameVisible:1b}",
            "/effect give @e[type=minecraft:allay,limit=1] minecraft:glowing 9999 0 true",
            "/say این منم! آوا. دستورها را در پنل اورلای ببین."
        )
        return AvaPlan(
            edition = ServerEdition.JAVA,
            name = "آوا",
            commands = commands,
            tips = "جاوا دستور summa با NBT را پشتیبانی میکند؛ دردسرهای formatting ساده شد."
        )
    }

    /**
     * Attempts to send the plan to the currently reachable console. Returns a human-readable
     * result. `onOneCommand` can be used by the caller to inject a real RCON/tmux sender.
     */
    fun deliver(context: Context, plan: AvaPlan, send: (String) -> String): String {
        val sb = StringBuilder()
        plan.commands.forEachIndexed { i, cmd ->
            val out = send(cmd)
            sb.appendLine("${i + 1}. $cmd")
            if (out.isNotBlank()) sb.appendLine("   → ${out.take(120)}")
        }
        return sb.toString()
    }

    /** Best-effort delivery through the same console path used by the rest of the app. */
    fun deliverWithAppConsole(context: Context): String {
        val config = ServerProfileStore.activeConfig() ?: ServerConfig()
        val plan = build(context, config)
        return deliver(context, plan) { cmd ->
            deliverViaNetwork(context, config, cmd)
        }
    }

    private fun deliverViaNetwork(context: Context, config: ServerConfig, command: String): String {
        // 1) On-device process has no stdin channel yet; the server console path is RCON/tmux.
        // 2) If RCON is enabled, use it (works for both Bedrock/Java).
        if (AppPrefs.rconEnabled) {
            return runCatching {
                val rcon = RconClient()
                val host = if (config.target == ServerTarget.VPS) config.host else "127.0.0.1"
                val auth = rcon.connect(host, AppPrefs.rconPort, AppPrefs.rconPassword, 5000)
                if (!auth.success) "RCON failed: ${auth.message}"
                else rcon.command(command).message
            }.getOrElse { "RCON error: ${it.message}" }
        }
        // 3) VPS tmux path (SSH key only).
        if (config.target == ServerTarget.VPS && config.host.isNotBlank() && config.sshKeyPath.isNotBlank()) {
            return runCatching {
                val ssh = SshClient()
                val session = ssh.connect(
                    host = config.host, user = config.user,
                    password = null, keyPath = config.sshKeyPath,
                    keyPassphrase = null, port = config.sshPort
                )
                val quoted = "'" + command.replace("'", "'\\''") + "'"
                val out = ssh.exec(session, "tmux send-keys -t MineAvaServer $quoted Enter").output
                session.disconnect()
                out
            }.getOrElse { "SSH error: ${it.message}" }
        }
        return "دریافت مستقیم در دسترس نیست؛ دستور در کلیپبورد/پیشنهادها قرار میگیرد."
    }
}
