package com.notch.overlay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        try {
            if (Cfg.get(c, "on") == 1 && Settings.canDrawOverlays(c) && !Cfg.aksesAktif(c)) {
                c.startForegroundService(new Intent(c, NotchService.class));
            }
        } catch (Throwable t) { }
    }
}
