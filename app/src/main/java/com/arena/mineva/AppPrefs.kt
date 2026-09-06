package com.arena.mineva

import android.content.Context
import android.content.SharedPreferences

/**
 * Simple app preferences. In a production build this should move into DataStore.
 */
object AppPrefs {
    private const val FILE = "mineava_prefs"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    }

    private fun get(): SharedPreferences = prefs

    var onboarded: Boolean
        get() = get().getBoolean("onboarded", false)
        set(value) = get().edit().putBoolean("onboarded", value).apply()

    var overlayHintShown: Boolean
        get() = get().getBoolean("overlay_hint", false)
        set(value) = get().edit().putBoolean("overlay_hint", value).apply()

    var geminiApiKey: String
        get() = get().getString("gemini_api_key", "") ?: ""
        set(value) = get().edit().putString("gemini_api_key", value).apply()

    var selectedPersianVoice: String
        get() = get().getString("selected_persian_voice", "") ?: ""
        set(value) = get().edit().putString("selected_persian_voice", value).apply()

    var preferFemaleVoice: Boolean
        get() = get().getBoolean("prefer_female_voice", true)
        set(value) = get().edit().putBoolean("prefer_female_voice", value).apply()

    var speechRate: Float
        get() = get().getFloat("speech_rate", 0.92f)
        set(value) = get().edit().putFloat("speech_rate", value).apply()

    var homeServerTarget: String
        get() = get().getString("server_target", "") ?: ""
        set(value) = get().edit().putString("server_target", value).apply()

    var lastServerConfigJson: String
        get() = get().getString("last_server_config_json", "") ?: ""
        set(value) = get().edit().putString("last_server_config_json", value).apply()

    fun removePackage(name: String) {
        get().edit().remove("pkg_$name").apply()
    }

    fun packageInstalled(name: String): Boolean = get().getBoolean("pkg_$name", false)

    fun setPackageInstalled(name: String, installed: Boolean) {
        get().edit().putBoolean("pkg_$name", installed).apply()
    }
}
