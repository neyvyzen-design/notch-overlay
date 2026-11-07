package com.notch.overlay;

import android.content.Context;
import android.provider.Settings;

final class Cfg {
    static final String PREF = "notch";
    static final String[] KUNCI_TAMPILAN = {"lebar", "tinggi", "geser", "dotsize", "dotpos"};

    static int def(String k) {
        switch (k) {
            case "on": return 1;
            case "lebar": return 40;
            case "tinggi": return 100;
            case "geser": return 0;
            case "dotsize": return 11;
            case "dotpos": return 80;
            default: return 0;
        }
    }

    static int get(Context c, String k) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(k, def(k));
    }

    // true jika Aksesibilitas "Notch iPhone" sudah diaktifkan di pengaturan
    static boolean aksesAktif(Context c) {
        String s = Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return s != null && s.contains(c.getPackageName() + "/");
    }
}
