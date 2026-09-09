package com.atta.mcpanel.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** دریافت دکمهٔ «توقف پنل» از اعلان. */
class OverlayReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_STOP) {
            OverlayService.stop(context)
        }
    }

    companion object {
        const val ACTION_STOP = "com.atta.mcpanel.action.STOP_OVERLAY"
    }
}
