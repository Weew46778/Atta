package com.atta.mcpanel.overlay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** دریافت دکمهٔ «توقف پنل» از اعلان */
public class OverlayReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        OverlayService.stop(context);
    }
}
