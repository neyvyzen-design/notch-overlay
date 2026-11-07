package com.notch.overlay;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
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
import android.view.animation.AccelerateDecelerateInterpolator;

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
    private KartuView kartu;
    private int rot = -1;
    private boolean mulai = false;
    private boolean micAktif = false;
    private int curSB, curHH, curSisi, curMode;
    private float curDens = 1f;
    private final Set<String> kameraDipakai = new HashSet<String>();
    private final Handler h = new Handler(Looper.getMainLooper());

    private final Runnable bangunUlang = new Runnable() {
        @Override public void run() { pasangNotch(); }
    };

    private final Runnable tutupKartu = new Runnable() {
        @Override public void run() {
            final KartuView k = kartu;
            if (k == null) return;
            k.keluar(new Runnable() {
                @Override public void run() {
                    if (kartu == k) lepasKartu();
                }
            });
        }
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener prefL =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences p, String k) {
                    if ("on".equals(k)) return;
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

    static void tampilkan(String a, String b, long ms) {
        tampilkan(a, b, ms, null);
    }

    static void tampilkan(final String a, final String b, final long ms, final String pkg) {
        final NotchService s = instance;
        if (s == null) return;
        s.h.post(new Runnable() {
            @Override public void run() {
                if (s.mulai) s.tampilKartu(a, b, ms, pkg);
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
        }
        return START_STICKY;
    }

    private void lepasNotch() {
        if (view != null) {
            try { wm.removeView(view); } catch (Throwable t) { }
            view = null;
        }
    }

    private void lepasKartu() {
        h.removeCallbacks(tutupKartu);
        if (kartu != null) {
            try { wm.removeView(kartu); } catch (Throwable t) { }
            kartu = null;
        }
    }

    private void hitungDasar() {
        Display d = dmgr.getDisplay(Display.DEFAULT_DISPLAY);
        rot = d.getRotation();
        DisplayMetrics dm = new DisplayMetrics();
        d.getRealMetrics(dm);
        curDens = dm.density;
        curSisi = Math.min(dm.widthPixels, dm.heightPixels);
        curMode = 0;
        if (rot == Surface.ROTATION_90) curMode = 1;
        else if (rot == Surface.ROTATION_270) curMode = 2;
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        curSB = resId > 0
                ? getResources().getDimensionPixelSize(resId)
                : Math.round(24 * curDens);
        curHH = Math.max(8, Math.round(curSB * Cfg.get(this, "tinggi") / 100f));
    }

    private void aturCutout(WindowManager.LayoutParams p) {
        if (Build.VERSION.SDK_INT >= 30) {
            p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        } else if (Build.VERSION.SDK_INT >= 28) {
            p.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
    }

    private int flagOverlay() {
        return WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
    }

    private void pasangNotch() {
        lepasNotch();
        lepasKartu();
        try {
            hitungDasar();
            if (curMode != 0 && Cfg.get(this, "sembunyi") == 1) return;

            int hh = curHH;
            int w = Math.round(curSisi * Cfg.get(this, "lebar") / 100f);
            int ear = Math.round(hh * 0.25f);
            int geser = Math.round(Cfg.get(this, "geser") * curDens);
            float dotR = hh * Cfg.get(this, "dotsize") / 100f;
            float dotPos = Cfg.get(this, "dotpos") / 100f;
            int warna = Cfg.WARNA[Math.max(0, Math.min(Cfg.WARNA.length - 1, Cfg.get(this, "warna")))];
            int panjang = w + 2 * ear;

            NotchView v = new NotchView(this, w, hh, ear, curMode, warna, dotR, dotPos);
            v.setSedangDipakai(micAktif || !kameraDipakai.isEmpty());

            WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                    curMode == 0 ? panjang : hh,
                    curMode == 0 ? hh : panjang,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    flagOverlay(),
                    PixelFormat.TRANSLUCENT);
            if (curMode == 0) {
                p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                p.x = geser;
                p.y = 0;
            } else if (curMode == 1) {
                p.gravity = Gravity.LEFT | Gravity.CENTER_VERTICAL;
                p.x = 0;
                p.y = -geser;
            } else {
                p.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
                p.x = 0;
                p.y = geser;
            }
            aturCutout(p);
            wm.addView(v, p);
            view = v;
        } catch (Throwable t) {
            view = null;
        }
    }

    private void tampilKartu(String l1, String l2, long ms, String pkg) {
        try {
            if (curSB == 0) hitungDasar();
            Drawable ikon = null;
            if (pkg != null) {
                try { ikon = getPackageManager().getApplicationIcon(pkg); } catch (Throwable t) { }
            }
            h.removeCallbacks(tutupKartu);
            if (kartu != null) {
                kartu.update(l1, l2, ikon);
            } else {
                int extra = Math.round(Cfg.get(this, "kartu_turun") * curDens);
                int cardTop = (curMode == 0 ? curHH : 0) + extra;
                int cardH = Math.round(curSB * 2.0f);
                int cardW = Math.round(curSisi * 0.90f);
                int viewH = cardTop + cardH + Math.round(6 * curDens);

                int wNotch = Math.round(curSisi * Cfg.get(this, "lebar") / 100f)
                        + 2 * Math.round(curHH * 0.25f);
                float sx0 = Math.min(1f, Math.max(0.2f, (float) wNotch / cardW));
                float sy0 = Math.min(1f, Math.max(0.2f, (float) curHH / cardH));

                KartuView k = new KartuView(this, cardW, cardTop, cardH,
                        Cfg.get(this, "kaca"), curDens, l1, l2, ikon,
                        Cfg.get(this, "kaca_on") == 1, sx0, sy0);

                WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                        cardW, viewH,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        flagOverlay(),
                        PixelFormat.TRANSLUCENT);
                p.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
                p.x = (curMode == 0) ? Math.round(Cfg.get(this, "geser") * curDens) : 0;
                p.y = 0;
                aturCutout(p);
                wm.addView(k, p);
                kartu = k;
                k.masuk();
            }
            h.postDelayed(tutupKartu, ms);
        } catch (Throwable t) {
            kartu = null;
        }
    }

    @Override
    public void onDestroy() {
        instance = null;
        try { dmgr.unregisterDisplayListener(dispL); } catch (Throwable t) { }
        try { sp.unregisterOnSharedPreferenceChangeListener(prefL); } catch (Throwable t) { }
        try { cam.unregisterAvailabilityCallback(camCb); } catch (Throwable t) { }
        try { am.unregisterAudioRecordingCallback(micCb); } catch (Throwable t) { }
        h.removeCallbacksAndMessages(null);
        lepasKartu();
        lepasNotch();
        super.onDestroy();
    }

    // ===================== NOTCH (bentuk tetap + titik) =====================
    static class NotchView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final float cx, cy, dotR, glowR;
        private float kedip = 1f;
        private boolean dipakai = false;
        private ValueAnimator animKedip;

        NotchView(Context c, int w, int h, int e, int mode, int warna, float dotR, float dotPos) {
            super(c);
            this.dotR = dotR;
            this.glowR = dotR * 2.9f;

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

            float[] pt = { e + w * dotPos, h * 0.5f };
            Matrix m = new Matrix();
            float panjang = w + 2f * e;
            if (mode == 1) {
                m.postRotate(-90);
                m.postTranslate(0, panjang);
            } else if (mode == 2) {
                m.postRotate(90);
                m.postTranslate(h, 0);
            }
            path.transform(m);
            m.mapPoints(pt);
            this.cx = pt[0];
            this.cy = pt[1];

            dotPaint.setColor(warna | 0xFF000000);
            dotPaint.setStyle(Paint.Style.FILL);
            int rgb = warna & 0x00FFFFFF;
            glowPaint.setShader(new RadialGradient(0f, 0f, glowR,
                    new int[]{(150 << 24) | rgb, rgb},
                    new float[]{0.2f, 1f}, Shader.TileMode.CLAMP));
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
            if (animKedip != null) animKedip.cancel();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawPath(path, paint);
            int al = (int) (255 * kedip);
            glowPaint.setAlpha(al);
            dotPaint.setAlpha(al);
            canvas.save();
            canvas.translate(cx, cy);
            canvas.drawCircle(0f, 0f, glowR, glowPaint);
            canvas.restore();
            canvas.drawCircle(cx, cy, dotR, dotPaint);
        }
    }

    // ===================== Interpolator pegas (spring) ala iOS =====================
    static class PegasInterpolator implements TimeInterpolator {
        private final float zeta, omega, omegaD;

        PegasInterpolator(float zeta, float omega) {
            this.zeta = zeta;
            this.omega = omega;
            this.omegaD = (float) (omega * Math.sqrt(1.0 - zeta * zeta));
        }

        @Override
        public float getInterpolation(float t) {
            if (t <= 0f) return 0f;
            if (t >= 1f) return 1f;
            double e = Math.exp(-zeta * omega * t);
            double v = 1.0 - e * (Math.cos(omegaD * t) + (zeta * omega / omegaD) * Math.sin(omegaD * t));
            return (float) v;
        }
    }

    // ===================== KARTU NOTIFIKASI (liquid glass + morph dari notch) =====================
    static class KartuView extends View {
        private final float L, T, R, B, r, cardH, cardTop, dens, sx0, sy0;
        private final boolean kacaOn;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint blob = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hi = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint ringFill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint tp1 = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint tp2 = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final RectF rectHi = new RectF();
        private final RectF rectRim = new RectF();
        private final Path lingkaran = new Path();
        private Drawable ikon;
        private String l1, l2;
        private boolean sedangKeluar = false;
        private float p = 0f;
        private float konten = 0f;
        private ValueAnimator anim;

        KartuView(Context c, int viewW, int cardTop, int cardH, int opas, float dens,
                  String l1, String l2, Drawable ikon, boolean kacaOn, float sx0, float sy0) {
            super(c);
            this.cardTop = cardTop;
            this.cardH = cardH;
            this.dens = dens;
            this.kacaOn = kacaOn;
            this.sx0 = sx0;
            this.sy0 = sy0;
            this.l1 = l1 == null ? "" : l1;
            this.l2 = l2 == null ? "" : l2;
            this.ikon = ikon;
            float op = opas / 100f;
            L = 0f;
            R = viewW;
            T = cardTop;
            B = cardTop + cardH;
            r = cardH * 0.46f;

            rect.set(L + dens, T, R - dens, B);
            rectHi.set(L + dens * 5f, T + dens * 2.5f, R - dens * 5f, T + cardH * 0.5f);
            float sw = dens * 1.3f;
            rectRim.set(rect.left + sw / 2f, rect.top + sw / 2f, rect.right - sw / 2f, rect.bottom - sw / 2f);
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(sw);

            if (kacaOn) {
                int aTop = Math.min(255, Math.round(255 * op * 0.92f));
                int aBot = Math.min(255, Math.round(255 * op));
                fill.setShader(new LinearGradient(0f, T, 0f, B,
                        Color.argb(aTop, 62, 66, 78), Color.argb(aBot, 14, 16, 22),
                        Shader.TileMode.CLAMP));
                hi.setShader(new LinearGradient(0f, rectHi.top, 0f, rectHi.bottom,
                        0x4DFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
                blob.setShader(new RadialGradient(L + cardH * 0.8f, T + cardH * 0.15f, cardH * 1.3f,
                        0x38FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
                rim.setShader(new LinearGradient(L, T, R, B,
                        new int[]{0xD8FFFFFF, 0x1CFFFFFF, 0x80FFFFFF},
                        new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            } else {
                fill.setColor(Color.argb(240, 22, 22, 26));
                rim.setColor(0x33FFFFFF);
            }

            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(dens);
            ring.setColor(0x66FFFFFF);
            ringFill.setStyle(Paint.Style.FILL);
            ringFill.setColor(0x33FFFFFF);
            glyph.setColor(Color.WHITE);
            glyph.setTextAlign(Paint.Align.CENTER);
            glyph.setTextSize(cardH * 0.3f);

            tp1.setColor(Color.WHITE);
            tp1.setTextSize(cardH * 0.24f);
            tp1.setTypeface(Typeface.DEFAULT_BOLD);
            tp2.setColor(0xFFD0D0D0);
            tp2.setTextSize(cardH * 0.20f);

            setPivotX(R / 2f);
            setPivotY(T);
            terapkan(0f);
        }

        // v = 0 : sebesar notch di atas layar, v = 1 : kartu penuh (v > 1 = pantulan pegas)
        private void terapkan(float v) {
            p = v;
            setScaleX(sx0 + (1f - sx0) * v);
            setScaleY(sy0 + (1f - sy0) * v);
            setTranslationY(-cardTop * (1f - v));
            setAlpha(Math.max(0f, Math.min(1f, v * 3f)));
            konten = Math.max(0f, Math.min(1f, (v - 0.6f) / 0.3f));
            invalidate();
        }

        private void jalankan(float dari, float ke, long durasi, TimeInterpolator ti) {
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(dari, ke);
            anim.setDuration(durasi);
            anim.setInterpolator(ti);
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) {
                    terapkan((Float) a.getAnimatedValue());
                }
            });
            anim.start();
        }

        void masuk() {
            sedangKeluar = false;
            terapkan(0f);
            jalankan(0f, 1f, 700, new PegasInterpolator(0.72f, 13f));
        }

        void keluar(final Runnable selesai) {
            sedangKeluar = true;
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(p, 0f);
            anim.setDuration(340);
            anim.setInterpolator(new AccelerateDecelerateInterpolator());
            anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override public void onAnimationUpdate(ValueAnimator a) {
                    terapkan((Float) a.getAnimatedValue());
                }
            });
            anim.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator a) {
                    if (sedangKeluar) selesai.run();
                }
            });
            anim.start();
        }

        void update(String a, String b, Drawable ic) {
            sedangKeluar = false;
            if (anim != null) anim.cancel();
            l1 = a == null ? "" : a;
            l2 = b == null ? "" : b;
            ikon = ic;
            jalankan(Math.min(p, 0.85f), 1f, 450, new PegasInterpolator(0.65f, 14f));
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (anim != null) anim.cancel();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawRoundRect(rect, r, r, fill);
            if (kacaOn) {
                canvas.drawRoundRect(rect, r, r, blob);
                canvas.drawRoundRect(rectHi, r, r, hi);
            }
            canvas.drawRoundRect(rectRim, r, r, rim);

            if (konten <= 0.01f) return;
            int ala = (int) (255 * konten);

            float s = cardH * 0.56f;
            float ix = L + cardH * 0.22f;
            float iy = T + (cardH - s) / 2f;
            float cxi = ix + s / 2f;
            float cyi = iy + s / 2f;
            ring.setAlpha((int) (102 * konten));
            if (ikon != null) {
                canvas.save();
                lingkaran.reset();
                lingkaran.addCircle(cxi, cyi, s / 2f, Path.Direction.CW);
                canvas.clipPath(lingkaran);
                ikon.setAlpha(ala);
                ikon.setBounds(Math.round(ix), Math.round(iy), Math.round(ix + s), Math.round(iy + s));
                ikon.draw(canvas);
                canvas.restore();
                canvas.drawCircle(cxi, cyi, s / 2f, ring);
            } else {
                ringFill.setAlpha((int) (51 * konten));
                canvas.drawCircle(cxi, cyi, s / 2f, ringFill);
                canvas.drawCircle(cxi, cyi, s / 2f, ring);
                glyph.setAlpha(ala);
                String g = l1.startsWith("\u266A") ? "\u266A" : "N";
                canvas.drawText(g, cxi, cyi - (glyph.ascent() + glyph.descent()) / 2f, glyph);
            }

            float tx = ix + s + cardH * 0.2f;
            float avail = R - tx - cardH * 0.3f;
            tp1.setAlpha(ala);
            tp2.setAlpha(ala);
            if (l1.length() > 0) {
                if (l2.length() > 0) {
                    canvas.drawText(TextUtils.ellipsize(l1, tp1, avail, TextUtils.TruncateAt.END).toString(),
                            tx, T + cardH * 0.43f, tp1);
                    canvas.drawText(TextUtils.ellipsize(l2, tp2, avail, TextUtils.TruncateAt.END).toString(),
                            tx, T + cardH * 0.75f, tp2);
                } else {
                    canvas.drawText(TextUtils.ellipsize(l1, tp1, avail, TextUtils.TruncateAt.END).toString(),
                            tx, T + cardH * 0.60f, tp1);
                }
            }
        }
    }
}
