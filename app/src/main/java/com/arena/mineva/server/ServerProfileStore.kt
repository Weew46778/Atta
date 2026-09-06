package com.arena.mineva.server

import com.arena.mineva.AppPrefs
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores multiple server profiles (local + VPS, Java/Bedrock/hybrid) in AppPrefs.
 *
 * This is the "billboard"/dashboard layer: one on-device Bedrock server, one Java VPS,
 * one hybrid in a Docker host, etc., each with its own config, while only one profile is
 * "active" for panels/logs/quick commands.
 */
object ServerProfileStore {

    data class Profile(
        val id: String,
        val name: String,
        val configJson: String
    )

    fun all(): List<Profile> = runCatching {
        val arr = JSONArray(AppPrefs.serverProfilesJson)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Profile(o.optString("id"), o.optString("name"), o.optString("config", "{}"))
        }
    }.getOrDefault(emptyList())

    fun activeProfile(): Profile? =
        all().firstOrNull { it.id == AppPrefs.activeServerProfileId } ?: all().firstOrNull()

    fun activeConfig(): ServerConfig? =
        activeProfile()?.let { ServerConfig.fromJson(it.configJson) }
            ?: ServerConfig.fromJson(AppPrefs.lastServerConfigJson)

    fun add(config: ServerConfig, name: String? = null): String {
        val id = "srv_${System.currentTimeMillis()}"
        val profiles = all().toMutableList()
        profiles += Profile(id, name ?: autoName(profiles.size), config.toJson())
        save(profiles)
        if (AppPrefs.activeServerProfileId.isBlank()) AppPrefs.activeServerProfileId = id
        return id
    }

    fun updateName(id: String, name: String) {
        val profiles = all().map { if (it.id == id) it.copy(name = name) else it }
        save(profiles)
    }

    fun importConfigAsProfile(config: ServerConfig, name: String? = null): String =
        add(config, name)

    fun remove(id: String) {
        val profiles = all().filterNot { it.id == id }
        save(profiles)
        if (AppPrefs.activeServerProfileId == id) {
            AppPrefs.activeServerProfileId = profiles.firstOrNull()?.id ?: ""
        }
    }

    fun setActive(id: String) {
        AppPrefs.activeServerProfileId = id
        activeProfile()?.let { AppPrefs.lastServerConfigJson = it.configJson }
    }

    fun saveAndActivate(config: ServerConfig, name: String? = null): String {
        val existing = activeProfile()
        val id = if (existing != null) {
            val profiles = all().map {
                if (it.id == existing.id) it.copy(name = name ?: existing.name, configJson = config.toJson()) else it
            }
            save(profiles)
            existing.id
        } else {
            add(config, name)
        }
        AppPrefs.lastServerConfigJson = config.toJson()
        return id
    }

    private fun autoName(index: Int): String = "سرور ${index + 1}"

    private fun save(profiles: List<Profile>) {
        val arr = JSONArray()
        profiles.forEach { p ->
            arr.put(JSONObject().apply {
                put("id", p.id)
                put("name", p.name)
                put("config", p.configJson)
            })
        }
        AppPrefs.serverProfilesJson = arr.toString()
    }
}
