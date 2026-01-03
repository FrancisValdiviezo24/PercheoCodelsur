package com.codelsur.percheo;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class PermissionUtils {

    public static final int REQ_CAMERA = 1001;
    public static final int REQ_BT12 = 2001;

    // ===== CAMERA =====
    public static boolean hasCamera(Activity a) {
        return ContextCompat.checkSelfPermission(a, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    // ===== BLUETOOTH (Android 12+) =====
    public static boolean isAndroid12Plus() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S; // API 31
    }

    public static boolean hasBluetooth12Permissions(Activity a) {
        if (!isAndroid12Plus()) return true; // en Android 10/11 se concede al instalar

        boolean connect = ContextCompat.checkSelfPermission(a, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
        boolean scan = ContextCompat.checkSelfPermission(a, Manifest.permission.BLUETOOTH_SCAN)
                == PackageManager.PERMISSION_GRANTED;

        return connect && scan;
    }

    public static void requestBluetooth12Permissions(Activity a) {
        if (!isAndroid12Plus()) return;

        ActivityCompat.requestPermissions(
                a,
                new String[]{
                        Manifest.permission.BLUETOOTH_CONNECT,
                        Manifest.permission.BLUETOOTH_SCAN
                },
                REQ_BT12
        );
    }
}
