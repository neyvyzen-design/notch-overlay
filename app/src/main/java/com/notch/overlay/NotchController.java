package com.notch.overlay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.hardware.camera2.CameraManager;
import android.hardware.display.DisplayManager;
import android.media.AudioManager;
import android.media.AudioRecordingConfiguration;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.Surface;
import android.view.WindowManager;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Mengurus tampilan notch + titik indikator: dipakai oleh NotchService (overlay biasa)
// dan NotchAccessibilityService (tembus control center).
final class NotchController {
    private static final int HIJAU = 0xFF30D158;
    private static final int KUNING = 0xFFFFD60A;
    private static final int MERAH_GELAP = 0xFF8B1A1A;
    private static final int MERAH_REKAM = 0xFFFF3B30;

    private final Context ctx;
    private final int tipeWindow;
    private WindowManager wm;
    private DisplayManager dmgr;
    private CameraManager cam;
    private AudioManager am;
    private SharedPreferences sp;
    private final Handler h = new Handler(Looper.getMainLooper());
    private NotchView view;
    private int rot = -1;
    private boolean berjalan = false;
    private boolean micAktif = false;
    private boolean mengisi = false;
    private int persen = 100;
    private int warnaNow = HIJAU;
    private boolean kedipNow = false;
    private final Set<String> kameraDipakai = new HashSet<String>();

    private final Runnable bangunUlang = new Runnable() {
        @Override public void run() { pasangNotch(); }
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener prefL =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences p, String k) {
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
            perbaruiStatus();
        }
        @Override public void onCameraUnavailable(String id) {
            kameraDipakai.add(id);
            perbaruiStatus();
        }
    };

    private final AudioManager.AudioRecordingCallback micCb = new AudioManager.AudioRecordingCallback() {
        @Override
        public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configs) {
            micAktif = configs != null && !configs.isEmpty();
            perbaruiStatus();
        }
    };

    private final BroadcastReceiver batRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (level >= 0 && scale > 0) persen = Math.round(level * 100f / scale);
            int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            int plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            mengisi = plugged != 0 && status != BatteryManager.BATTERY_STATUS_FULL;
            perbaruiStatus();
        }
    };

    NotchController(Context ctx, int tipeWindow) {
        this.ctx = ctx;
        this.tipeWindow = tipeWindow;
        this.wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        this.dmgr = (DisplayManager) ctx.getSystemService(Context.DISPLAY_SERVICE);
        this.cam = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
        this.am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
        this.sp = ctx.getSharedPreferences(Cfg.PREF, Context.MODE_PRIVATE);
    }

    void mulai() {
        if (berjalan) return;
        berjalan = true;
        try {
            Intent st = ctx.registerReceiver(batRx, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (st != null) batRx.onReceive(ctx, st);
        } catch (Throwable t) { }
        pasangNotch();
        try { dmgr.registerDisplayListener(dispL, h); } catch (Throwable t) { }
        try { sp.registerOnSharedPreferenceChangeListener(prefL); } catch (Throwable t) { }
        try { cam.registerAvailabilityCallback(camCb, h); } catch (Throwable t) { }
        try { am.registerAudioRecordingCallback(micCb, h); } catch (Throwable t) { }
    }

    void berhenti() {
        if (!berjalan) return;
        berjalan = false;
        try { ctx.unregisterReceiver(batRx); } catch (Throwable t) { }
        try { dmgr.unregisterDisplayListener(dispL); } catch (Throwable t) { }
        try { sp.unregisterOnSharedPreferenceChangeListener(prefL); } catch (Throwable t) { }
        try { cam.unregisterAvailabilityCallback(camCb); } catch (Throwable t) { }
        try { am.unregisterAudioRecordingCallback(micCb); } catch (Throwable t) { }
        h.removeCallbacksAndMessages(null);
        lepasNotch();
    }

    // Prioritas: merekam > mengisi daya > baterai rendah > normal
    private void perbaruiStatus() {
        int warna;
        boolean kedip = false;
        if (micAktif || !kameraDipakai.isEmpty()) {
            warna = MERAH_REKAM;
        } else if (mengisi) {
            warna = HIJAU;
            kedip = true;
        } else if (persen <= 15) {
            warna = MERAH_GELAP;
        } else if (persen < 30) {
            warna = KUNING;
        } else {
            warna = HIJAU;
        }
        warnaNow = warna;
        kedipNow = kedip;
        if (view != null) view.setStatus(warna, kedip, true);
    }

    private void lepasNotch() {
        if (view != null) {
            try { wm.removeView(view); } catch (Throwable t) { }
            view = null;
        }
    }

    private void pasangNotch() {
        lepasNotch();
        if (Cfg.get(ctx, "on") != 1) return;
        perbaruiStatus();
        try {
            Display d = dmgr.getDisplay(Display.DEFAULT_DISPLAY);
            rot = d.getRotation();
            DisplayMetrics dm = new DisplayMetrics();
            d.getRealMetrics(dm);
            float dens = dm.density;
            int sisiPendek = Math.min(dm.widthPixels, dm.heightPixels);

            // mode 0 = atas tengah, 1 = kiri (landscape), 2 = kanan (landscape)
            int mode = 0;
            if (rot == Surface.ROTATION_90) mode = 1;
            else if (rot == Surface.ROTATION_270) mode = 2;

            int resId = ctx.getResources().getIdentifier("status_bar_height", "dimen", "android");
            int sbh = resId > 0
                    ? ctx.getResources().getDimensionPixelSize(resId)
                    : Math.round(24 * dens);
            int hh = Math.max(8, Math.round(sbh * Cfg.get(ctx, "tinggi") / 100f));
            int w = Math.round(sisiPendek * Cfg.get(ctx, "lebar") / 100f);
            int ear = Math.round(hh * 0.25f);
            int geser = Math.round(Cfg.get(ctx, "geser") * dens);
            float dotR = hh * Cfg.get(ctx, "dotsize") / 100f;
            float dotPos = Cfg.get(ctx, "dotpos") / 100f;
            int panjang = w + 2 * ear;

            NotchView v = new NotchView(ctx, w, hh, ear, mode, dotR, dotPos);
            v.setStatus(warnaNow, kedipNow, false);

            WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                    mode == 0 ? panjang : hh,
                    mode == 0 ? hh : panjang,
                    tipeWindow,
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
}
