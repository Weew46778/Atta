package com.arena.mineva.assistant

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import org.json.JSONObject
import java.io.File

/**
 * An embeddable Persian "voice pack".
 *
 * A voice pack is a ZIP placed in app storage with:
 *   voice_pack.json  -> {"voice":"Ava","phrases":{"hello":"hello.wav","greet-salam":"salam.wav"}}
 *   *.wav           -> the actual audio files, produced from the licensed female Persian voice.
 *
 * This lets a real female Persian voice be embedded/installed into the app, instead of
 * relying entirely on the device TTS engine. The retail audio itself is not committed into
 * this repository because of licensing/size, but the full import + playback path is live.
 */
class VoicePack(context: Context) {

    private val filesDir: File = context.filesDir

    private val manifestFile: File = File(filesDir, "voice_pack/voice_pack.json")
    private val audioDir: File = File(filesDir, "voice_pack")

    private var manifest: JSONObject? = null
    private var player: MediaPlayer? = null

    fun isAvailable(): Boolean {
        loadOnce()
        val m = manifest ?: return false
        val phrases = m.optJSONObject("phrases") ?: return false
        return phrases.length() > 0 && audioDir.exists()
    }

    fun voiceName(): String {
        loadOnce()
        return manifest?.optString("voice", "صدای داخل اپ").ifBlank { "صدای داخل اپ" }
    }

    fun phraseCount(): Int {
        loadOnce()
        return manifest?.optJSONObject("phrases")?.length() ?: 0
    }

    /**
     * Speaks the best-matching phrase. Returns true when the bundled audio was played.
     */
    fun speak(text: String): Boolean {
        loadOnce()
        val m = manifest ?: return false
        val phrases = m.optJSONObject("phrases") ?: return false
        val normalized = text.trim().lowercase()
        var key: String? = null
        val keys = phrases.keys().asSequence().toList()

        // 1) exact match
        key = keys.firstOrNull { it.lowercase() == normalized }
        // 2) prefix / phrase appears in text
        if (key == null) {
            key = keys.firstOrNull { normalized.contains(it.lowercase()) }
        }
        // 3) fallback to the first available phrase
        if (key == null) key = keys.firstOrNull()

        val fileName = key?.let { phrases.optString(it) } ?: return false
        if (fileName.isBlank()) return false
        val file = File(audioDir, fileName)
        if (!file.exists()) return false

        stop()
        return runCatching {
            player = MediaPlayer.create(context, Uri.fromFile(file))?.apply { start() }
            true
        }.getOrDefault(false)
    }

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
    }

    private fun loadOnce() {
        if (manifest != null) return
        if (!manifestFile.exists()) return
        runCatching {
            manifest = JSONObject(manifestFile.readText())
        }
    }
}
