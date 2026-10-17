package com.example.hardware;
import android.bluetooth.BluetoothAdapter;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
public class Blueadd extends DiagnosticActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        action("Refresh", R.id.test_action, v -> {
            if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, 3);
            } else refresh();
        });
        refresh();
    }
    private void refresh() {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            status.setText("Bluetooth permission is needed");
            detail.setText("Tap Refresh to allow access to Bluetooth information.");
            return;
        }
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) { status.setText("Bluetooth not available"); detail.setText("Android does not report a Bluetooth adapter."); return; }
        status.setText(adapter.isEnabled() ? "Bluetooth enabled" : "Bluetooth is turned off");
        String address = adapter.getAddress();
        if (address == null || "02:00:00:00:00:00".equals(address)) address = "Restricted by Android";
        detail.setText("Name: " + (adapter.getName() == null ? "Not reported" : adapter.getName()) + "\nMAC address: " + address + "\n\nUse Bluetooth settings to enable or pair a device.");
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        refresh();
    }
}
