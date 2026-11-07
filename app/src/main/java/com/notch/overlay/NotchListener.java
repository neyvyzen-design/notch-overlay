package com.notch.overlay;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.ArrayList;
import java.util.List;

public class NotchListener extends NotificationListenerService {
    private MediaSessionManager msm;
    private ComponentName cn;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final List<Pantau> pantau = new ArrayList<Pantau>();
    private String terakhir = "";
    private long waktu = 0;

    private final MediaSessionManager.OnActiveSessionsChangedListener sesiL =
            new MediaSessionManager.OnActiveSessionsChangedListener() {
                @Override
                public void onActiveSessionsChanged(List<MediaController> list) {
                    pasangSesi(list);
                }
            };

    private class Pantau extends MediaController.Callback {
        final MediaController c;
        boolean main;

        Pantau(MediaController c) {
            this.c = c;
            PlaybackState s = c.getPlaybackState();
            main = s != null && s.getState() == PlaybackState.STATE_PLAYING;
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState s) {
            boolean m = s != null && s.getState() == PlaybackState.STATE_PLAYING;
            if (m && !main) tampilLagu(c.getMetadata());
            main = m;
        }

        @Override
        public void onMetadataChanged(MediaMetadata md) {
            if (main) tampilLagu(md);
        }
    }

    private void tampilLagu(MediaMetadata md) {
        try {
            if (md == null) return;
            String t = md.getString(MediaMetadata.METADATA_KEY_TITLE);
            String a = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
            if (t == null) return;
            String key = t + "|" + a;
            long now = SystemClock.elapsedRealtime();
            if (key.equals(terakhir) && now - waktu < 5000) return;
            terakhir = key;
            waktu = now;
            NotchService.tampilkan("\u266A " + t, a == null ? "" : a, 3500);
        } catch (Throwable t) { }
    }

    private void lepasSesi() {
        for (Pantau p : pantau) {
            try { p.c.unregisterCallback(p); } catch (Throwable t) { }
        }
        pantau.clear();
    }

    private void pasangSesi(List<MediaController> list) {
        lepasSesi();
        if (list == null) return;
        for (MediaController c : list) {
            try {
                Pantau p = new Pantau(c);
                c.registerCallback(p, h);
                pantau.add(p);
            } catch (Throwable t) { }
        }
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        try {
            msm = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            cn = new ComponentName(this, NotchListener.class);
            msm.addOnActiveSessionsChangedListener(sesiL, cn);
            pasangSesi(msm.getActiveSessions(cn));
        } catch (Throwable t) { }
    }

    @Override
    public void onListenerDisconnected() {
        try {
            if (msm != null) msm.removeOnActiveSessionsChangedListener(sesiL);
        } catch (Throwable t) { }
        lepasSesi();
        super.onListenerDisconnected();
    }

    private String namaApp(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Throwable t) {
            return "";
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            if (sbn == null || getPackageName().equals(sbn.getPackageName())) return;
            Notification n = sbn.getNotification();
            if (n == null) return;
            if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
            if ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0) return;
            if (Notification.CATEGORY_TRANSPORT.equals(n.category)) return;
            Bundle ex = n.extras;
            if (ex == null) return;
            CharSequence judul = ex.getCharSequence(Notification.EXTRA_TITLE);
            CharSequence isi = ex.getCharSequence(Notification.EXTRA_TEXT);
            if (judul == null && isi == null) return;
            String l1 = judul != null ? judul.toString() : namaApp(sbn.getPackageName());
            String l2 = isi != null ? isi.toString() : "";
            NotchService.tampilkan(l1, l2, 3500, sbn.getPackageName());
        } catch (Throwable t) { }
    }
}
