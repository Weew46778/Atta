package com.arena.mineva.server

import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Validates and downloads server packages over HTTPS/HTTP.
 *
 * The validator does a real network check: status code, content type and size.
 * Files whose page returns HTML (e.g. a web page rather than a jar/zip) are marked as
 * "reference only" and are not auto-deployed.
 */
object PackageLinkValidator {

    data class LinkHealth(
        val ok: Boolean,
        val code: Int,
        val contentType: String,
        val size: Long,
        val isDirectDownload: Boolean,
        val detail: String
    )

    private const val USER_AGENT = "MineAva/0.1 Android"

    fun check(url: String, timeoutMs: Int = 15_000): LinkHealth {
        if (url.isBlank()) {
            return LinkHealth(false, 0, "", 0, false, "لینک خالی است.")
        }
        runCatching { java.net.URI(url) }.getOrElse {
            return LinkHealth(false, 0, "", 0, false, "آدرس نامعتبر است.")
        }

        // 1) Try HEAD first.
        try {
            val conn = open(url, "HEAD", timeoutMs)
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", USER_AGENT)
            val code = conn.responseCode
            val type = conn.contentType.orEmpty()
            val size = conn.contentLengthLong.coerceAtLeast(0)
            conn.disconnect()
            if (code in 200..299) {
                val direct = !isHtml(type)
                return LinkHealth(
                    ok = true,
                    code = code,
                    contentType = type,
                    size = size,
                    isDirectDownload = direct,
                    detail = if (direct) "لینک سالم و قابل دانلود." else "صفحه مرجع؛ لینک مستقیم ندارد."
                )
            }
        } catch (_: Exception) {
            // Some hosts reject HEAD; fall through to GET.
        }

        // 2) Fallback GET with Range and read a small sample.
        return try {
            val conn = open(url, "GET", timeoutMs)
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Range", "bytes=0-4095")
            val code = conn.responseCode
            val type = conn.contentType.orEmpty()
            val sample = if (code in 200..299) conn.inputStream.use { readSample(it) } else ""
            val direct = !isHtml(type) && sample.isNotBlank()
            conn.disconnect()
            LinkHealth(
                ok = code in 200..299,
                code = code,
                contentType = type,
                size = 0,
                isDirectDownload = direct,
                detail = if (code in 200..299) "لینک پاسخ میدهد." else "خطای HTTP $code."
            )
        } catch (e: Exception) {
            LinkHealth(false, 0, "", 0, false, "خطا در بررسی: ${e.message}")
        }
    }

    fun download(
        url: String,
        target: File,
        timeoutMs: Int = 60_000,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): Long {
        val conn = open(url, "GET", timeoutMs)
        conn.instanceFollowRedirects = true
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.setRequestProperty("User-Agent", USER_AGENT)
        val code = conn.responseCode
        if (code !in 200..299) {
            conn.disconnect()
            throw RuntimeException("HTTP $code")
        }
        val total = conn.contentLengthLong
        target.outputStream().use { out ->
            conn.inputStream.use { input ->
                val buf = ByteArray(32 * 1024)
                var count = 0L
                while (true) {
                    val read = input.read(buf)
                    if (read < 0) break
                    out.write(buf, 0, read)
                    count += read
                    if (total > 0) onProgress(count, total)
                }
            }
        }
        conn.disconnect()
        return target.length()
    }

    fun fileNameFromUrl(url: String, fallback: String): String {
        val raw = runCatching { URL(url).path }.getOrNull() ?: return fallback
        val name = raw.substringAfterLast('/').ifBlank { fallback }
        return runCatching { java.net.URLDecoder.decode(name, "UTF-8") }.getOrElse { name }
    }

    private fun open(url: String, method: String, timeoutMs: Int): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        return conn
    }

    private fun isHtml(contentType: String): Boolean {
        val type = contentType.lowercase()
        return type.contains("text/html") || type.contains("text/plain") ||
            type.contains("application/xhtml") || type.contains("application/json")
    }

    private fun readSample(input: InputStream): String {
        val buf = ByteArray(512)
        val n = input.read(buf)
        return if (n > 0) String(buf, 0, n) else ""
    }
}
