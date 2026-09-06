package com.arena.mineva.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Wraps Android SpeechRecognizer for Persian voice input.
 */
class SpeechRecognitionManager(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(
        locale: Locale = Locale("fa", "IR"),
        onPartial: (String) -> Unit = {},
        onResult: (String) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        destroy()
        if (!isAvailable()) {
            onError("خدمت تشخیص گفتار روی این دستگاه نصب نیست.")
            return
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    onError(errorName(error))
                }

                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    onResult(text)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.let(onPartial)
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale.toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            }
            startListening(intent)
        }
    }

    private fun errorName(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "مشکل ضبط صدا."
        SpeechRecognizer.ERROR_CLIENT -> "کلاینت تشخیص گفتار خطا داد."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "دسترسی میکروفن داده نشده است."
        SpeechRecognizer.ERROR_NETWORK -> "برای تشخیص گفتار به اینترنت نیاز است."
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "تشخیص گفتار مهلت شبکه تمام شد."
        SpeechRecognizer.ERROR_NO_MATCH -> "متوجه نشدم، دوباره بگو."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "تشخیص گفتار مشغول است."
        SpeechRecognizer.ERROR_SERVER -> "خطای سرور تشخیص گفتار."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "صدایی نشنیدم."
        else -> "خطای تشخیص گفتار ($error)."
    }

    fun stop() {
        recognizer?.stopListening()
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }
}
