package com.arena.mineva.speech

import android.content.Context
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Owns the offline Persian speech models used by the bundled STT (Vosk) and TTS (Piper
 * / Sherpa-ONNX) engines.
 *
 * The speech runtime is inside the APK. The voice/acoustic data is downloaded once by the
 * app itself and stored in the app's private storage, so after that it works fully offline
 * and does not depend on the device's TTS service or Google speech recognizer.
 */
object SpeechModelStore {

    private const val VOSK_FA_URL =
        "https://alphacephei.com/vosk/models/vosk-model-small-fa-0.4.zip"
    private const val PIPER_FA_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-fa_IR-amir-medium.tar.bz2"

    private const val PIPER_MODEL_NAME = "fa_IR-amir-medium.onnx"
    private const val PIPER_TOKENS_NAME = "tokens.txt"
    private const val PIPER_ESPEAK_DIR = "espeak-ng-data"

    data class ModelStatus(
        val sttReady: Boolean = false,
        val ttsReady: Boolean = false,
        val sttPath: String = "",
        val ttsPath: String = "",
        val detail: String = ""
    )

    data class DownloadResult(val success: Boolean, val detail: String)

    fun status(context: Context): ModelStatus {
        val stt = sttDir(context)
        val tts = ttsDir(context)
        val sttReady = File(stt, "am/final.mdl").exists() &&
            File(stt, "conf/mfcc.conf").exists()
        val ttsReady = File(tts, PIPER_MODEL_NAME).exists() &&
            File(tts, PIPER_TOKENS_NAME).exists() &&
            File(tts, PIPER_ESPEAK_DIR).isDirectory
        val detail = buildString {
            append(if (sttReady) "تشخیص گفتار: آماده ✓\n" else "تشخیص گفتار: دانلود لازم است.\n")
            append(if (ttsReady) "گفتار پاسخ: آماده ✓\n" else "گفتار پاسخ: دانلود لازم است.")
        }
        return ModelStatus(sttReady, ttsReady, stt.absolutePath, tts.absolutePath, detail)
    }

    fun sttDir(context: Context): File =
        File(context.filesDir, "speech/stt").apply { mkdirs() }

    fun ttsDir(context: Context): File =
        File(context.filesDir, "speech/tts/vits-piper-fa_IR-amir-medium").apply { mkdirs() }

    /**
     * Downloads and extracts both Serbian... no, both Persian models into app storage.
     * This is intentionally called from a background thread.
     */
    fun download(context: Context, onProgress: (String) -> Unit = {}): DownloadResult {
        return runCatching {
            onProgress("دانلود مدل تشخیص گفتار (حدود ۴۷ مگابایت)...")
            val sttTmp = File(context.cacheDir, "vosk-fa.zip")
            downloadFile(VOSK_FA_URL, sttTmp, onProgress)
            extractStt(sttTmp, sttDir(context))
            sttTmp.delete()

            onProgress("دانلود مدل صدای آوا (حدود ۴۰ مگابایت)...")
            val ttsTmp = File(context.cacheDir, "piper-fa.tar.bz2")
            downloadFile(PIPER_FA_URL, ttsTmp, onProgress)
            extractTts(ttsTmp, ttsDir(context))
            ttsTmp.delete()

            val s = status(context)
            if (s.sttReady && s.ttsReady) {
                DownloadResult(true, "مدلهای صوتی کامل شدند و از حالا کاملاً آفلاین کار میکنند.")
            } else {
                DownloadResult(false, "دانلود کامل شد ولی یکی از فایلها سالم نیست. دوباره امتحان کن.")
            }
        }.getOrElse { e ->
            DownloadResult(false, "خطا در دانلود مدل صوتی: ${e.message ?: "نامشخص"}")
        }
    }

    private fun downloadFile(url: String, target: File, onProgress: (String) -> Unit) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        conn.instanceFollowRedirects = true
        conn.requestMethod = "GET"
        val code = conn.responseCode
        if (code !in 200..299) {
            throw IllegalStateException("HTTP $code در دانلود مدل")
        }
        val total = conn.contentLengthLong
        var read = 0L
        conn.inputStream.use { input ->
            FileOutputStream(target).use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    read += n
                    if (total > 0 && read % (512 * 1024) < n) {
                        onProgress("${read / (1024 * 1024)}/${total / (1024 * 1024)} مگابایت")
                    }
                }
            }
        }
        conn.disconnect()
        if (target.length() < 1000) throw IllegalStateException("فایل دانلودشده کمحجم است.")
    }

    private fun extractStt(zip: File, outDir: File) {
        outDir.deleteRecursively()
        outDir.mkdirs()
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (entry.isDirectory) {
                    zis.closeEntry()
                    continue
                }
                val parts = entry.name.split('/').drop(1).filter { it.isNotBlank() }
                if (parts.isEmpty() || parts.first().contains("..")) {
                    zis.closeEntry()
                    continue
                }
                val dest = File(outDir, parts.joinToString("/"))
                dest.parentFile?.mkdirs()
                FileOutputStream(dest).use { zis.copyTo(it) }
                zis.closeEntry()
            }
        }
    }

    private fun extractTts(bz2: File, outDir: File) {
        outDir.deleteRecursively()
        outDir.mkdirs()
        BZip2CompressorInputStream(bz2.inputStream().buffered()).use { bz ->
            TarArchiveInputStream(bz).use { tar ->
                while (true) {
                    val entry = tar.nextEntry as? TarArchiveEntry ?: break
                    // The archive has a single top-level folder
                    // "vits-piper-fa_IR-amir-medium/..."; strip it.
                    val parts = entry.name.split('/').filter { it.isNotBlank() }
                    if (parts.size < 2) {
                        continue
                    }
                    val rel = parts.drop(1).joinToString("/")
                    if (rel.isBlank() || rel.contains("..")) continue
                    val dest = File(outDir, rel)
                    if (entry.isDirectory) {
                        dest.mkdirs()
                    } else {
                        dest.parentFile?.mkdirs()
                        FileOutputStream(dest).use { tar.copyTo(it) }
                    }
                }
            }
        }
    }
}
