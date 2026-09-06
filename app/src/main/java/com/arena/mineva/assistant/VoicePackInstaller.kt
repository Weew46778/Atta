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
 */
object VoicePackInstaller {

    data class InstallResult(val success: Boolean, val detail: String)

    fun install(context: Context, uri: Uri): InstallResult {
        return runCatching {
            val targetDir = File(context.filesDir, "voice_pack")
            targetDir.deleteRecursively()
            targetDir.mkdirs()

            val input = context.contentResolver.openInputStream(uri)
                ?: return InstallResult(false, "نمی‌توانم فایل را باز کنم.")
            val zis = ZipInputStream(input.buffered())

            var manifestFound = false
            while (true) {
                val entry = zis.nextEntry ?: break
                val name = entry.name.substringAfterLast('/')
                if (name.isBlank() || entry.isDirectory) continue
                if (name.endsWith(".json")) {
                    manifestFound = true
                }
                val out = FileOutputStream(File(targetDir, name))
                out.use { zis.copyTo(it) }
                zis.closeEntry()
            }
            zis.close()

            val manifestFile = File(targetDir, "voice_pack.json")
            if (!manifestFile.exists()) {
                targetDir.deleteRecursively()
                return InstallResult(false, "فایل voice_pack.json داخل ZIP پیدا نشد.")
            }
            val m = JSONObject(manifestFile.readText())
            val phrases = m.optJSONObject("phrases")
            if (phrases == null || phrases.length() == 0) {
                targetDir.deleteRecursively()
                return InstallResult(false, "بستهٔ صوتی هیچ فریزی (phrases) ندارد.")
            }

            InstallResult(true, "صدای «${m.optString("voice", "Ava")}» با ${phrases.length()} فریز نصب شد.")
        }.getOrElse { e ->
            InstallResult(false, "خطا در نصب بستهٔ صوتی: ${e.message ?: "نامشخص"}")
        }
    }
}
