package com.notch.overlay;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

public class NotchAccessibilityService extends AccessibilityService {
    private WindowManager wm;
    private View view;

    // Ukuran notch (ubah angka ini kalau mau lebih besar/kecil)
    private static final float LEBAR = 0.43f;   // lebar notch = 43% lebar layar
    private static final float TINGGI = 0.095f; // tinggi notch = 9.5% lebar layar

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        if (view != null) return;
        int sw = getResources().getDisplayMetrics().widthPixels;
        int h = Math.round(sw * TINGGI);
        int w = Math.round(sw * LEBAR);
        int ear = Math.round(h * 0.25f);

        view = new NotchView(this, w, h, ear);
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                w + ear * 2, h,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        p.x = 0;
        p.y = 0;
        if (Build.VERSION.SDK_INT >= 30) {
            p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        } else if (Build.VERSION.SDK_INT >= 28) {
            p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        wm.addView(view, p);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override
    public void onInterrupt() { }

    @Override
    public void onDestroy() {
        if (wm != null && view != null) {
            try { wm.removeView(view); } catch (Exception e) { }
            view = null;
        }
        super.onDestroy();
    }

    static class NotchView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        NotchView(Context c, int w, int h, int e) {
            super(c);
            paint.setColor(Color.BLACK);
            paint.setStyle(Paint.Style.FILL);
            float r = h * 0.6f;
            path.moveTo(0, 0);
            path.quadTo(e, 0, e, e);
            path.lineTo(e, h - r);
            path.quadTo(e, h, e + r, h);
            path.lineTo(e + w - r, h);
            path.quadTo(e + w, h, e + w, h - r);
            path.lineTo(e + w, e);
            path.quadTo(e + w, 0, e + w + e, 0);
            path.close();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawPath(path, paint);
        }
    }
}
