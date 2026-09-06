package com.arena.mineva.assistant

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads and installs a Persian female voice-pack ZIP from the internet.
 *
 * The pack source is deliberately configurable: a project may host a licensed female
 * Persian pack (ZIP) at a known URL, or a user can paste any direct ZIP URL. The app
 * health-checks the URL before downloading so broken links are not offered. If no hosted
 * pack is configured, the catalog explains exactly what is missing instead of pretending
 * that a voice already exists.
 */
object VoicePackDownloader {

    data class CatalogEntry(
        val id: String,
        val name: String,
        val source: String,
        val license: String,
        val type: String,
        val notes: String
    )

    data class DownloadResult(val success: Boolean, val detail: String, val path: String = "")

    private const val CATALOG_FILE = "voice_pack_catalog.json"
    private const val REMOTE_CATALOG_URL = "https://raw.githubusercontent.com/Weew46778/Atta/main/app/src/main/assets/voice_pack_catalog.json"
    private const val MAX_BYTES = 64L * 1024 * 1024 // 64 MB safety cap

    fun localCatalog(context: Context): List<CatalogEntry> {
        return runCatching {
            val text = context.assets.open(CATALOG_FILE).bufferedReader().readText()
            parseCatalog(text)
        }.getOrElse { emptyList() }
    }

    fun remoteCatalog(context: Context): String {
        return runCatching {
            val conn = URL(REMOTE_CATALOG_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.requestMethod = "GET"
            val code = conn.responseCode
            if (code != 200) return "کاتالوگ آنلاین در دسترس نیست (HTTP $code)."
            val text = conn.inputStream.bufferedReader().readText()
            val entries = parseCatalog(text)
            if (entries.isEmpty()) return "کاتالوگ آنلاین خالی است."
            renderCatalog(entries)
        }.getOrElse { e ->
            "کاتالوگ آنلاین در دسترس نیست: ${e.message ?: "نامشخص"}"
        }
    }

    fun download(context: Context, url: String): DownloadResult {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return DownloadResult(false, "لینک باید با http:// یا https:// شروع شود.")
        }
        return runCatching {
            val conn = URL(trimmed).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 30_000
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = true

            val code = conn.responseCode
            if (code != 200) return DownloadResult(false, "دانلود ناموفق: HTTP $code")

            val length = conn.contentLengthLong
            if (length > MAX_BYTES) return DownloadResult(false, "حجم بسته از حد مجاز ($MAX_BYTES بایت) بیشتر است.")

            val tmp = File(context.cacheDir, "voice_pack_download.zip")
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) return DownloadResult(false, "حجم بسته از حد مجاز بیشتر است.")
                        out.write(buffer, 0, n)
                    }
                }
            }
            conn.disconnect()

            if (tmp.length() < 100) return DownloadResult(false, "فایل دانلودشده خالی/کم‌حجم است.")

            val installed = VoicePackInstaller.install(context, Uri.fromFile(tmp))
            tmp.delete()
            if (installed.success) DownloadResult(true, installed.detail, tmp.absolutePath)
            else DownloadResult(false, installed.detail)
        }.getOrElse { e ->
            DownloadResult(false, "خطا در دانلود بستهٔ صوتی: ${e.message ?: "نامشخص"}")
        }
    }

    private fun parseCatalog(json: String): List<CatalogEntry> {
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            CatalogEntry(
                id = o.optString("id"),
                name = o.optString("name"),
                source = o.optString("source"),
                license = o.optString("license", "نامشخص"),
                type = o.optString("type", "zip"),
                notes = o.optString("notes")
            )
        }
    }

    private fun renderCatalog(entries: List<CatalogEntry>): String {
        val sb = StringBuilder("کاتالوگ بستهٔ صوتی:\n")
        entries.forEach { e ->
            sb.append("• ${e.name} [${e.type}]\n")
            sb.append("  مجوز: ${e.license}\n")
            if (e.source.isNotBlank()) sb.append("  لینک: ${e.source}\n")
            if (e.notes.isNotBlank()) sb.append("  نکته: ${e.notes}\n")
        }
        return sb.toString().trim()
    }
}
