package com.arena.mineva.server

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Self-updating package catalog.
 *
 * For GitHub items it queries the latest release and picks the first jar/zip asset.
 * For Modrinth items it queries versions and picks the newest release (or beta) whose
 * supported game versions overlap the requested server version. Results are written to
 * filesDir/package_catalog_user.json so the app's next catalog load uses them automatically.
 */
object PackageCatalogUpdater {

    data class UpdateResult(
        val updated: List<String>,
        val failed: List<String>,
        val incompatible: List<String>
    ) {
        fun summary(): String =
            "به‌روزرسانی شد: ${updated.size} | ناموفق: ${failed.size} | ناسازگار: ${incompatible.size}"
    }

    private const val USER_AGENT = "MineAva/0.1 Android"

    fun updateAll(
        context: Context,
        serverVersion: String,
        limit: Int = 20,
        onProgress: (String) -> Unit = {}
    ): UpdateResult {
        val base = PackageCatalog(context)
        val updated = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val incompatible = mutableListOf<String>()
        val targetVersion = serverVersion.ifBlank { "1.21" }

        base.all().filter { it.isAutoUpdatable() }
            .take(limit)
            .forEach { item ->
                onProgress("چک ${item.name}...")
                val resolved = runCatching { resolve(item, targetVersion) }
                    .getOrElse { e ->
                        onProgress("ناموفق ${item.name}: ${e.message}")
                        failed += item.name
                        null
                    }
                if (resolved != null) {
                    if (resolved.isCompatibleWith(targetVersion)) {
                        updated += item.name
                        onProgress("✓ ${item.name} به ${resolved.version} به‌روز شد.")
                    } else {
                        incompatible += item.name
                        onProgress("✖ ${item.name} ناسازگار با نسخه سرور.")
                    }
                }
            }

        val updatedItems = mutableListOf<JSONObject>()
        base.all().forEach { item ->
            val updatedOne = updated.contains(item.name)
            if (updatedOne) {
                val resolved = runCatching { resolve(item, targetVersion) }.getOrNull()
                if (resolved != null) {
                    updatedItems += item.toJson(resolved)
                } else {
                    updatedItems += item.toJson(item)
                }
            } else {
                updatedItems += item.toJson(item)
            }
        }

        val userFile = File(context.filesDir, "package_catalog_user.json")
        runCatching {
            userFile.parentFile?.mkdirs()
            userFile.writeText(JSONArray(updatedItems).toString())
        }

        return UpdateResult(updated, failed, incompatible)
    }

    private fun resolve(item: CatalogItem, serverVersion: String): CatalogItem {
        val resolved = when (item.apiType) {
            "github" -> resolveGitHub(item, serverVersion)
            "modrinth" -> resolveModrinth(item, serverVersion)
            else -> item
        }
        if (!resolved.hasDirectDownload()) {
            throw RuntimeException("لینک مستقیم پیدا نشد")
        }
        return resolved
    }

    private fun resolveGitHub(item: CatalogItem, serverVersion: String): CatalogItem {
        val url = "https://api.github.com/repos/${item.apiOwner}/${item.apiRepo}/releases/latest"
        val json = getJson(url)
        val release = JSONObject(json)
        val version = release.optString("tag_name").ifBlank { item.version }
        val assets = release.optJSONArray("assets") ?: JSONArray()
        var downloadUrl = ""
        var name = ""
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val fname = a.optString("name").lowercase()
            if (fname.endsWith(".jar") || fname.endsWith(".zip")) {
                downloadUrl = a.optString("browser_download_url", "")
                name = a.optString("name")
                break
            }
        }
        if (downloadUrl.isBlank()) throw RuntimeException("asset یافت نشد")
        return item.copy(
            version = version,
            downloadUrl = downloadUrl,
            url = release.optString("html_url", item.url)
        )
    }

    private fun resolveModrinth(item: CatalogItem, serverVersion: String): CatalogItem {
        val url = "https://api.modrinth.com/v2/project/${item.apiSlug}/version"
        val json = getJson(url)
        val arr = JSONArray(json)
        // Prefer releases, then betas, and at least one matching game version.
        val candidates = (0 until arr.length()).map { arr.getJSONObject(it) }
        val matching = candidates.filter { o ->
            val game = o.optJSONArray("game_versions").stringList()
            game.any { it.startsWith(serverVersion.take(6)) || serverVersion.startsWith(it.take(6)) }
        }
        val pool = (matching + candidates).distinctBy { it.optString("id") }
        val chosen = pool.firstOrNull { it.optString("version_type") == "release" }
            ?: pool.firstOrNull()
            ?: throw RuntimeException("نسخه ای یافت نشد")
        val files = chosen.optJSONArray("files") ?: JSONArray()
        val file = if (files.length() > 0) files.getJSONObject(0) else throw RuntimeException("فایل یافت نشد")
        return item.copy(
            version = chosen.optString("version_number", item.version),
            downloadUrl = file.optString("url", item.downloadUrl),
            url = "https://modrinth.com/project/${item.apiSlug}"
        )
    }

    private fun getJson(url: String, timeoutMs: Int = 20_000): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Accept", "application/json")
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            throw RuntimeException("HTTP $code")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        conn.disconnect()
        return body
    }

    private fun JSONArray?.stringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).map { getString(it) }
    }

    private fun CatalogItem.toJson(updated: CatalogItem): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("category", category)
        put("version", updated.version)
        put("url", updated.url)
        put("downloadUrl", updated.downloadUrl)
        put("description", description)
        put("source", updated.source)
        put("apiType", updated.apiType)
        put("apiOwner", updated.apiOwner)
        put("apiRepo", updated.apiRepo)
        put("apiSlug", updated.apiSlug)
        put("compatiblePrefixes", JSONArray(updated.compatiblePrefixes))
    }
}
