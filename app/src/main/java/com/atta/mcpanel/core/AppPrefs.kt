package com.atta.mcpanel.core

import android.content.Context
import android.content.SharedPreferences

enum class PanelWidthMode { NARROW, NORMAL, WIDE }

/**
 * ذخیرهٔ همهٔ تنظیمات برنامه در SharedPreferences.
 */
class AppPrefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("atta_prefs", Context.MODE_PRIVATE)

    /** نام بازیکن هدف؛ در قالب دستورها با {p} جایگزین می‌شود. */
    var playerName: String
        get() = sp.getString(KEY_PLAYER, "@s")!!.ifBlank { "@s" }
        set(value) = sp.edit().putString(KEY_PLAYER, value).apply()

    var rconHost: String
        get() = sp.getString(KEY_RCON_HOST, "")!!
        set(value) = sp.edit().putString(KEY_RCON_HOST, value.trim()).apply()

    var rconPort: Int
        get() = sp.getInt(KEY_RCON_PORT, 25575)
        set(value) = sp.edit().putInt(KEY_RCON_PORT, value).apply()

    var rconPassword: String
        get() = sp.getString(KEY_RCON_PASS, "")!!
        set(value) = sp.edit().putString(KEY_RCON_PASS, value).apply()

    var panelWidthMode: PanelWidthMode
        get() = runCatching { PanelWidthMode.valueOf(sp.getString(KEY_PANEL_WIDTH, "NORMAL")!!) }
            .getOrDefault(PanelWidthMode.NORMAL)
        set(value) = sp.edit().putString(KEY_PANEL_WIDTH, value.name).apply()

    var overlayAlpha: Float
        get() = sp.getFloat(KEY_ALPHA, 1f)
        set(value) = sp.edit().putFloat(KEY_ALPHA, value.coerceIn(0.45f, 1f)).apply()

    var handleFraction: Float
        get() = sp.getFloat(KEY_HANDLE, 0.5f)
        set(value) = sp.edit().putFloat(KEY_HANDLE, value.coerceIn(0f, 1f)).apply()

    var vibrationOn: Boolean
        get() = sp.getBoolean(KEY_VIBRATION, true)
        set(value) = sp.edit().putBoolean(KEY_VIBRATION, value).apply()

    var autoPanelOnLaunch: Boolean
        get() = sp.getBoolean(KEY_AUTO_PANEL, true)
        set(value) = sp.edit().putBoolean(KEY_AUTO_PANEL, value).apply()

    companion object {
        private const val KEY_PLAYER = "player"
        private const val KEY_RCON_HOST = "rcon_host"
        private const val KEY_RCON_PORT = "rcon_port"
        private const val KEY_RCON_PASS = "rcon_pass"
        private const val KEY_PANEL_WIDTH = "panel_width"
        private const val KEY_ALPHA = "overlay_alpha"
        private const val KEY_HANDLE = "handle_frac"
        private const val KEY_VIBRATION = "vib"
        private const val KEY_AUTO_PANEL = "auto_panel"
    }
}
