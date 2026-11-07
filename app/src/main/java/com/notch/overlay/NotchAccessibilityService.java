package com.notch.overlay;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.provider.Settings;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

public class NotchAccessibilityService extends AccessibilityService {
    static NotchAccessibilityService instance;
    private NotchController ctl;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        try { stopService(new Intent(this, NotchService.class)); } catch (Throwable t) { }
        if (ctl == null) {
            ctl = new NotchController(this, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
        }
        ctl.mulai();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() { }

    private void bersih() {
        if (ctl != null) {
            ctl.berhenti();
            ctl = null;
        }
        if (instance == this) instance = null;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        bersih();
        // Aksesibilitas dimatikan: kembali ke overlay biasa
        try {
            if (Cfg.get(this, "on") == 1 && Settings.canDrawOverlays(this)) {
                startForegroundService(new Intent(this, NotchService.class));
            }
        } catch (Throwable t) { }
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        bersih();
        super.onDestroy();
    }
}
