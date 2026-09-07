package com.arena.mineva.server

import org.json.JSONArray
import org.json.JSONObject

enum class ServerTarget { LOCAL, VPS }
enum class ServerEdition { JAVA, BEDROCK, HYBRID }

data class ServerConfig(
    val target: ServerTarget = ServerTarget.LOCAL,
    val edition: ServerEdition = ServerEdition.BEDROCK,
    val version: String = "1.21",
    val host: String = "",
    val user: String = "",
    val sshKeyPath: String = "",
    val sshPort: Int = 22,
    // Password is intentionally ephemeral; it is carried in memory during provisioning
    // but never written to the persistent JSON config.
    val sshPassword: String = "",
    val sshKeyPassphrase: String = "",
    val playitSecret: String = "",
    val port: Int = 19132,
    val maxPlayers: Int = 10,
    val memoryMb: Int = 2048,
    val useGeyser: Boolean = false,
    val useBungee: Boolean = false,
    val pluginNames: List<String> = listOf("EssentialsX"),
    val modNames: List<String> = listOf("Fabric API"),
    val modpackNames: List<String> = emptyList(),
    val resourcePackNames: List<String> = emptyList(),
    val shaderPackNames: List<String> = emptyList(),
    val texturePackNames: List<String> = emptyList(),
    val allowJavaClients: Boolean = true,
    val allowBedrockClients: Boolean = true
) {

    fun toJson(): String = JSONObject().apply {
        put("target", target.name)
        put("edition", edition.name)
        put("version", version)
        put("host", host)
        put("user", user)
        put("sshKeyPath", sshKeyPath)
        put("sshPort", sshPort)
        put("playitSecret", playitSecret)
        put("port", port)
        put("maxPlayers", maxPlayers)
        put("memoryMb", memoryMb)
        put("useGeyser", useGeyser)
        put("useBungee", useBungee)
        put("pluginNames", JSONArray(pluginNames))
        put("modNames", JSONArray(modNames))
        put("modpackNames", JSONArray(modpackNames))
        put("resourcePackNames", JSONArray(resourcePackNames))
        put("shaderPackNames", JSONArray(shaderPackNames))
        put("texturePackNames", JSONArray(texturePackNames))
        put("allowJavaClients", allowJavaClients)
        put("allowBedrockClients", allowBedrockClients)
    }.toString()

    companion object {
        fun fromJson(json: String): ServerConfig? = runCatching {
            val o = JSONObject(json)
            ServerConfig(
                target = runCatching { ServerTarget.valueOf(o.optString("target", "LOCAL")) }
                    .getOrDefault(ServerTarget.LOCAL),
                edition = runCatching { ServerEdition.valueOf(o.optString("edition", "BEDROCK")) }
                    .getOrDefault(ServerEdition.BEDROCK),
                version = o.optString("version", "1.21"),
                host = o.optString("host"),
                user = o.optString("user"),
                sshKeyPath = o.optString("sshKeyPath"),
                sshPort = o.optInt("sshPort", 22),
                sshPassword = "",
                sshKeyPassphrase = "",
                playitSecret = o.optString("playitSecret", ""),
                port = o.optInt("port", 19132),
                maxPlayers = o.optInt("maxPlayers", 10),
                memoryMb = o.optInt("memoryMb", 2048),
                useGeyser = o.optBoolean("useGeyser", false),
                useBungee = o.optBoolean("useBungee", false),
                pluginNames = o.optJSONArray("pluginNames").stringList(),
                modNames = o.optJSONArray("modNames").stringList(),
                modpackNames = o.optJSONArray("modpackNames").stringList(),
                resourcePackNames = o.optJSONArray("resourcePackNames").stringList(),
                shaderPackNames = o.optJSONArray("shaderPackNames").stringList(),
                texturePackNames = o.optJSONArray("texturePackNames").stringList(),
                allowJavaClients = o.optBoolean("allowJavaClients", true),
                allowBedrockClients = o.optBoolean("allowBedrockClients", true)
            )
        }.getOrNull()

        private fun JSONArray?.stringList(): List<String> {
            if (this == null) return emptyList()
            return (0 until length()).map { getString(it) }
        }
    }
}
