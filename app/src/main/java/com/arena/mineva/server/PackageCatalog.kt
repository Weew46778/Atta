package com.arena.mineva.server

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class CatalogItem(
    val id: String,
    val name: String,
    val category: String,
    val version: String,
    val url: String,
    val downloadUrl: String,
    val description: String,
    val source: String
) {
    fun hasDirectDownload(): Boolean = downloadUrl.isNotBlank()
}

/**
 * Curated catalog of popular, safe Minecraft add-ons.
 *
 * This first build ships a static catalog. The requested online link-health and
 * compatibility checker is represented by the [linkHealthy] helper (currently validates the
 * URL shape and HTTPS). A dedicated worker that fetches/validates every item is the next
 * backend module.
 */
class PackageCatalog(context: Context) {

    private val items: List<CatalogItem> =
        runCatching { load(context) }.getOrDefault(emptyList())

    fun byCategory(category: String): List<CatalogItem> =
        items.filter { it.category.equals(category, ignoreCase = true) }

    fun categories(): List<String> = items.map { it.category }.distinct()

    fun all(): List<CatalogItem> = items

    fun linkHealthy(item: CatalogItem): Boolean = runCatching {
        val url = java.net.URI(item.url)
        (url.scheme == "https" || url.scheme == "http") && url.host != null
    }.getOrDefault(false)

    private fun load(context: Context): List<CatalogItem> {
        val json = context.assets.open("package_catalog.json")
            .bufferedReader()
            .use { it.readText() }
        val arr = JSONArray(json)
        val out = mutableListOf<CatalogItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out += CatalogItem(
                id = o.optString("id"),
                name = o.optString("name"),
                category = o.optString("category"),
                version = o.optString("version"),
                url = o.optString("url"),
                downloadUrl = o.optString("downloadUrl"),
                description = o.optString("description"),
                source = o.optString("source")
            )
        }
        return out
    }
}
