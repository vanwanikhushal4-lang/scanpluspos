package com.example.hardware;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import java.util.Locale;
public class Wifiaddress extends DiagnosticActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        action("Refresh", R.id.test_action, v -> refresh());
        refresh();
    }
    private void refresh() {
        WifiManager manager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (manager == null) { status.setText("Wi-Fi not available"); return; }
        WifiInfo info = manager.getConnectionInfo();
        if (!manager.isWifiEnabled() || info == null) { status.setText("Wi-Fi is turned off"); detail.setText("Enable Wi-Fi in Settings to test a connection."); return; }
        int ip = info.getIpAddress();
        String address = String.format(Locale.US, "%d.%d.%d.%d", ip & 255, (ip >>> 8) & 255, (ip >>> 16) & 255, (ip >>> 24) & 255);
        String mac = info.getMacAddress();
        if (mac == null || "02:00:00:00:00:00".equals(mac)) mac = "Restricted by Android";
        status.setText(ip == 0 ? "Wi-Fi enabled; no address assigned" : "Connected to Wi-Fi");
        detail.setText("Network: " + info.getSSID() + "\nIP address: " + address + "\nLink speed: " + info.getLinkSpeed() + " Mbps\nSignal: " + info.getRssi() + " dBm\nMAC address: " + mac);
    }
}
