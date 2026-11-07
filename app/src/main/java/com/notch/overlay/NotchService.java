package com.notch.overlay;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.WindowManager;

public class NotchService extends Service {
    private NotchController ctl;

    @Override
    public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        ctl = new NotchController(this, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
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

        // Kalau mode Aksesibilitas sedang jalan, overlay biasa tidak diperlukan
        if (!Settings.canDrawOverlays(this) || NotchAccessibilityService.instance != null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        ctl.mulai();
        return START_STICKY;
    }

    // Kalau app digeser dari Recent apps, nyalakan lagi otomatis
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        try {
            if (Cfg.get(this, "on") == 1 && NotchAccessibilityService.instance == null) {
                Intent i = new Intent(getApplicationContext(), NotchService.class);
                PendingIntent pi = PendingIntent.getForegroundService(getApplicationContext(), 1, i,
                        PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
                AlarmManager a = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                a.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000, pi);
            }
        } catch (Throwable t) { }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        if (ctl != null) ctl.berhenti();
        super.onDestroy();
    }
}
