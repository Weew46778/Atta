package com.arena.mineva.server

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class CatalogItem(
    val id: String,
    val name: String,
    val category: String,
    val version: String,
    val url: String,
    val downloadUrl: String,
    val description: String,
    val source: String,
    val apiType: String = "none",
    val apiOwner: String = "",
    val apiRepo: String = "",
    val apiSlug: String = "",
    val compatiblePrefixes: List<String> = listOf("1.21")
) {
    fun hasDirectDownload(): Boolean = downloadUrl.isNotBlank() && downloadUrl.startsWith("http")
    fun isAutoUpdatable(): Boolean = apiType == "github" || apiType == "modrinth"

    fun isCompatibleWith(serverVersion: String): Boolean {
        val target = serverVersion.trim()
        if (target.isBlank()) return true
        return compatiblePrefixes.any { prefix ->
            target.startsWith(prefix, ignoreCase = true) || prefix.startsWith(target, ignoreCase = true)
        }
    }
}

/**
 * Local catalog with a user-updated overlay (filesDir/package_catalog_user.json) that is
 * written by [PackageCatalogUpdater]. Assets are the immutable base; updates are merged on
 * top so the app can refresh versions/download URLs without shipping a new APK.
 */
class PackageCatalog(context: Context) {

    private val appContext: Context = context.applicationContext
    private val userFile: File = File(appContext.filesDir, "package_catalog_user.json")

    private var baseItems: List<CatalogItem> = emptyList()
    private var userItems: List<CatalogItem> = emptyList()
    private var merged: List<CatalogItem> = emptyList()

    init {
        reload()
    }

    fun reload() {
        baseItems = runCatching { loadJsonFrom(appContext.assets.open("package_catalog.json")) }
            .getOrDefault(emptyList())
        userItems = runCatching { if (userFile.exists()) loadJson(userFile.readText()) else emptyList() }
            .getOrDefault(emptyList())
        merged = (baseItems + userItems).associateBy { it.id }.values.toList()
    }

    fun byCategory(category: String): List<CatalogItem> =
        merged.filter { it.category.equals(category, ignoreCase = true) }

    fun categories(): List<String> = merged.map { it.category }.distinct()

    fun all(): List<CatalogItem> = merged

    fun byId(id: String): CatalogItem? = merged.firstOrNull { it.id == id }

    fun linkHealthy(item: CatalogItem): Boolean = runCatching {
        val url = java.net.URI(item.downloadUrl.ifBlank { item.url })
        (url.scheme == "https" || url.scheme == "http") && url.host != null
    }.getOrDefault(false)

    private fun loadJson(json: String): List<CatalogItem> {
        val arr = JSONArray(json)
        val out = mutableListOf<CatalogItem>()
        for (i in 0 until arr.length()) {
            out += parse(arr.getJSONObject(i))
        }
        return out
    }

    private fun loadJsonFrom(input: java.io.InputStream): List<CatalogItem> {
        val json = input.bufferedReader().use { it.readText() }
        return loadJson(json)
    }

    private fun parse(o: JSONObject): CatalogItem = CatalogItem(
        id = o.optString("id"),
        name = o.optString("name"),
        category = o.optString("category"),
        version = o.optString("version"),
        url = o.optString("url"),
        downloadUrl = o.optString("downloadUrl"),
        description = o.optString("description"),
        source = o.optString("source"),
        apiType = o.optString("apiType", "none"),
        apiOwner = o.optString("apiOwner"),
        apiRepo = o.optString("apiRepo"),
        apiSlug = o.optString("apiSlug"),
        compatiblePrefixes = o.optJSONArray("compatiblePrefixes").stringList()
    )

    private fun JSONArray?.stringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).map { getString(it) }
    }
}
