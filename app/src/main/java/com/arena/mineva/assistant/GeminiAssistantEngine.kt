package com.arena.mineva.assistant

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lightweight Gemini (Generative Language API) client built on HttpURLConnection so the
 * first MVP has zero heavy network dependencies.
 */
object GeminiAssistantEngine {

    fun ask(
        apiKey: String,
        system: String,
        question: String,
        timeoutMs: Int = 25_000
    ): String {
        val url = URL(
            "https://generativelanguage.googleapis.com/v1beta/models/" +
                "gemini-1.5-flash:generateContent?key=$apiKey"
        )
        val body = JSONObject()
            .put(
                "system_instruction",
                JSONObject().put(
                    "parts",
                    JSONArray().put(JSONObject().put("text", system))
                )
            )
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", question))
                    )
                )
            )
            .put(
                "generationConfig",
                JSONObject().put("temperature", 0.6).put("maxOutputTokens", 1024)
            )

        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.doInput = true
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

        if (code !in 200..299) {
            val msg = runCatching { JSONObject(raw).optJSONObject("error")?.optString("message") }
                .getOrNull().orEmpty()
            throw RuntimeException("HTTP $code: ${msg.ifBlank { raw.take(160) }}")
        }

        val response = JSONObject(raw)
        val candidates = response.optJSONArray("candidates") ?: JSONArray()
        if (candidates.length() == 0) throw RuntimeException("پاسخ خالی بود.")
        val candidate = candidates.getJSONObject(0)
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            sb.append(parts.getJSONObject(i).optString("text"))
        }
        return sb.toString().trim()
    }
}
