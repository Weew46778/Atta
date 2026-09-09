package com.atta.mcpanel.auto;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

import com.atta.mcpanel.overlay.PanelHost;

/**
 * سرویس دسترسی‌پذیری Atta — روش دوم (مقاوم‌تر) برای اجرای یک‌کلیکی در بازی
 *
 * مسیر:
 * ۱) لمس خودکار روی موقعیت کالیبره‌شدهٔ آیکون چت → چت بازی باز می‌شود.
 * ۲) کیبورد Atta (IME همین اپ) فعال می‌شود → دستور را تایپ و Enter می‌کند.
 * ۳) اگر کیبورد Atta نیامد → «بازگشت دستی»: از کاربر می‌خواهد خودش یک بار
 *    روی آیکون چت بزند؛ دستور همچنان در صف می‌ماند و همین‌که چت باز شود ارسال می‌شود.
 * ۴) اگر چند دستور پشت‌سرهم باشد، بعد از هر ارسال، دستور بعدی خودکار می‌رود.
 *
 * هیچ محتوایی از صفحه خوانده یا ذخیره نمی‌شود؛ فقط لمس و تایپ.
 */
public class AutoAccessibilityService extends AccessibilityService {

    private static volatile AutoAccessibilityService instance = null;

    // --- دستور در انتظار ---
    private static volatile String pendingCmd = null;
    private static volatile PanelHost pendingHost = null;
    private static volatile long armSeq = 0;
    private static volatile boolean imeActive = false;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable retry1 = new Runnable() {
        @Override public void run() { retryTap(); }
    };
    private final Runnable giveUp = new Runnable() {
        @Override public void run() { notifyMiss(); }
    };

    public static boolean isEnabled() { return instance != null; }

    // =====================================================================
    // شروع یک دستور: لمس خودکار + پیگیری
    // =====================================================================

    /** دستور را در صف می‌گذارد و چت بازی را خودکار لمس می‌کند */
    public static void arm(final PanelHost host, final String command) {
        pendingHost = host;
        pendingCmd = command;
        imeActive = false;
        armSeq++;
        final long id = armSeq;
        final AutoAccessibilityService svc = instance;
        if (svc == null) {
            host.flash("سرویس اجرای خودکار در دسترس نیست",
                    "در تنظیمات گوشی: دسترسی‌پذیری ← «اجرای خودکار Atta» را روشن کن");
            pendingCmd = null;
            pendingHost = null;
            return;
        }
        svc.main.removeCallbacks(svc.retry1);
        svc.main.removeCallbacks(svc.giveUp);
        boolean ok = svc.tapAt(host.getPrefs().getChatX(), host.getPrefs().getChatY());
        if (!ok) {
            svc.main.postDelayed(svc.giveUp, 300);
        } else {
            // اگر تا ۱٫۴ ثانیه کیبورد Atta نیامد، یک بار دیگر لمس کن
            svc.main.postDelayed(svc.retry1, 1400);
            // اگر باز هم نیامد → راهنمای بازگشت دستی + باز کردن انتخابگر کیبورد
            svc.main.postDelayed(svc.giveUp, 3200);
        }
    }

    /** فقط صف‌کردن دستور (بدون لمس) — کاربر خودش چت را باز می‌کند */
    public static void armManual(final PanelHost host, final String command) {
        pendingHost = host;
        pendingCmd = command;
        imeActive = false;
        armSeq++;
        final AutoAccessibilityService svc = instance;
        if (svc != null) {
            svc.main.removeCallbacks(svc.retry1);
            svc.main.removeCallbacks(svc.giveUp);
        }
    }

    /** کیبورد Atta فعال شد و دستور را گرفت */
    public static void imeTookCommand() {
        imeActive = true;
        final AutoAccessibilityService svc = instance;
        if (svc != null) {
            svc.main.removeCallbacks(svc.retry1);
            svc.main.removeCallbacks(svc.giveUp);
        }
    }

    /** کیبورد Atta دستور را ارسال کرد → ادامهٔ صف */
    public static void finishSent() {
        final PanelHost h = pendingHost;
        final long id = armSeq;
        pendingCmd = null;
        pendingHost = null;
        armSeq++;
        if (h != null) h.notifyChatSent();
    }

    /** لغو دستور در انتظار */
    public static void cancelArm() {
        final AutoAccessibilityService svc = instance;
        if (svc != null) {
            svc.main.removeCallbacks(svc.retry1);
            svc.main.removeCallbacks(svc.giveUp);
        }
        pendingCmd = null;
        pendingHost = null;
        armSeq++;
    }

    /** دستور در انتظار را برمی‌دارد (کیبورد Atta) */
    public static String takePending() {
        String c = pendingCmd;
        pendingCmd = null;
        return c;
    }

    public static String peekPending() { return pendingCmd; }
    public static boolean hasPending() { return pendingCmd != null; }

    public static PanelHost host() { return pendingHost; }

    public static void clearHost(PanelHost h) {
        if (pendingHost == h) {
            pendingHost = null;
            pendingCmd = null;
            armSeq++;
        }
    }

    // =====================================================================
    // لمس خودکار
    // =====================================================================

    private boolean tapAt(int x, int y) {
        if (x < 0 || y < 0) return false;
        try {
            Path path = new Path();
            path.moveTo(x + 0.5f, y + 0.5f);
            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(path, 0, 120);
            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(stroke)
                    .build();
            return dispatchGesture(gesture, null, null);
        } catch (Throwable t) {
            return false;
        }
    }

    private void retryTap() {
        final PanelHost h = pendingHost;
        if (h == null) return;
        tapAt(h.getPrefs().getChatX(), h.getPrefs().getChatY());
    }

    private void notifyMiss() {
        if (pendingCmd == null) return;
        if (imeActive) return;
        final PanelHost h = pendingHost;
        if (h == null) return;
        pendingCmd = null;
        pendingHost = null;
        h.onChatAutoMiss();
    }

    // =====================================================================
    // چرخهٔ سرویس
    // =====================================================================

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // بدون استفاده — فقط ژست لمس
    }

    @Override
    public void onInterrupt() {
        // بی‌صدا
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        if (instance == this) instance = null;
        pendingCmd = null;
        pendingHost = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        pendingCmd = null;
        pendingHost = null;
        main.removeCallbacks(retry1);
        main.removeCallbacks(giveUp);
        super.onDestroy();
    }
}
