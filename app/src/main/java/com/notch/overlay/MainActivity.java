package com.notch.overlay;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Izinkan 'Tampilkan di atas aplikasi lain', lalu buka app ini lagi", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } else {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
            }
            startForegroundService(new Intent(this, NotchService.class));
            Toast.makeText(this, "Notch aktif", Toast.LENGTH_SHORT).show();
        }
        finish();
    }
}
