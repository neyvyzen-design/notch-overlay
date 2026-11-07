package com.notch.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;

public class NotchService extends Service {
    private WindowManager wm;
    private DisplayManager dmgr;
    private View view;
    private int rot = -1;
    private boolean listenerAktif = false;

    // Ukuran notch (ubah angka ini kalau mau lebih besar/kecil)
    private static final float LEBAR = 0.40f;        // panjang notch = 40% sisi pendek layar
    private static final float SKALA_TINGGI = 1.0f;  // 1.0 = sama dengan tinggi status bar

    private final DisplayManager.DisplayListener listener = new DisplayManager.DisplayListener() {
        @Override public void onDisplayAdded(int id) { }
        @Override public void onDisplayRemoved(int id) { }
        @Override public void onDisplayChanged(int id) {
            if (id != Display.DEFAULT_DISPLAY) return;
            Display d = dmgr.getDisplay(Display.DEFAULT_DISPLAY);
            if (d != null && d.getRotation() != rot) pasangNotch();
        }
    };

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent i, int f, int id) {
        if (view == null) {
            NotificationChannel ch = new NotificationChannel("notch", "Notch", NotificationManager.IMPORTANCE_MIN);
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(ch);
            Notification n = new Notification.Builder(this, "notch")
                    .setContentTitle("Notch aktif")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .build();
            startForeground(1, n);
            wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            dmgr = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
            pasangNotch();
            if (!listenerAktif) {
                dmgr.registerDisplayListener(listener, new Handler(Looper.getMainLooper()));
                listenerAktif = true;
            }
        }
        return START_STICKY;
    }

    private void lepasNotch() {
        if (wm != null && view != null) {
            try { wm.removeView(view); } catch (Throwable t) { }
        }
        view = null;
    }

    private void pasangNotch() {
        lepasNotch();
        try {
            Display d = dmgr.getDisplay(Display.DEFAULT_DISPLAY);
            rot = d.getRotation();
            DisplayMetrics dm = new DisplayMetrics();
            d.getRealMetrics(dm);
            int sisiPendek = Math.min(dm.widthPixels, dm.heightPixels);

            int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
            int sbh = resId > 0
                    ? getResources().getDimensionPixelSize(resId)
                    : Math.round(24 * getResources().getDisplayMetrics().density);
            int h = Math.round(sbh * SKALA_TINGGI);
            int w = Math.round(sisiPendek * LEBAR);
            int ear = Math.round(h * 0.25f);

            // mode 0 = atas tengah, 1 = kiri (landscape), 2 = kanan (landscape)
            int mode = 0;
            if (rot == Surface.ROTATION_90) mode = 1;
            else if (rot == Surface.ROTATION_270) mode = 2;

            int lebarWin = (mode == 0) ? (w + ear * 2) : h;
            int tinggiWin = (mode == 0) ? h : (w + ear * 2);

            View v = new NotchView(this, w, h, ear, mode);

            WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                    lebarWin, tinggiWin,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            if (mode == 0) p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            else if (mode == 1) p.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
            else p.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
            p.x = 0;
            p.y = 0;
            if (Build.VERSION.SDK_INT >= 30) {
                p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            } else if (Build.VERSION.SDK_INT >= 28) {
                p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
            wm.addView(v, p);
            view = v;
        } catch (Throwable t) {
            view = null;
        }
    }

    @Override
    public void onDestroy() {
        if (dmgr != null && listenerAktif) {
            try { dmgr.unregisterDisplayListener(listener); } catch (Throwable t) { }
            listenerAktif = false;
        }
        lepasNotch();
        super.onDestroy();
    }

    static class NotchView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final float cx, cy, dotR, glowR;

        NotchView(Context c, int w, int h, int e, int mode) {
            super(c);
            paint.setColor(Color.BLACK);
            paint.setStyle(Paint.Style.FILL);

            // Bentuk notch (versi portrait)
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

            // Titik hijau (80% dari lebar notch, di tengah tinggi)
            float[] pt = { e + w * 0.80f, h * 0.5f };
            dotR = h * 0.11f;
            glowR = h * 0.32f;

            // Putar untuk landscape
            float panjang = w + 2f * e;
            Matrix m = new Matrix();
            if (mode == 1) {
                m.postRotate(-90);
                m.postTranslate(0, panjang);
            } else if (mode == 2) {
                m.postRotate(90);
                m.postTranslate(h, 0);
            }
            path.transform(m);
            m.mapPoints(pt);
            cx = pt[0];
            cy = pt[1];

            dotPaint.setColor(Color.rgb(48, 209, 88));
            dotPaint.setStyle(Paint.Style.FILL);
            glowPaint.setShader(new RadialGradient(cx, cy, glowR,
                    new int[]{Color.argb(150, 48, 209, 88), Color.argb(0, 48, 209, 88)},
                    new float[]{0.2f, 1f}, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawPath(path, paint);
            canvas.drawCircle(cx, cy, glowR, glowPaint);
            canvas.drawCircle(cx, cy, dotR, dotPaint);
        }
    }
}
