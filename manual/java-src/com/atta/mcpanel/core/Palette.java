package com.atta.mcpanel.core;

/** پالت رنگ برنامه (تمام‌رنگ‌ها برنامه‌ای) */
public final class Palette {
    private Palette() {}

    public static final int ROOT = 0xFF0D1118;
    public static final int SURFACE = 0xFF151B25;
    public static final int SURFACE_HI = 0xFF1D2532;
    public static final int STROKE = 0x1FFFFFFF;
    public static final int STROKE2 = 0x33FFFFFF;

    public static final int ACCENT = 0xFF7CCB58;      // سبز
    public static final int ACCENT_DEEP = 0xFF4E8A33;
    public static final int ON_ACCENT = 0xFF0A1206;
    public static final int GOLD = 0xFFE8B84B;
    public static final int ON_GOLD = 0xFF241A04;

    public static final int TEXT_MAIN = 0xFFEEF2F6;
    public static final int TEXT_SUB = 0xFF9AA6B3;
    public static final int TEXT_DIM = 0xFF67727E;

    public static final int DANGER = 0xFFE5533C;
    public static final int DANGER_TEXT = 0xFFFFB3A6;
    public static final int DANGER_BG = 0xFF351713;

    public static final int INPUT_BG = 0xFF0F141C;
    public static final int LOG_BG = 0xFF0A0E13;
    public static final int PANEL_SOLID = 0xF51F2733;
    public static final int FLASH_BG = 0xF02A3445;
    public static final int FLASH_STROKE = 0x40FFFFFF;

    public static final int HANDLE_START = 0xFFA9E37B;
    public static final int HANDLE_END = 0xFF3F7D28;

    public static final int DOT_OK = 0xFF57C15B;
    public static final int DOT_BAD = 0xFFE5533C;
    public static final int DOT_OFF = 0xFF5B6673;
    public static final int DOT_BUSY = 0xFFE8B84B;

    public static final int CHIP_BG = 0x1FFFFFFF;
    public static final int CHIP_BG_DOWN = 0x38FFFFFF;
    public static final int CHIP_TEXT = 0xFFE7EDF2;

    /** تغییر شفافیت یک رنگ (alpha ۰ تا ۱) */
    public static int alphaColor(int rgb, float alpha) {
        int a = Math.round(255 * Math.max(0f, Math.min(1f, alpha)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
