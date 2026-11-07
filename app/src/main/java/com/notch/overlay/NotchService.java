package com.notch.overlay;

import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.BatteryManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.hardware.camera2.CameraManager;
import android.hardware.display.DisplayManager;
import android.media.AudioManager;
import android.media.AudioRecordingConfiguration;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NotchService extends Service {
    static NotchService instance;

    private WindowManager wm;
    private DisplayManager dmgr;
    private CameraManager cam;
    private AudioManager am;
    private SharedPreferences sp;
    private NotchView view;
    private int rot = -1;
    private boolean mulai = false;
    private boolean micAktif = false;
    private boolean penuh = false;
    private final Set<String> kameraDipakai = new HashSet<String>();
    private final Handler h = new Handler(Looper.getMainLooper());

    private final Runnable bangunUlang = new Runnable() {
        @Override public void run() { pasangNotch(); }
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener prefL =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences p, String k) {
                    if (k == null || Cfg.tanpaBangun(k)) return;
                    h.removeCallbacks(bangunUlang);
                    h.postDelayed(bangunUlang, 150);
                }
            };

    private final DisplayManager.DisplayListener dispL = new DisplayManager.DisplayListener() {
        @Override public void onDisplayAdded(int id) { }
        @Override public void onDisplayRemoved(int id) { }
        @Override public void onDisplayChanged(int id) {
            if (id != Display.DEFAULT_DISPLAY) return;
            Display d = dmgr.getDisplay(Display.DEFAULT_DISPLAY);
            if (d != null && d.getRotation() != rot) pasangNotch();
        }
    };

    private final CameraManager.AvailabilityCallback camCb = new CameraManager.AvailabilityCallback() {
        @Override public void onCameraAvailable(String id) {
            kameraDipakai.remove(id);
            updateAktif();
        }
        @Override public void onCameraUnavailable(String id) {
            kameraDipakai.add(id);
            updateAktif();
        }
    };

    private final AudioManager.AudioRecordingCallback micCb = new AudioManager.AudioRecordingCallback() {
        @Override
        public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configs) {
            micAktif = configs != null && !configs.isEmpty();
            updateAktif();
        }
    };

    private int persenBaterai() {
        try {
            Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (b == null) return -1;
            int lv = b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int sc = b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (lv < 0 || sc <= 0) return -1;
            return lv * 100 / sc;
        } catch (Throwable t) {
            return -1;
        }
    }

    private final BroadcastReceiver batL = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            try {
                if (i == null || Cfg.get(NotchService.this, "baterai") != 1) return;
                String a = i.getAction();
                if (a == null) return;
                if (Intent.ACTION_POWER_CONNECTED.equals(a)) {
                    int lv = persenBaterai();
                    penuh = lv >= 100;
                    tampilkan("\u26A1 Mengisi daya", lv >= 0 ? lv + "%" : "", 2500);
                } else if (Intent.ACTION_POWER_DISCONNECTED.equals(a)) {
                    penuh = false;
                    int lv = persenBaterai();
                    tampilkan("Pengisian dilepas", lv >= 0 ? lv + "%" : "", 2500);
                } else if (Intent.ACTION_BATTERY_LOW.equals(a)) {
                    int lv = persenBaterai();
                    tampilkan("Baterai lemah", lv >= 0 ? lv + "%" : "", 3500);
                } else if (Intent.ACTION_BATTERY_CHANGED.equals(a)) {
                    int lv = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int sc = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                    int plug = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                    int pct = (lv >= 0 && sc > 0) ? lv * 100 / sc : -1;
                    if (pct >= 100 && plug != 0 && !penuh) {
                        penuh = true;
                        tampilkan("Baterai penuh", "100%", 3000);
                    } else if (pct >= 0 && pct < 100) {
                        penuh = false;
                    }
                }
            } catch (Throwable t) { }
        }
    };

    private void getar() {
        try {
            if (Cfg.get(this, "getar") != 1) return;
            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Throwable t) { }
    }

    static void tampilkan(final String a, final String b, final long ms) {
        final NotchService s = instance;
        if (s == null) return;
        s.h.post(new Runnable() {
            @Override public void run() {
                if (s.view != null) {
                    s.getar();
                    s.view.tampil(a, b, ms);
                }
            }
        });
    }

    private void updateAktif() {
        if (view != null) view.setSedangDipakai(micAktif || !kameraDipakai.isEmpty());
    }

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        sp = getSharedPreferences(Cfg.PREF, MODE_PRIVATE);
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        dmgr = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
        cam = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    public int onStartCommand(Intent i, int f, int id) {
        NotificationChannel ch = new NotificationChannel("notch", "Notch", NotificationManager.IMPORTANCE_MIN);
        ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(ch);
        Notification n = new Notification.Builder(this, "notch")
                .setContentTitle("Notch aktif")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .build();
        startForeground(1, n);

        if (!Settings.canDrawOverlays(this)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!mulai) {
            mulai = true;
            pasangNotch();
            try { dmgr.registerDisplayListener(dispL, h); } catch (Throwable t) { }
            try { sp.registerOnSharedPreferenceChangeListener(prefL); } catch (Throwable t) { }
            try { cam.registerAvailabilityCallback(camCb, h); } catch (Throwable t) { }
            try { am.registerAudioRecordingCallback(micCb, h); } catch (Throwable t) { }
            try {
                penuh = persenBaterai() >= 100;
                IntentFilter bf = new IntentFilter();
                bf.addAction(Intent.ACTION_POWER_CONNECTED);
                bf.addAction(Intent.ACTION_POWER_DISCONNECTED);
                bf.addAction(Intent.ACTION_BATTERY_LOW);
                bf.addAction(Intent.ACTION_BATTERY_CHANGED);
                registerReceiver(batL, bf);
            } catch (Throwable t) { }
        }
        return START_STICKY;
    }

    private void lepasNotch() {
        if (view != null) {
            try { wm.removeView(view); } catch (Throwable t) { }
            view = null;
        }
    }

    private void pasangNotch() {
        lepasNotch();
        try {
            Display d = dmgr.getDisplay(Display.DEFAULT_DISPLAY);
            rot = d.getRotation();
            DisplayMetrics dm = new DisplayMetrics();
            d.getRealMetrics(dm);
            int sisiPendek = Math.min(dm.widthPixels, dm.heightPixels);

            // mode 0 = atas tengah, 1 = kiri (landscape), 2 = kanan (landscape)
            int mode = 0;
            if (rot == Surface.ROTATION_90) mode = 1;
            else if (rot == Surface.ROTATION_270) mode = 2;
            if (mode != 0 && Cfg.get(this, "sembunyi") == 1) return;

            int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
            int sbh = resId > 0
                    ? getResources().getDimensionPixelSize(resId)
                    : Math.round(24 * dm.density);
            int hh = Math.max(8, Math.round(sbh * Cfg.get(this, "tinggi") / 100f));
            int w = Math.round(sisiPendek * Cfg.get(this, "lebar") / 100f);
            int ear = Math.round(hh * 0.25f);
            int wMax = Math.max(w, Math.min(Math.round(w * 1.7f), sisiPendek - 2 * ear - Math.round(8 * dm.density)));
            int hMax = Math.round(hh * 2.0f);
            int geser = Math.round(Cfg.get(this, "geser") * dm.density);

            float dotR = hh * Cfg.get(this, "dotsize") / 100f;
            float dotPos = Cfg.get(this, "dotpos") / 100f;
            int warna = Cfg.WARNA[Math.max(0, Math.min(Cfg.WARNA.length - 1, Cfg.get(this, "warna")))];

            int winW = wMax + 2 * ear;
            int winH = hMax;

            NotchView v = new NotchView(this, w, hh, ear, wMax, hMax, mode, warna, dotR, dotPos);
            v.setSedangDipakai(micAktif || !kameraDipakai.isEmpty());

            WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                    mode == 0 ? winW : winH,
                    mode == 0 ? winH : winW,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT);
            if (mode == 0) {
                p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                p.x = geser;
                p.y = 0;
            } else if (mode == 1) {
                p.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
                p.x = 0;
                p.y = -geser;
            } else {
                p.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
                p.x = 0;
                p.y = geser;
            }
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
        instance = null;
        try { dmgr.unregisterDisplayListener(dispL); } catch (Throwable t) { }
        try { sp.unregisterOnSharedPreferenceChangeListener(prefL); } catch (Throwable t) { }
        try { cam.unregisterAvailabilityCallback(camCb); } catch (Throwable t) { }
        try { am.unregisterAudioRecordingCallback(micCb); } catch (Throwable t) { }
        try { unregisterReceiver(batL); } catch (Throwable t) { }
        h.removeCallbacksAndMessages(null);
        lepasNotch();
        super.onDestroy();
    }

    static class NotchView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint tp1 = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint tp2 = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final Matrix m = new Matrix();
        private final int w, h, e, wMax, hMax;
        private final float winW, winH, dotR, glowR, dotPos;

        private float prog = 0f;
        private float kedip = 1f;
        private boolean dipakai = false;
        private String l1 = "";
        private String l2 = "";
        private ValueAnimator animProg;
        private ValueAnimator animKedip;
        private final Runnable tutup = new Runnable() {
            @Override public void run() { animasiKe(0f); }
        };

        NotchView(Context c, int w, int h, int e, int wMax, int hMax, int mode,
                  int warna, float dotR, float dotPos) {
            super(c);
            this.w = w;
            this.h = h;
            this.e = e;
            this.wMax = wMax;
            this.hMax = hMax;
            this.dotR = dotR;
            this.dotPos = dotPos;
            this.glowR = dotR * 2.9f;
            this.winW = wMax + 2f * e;
            this.winH = hMax;

            paint.setColor(Color.BLACK);
            paint.setStyle(Paint.Style.FILL);

            dotPaint.setColor(warna | 0xFF000000);
            dotPaint.setStyle(Paint.Style.FILL);
            int rgb = warna & 0x00FFFFFF;
            glowPaint.setShader(new RadialGradient(0f, 0f, glowR,
                    new int[]{(150 << 24) | rgb, rgb},
                    new float[]{0.2f, 1f}, Shader.TileMode.CLAMP));

            tp1.setColor(Color.WHITE);
            tp1.setTextSize(h * 0.36f);
            tp1.setTypeface(Typeface.DEFAULT_BOLD);
            tp2.setColor(0xFFBDBDBD);
            tp2.setTextSize(h * 0.32f);

            if (mode == 1) {
                m.postRotate(-90);
                m.postTranslate(0, winW);
            } else if (mode == 2) {
                m.postRotate(90);
                m.postTranslate(winH, 0);
            }
        }

        void animasiKe(float target) {
            if (animProg != null) animProg.cancel();
            animProg = ValueAnimator.ofFloat(prog, target);
            animProg.setDuration(target > prog ? 280 : 240);
            animProg.setInterpolator(new DecelerateInterpolator());
            animProg.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) {
                    prog = (Float) a.getAnimatedValue();
                    invalidate();
                }
            });
            animProg.start();
        }

        void tampil(String a, String b, long ms) {
            l1 = a == null ? "" : a;
            l2 = b == null ? "" : b;
            removeCallbacks(tutup);
            animasiKe(1f);
            postDelayed(tutup, ms);
        }

        void setSedangDipakai(boolean b) {
            if (b == dipakai) return;
            dipakai = b;
            if (animKedip != null) {
                animKedip.cancel();
                animKedip = null;
            }
            if (b) {
                animKedip = ValueAnimator.ofFloat(1f, 0.15f);
                animKedip.setDuration(550);
                animKedip.setRepeatCount(ValueAnimator.INFINITE);
                animKedip.setRepeatMode(ValueAnimator.REVERSE);
                animKedip.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override public void onAnimationUpdate(ValueAnimator a) {
                        kedip = (Float) a.getAnimatedValue();
                        invalidate();
                    }
                });
                animKedip.start();
            } else {
                kedip = 1f;
                invalidate();
            }
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (animProg != null) animProg.cancel();
            if (animKedip != null) animKedip.cancel();
            removeCallbacks(tutup);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float wc = w + (wMax - w) * prog;
            float hc = h + (hMax - h) * prog;
            float left = (winW - (wc + 2f * e)) / 2f;
            float xl = left + e;
            float xr = left + e + wc;
            float r = Math.min(h * 0.6f + (hc - h) * 0.35f, wc / 2f);

            path.reset();
            path.moveTo(left, 0);
            path.quadTo(xl, 0, xl, e);
            path.lineTo(xl, hc - r);
            path.quadTo(xl, hc, xl + r, hc);
            path.lineTo(xr - r, hc);
            path.quadTo(xr, hc, xr, hc - r);
            path.lineTo(xr, e);
            path.quadTo(xr, 0, xr + e, 0);
            path.close();

            canvas.save();
            canvas.concat(m);
            canvas.drawPath(path, paint);

            float da = Math.max(0f, 1f - prog * 2.5f);
            if (da > 0f) {
                float cx = xl + wc * dotPos;
                float cy = h * 0.5f;
                int al = (int) (255 * da * kedip);
                glowPaint.setAlpha(al);
                dotPaint.setAlpha(al);
                canvas.save();
                canvas.translate(cx, cy);
                canvas.drawCircle(0f, 0f, glowR, glowPaint);
                canvas.restore();
                canvas.drawCircle(cx, cy, dotR, dotPaint);
            }

            if (prog > 0.55f && l1.length() > 0) {
                float ta = Math.min(1f, (prog - 0.55f) / 0.45f);
                int ala = (int) (255 * ta);
                float tx = xl + h * 0.55f;
                float avail = wc - h * 1.1f;
                tp1.setAlpha(ala);
                tp2.setAlpha(ala);
                if (l2.length() > 0) {
                    canvas.drawText(TextUtils.ellipsize(l1, tp1, avail, TextUtils.TruncateAt.END).toString(),
                            tx, hc * 0.42f, tp1);
                    canvas.drawText(TextUtils.ellipsize(l2, tp2, avail, TextUtils.TruncateAt.END).toString(),
                            tx, hc * 0.80f, tp2);
                } else {
                    canvas.drawText(TextUtils.ellipsize(l1, tp1, avail, TextUtils.TruncateAt.END).toString(),
                            tx, hc * 0.60f, tp1);
                }
            }
            canvas.restore();
        }
    }
}
