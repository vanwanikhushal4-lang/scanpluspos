package com.example.hardware;
import android.app.ActivityManager;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
public class Systeminfo extends DiagnosticActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        status.setText(Build.MANUFACTURER + " " + Build.MODEL);
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(memory);
        detail.setText("Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n" +
            "Build: " + Build.DISPLAY + "\nBoard: " + Build.BOARD + "\nBootloader: " + Build.BOOTLOADER +
            "\nBrand: " + Build.BRAND + "\nDevice: " + Build.DEVICE + "\nHardware: " + Build.HARDWARE +
            "\nSerial: " + Build.SERIAL + "\nCPU: " + java.util.Arrays.toString(Build.SUPPORTED_ABIS) +
            "\nScreen: " + metrics.widthPixels + " × " + metrics.heightPixels + " px\nDensity: " + metrics.densityDpi +
            " dpi\nMemory: " + memory.totalMem / (1024 * 1024) + " MB");
    }
}
