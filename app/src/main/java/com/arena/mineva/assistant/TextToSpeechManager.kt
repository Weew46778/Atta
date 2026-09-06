package com.arena.mineva.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Persian female-voice TTS wrapper.
 *
 * Android's TextToSpeech picks the device's installed engine. If a Persian voice is not
 * available the manager falls back gracefully and exposes [persianVoiceAvailable].
 * Bundling a proprietary TTS engine inside the APK is a licensing/size decision that is
 * intentionally left to a later build; this first version uses the system engine.
 */
class TextToSpeechManager(private val context: Context) {

    private var tts: TextToSpeech? = null
    var ready: Boolean = false
        private set
    var persianVoiceAvailable: Boolean = false
        private set

    fun init(onReady: () -> Unit) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                persianVoiceAvailable = configurePersian()
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
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            val fallback = engine.setLanguage(Locale("fa"))
            return fallback != TextToSpeech.LANG_MISSING_DATA && fallback != TextToSpeech.LANG_NOT_SUPPORTED
        }
        return true
    }

    fun speak(text: String, interrupt: Boolean = true) {
        val engine = tts ?: return
        if (!ready) return
        if (interrupt) engine.stop()
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mineava_${System.currentTimeMillis()}")
    }

    fun getAvailableLanguages(): List<String> {
        val engine = tts ?: return emptyList()
        return runCatching {
            engine.availableLanguages.map { it.displayName }
        }.getOrDefault(emptyList())
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
