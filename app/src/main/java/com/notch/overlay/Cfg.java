package com.notch.overlay;

import android.content.Context;

import java.util.Calendar;

final class Cfg {
    static final String PREF = "notch";
    static final int[] WARNA = {0xFF30D158, 0xFF0A84FF, 0xFFFF453A, 0xFFFFD60A, 0xFFFFFFFF};
    static final String[] NAMA_WARNA = {"Hijau", "Biru", "Merah", "Kuning", "Putih"};
    static final String[] KUNCI_TAMPILAN = {"lebar", "tinggi", "geser", "dotsize", "dotpos", "warna", "sembunyi",
            "durasi", "baterai", "getar", "senyap", "senyap_mulai", "senyap_selesai"};

    static final String[] TANPA_BANGUN = {"on", "durasi", "baterai", "getar", "senyap", "senyap_mulai", "senyap_selesai"};

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
            case "durasi": return 3;
            case "baterai": return 1;
            case "getar": return 0;
            case "senyap": return 0;
            case "senyap_mulai": return 22;
            case "senyap_selesai": return 6;
            default: return 0;
        }
    }

    static boolean tanpaBangun(String k) {
        for (String s : TANPA_BANGUN) if (s.equals(k)) return true;
        return false;
    }

    static boolean senyapSekarang(Context c) {
        if (get(c, "senyap") != 1) return false;
        int m = get(c, "senyap_mulai");
        int s = get(c, "senyap_selesai");
        if (m == s) return false;
        int j = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return m < s ? (j >= m && j < s) : (j >= m || j < s);
    }

    static int get(Context c, String k) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(k, def(k));
    }
}
