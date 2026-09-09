package com.atta.mcpanel.core;

import android.content.Context;
import android.content.SharedPreferences;

/** ذخیرهٔ تنظیمات برنامه (بدون وابستگی) */
public class AppPrefs {

    public enum PanelWidthMode { NARROW, NORMAL, WIDE }

    private final SharedPreferences sp;

    public AppPrefs(Context ctx) {
        sp = ctx.getApplicationContext()
                .getSharedPreferences("atta_prefs", Context.MODE_PRIVATE);
    }

    public String getPlayerName() {
        String v = sp.getString("player", "@s");
        return v == null || v.trim().isEmpty() ? "@s" : v;
    }

    public void setPlayerName(String v) {
        sp.edit().putString("player", v == null ? "" : v.trim()).apply();
    }

    public String getRconHost() {
        String v = sp.getString("rcon_host", "");
        return v == null ? "" : v;
    }

    public void setRconHost(String v) {
        sp.edit().putString("rcon_host", v == null ? "" : v.trim()).apply();
    }

    public int getRconPort() {
        return sp.getInt("rcon_port", 25575);
    }

    public void setRconPort(int v) {
        sp.edit().putInt("rcon_port", v).apply();
    }

    public String getRconPassword() {
        String v = sp.getString("rcon_pass", "");
        return v == null ? "" : v;
    }

    public void setRconPassword(String v) {
        sp.edit().putString("rcon_pass", v == null ? "" : v).apply();
    }

    public PanelWidthMode getPanelWidthMode() {
        try {
            return PanelWidthMode.valueOf(sp.getString("panel_width", "NORMAL"));
        } catch (Exception e) {
            return PanelWidthMode.NORMAL;
        }
    }

    public void setPanelWidthMode(PanelWidthMode m) {
        sp.edit().putString("panel_width", m.name()).apply();
    }

    public float getOverlayAlpha() {
        return sp.getFloat("overlay_alpha", 1f);
    }

    public void setOverlayAlpha(float v) {
        sp.edit().putFloat("overlay_alpha", Math.max(0.45f, Math.min(1f, v))).apply();
    }

    public float getHandleFraction() {
        return sp.getFloat("handle_frac", 0.5f);
    }

    public void setHandleFraction(float v) {
        sp.edit().putFloat("handle_frac", Math.max(0f, Math.min(1f, v))).apply();
    }

    public boolean getVibrationOn() {
        return sp.getBoolean("vib", true);
    }

    public void setVibrationOn(boolean v) {
        sp.edit().putBoolean("vib", v).apply();
    }

    public boolean getAutoPanelOnLaunch() {
        return sp.getBoolean("auto_panel", true);
    }

    public void setAutoPanelOnLaunch(boolean v) {
        sp.edit().putBoolean("auto_panel", v).apply();
    }

    // ---------- اتوماسیون تک‌نفره (Accessibility + IME) ----------

    /** مختصات دکمهٔ چت بازی (کالیبره‌شده)؛ -1 یعنی هنوز کالیبره نشده */
    public int getChatX() {
        return sp.getInt("chat_x", -1);
    }

    public void setChatX(int v) {
        sp.edit().putInt("chat_x", v).apply();
    }

    public int getChatY() {
        return sp.getInt("chat_y", -1);
    }

    public void setChatY(int v) {
        sp.edit().putInt("chat_y", v).apply();
    }

    public void setChatPoint(int x, int y) {
        sp.edit().putInt("chat_x", x).putInt("chat_y", y).apply();
    }

    // ---------- رشته‌های عمومی (پروفایل VPS و…) ----------
    public String getString(String key, String def) {
        String v = sp.getString(key, def);
        return v == null ? def : v;
    }

    public void setString(String key, String v) {
        sp.edit().putString(key, v == null ? "" : v).apply();
    }
}
