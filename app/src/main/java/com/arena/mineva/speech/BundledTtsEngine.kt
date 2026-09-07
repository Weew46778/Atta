package com.arena.mineva.speech

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File

/**
 * Self-contained neural Persian TTS using the Sherpa-ONNX runtime bundled in the APK and
 * the Piper Persian voice stored in the app's private speech directory.
 *
 * The engine is intentionally tiny: it does not use Android's TextToSpeech service at all.
 */
class BundledTtsEngine(private val modelDir: File) {

    private var tts: OfflineTts? = null
    private var lock = Any()
    private var playing = false

    private val onnx = File(modelDir, "fa_IR-amir-medium.onnx")
    private val tokens = File(modelDir, "tokens.txt")
    private val espeak = File(modelDir, "espeak-ng-data")

    fun isReady(): Boolean =
        onnx.exists() && tokens.exists() && espeak.isDirectory

    /** Prepare the engine on a background thread. Safe to call repeatedly. */
    fun ensureLoaded(): Boolean {
        synchronized(lock) {
            if (tts != null) return true
            if (!isReady()) return false
            return runCatching {
                val config = OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        vits = OfflineTtsVitsModelConfig(
                            model = onnx.absolutePath,
                            tokens = tokens.absolutePath,
                            dataDir = espeak.absolutePath
                        ),
                        numThreads = 1,
                        debug = false
                    ),
                    maxNumSentences = 1
                )
                tts = OfflineTts(config = config)
                true
            }.getOrDefault(false)
        }
    }

    /**
     * Generates the requested Persian text and plays it through AudioTrack.
     * This is blocking and should normally be called from a worker thread.
     */
    fun speak(text: String, speed: Float = 0.96f): Boolean {
        val engine = tts
        if (engine == null) {
            if (!ensureLoaded()) return false
        }
        val e = tts ?: return false
        if (text.isBlank()) return false
        return runCatching {
            val audio = e.generateWithConfigAndCallback(
                text = text.take(900),
                config = GenerationConfig(
                    sid = 0,
                    speed = speed,
                    silenceScale = 0.25f
                ),
                callback = { 1 }
            )
            val samples = audio.samples
            if (samples == null || samples.isEmpty()) return false
            play(samples, audio.sampleRate)
            true
        }.getOrDefault(false)
    }

    private fun play(samples: FloatArray, sampleRate: Int) {
        stop()
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes((samples.size * 4).coerceAtLeast(minBuffer * 2))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        playing = true
        try {
            track.play()
            var offset = 0
            while (offset < samples.size && playing) {
                val n = minOf(1024 * 4, samples.size - offset)
                val written = track.write(samples, offset, n, AudioTrack.WRITE_BLOCKING)
                if (written <= 0) break
                offset += written
            }
            track.stop()
        } finally {
            runCatching { track.release() }
            playing = false
        }
    }

    fun stop() {
        playing = false
    }

    fun release() {
        synchronized(lock) {
            runCatching { tts?.release() }
            tts = null
        }
    }
}
