package com.notch.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

public class NotchService extends Service {
    private WindowManager wm;
    private View view;

    // Ukuran notch (ubah angka ini kalau mau lebih besar/kecil)
    private static final float LEBAR = 0.40f;        // lebar notch = 40% lebar layar
    private static final float SKALA_TINGGI = 1.0f;  // 1.0 = sama dengan tinggi status bar

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
            tambahNotch();
        }
        return START_STICKY;
    }

    private void tambahNotch() {
        int sw = getResources().getDisplayMetrics().widthPixels;
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        int sbh = resId > 0
                ? getResources().getDimensionPixelSize(resId)
                : Math.round(24 * getResources().getDisplayMetrics().density);
        int h = Math.round(sbh * SKALA_TINGGI);
        int w = Math.round(sw * LEBAR);
        int ear = Math.round(h * 0.25f);

        view = new NotchView(this, w, h, ear);
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                w + ear * 2, h,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
    public void onDestroy() {
        if (wm != null && view != null) wm.removeView(view);
        super.onDestroy();
    }

    static class NotchView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final float cx, cy, dotR, glowR;

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

            // Titik hijau (posisi: 68% dari lebar notch, di tengah tinggi)
            cx = e + w * 0.68f;
            cy = h * 0.5f;
            dotR = h * 0.11f;   // ukuran titik, besarkan kalau kurang kelihatan
            glowR = h * 0.32f;  // ukuran cahaya di sekeliling titik
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
