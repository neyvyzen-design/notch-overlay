package com.notch.overlay;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
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
        if (Settings.canDrawOverlays(this) && Cfg.get(this, "on") == 1) {
            startForegroundService(new Intent(this, NotchService.class));
        }
        bangun();
    }

    private boolean notifAktif() {
        String s = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(getPackageName());
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
        boolean izinNotif = notifAktif();

        Switch sw = new Switch(this);
        sw.setText("Notch aktif (jalan otomatis setelah restart)");
        sw.setTextSize(16);
        sw.setPadding(0, dp(10), 0, dp(10));
        sw.setChecked(izinOverlay && Cfg.get(this, "on") == 1);
        sw.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton v, boolean c) {
                if (c) {
                    if (!Settings.canDrawOverlays(MainActivity.this)) {
                        Toast.makeText(MainActivity.this, "Izinkan 'Tampilkan di atas aplikasi lain' dulu", Toast.LENGTH_LONG).show();
                        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName())));
                        v.setChecked(false);
                        return;
                    }
                    sp.edit().putInt("on", 1).apply();
                    if (Build.VERSION.SDK_INT >= 33) {
                        requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
                    }
                    startForegroundService(new Intent(MainActivity.this, NotchService.class));
                } else {
                    sp.edit().putInt("on", 0).apply();
                    stopService(new Intent(MainActivity.this, NotchService.class));
                }
            }
        });
        root.addView(sw);

        teks(izinOverlay ? "\u2714 Izin overlay aktif" : "\u2718 Izin overlay belum aktif", 14, false);
        if (!izinOverlay) {
            tombol("Buka izin overlay", new Runnable() {
                @Override public void run() {
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + getPackageName())));
                }
            });
        }
        teks(izinNotif ? "\u2714 Akses notifikasi aktif (animasi notif dan musik)"
                : "\u2718 Akses notifikasi belum aktif (perlu untuk animasi notif dan musik)", 14, false);
        if (!izinNotif) {
            tombol("Buka akses notifikasi", new Runnable() {
                @Override public void run() {
                    startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                }
            });
        }

        teks("Tampilan", 20, true);
        seek("Panjang notch (%)", "lebar", 20, 60);
        seek("Tinggi notch (%)", "tinggi", 70, 140);
        seek("Geser kiri/kanan", "geser", -100, 100);
        seek("Ukuran titik", "dotsize", 6, 20);
        seek("Posisi titik (%)", "dotpos", 50, 90);

        teks("Warna titik", 14, false);
        final Spinner sp2 = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, Cfg.NAMA_WARNA);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp2.setAdapter(ad);
        sp2.setSelection(Cfg.get(this, "warna"));
        sp2.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (pos != Cfg.get(MainActivity.this, "warna")) sp.edit().putInt("warna", pos).apply();
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });
        root.addView(sp2);

        Switch sl = new Switch(this);
        sl.setText("Sembunyikan notch saat landscape");
        sl.setPadding(0, dp(16), 0, dp(8));
        sl.setChecked(Cfg.get(this, "sembunyi") == 1);
        sl.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton v, boolean c) {
                sp.edit().putInt("sembunyi", c ? 1 : 0).apply();
            }
        });
        root.addView(sl);

        teks("Uji coba", 20, true);
        tombol("Tes animasi notifikasi", new Runnable() {
            @Override public void run() {
                if (NotchService.instance == null) {
                    Toast.makeText(MainActivity.this, "Aktifkan notch dulu", Toast.LENGTH_SHORT).show();
                } else {
                    NotchService.tampilkan("Tes notifikasi", "Notch bekerja dengan baik", 3000);
                }
            }
        });
        tombol("Reset tampilan ke awal", new Runnable() {
            @Override public void run() {
                SharedPreferences.Editor e = sp.edit();
                for (String k : Cfg.KUNCI_TAMPILAN) e.remove(k);
                e.apply();
                bangun();
            }
        });
    }
}
