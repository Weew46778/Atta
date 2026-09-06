package com.arena.mineva.assistant

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Installs a Persian voice pack ZIP from a user-selected file into app storage.
 *
 * Expected ZIP layout:
 *   voice_pack.json
 *   hello.wav
 *   salam.wav
 *   ...
 *
 * The ZIP may store the manifest at the archive root or inside a top-level folder
 * (for Android's DocumentsProvider which sometimes wraps with a folder).
 */
object VoicePackInstaller {

    data class InstallResult(val success: Boolean, val detail: String)

    private val acceptedAudio = listOf("wav", "ogg", "mp3", "m4a", "flac")

    fun install(context: Context, uri: Uri): InstallResult {
        return runCatching {
            val targetDir = File(context.filesDir, "voice_pack")
            targetDir.deleteRecursively()
            targetDir.mkdirs()

            val input = context.contentResolver.openInputStream(uri)
                ?: return InstallResult(false, "نمی‌توانم فایل را باز کنم.")
            val zis = ZipInputStream(input.buffered())

            val manifestJsons = mutableListOf<String>()
            val audioFiles = mutableListOf<String>()

            while (true) {
                val entry = zis.nextEntry ?: break
                val name = entry.name
                if (entry.isDirectory || name.isBlank()) continue

                // Reject path traversal.
                if (name.contains("..")) continue

                val base = name.substringAfterLast('/')
                if (base.isBlank()) continue

                val lower = base.lowercase()
                when {
                    lower == "voice_pack.json" || lower.endsWith(".json") -> {
                        // Read manifest content first (small file).
                        val bytes = zis.readBytes()
                        manifestJsons += String(bytes, Charsets.UTF_8)
                        // Also write it so playback can use the on-disk manifest.
                        val out = FileOutputStream(File(targetDir, base))
                        out.use { it.write(bytes) }
                    }
                    lower.substringAfterLast('.').lowercase() in acceptedAudio -> {
                        val out = FileOutputStream(File(targetDir, base))
                        out.use { zis.copyTo(it) }
                        audioFiles += base
                    }
                    // Anything else (licence files, README, etc.) is kept under its base name
                    // so the pack can carry attribution text without harming playback.
                    else -> {
                        val out = FileOutputStream(File(targetDir, base))
                        out.use { zis.copyTo(it) }
                    }
                }
                zis.closeEntry()
            }
            zis.close()

            val manifestFile = File(targetDir, "voice_pack.json")
            if (!manifestFile.exists() && manifestJsons.isEmpty()) {
                targetDir.deleteRecursively()
                return InstallResult(false, "فایل voice_pack.json داخل ZIP پیدا نشد.")
            }
            if (!manifestFile.exists()) {
                manifestFile.writeText(manifestJsons.first())
            }

            val m = JSONObject(manifestJsons.firstOrNull() ?: manifestFile.readText())
            val phrases = m.optJSONObject("phrases")
            if (phrases == null || phrases.length() == 0) {
                targetDir.deleteRecursively()
                return InstallResult(false, "بستهٔ صوتی هیچ فریزی (phrases) ندارد.")
            }

            // Verify every referenced audio file exists.
            val missing = phrases.keys().asSequence()
                .mapNotNull { phrases.optString(it) }
                .filter { it.isNotBlank() }
                .filter { !File(targetDir, it.substringAfterLast('/')).exists() }
                .toList()
            if (missing.isNotEmpty()) {
                targetDir.deleteRecursively()
                return InstallResult(
                    false,
                    "فایل‌های صوتی «${missing.take(4).joinToString("، ")}» در بسته وجود ندارند."
                )
            }

            val foundAudioCount = if (audioFiles.isNotEmpty()) audioFiles.size else phrases.length()
            InstallResult(
                true,
                "صدای «${m.optString("voice", "Ava")}» با ${phrases.length()} فریز (${foundAudioCount} فایل صوتی) نصب شد."
            )
        }.getOrElse { e ->
            InstallResult(false, "خطا در نصب بستهٔ صوتی: ${e.message ?: "نامشخص"}")
        }
    }
}
