package com.arena.mineva.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.arena.mineva.AppPrefs
import java.util.Locale

/**
 * Persian TTS wrapper.
 *
 * Android's TextToSpeech picks the device's installed engine. This manager is a bit smarter:
 * it prefers a female Persian voice when one is available (based on voice metadata / gender
 * / name / gender hint) and lets the user inspect and install Persian voice data from the
 * Voice Settings screen. A fully embedded proprietary engine is a separate licensing/size
 * decision; this layer is where it would be plugged in later.
 */
class TextToSpeechManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    private val voicePack = VoicePack(context)
    var ready: Boolean = false
        private set
    var persianVoiceAvailable: Boolean = false
        private set
    var femalePersianSelected: Boolean = false
        private set

    var preferFemale: Boolean
        get() = AppPrefs.preferFemaleVoice
        set(value) {
            AppPrefs.preferFemaleVoice = value
            configurePersian()
        }
    var speechRate: Float
        get() = AppPrefs.speechRate
        set(value) {
            AppPrefs.speechRate = value
            tts?.setSpeechRate(value)
        }

    fun init(onReady: () -> Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                persianVoiceAvailable = configurePersian()
                onReady()
            } else {
                ready = false
                onReady()
            }
        }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {}
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {}
            override fun onError(utteranceId: String?, errorCode: Int) {}
        })
    }

    private fun configurePersian(): Boolean {
        val engine = tts ?: return false
        val fa = Locale("fa", "IR")
        val result = engine.setLanguage(fa)
        val supported = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED

        val saved = AppPrefs.selectedPersianVoice
        val voices = availableVoices().filter { it.locale.language.equals("fa", ignoreCase = true) }

        val chosen = when {
            saved.isNotBlank() -> voices.firstOrNull { it.name == saved }
            preferFemale -> voices.firstOrNull { voice ->
                val name = (voice.name ?: "").lowercase()
                name.contains("female") || name.contains("زهرا") ||
                    name.contains("zahra") || name.contains("ara")
            }
            else -> voices.firstOrNull()
        }
        if (chosen != null) {
            engine.voice = chosen
            femalePersianSelected = (chosen.name ?: "").lowercase().contains("female") ||
                (chosen.name ?: "").contains("زهرا") || (chosen.name ?: "").contains("زرین")
        }
        engine.setSpeechRate(speechRate)
        return supported
    }

    fun setVoice(voice: Voice) {
        tts?.voice = voice
    }

    fun availableVoices(): List<Voice> {
        val engine = tts ?: return emptyList()
        return runCatching { engine.voices.orEmpty().toList() }.getOrDefault(emptyList())
    }

    fun persianVoices(): List<Voice> =
        availableVoices().filter { it.locale.language.equals("fa", ignoreCase = true) }

    /**
     * When a bundled voice pack is installed and enabled, matching phrases are played
     * directly from the embedded audio. Falls back to system TTS otherwise.
     */
    fun speak(text: String, interrupt: Boolean = true) {
        if (AppPrefs.useBundledVoice && voicePack.isAvailable() && voicePack.speak(text)) {
            return
        }
        val engine = tts ?: return
        if (!ready) return
        if (interrupt) engine.stop()
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mineava_${System.currentTimeMillis()}")
    }

    fun getAvailableLanguages(): List<String> {
        val engine = tts ?: return emptyList()
        return runCatching { engine.availableLanguages.map { it.displayName } }.getOrDefault(emptyList())
    }

    fun stop() {
        tts?.stop()
    }

    fun bundledVoiceStatus(): String {
        if (!voicePack.isAvailable()) return "بستهٔ صوتی داخل اپ نصب نشده است."
        return "صدای «${voicePack.voiceName()}» — ${voicePack.phraseCount()} فریز"
    }

    fun installVoicePack(uri: android.net.Uri): VoicePackInstaller.InstallResult =
        VoicePackInstaller.install(context, uri)

    fun shutdown() {
        voicePack.stop()
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
