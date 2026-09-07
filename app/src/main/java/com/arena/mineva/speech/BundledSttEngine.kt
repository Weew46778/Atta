package com.arena.mineva.speech

import android.content.Context
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.File

/**
 * Self-contained Persian STT using Vosk bundled in the APK.
 *
 * It records from the microphone and decodes fully on-device. If the model is not present
 * yet, [startListening] returns a clear message instead of pretending to be offline-ready.
 */
class BundledSttEngine(context: Context) {

    private val modelDir: File = SpeechModelStore.sttDir(context)
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var service: SpeechService? = null
    private var loaded = false

    fun isReady(): Boolean = File(modelDir, "am/final.mdl").exists() &&
        File(modelDir, "conf/mfcc.conf").exists()

    fun ensureLoaded(): Boolean {
        if (loaded && model != null) return true
        if (!isReady()) return false
        return runCatching {
            LibVosk.setLogLevel(LogLevel.WARN)
            model = Model(modelDir.absolutePath)
            loaded = true
            true
        }.getOrDefault(false)
    }

    /**
     * Starts listening. Returns null when listening started, otherwise a Persian error.
     * The callbacks are invoked from Vosk's background thread.
     */
    fun startListening(
        onPartial: (String) -> Unit,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ): String? {
        stop()
        if (!ensureLoaded()) {
            return "حالت تشخیص گفتار آفلاین هنوز آماده نیست. اول از تنظیمات صوتی مدل فارسی را دانلود کن."
        }
        return runCatching {
            val m = model ?: return "مدل تشخیص گفتار بارگذاری نشد."
            recognizer = Recognizer(m, 16000f)
            val rec = recognizer ?: return "شناسه صوتی ساخته نشد."
            service = SpeechService(rec, 16000f).apply {
                startListening(object : RecognitionListener {
                    override fun onPartialResult(hypothesis: String?) {
                        parseText(hypothesis)?.let(onPartial)
                    }

                    override fun onResult(hypothesis: String?) {
                        // Vosk delivers final text in the middle of a long phrase.
                        parseText(hypothesis)?.let(onResult)
                    }

                    override fun onFinalResult(hypothesis: String?) {
                        parseText(hypothesis)?.let(onResult)
                    }

                    override fun onError(exception: Exception?) {
                        onError(exception?.message ?: "خطای موتور تشخیص گفتار")
                    }

                    override fun onTimeout() {
                        onResult("")
                    }
                })
            }
            null
        }.getOrElse { e ->
            "خطا در راهاندازی تشخیص گفتار: ${e.message ?: "نامشخص"}"
        }
    }

    private fun parseText(json: String?): String? {
        if (json.isNullOrBlank()) return ""
        return runCatching {
            val obj = org.json.JSONObject(json)
            val text = obj.optString("text", "")
            if (text.isBlank()) "" else text
        }.getOrDefault("")
    }

    fun stop() {
        runCatching { service?.stop() }
        runCatching { service?.shutdown() }
        runCatching { recognizer?.close() }
        service = null
        recognizer = null
    }

    fun release() {
        stop()
        runCatching { model?.close() }
        model = null
        loaded = false
    }
}
