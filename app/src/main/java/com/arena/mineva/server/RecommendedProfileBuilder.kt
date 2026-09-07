package com.arena.mineva.server

import com.arena.mineva.AppPrefs

/**
 * Builds and installs a sensible recommended server profile with one click.
 *
 * Used on first-run / after onboarding so the user doesn't have to configure anything:
 * a local hybrid (Java + Bedrock-friendly) server on a safe default port, or a local
 * Bedrock-only profile if the user prefers the simplest on-device option.
 */
object RecommendedProfileBuilder {

    data class Recommended(val profileId: String, val config: ServerConfig, val note: String)

    fun localBedrock(): Recommended {
        val config = ServerConfig(
            target = ServerTarget.LOCAL,
            edition = ServerEdition.BEDROCK,
            version = "1.21",
            port = 19132,
            maxPlayers = 8,
            memoryMb = 1024,
            allowJavaClients = false,
            allowBedrockClients = true,
            pluginNames = emptyList(),
            modNames = emptyList()
        )
        return install(config, "سرور پیشنهادی روی گوشی (Bedrock)")
    }

    fun localHybrid(): Recommended {
        val config = ServerConfig(
            target = ServerTarget.LOCAL,
            edition = ServerEdition.HYBRID,
            version = "1.21",
            port = 19132,
            maxPlayers = 10,
            memoryMb = 2048,
            allowJavaClients = true,
            allowBedrockClients = true,
            useGeyser = true,
            pluginNames = listOf("EssentialsX"),
            modNames = emptyList()
        )
        return install(config, "سرور پیشنهادی هیبرید (Java+Bedrock)")
    }

    fun vps(host: String, user: String): Recommended {
        val config = ServerConfig(
            target = ServerTarget.VPS,
            edition = ServerEdition.HYBRID,
            version = "1.21",
            host = host,
            user = user,
            port = 25565,
            maxPlayers = 20,
            memoryMb = 4096,
            allowJavaClients = true,
            allowBedrockClients = true,
            useGeyser = true,
            pluginNames = listOf("EssentialsX"),
            modNames = emptyList()
        )
        return install(config, "سرور پیشنهادی VPS هیبرید")
    }

    private fun install(config: ServerConfig, name: String): Recommended {
        val id = ServerProfileStore.saveAndActivate(config, name)
        AppPrefs.lastServerConfigJson = config.toJson()
        AppPrefs.homeServerTarget = config.target.name
        return Recommended(id, config, "پروفایل «$name» فعال شد.")
    }
}
