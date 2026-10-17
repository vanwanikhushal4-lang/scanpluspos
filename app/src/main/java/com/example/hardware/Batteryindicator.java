package com.example.hardware;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Bundle;
import java.util.Locale;
public class Batteryindicator extends DiagnosticActivity {
    private boolean registered;
    int percentage = -1;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            percentage = level < 0 || scale <= 0 ? -1 : Math.round(level * 100f / scale);
            status.setText(percentage < 0 ? "Battery reading unavailable" : "Battery: " + percentage + "%");
            int charging = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            String state = charging == BatteryManager.BATTERY_STATUS_CHARGING ? "Charging" :
                charging == BatteryManager.BATTERY_STATUS_FULL ? "Full" : "Not charging";
            detail.setText(String.format(Locale.US, "%s\nTemperature: %.1f °C\nVoltage: %.3f V",
                state, intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f,
                intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) / 1000f));
        }
    };
    @Override protected void onCreate(Bundle state) { super.onCreate(state); status.setText("Reading battery"); }
    @Override protected void onStart() { super.onStart(); registerReceiver(receiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED)); registered = true; }
    @Override protected void onStop() { if (registered) { unregisterReceiver(receiver); registered = false; } super.onStop(); }
}
