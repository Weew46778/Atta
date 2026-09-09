package com.atta.mcpanel.overlay;

import android.content.Intent;
import android.provider.Settings;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** کاشی تنظیمات سریع: روشن/خاموش پنل */
public class OverlayTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (!Settings.canDrawOverlays(this)) {
            if (!isLocked()) {
                Intent i = new Intent(this, com.atta.mcpanel.MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(i);
                } catch (Exception ignored) {}
            }
            updateTile();
            return;
        }
        if (OverlayService.isRunning()) {
            OverlayService.stop(this);
        } else {
            OverlayService.start(this);
        }
        updateTile();
        // سرویس کمی دیرتر استارت می‌شود؛ دوباره به‌روزرسانی کن
        new android.os.Handler(android.os.Looper.getMainLooper())
                .postDelayed(new Runnable() {
                    @Override public void run() { updateTile(); }
                }, 500);
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;
        if (!Settings.canDrawOverlays(this)) {
            tile.setState(Tile.STATE_UNAVAILABLE);
            tile.setLabel("پنل اپراتور");
            tile.setSubtitle("اجازهٔ نمایش لازم است");
        } else {
            tile.setState(OverlayService.isRunning()
                    ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
            tile.setSubtitle(OverlayService.isRunning() ? "روشن" : "خاموش");
        }
        tile.updateTile();
    }
}
