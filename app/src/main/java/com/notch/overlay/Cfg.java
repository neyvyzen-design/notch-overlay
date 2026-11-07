package com.notch.overlay;

import android.content.Context;

final class Cfg {
    static final String PREF = "notch";
    static final int[] WARNA = {0xFF30D158, 0xFF0A84FF, 0xFFFF453A, 0xFFFFD60A, 0xFFFFFFFF};
    static final String[] NAMA_WARNA = {"Hijau", "Biru", "Merah", "Kuning", "Putih"};
    static final String[] KUNCI_TAMPILAN = {"lebar", "tinggi", "geser", "dotsize", "dotpos", "warna", "sembunyi"};

    static int def(String k) {
        switch (k) {
            case "on": return 1;
            case "lebar": return 40;
            case "tinggi": return 100;
            case "geser": return 0;
            case "dotsize": return 11;
            case "dotpos": return 80;
            case "warna": return 0;
            case "sembunyi": return 0;
            default: return 0;
        }
    }

    static int get(Context c, String k) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(k, def(k));
    }
}
