package com.arena.mineva.assistant

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/**
 * Generates a small, fully-functional *sample* voice pack inside app storage.
 *
 * This is deliberately NOT a licensed Persian female voice. It writes short, audible
 * WAV tones so the full bundled-voice import + playback + toggle path can be tested
 * without any external file. To get the real voice, either:
 *
 *  1. Import `VoicePackSample.install(context)`'s produced ZIP, then replace its WAV
 *     files with the licensed female Persian recordings, keeping the same filenames.
 *  2. Or build a ZIP following docs/VOICE_PACK_GUIDE.md and import it from storage.
 */
object VoicePackSample {

    data class SampleResult(val success: Boolean, val detail: String)

    private val phrases = linkedMapOf(
        "سلام" to "salam.wav",
        "سلام! من آوا هستم" to "ava-hello.wav",
        "خوش آمدی" to "welcome.wav",
        "سرور روشن شد" to "server-on.wav",
        "خاموش شد" to "server-off.wav",
        "منابع آماده است" to "resources.wav",
        "بکاپ آماده شد" to "backup.wav",
        "خروجی آماده است" to "output.wav",
        "خطا" to "error.wav",
        "ارسال شد" to "sent.wav"
    )

    fun install(context: Context): SampleResult {
        return runCatching {
            val dir = File(context.filesDir, "voice_pack")
            dir.deleteRecursively()
            dir.mkdirs()

            phrases.forEach { (_, fileName) ->
                writeToneWav(File(dir, fileName))
            }

            val json = JSONObject()
            json.put("voice", "Ava (نمونه)")
            json.put("note", "Sample test pack with WAV tones. Replace the .wav files with the licensed female Persian recordings.")
            val p = JSONObject()
            phrases.forEach { (phrase, file) -> p.put(phrase, file) }
            json.put("phrases", p)

            File(dir, "voice_pack.json").writeText(json.toString(2))
            SampleResult(true, "بستهٔ نمونهٔ آوا در اپ ساخته شد (${phrases.size} فریز). این صدا فقط برای تست مسیر پخش است.")
        }.getOrElse { e ->
            SampleResult(false, "ساخت بستهٔ نمونه ناموفق بود: ${e.message ?: "نامشخص"}")
        }
    }

    /** Build the same sample pack as a ZIP in app-specific Download storage. */
    fun exportZip(context: Context): File? {
        return runCatching {
            val dir = File(context.filesDir, "voice_pack")
            install(context)
            // App-scoped external dir: works on scoped storage without WRITE_EXTERNAL_STORAGE.
            val downloads = File(
                context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS)
                    ?: File(context.filesDir, "downloads"),
                "MineAva"
            )
            downloads.mkdirs()
            val zip = File(downloads, "voice-pack-ava-sample.zip")
            FileOutputStream(zip).use { out ->
                java.util.zip.ZipOutputStream(out).use { zos ->
                    dir.listFiles()?.sortedBy { it.name }?.forEach { file ->
                        zos.putNextEntry(java.util.zip.ZipEntry(file.name))
                        file.inputStream().use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
            zip
        }.getOrNull()
    }

    // ------------------------------------------------------------------
    // Minimal PCM WAV writer (8 kHz mono 16-bit). Short two-tone "chirp"
    // so the user can hear the phrase boundaries.
    // ------------------------------------------------------------------
    private fun writeToneWav(file: File) {
        val sampleRate = 8000
        val durationSec = 0.45
        val count = (sampleRate * durationSec).toInt()
        val dataSize = count * 2
        val out = FileOutputStream(file)

        // RIFF header
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        out.writeIntLe(36 + dataSize)
        out.write("WAVE".toByteArray(Charsets.US_ASCII))

        // fmt chunk
        out.write("fmt ".toByteArray(Charsets.US_ASCII))
        out.writeIntLe(16)
        out.writeShortLe(1)        // PCM
        out.writeShortLe(1)        // mono
        out.writeIntLe(sampleRate)
        out.writeIntLe(sampleRate * 2)
        out.writeShortLe(2)
        out.writeShortLe(16)

        // data chunk
        out.write("data".toByteArray(Charsets.US_ASCII))
        out.writeIntLe(dataSize)

        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            // two-tone chirp: 620 Hz then 940 Hz; 8 ms gap at the end.
            val freq = if (phase < 1.0) 620.0 else 940.0
            phase += 1.0 / (60.0 * 0.45)
            val sample = (Math.sin(2.0 * Math.PI * freq * t) * 12000.0).toInt()
            out.writeShortLe(sample.coerceIn(-32768, 32767))
        }
        out.close()
    }

    private fun FileOutputStream.writeIntLe(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
        write((value shr 16) and 0xFF)
        write((value shr 24) and 0xFF)
    }

    private fun FileOutputStream.writeShortLe(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
    }
}
