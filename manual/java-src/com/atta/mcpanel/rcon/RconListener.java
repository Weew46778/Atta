package com.atta.mcpanel.rcon;

/** رویدادهای اتصال RCON */
public abstract class RconListener {
    public void onConnecting() {}
    public void onAuth(boolean ok, String message) {}
    public void onOutput(String text) {}
    public void onClosed(String reason) {}
}
