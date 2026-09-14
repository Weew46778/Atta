package com.atta.mcpanel.server;

import com.atta.mcpanel.aternos.AternosPanel;

/** اشتراک موتور Aternos بین صفحات (اترنوس + کنسول) */
public final class ServerState {
    private static AternosPanel aternos;

    private ServerState() {}

    public static void set(AternosPanel a) { aternos = a; }

    public static AternosPanel get() { return aternos; }

    public static void clear(AternosPanel a) {
        if (aternos == a) aternos = null;
    }
}
