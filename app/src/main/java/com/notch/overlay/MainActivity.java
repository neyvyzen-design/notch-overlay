package com.notch.overlay;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private SharedPreferences sp;
    private LinearLayout root;

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences(Cfg.PREF, MODE_PRIVATE);
        ScrollView sv = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(28), dp(18), dp(36));
        sv.addView(root);
        setContentView(sv);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Cfg.get(this, "on") == 1 && !Cfg.aksesAktif(this) && Settings.canDrawOverlays(this)) {
            startForegroundService(new Intent(this, NotchService.class));
        }
        bangun();
    }

    private TextView teks(String t, int size, boolean tebal) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(size);
        tv.setPadding(0, dp(8), 0, dp(4));
        if (tebal) tv.setTypeface(tv.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(tv);
        return tv;
    }

    private void tombol(String t, final Runnable aksi) {
        Button bt = new Button(this);
        bt.setText(t);
        bt.setAllCaps(false);
        bt.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { aksi.run(); }
        });
        root.addView(bt);
    }

    // Buka layar pengaturan; kalau gagal, buka info aplikasi
    private void buka(Intent utama) {
        try {
            startActivity(utama);
        } catch (Throwable t) {
            try {
                Toast.makeText(this, "Buka manual: Info aplikasi > Mulai otomatis / Penghemat baterai", Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Throwable t2) { }
        }
    }

    private void seek(final String label, final String key, int min, int max) {
        final TextView tv = new TextView(this);
        tv.setTextSize(14);
        tv.setPadding(0, dp(14), 0, 0);
        int cur = Cfg.get(this, key);
        tv.setText(label + ": " + cur);
        root.addView(tv);
        SeekBar sb = new SeekBar(this);
        sb.setMin(min);
        sb.setMax(max);
        sb.setProgress(cur);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean user) {
                tv.setText(label + ": " + p);
                if (user) sp.edit().putInt(key, p).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        root.addView(sb);
    }

    private void bangun() {
        root.removeAllViews();
        teks("Notch iPhone", 26, true);

        final boolean izinOverlay = Settings.canDrawOverlays(this);
        final boolean akses = Cfg.aksesAktif(this);

        Switch sw = new Switch(this);
        sw.setText("Notch aktif (jalan otomatis setelah restart)");
        sw.setTextSize(16);
        sw.setPadding(0, dp(10), 0, dp(10));
        sw.setChecked(Cfg.get(this, "on") == 1 && (izinOverlay || akses));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton v, boolean c) {
                if (c) {
                    if (!Settings.canDrawOverlays(MainActivity.this) && !Cfg.aksesAktif(MainActivity.this)) {
                        Toast.makeText(MainActivity.this, "Izinkan 'Tampilkan di atas aplikasi lain' dulu", Toast.LENGTH_LONG).show();
                        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName())));
                        v.setChecked(false);
                        return;
                    }
                    sp.edit().putInt("on", 1).apply();
                    if (!Cfg.aksesAktif(MainActivity.this)) {
                        startForegroundService(new Intent(MainActivity.this, NotchService.class));
                    }
                } else {
                    sp.edit().putInt("on", 0).apply();
                    stopService(new Intent(MainActivity.this, NotchService.class));
                }
            }
        });
        root.addView(sw);

        teks("Mode tampil: " + (akses
                ? "Aksesibilitas (di atas control center dan lock screen)"
                : "Overlay biasa (di bawah control center)"), 14, false);

        teks("Izin dan mode", 20, true);
        teks(izinOverlay ? "\u2714 Izin overlay aktif" : "\u2718 Izin overlay belum aktif", 14, false);
        if (!izinOverlay) {
            tombol("Buka izin overlay", new Runnable() {
                @Override public void run() {
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName())));
                }
            });
        }
        teks(akses ? "\u2714 Aksesibilitas aktif (tembus control center)"
                : "\u2718 Aksesibilitas belum aktif. Aktifkan agar notch tampil di atas control center dan lock screen.", 14, false);
        if (!akses) {
            tombol("Buka pengaturan Aksesibilitas", new Runnable() {
                @Override public void run() {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                }
            });
        }

        teks("Latar belakang", 20, true);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean bebas = pm.isIgnoringBatteryOptimizations(getPackageName());
        teks(bebas ? "\u2714 Bebas dari batasan baterai" : "\u2718 Masih dibatasi optimasi baterai", 14, false);
        if (!bebas) {
            tombol("Bebaskan dari batasan baterai", new Runnable() {
                @Override public void run() {
                    try {
                        startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:" + getPackageName())));
                    } catch (Throwable t) {
                        buka(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                    }
                }
            });
        }
        tombol("Mulai otomatis (Xiaomi/Redmi)", new Runnable() {
            @Override public void run() {
                Intent i = new Intent();
                i.setComponent(new ComponentName("com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"));
                buka(i);
            }
        });
        tombol("Batasan baterai app (Xiaomi/Redmi)", new Runnable() {
            @Override public void run() {
                Intent i = new Intent();
                i.setComponent(new ComponentName("com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"));
                i.putExtra("package_name", getPackageName());
                i.putExtra("package_label", "Notch iPhone");
                buka(i);
            }
        });

        teks("Ukuran dan posisi poni", 20, true);
        seek("Panjang notch (%)", "lebar", 20, 60);
        seek("Tinggi notch (%)", "tinggi", 70, 140);
        seek("Geser posisi kiri/kanan", "geser", -100, 100);

        teks("Titik hijau", 20, true);
        seek("Ukuran titik", "dotsize", 6, 20);
        seek("Posisi titik (%)", "dotpos", 50, 90);

        tombol("Reset ukuran dan posisi", new Runnable() {
            @Override public void run() {
                SharedPreferences.Editor e = sp.edit();
                for (String k : Cfg.KUNCI_TAMPILAN) e.remove(k);
                e.apply();
                bangun();
            }
        });
    }
}
