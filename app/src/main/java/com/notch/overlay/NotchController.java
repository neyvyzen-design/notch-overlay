package com.notch.overlay;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.PixelFormat;
import android.hardware.camera2.CameraManager;
import android.hardware.display.DisplayManager;
import android.location.LocationManager;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioRecordingConfiguration;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.nfc.NfcAdapter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.net.TrafficStats;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.Surface;
import android.view.WindowManager;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.Set;

// Mengurus tampilan notch + titik indikator: dipakai oleh NotchService (overlay biasa)
// dan NotchAccessibilityService (tembus control center).
final class NotchController {
    private static final int HIJAU = 0xFF30D158;
    private static final int MERAH = 0xFFFF3B30;
    private static final int ORANYE = 0xFFFF9500;
    private static final int KUNING = 0xFFFFD60A;
    private static final int KUNING_PUCAT = 0xFFFFF3A0;
    private static final int UNGU = 0xFFAF52DE;
    private static final int UNGU_MUDA = 0xFFC9A0FF;
    private static final int UNGU_TUA = 0xFF5B2A86;
    private static final int ABU = 0xFF8E8E93;
    private static final int PUTIH = 0xFFFFFFFF;
    private static final int PUTIH_HANGAT = 0xFFFFE9C4;
    private static final int CYAN = 0xFF00E5FF;
    private static final int CYAN_TERANG = 0xFF9CF6FF;
    private static final int BIRU = 0xFF0A84FF;
    private static final int BIRU_MUDA = 0xFF64D2FF;
    private static final int BIRU_TUA = 0xFF1F3FBF;
    private static final int MERAH_MUDA = 0xFFFF6FB5;

    private static final int KEDIP_CEPAT = 400;
    private static final int KEDIP_PELAN = 1200;
    private static final long AMBANG_CEPAT_MA = 1500;  // arus pengisian >= ini = "cepat"
    private static final long DURASI_EVENT_MS = 4000;  // lama warna event sesaat
    private static final long CEK_BERKALA_MS = 30000;  // cek RAM + penyimpanan

    private final Context ctx;
    private final int tipeWindow;
    private WindowManager wm;
    private DisplayManager dmgr;
    private CameraManager cam;
    private AudioManager am;
    private ConnectivityManager cm;
    private SharedPreferences sp;
    private final Handler h = new Handler(Looper.getMainLooper());
    private NotchView view;
    private int rot = -1;
    private boolean berjalan = false;
    private boolean siap = false;  // false = baca status awal tanpa memunculkan warna event
    private boolean micAktif = false;
    private boolean mengisi = false;
    private int persen = 100;
    private int warnaNow = HIJAU;
    private int kedipNow = 0;
    private final Set<String> kameraDipakai = new HashSet<String>();
    private final Set<String> senterNyala = new HashSet<String>();

    // status sistem (batch 1)
    private boolean suhuTinggi = false;
    private boolean hematBaterai = false;
    private boolean dnd = false;
    private boolean pesawat = false;

    // status sistem (batch 2)
    private Network netDefault = null;
    private boolean netAda = true, netWifi = false, netSeluler = false, netVpn = false, netValid = true;
    private boolean usb = false, cepat = false, headset = false;
    private boolean alarmAda = false, rotasi = false, nfc = false, gps = false;
    private boolean autoBright = false, gelap = false, hematData = false, terkunci = false;
    private int ringer = AudioManager.RINGER_MODE_NORMAL;
    private boolean ramKritis = false, storPenuh = false;

    // status sistem (batch 3)
    private boolean hotspot = false, roaming = false, thermalPanas = false;
    private boolean dlAktif = false, ulAktif = false, adb = false, brMax = false, brMin = false;
    private boolean reboot = false;
    private long tidakStabilHingga = 0;
    private final long[] riwayatCas = new long[4];
    private int idxCas = 0;
    private long rxPrev = -1, txPrev = -1, tPrev = 0;
    private Object thermalL = null;

    // warna event sesaat (Wi-Fi tersambung, volume, dll): tampil sebentar lalu kembali normal
    private int flashWarna = 0;
    private int flashKedip = 0;
    private long flashHingga = 0;

    private final Runnable bangunUlang = new Runnable() {
        @Override public void run() { pasangNotch(); }
    };
    private final Runnable flashSelesai = new Runnable() {
        @Override public void run() { perbaruiStatus(); }
    };
    private final Runnable tandaiSiap = new Runnable() {
        @Override public void run() { siap = true; }
    };
    private final Runnable cekBerkala = new Runnable() {
        @Override public void run() {
            try {
                ActivityManager a = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
                ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
                a.getMemoryInfo(mi);
                ramKritis = mi.lowMemory || (mi.totalMem > 0 && mi.availMem < mi.totalMem * 0.07);
            } catch (Throwable t) { }
            try {
                StatFs sf = new StatFs(Environment.getDataDirectory().getPath());
                long tot = sf.getTotalBytes();
                storPenuh = tot > 0 && sf.getAvailableBytes() * 10 < tot;
            } catch (Throwable t) { }
            perbaruiStatus();
            if (berjalan) h.postDelayed(this, CEK_BERKALA_MS);
        }
    };

    // Deteksi download/upload dari laju trafik, tiap 2 detik, hanya saat layar menyala
    private final Runnable cekTraffic = new Runnable() {
        @Override public void run() {
            try {
                PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
                if (pm != null && pm.isInteractive()) {
                    long rx = TrafficStats.getTotalRxBytes();
                    long tx = TrafficStats.getTotalTxBytes();
                    long now = SystemClock.elapsedRealtime();
                    if (rx != TrafficStats.UNSUPPORTED && tx != TrafficStats.UNSUPPORTED
                            && rxPrev >= 0 && now > tPrev) {
                        long dt = now - tPrev;
                        long rxr = (rx - rxPrev) * 1000 / dt;
                        long txr = (tx - txPrev) * 1000 / dt;
                        boolean dl = rxr >= 200 * 1024;
                        boolean ul = txr >= 100 * 1024 && txr * 3 > rxr;  // abaikan ACK dari download
                        if (dl != dlAktif || ul != ulAktif) {
                            dlAktif = dl;
                            ulAktif = ul;
                            perbaruiStatus();
                        }
                    }
                    rxPrev = rx;
                    txPrev = tx;
                    tPrev = now;
                } else {
                    rxPrev = -1;
                    if (dlAktif || ulAktif) {
                        dlAktif = false;
                        ulAktif = false;
                        perbaruiStatus();
                    }
                }
            } catch (Throwable t) { }
            if (berjalan) h.postDelayed(this, 2000);
        }
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

    private final CameraManager.TorchCallback torchCb = new CameraManager.TorchCallback() {
        @Override public void onTorchModeChanged(String id, boolean enabled) {
            if (enabled) senterNyala.add(id); else senterNyala.remove(id);
            perbaruiStatus();
        }
        @Override public void onTorchModeUnavailable(String id) {
            senterNyala.remove(id);
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

    private final AudioDeviceCallback devCb = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] d) { bacaHeadset(); perbaruiStatus(); }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] d) { bacaHeadset(); perbaruiStatus(); }
    };

    private final ConnectivityManager.NetworkCallback netCb = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(Network n) {
            netDefault = n;
            netAda = true;
            perbaruiStatus();
        }
        @Override public void onCapabilitiesChanged(Network n, NetworkCapabilities c) {
            netDefault = n;
            netAda = true;
            boolean wifi = c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
            boolean sel = c.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) && !wifi;
            naik(netWifi, wifi, CYAN);
            naik(netSeluler, sel, BIRU_MUDA);
            netWifi = wifi;
            netSeluler = sel;
            netVpn = c.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
            roaming = sel && !c.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING);
            netValid = c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
            perbaruiStatus();
        }
        @Override public void onLost(Network n) {
            if (netDefault != null && !n.equals(netDefault)) return;
            netDefault = null;
            netAda = false;
            netWifi = false;
            netSeluler = false;
            netVpn = false;
            roaming = false;
            perbaruiStatus();
        }
    };

    private final ContentObserver obs = new ContentObserver(h) {
        @Override public void onChange(boolean selfChange) {
            bacaStatusSistem();
            perbaruiStatus();
        }
    };

    private final BroadcastReceiver sysRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (Intent.ACTION_SHUTDOWN.equals(a) || Intent.ACTION_REBOOT.equals(a)) {
                reboot = true;
                perbaruiStatus();
                return;
            }
            if (Intent.ACTION_POWER_CONNECTED.equals(a) || Intent.ACTION_POWER_DISCONNECTED.equals(a)) {
                catatCharger();
                return;
            }
            if ("android.net.wifi.WIFI_AP_STATE_CHANGED".equals(a)) {
                hotspot = i.getIntExtra("wifi_state", 0) == 13;  // 13 = hotspot menyala
                perbaruiStatus();
                return;
            }
            if ("android.media.VOLUME_CHANGED_ACTION".equals(a)) {
                if (siap) flash(PUTIH, 1200, 250);
                return;
            }
            bacaStatusSistem();
            perbaruiStatus();
        }
    };

    private final BroadcastReceiver batRx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            if (level >= 0 && scale > 0) persen = Math.round(level * 100f / scale);
            int suhu = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            if (suhu != Integer.MIN_VALUE) {
                // satuan 0.1 C; nyala di >= 42.0, mati di < 41.0 (hindari kedip-kedip)
                if (suhu >= 420) suhuTinggi = true;
                else if (suhu < 410) suhuTinggi = false;
            }
            int status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            int plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            mengisi = plugged != 0 && status != BatteryManager.BATTERY_STATUS_FULL && persen < 100;
            usb = plugged == BatteryManager.BATTERY_PLUGGED_USB;
            try {
                BatteryManager bm = (BatteryManager) ctx.getSystemService(Context.BATTERY_SERVICE);
                long ua = Math.abs(bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW));
                long ma = ua > 20000 ? ua / 1000 : ua;  // sebagian HP melapor dalam uA, sebagian mA
                cepat = ma >= AMBANG_CEPAT_MA;
            } catch (Throwable t) { cepat = false; }
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
        this.cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        this.sp = ctx.getSharedPreferences(Cfg.PREF, Context.MODE_PRIVATE);
    }

    // ---- pembacaan status ----

    // Munculkan warna event sesaat hanya saat status berubah dari mati ke nyala
    private void naik(boolean lama, boolean baru, int warna) {
        if (siap && baru && !lama) flash(warna, DURASI_EVENT_MS, 0);
    }

    private void flash(int warna, long ms, int kedip) {
        flashWarna = warna;
        flashKedip = kedip;
        flashHingga = SystemClock.uptimeMillis() + ms;
        h.removeCallbacks(flashSelesai);
        h.postDelayed(flashSelesai, ms + 50);
        perbaruiStatus();
    }

    // Charger tidak stabil: >= 4x colok/cabut dalam 20 detik
    private void catatCharger() {
        long now = SystemClock.uptimeMillis();
        riwayatCas[idxCas % 4] = now;
        idxCas++;
        if (idxCas >= 4 && now - riwayatCas[idxCas % 4] < 20000) {
            tidakStabilHingga = now + 15000;
            h.removeCallbacks(flashSelesai);
            h.postDelayed(flashSelesai, 15050);
        }
        perbaruiStatus();
    }

    @android.annotation.TargetApi(29)
    private void pasangThermal() {
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            PowerManager.OnThermalStatusChangedListener l = new PowerManager.OnThermalStatusChangedListener() {
                @Override public void onThermalStatusChanged(int status) {
                    thermalPanas = status >= PowerManager.THERMAL_STATUS_SEVERE;
                    perbaruiStatus();
                }
            };
            thermalL = l;
            pm.addThermalStatusListener(new Executor() {
                @Override public void execute(Runnable r) { h.post(r); }
            }, l);
            thermalPanas = pm.getCurrentThermalStatus() >= PowerManager.THERMAL_STATUS_SEVERE;
        } catch (Throwable t) { }
    }

    @android.annotation.TargetApi(29)
    private void lepasThermal() {
        try {
            if (thermalL != null) {
                PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
                pm.removeThermalStatusListener((PowerManager.OnThermalStatusChangedListener) thermalL);
                thermalL = null;
            }
        } catch (Throwable t) { }
    }

    private void bacaHeadset() {
        try {
            boolean ada = false;
            for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                int t = d.getType();
                if (t == AudioDeviceInfo.TYPE_WIRED_HEADSET || t == AudioDeviceInfo.TYPE_WIRED_HEADPHONES
                        || t == AudioDeviceInfo.TYPE_USB_HEADSET || t == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                        || t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) ada = true;
            }
            headset = ada;
        } catch (Throwable t) { }
    }

    private void bacaStatusSistem() {
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            hematBaterai = pm != null && pm.isPowerSaveMode();
        } catch (Throwable t) { }
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            int f = nm != null ? nm.getCurrentInterruptionFilter() : NotificationManager.INTERRUPTION_FILTER_ALL;
            dnd = f != NotificationManager.INTERRUPTION_FILTER_ALL
                    && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN;
        } catch (Throwable t) { }
        try {
            pesawat = Settings.Global.getInt(ctx.getContentResolver(), Settings.Global.AIRPLANE_MODE_ON, 0) == 1;
        } catch (Throwable t) { }
        try { ringer = am.getRingerMode(); } catch (Throwable t) { }
        try {
            AlarmManager a = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            boolean b = a != null && a.getNextAlarmClock() != null;
            naik(alarmAda, b, KUNING);
            alarmAda = b;
        } catch (Throwable t) { }
        try {
            boolean b = Settings.System.getInt(ctx.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
            naik(rotasi, b, BIRU_MUDA);
            rotasi = b;
        } catch (Throwable t) { }
        try {
            boolean b = Settings.System.getInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC;
            naik(autoBright, b, BIRU_MUDA);
            autoBright = b;
        } catch (Throwable t) { }
        try {
            NfcAdapter na = NfcAdapter.getDefaultAdapter(ctx);
            boolean b = na != null && na.isEnabled();
            naik(nfc, b, UNGU);
            nfc = b;
        } catch (Throwable t) { }
        try {
            LocationManager lm = (LocationManager) ctx.getSystemService(Context.LOCATION_SERVICE);
            boolean b = lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
            naik(gps, b, BIRU);
            gps = b;
        } catch (Throwable t) { }
        try {
            int ui = Resources.getSystem().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            boolean b = ui == Configuration.UI_MODE_NIGHT_YES;
            naik(gelap, b, UNGU_TUA);
            gelap = b;
        } catch (Throwable t) { }
        try {
            boolean b = Settings.Global.getInt(ctx.getContentResolver(), Settings.Global.ADB_ENABLED, 0) == 1;
            naik(adb, b, KUNING);
            adb = b;
        } catch (Throwable t) { }
        try {
            int v = Settings.System.getInt(ctx.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, 128);
            boolean mx = v >= 250;
            boolean mn = v <= 5;
            naik(brMax, mx, PUTIH);
            naik(brMin, mn, ABU);
            brMax = mx;
            brMin = mn;
        } catch (Throwable t) { }
        try {
            hematData = cm.getRestrictBackgroundStatus() == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED;
        } catch (Throwable t) { }
        try {
            KeyguardManager km = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
            terkunci = km != null && km.isKeyguardLocked();
        } catch (Throwable t) { }
    }

    // ---- siklus hidup ----

    void mulai() {
        if (berjalan) return;
        berjalan = true;
        siap = false;
        try {
            Intent st = ctx.registerReceiver(batRx, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (st != null) batRx.onReceive(ctx, st);
        } catch (Throwable t) { }
        bacaStatusSistem();
        bacaHeadset();
        try {
            IntentFilter sf = new IntentFilter();
            sf.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
            sf.addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED);
            sf.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            sf.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
            sf.addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED);
            sf.addAction(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
            sf.addAction(LocationManager.PROVIDERS_CHANGED_ACTION);
            sf.addAction("android.location.MODE_CHANGED");
            sf.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
            sf.addAction(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED);
            sf.addAction(Intent.ACTION_SCREEN_ON);
            sf.addAction(Intent.ACTION_SCREEN_OFF);
            sf.addAction(Intent.ACTION_USER_PRESENT);
            sf.addAction("android.media.VOLUME_CHANGED_ACTION");
            sf.addAction(Intent.ACTION_SHUTDOWN);
            sf.addAction(Intent.ACTION_REBOOT);
            sf.addAction(Intent.ACTION_POWER_CONNECTED);
            sf.addAction(Intent.ACTION_POWER_DISCONNECTED);
            sf.addAction("android.net.wifi.WIFI_AP_STATE_CHANGED");
            ctx.registerReceiver(sysRx, sf);
        } catch (Throwable t) { }
        try {
            ctx.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false, obs);
            ctx.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE), false, obs);
            ctx.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, obs);
            ctx.getContentResolver().registerContentObserver(
                    Settings.Global.getUriFor(Settings.Global.ADB_ENABLED), false, obs);
        } catch (Throwable t) { }
        try { netAda = cm.getActiveNetwork() != null; } catch (Throwable t) { }
        try { cm.registerDefaultNetworkCallback(netCb, h); } catch (Throwable t) { }
        try { am.registerAudioDeviceCallback(devCb, h); } catch (Throwable t) { }
        try { cam.registerTorchCallback(torchCb, h); } catch (Throwable t) { }
        if (Build.VERSION.SDK_INT >= 29) pasangThermal();
        cekTraffic.run();
        pasangNotch();
        try { dmgr.registerDisplayListener(dispL, h); } catch (Throwable t) { }
        try { sp.registerOnSharedPreferenceChangeListener(prefL); } catch (Throwable t) { }
        try { cam.registerAvailabilityCallback(camCb, h); } catch (Throwable t) { }
        try { am.registerAudioRecordingCallback(micCb, h); } catch (Throwable t) { }
        h.postDelayed(tandaiSiap, 2000);
    }

    void berhenti() {
        if (!berjalan) return;
        berjalan = false;
        try { ctx.unregisterReceiver(batRx); } catch (Throwable t) { }
        try { ctx.unregisterReceiver(sysRx); } catch (Throwable t) { }
        try { ctx.getContentResolver().unregisterContentObserver(obs); } catch (Throwable t) { }
        try { cm.unregisterNetworkCallback(netCb); } catch (Throwable t) { }
        try { am.unregisterAudioDeviceCallback(devCb); } catch (Throwable t) { }
        try { cam.unregisterTorchCallback(torchCb); } catch (Throwable t) { }
        try { dmgr.unregisterDisplayListener(dispL); } catch (Throwable t) { }
        try { sp.unregisterOnSharedPreferenceChangeListener(prefL); } catch (Throwable t) { }
        try { cam.unregisterAvailabilityCallback(camCb); } catch (Throwable t) { }
        try { am.unregisterAudioRecordingCallback(micCb); } catch (Throwable t) { }
        if (Build.VERSION.SDK_INT >= 29) lepasThermal();
        h.removeCallbacksAndMessages(null);
        lepasNotch();
    }

    // Prioritas (atas = paling dominan). Hanya kondisi yang jarang/sementara yang boleh
    // menimpa hijau. Kondisi yang bisa aktif berjam-jam (penyimpanan, DND, hemat baterai,
    // VPN, USB, layar terkunci, dll) SENGAJA tidak menimpa warna standby hijau.
    //  1 reboot  2 kamera  3 mikrofon  4 suhu tinggi  5 thermal throttling
    //  6 baterai kritis  7 baterai lemah  8 senter  9 pesawat
    // 10 internet putus  11 Wi-Fi tanpa internet  12 charger tidak stabil
    // 13 warna event sesaat (4 detik)  14 roaming  15 hotspot  16 download  17 upload
    // 18 mengisi (hijau kedip cepat/lambat)  19 penuh/standby = hijau solid
    private void perbaruiStatus() {
        int warna = HIJAU;
        int kedip = 0;
        boolean kabel = mengisi;
        long sekarang = SystemClock.uptimeMillis();
        boolean event = sekarang < flashHingga;
        boolean netPutus = !netAda || (!netValid && netSeluler && !netWifi);
        boolean wifiTanpaNet = netAda && netWifi && !netValid;

        if (reboot) {
            warna = MERAH;
            kedip = KEDIP_CEPAT;
        } else if (!kameraDipakai.isEmpty()) {
            warna = MERAH;
        } else if (micAktif) {
            warna = ORANYE;
        } else if (suhuTinggi) {
            warna = ORANYE;
        } else if (thermalPanas) {
            warna = MERAH;
        } else if (persen < 10 && !kabel) {
            warna = MERAH;
            kedip = KEDIP_PELAN;
        } else if (persen < 20 && !kabel) {
            warna = MERAH;
        } else if (!senterNyala.isEmpty()) {
            warna = PUTIH_HANGAT;
        } else if (pesawat) {
            warna = ABU;
        } else if (netPutus) {
            warna = ABU;
            kedip = KEDIP_PELAN;
        } else if (wifiTanpaNet) {
            warna = ORANYE;
        } else if (sekarang < tidakStabilHingga) {
            warna = ORANYE;
            kedip = KEDIP_PELAN;
        } else if (event) {
            warna = flashWarna;
            kedip = flashKedip;
        } else if (roaming) {
            warna = UNGU;
        } else if (hotspot) {
            warna = BIRU_MUDA;
        } else if (dlAktif) {
            warna = CYAN;
            kedip = KEDIP_PELAN;
        } else if (ulAktif) {
            warna = BIRU;
            kedip = KEDIP_PELAN;
        } else if (kabel) {
            warna = HIJAU;
            kedip = cepat ? KEDIP_CEPAT : KEDIP_PELAN;
        } else {
            warna = HIJAU; // penuh (100%) = hijau solid; standby = hijau
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
