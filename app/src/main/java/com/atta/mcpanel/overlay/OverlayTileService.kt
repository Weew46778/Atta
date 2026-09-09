package com.atta.mcpanel.overlay

import android.content.Intent
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** کاشی تنظیمات سریع: روشن/خاموش کردن پنل بدون باز کردن اپ. */
class OverlayTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (!Settings.canDrawOverlays(this)) {
            if (!isLocked) {
                startActivity(
                    Intent(this, com.atta.mcpanel.MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            updateTile()
            return
        }
        if (OverlayService.isRunning()) {
            OverlayService.stop(this)
        } else {
            OverlayService.start(this)
        }
        updateTile()
        // سرویس کمی دیرتر start می‌شود؛ کاشی را دوباره به‌روز کن
        android.os.Handler(android.os.Looper.getMainLooper())
            .postDelayed({ updateTile() }, 400)
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        if (!Settings.canDrawOverlays(this)) {
            tile.state = Tile.STATE_UNAVAILABLE
            tile.label = "پنل اپراتور"
            tile.subtitle = "اجازهٔ نمایش لازم است"
        } else {
            tile.state = if (OverlayService.isRunning()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.subtitle = if (OverlayService.isRunning()) "روشن" else "خاموش"
        }
        tile.updateTile()
    }
}
