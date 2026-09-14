package com.atta.mcpanel;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import com.atta.mcpanel.core.AppPrefs;
import com.atta.mcpanel.core.Palette;
import com.atta.mcpanel.overlay.OverlayService;
import com.atta.mcpanel.overlay.UiKit;
import com.atta.mcpanel.ui.Pages;
import com.atta.mcpanel.voice.Homan;

/** صفحهٔ پنل اپراتور داخل بازی (اوورلی) */
public class OverlayPage extends Activity {

    private AppPrefs prefs;
    private TextView tvStatus;
    private TextView tvPermission;
    private LinearLayout overlayActions;
    private Switch swOverlay;
    private Switch swAutoPanel;
    private boolean suppressSwitch = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new AppPrefs(this);

        LinearLayout col = Pages.root(this);
        col.addView(Pages.titleBar(this, "🛠 پنل داخل بازی", new Runnable() {
            @Override public void run() { Homan.intro(OverlayPage.this, "overlay"); }
        }));

        tvStatus = Pages.statusLine(this, "پنل: خاموش");
        col.addView(tvStatus, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout swRow = UiKit.hrow(this);
        TextView label = new TextView(this);
        label.setText("نمایش پنل روی بازی");
        label.setTextSize(17f);
        label.setTextColor(Palette.TEXT_MAIN);
        swRow.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        swOverlay = new Switch(this);
        swOverlay.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                if (suppressSwitch) return;
                if (checked) requestStartOverlay();
                else OverlayService.stop(OverlayPage.this);
            }
        });
        swRow.addView(swOverlay);
        col.addView(swRow, UiKit.wrapParams(swRow, 4, 54));

        tvPermission = new TextView(this);
        tvPermission.setTextSize(14f);
        col.addView(tvPermission, UiKit.wrapParams(tvPermission, 4, 0));

        overlayActions = UiKit.vcol(this, 0);
        col.addView(overlayActions, UiKit.wrapParams(overlayActions, 4, 0));

        LinearLayout autoRow = UiKit.hrow(this);
        TextView tvAuto = new TextView(this);
        tvAuto.setText("اجرای خودکار پنل هنگام باز کردن بازی");
        tvAuto.setTextSize(14f);
        tvAuto.setTextColor(Palette.TEXT_SUB);
        autoRow.addView(tvAuto, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        swAutoPanel = new Switch(this);
        swAutoPanel.setChecked(prefs.getAutoPanelOnLaunch());
        swAutoPanel.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean checked) {
                prefs.setAutoPanelOnLaunch(checked);
            }
        });
        autoRow.addView(swAutoPanel);
        col.addView(autoRow, UiKit.wrapParams(autoRow, 4, 54));

        setContentView(col);
        Homan.intro(this, "overlay");
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private boolean canOverlay() {
        return Settings.canDrawOverlays(this);
    }

    private void refresh() {
        suppressSwitch = true;
        boolean running = OverlayService.isRunning();
        swOverlay.setChecked(running);
        tvStatus.setText(running ? "پنل: روشن ✓" : "پنل: خاموش");
        tvStatus.setTextColor(running ? 0xFF57C15B : 0xFF9AA7BA);

        if (canOverlay()) {
            tvPermission.setText("✓ اجازهٔ نمایش روی برنامه‌ها داده شده است");
            tvPermission.setTextColor(Palette.DOT_OK);
        } else {
            tvPermission.setText("اجازهٔ نمایش روی برنامه‌ها داده نشده — برای پنل لازم است");
            tvPermission.setTextColor(Palette.DANGER);
        }

        overlayActions.removeAllViews();
        if (!canOverlay()) {
            overlayActions.addView(Pages.action(this, "اعطای اجازهٔ نمایش روی برنامه‌ها", 1, new Runnable() {
                @Override public void run() { goManageOverlayPermission(); }
            }));
        } else {
            overlayActions.addView(Pages.action(this,
                    OverlayService.isRunning() ? "خاموش کردن پنل" : "روشن کردن پنل", 1, new Runnable() {
                        @Override public void run() {
                            if (OverlayService.isRunning()) {
                                OverlayService.stop(OverlayPage.this);
                                refresh();
                            } else {
                                requestStartOverlay();
                            }
                        }
                    }));
        }
        suppressSwitch = false;
    }

    private void requestStartOverlay() {
        if (!canOverlay()) {
            swOverlay.setChecked(false);
            goManageOverlayPermission();
            return;
        }
        OverlayService.start(this);
        refresh();
    }

    private void goManageOverlayPermission() {
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            } catch (Exception ignored) {}
        }
    }
}
