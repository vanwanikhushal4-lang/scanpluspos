package com.example.hardware;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Bundle;
public class Headphone extends DiagnosticActivity {
    private boolean registered;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { refresh(); }
    };
    @Override protected void onCreate(Bundle state) { super.onCreate(state); action("Refresh", R.id.test_action, v -> refresh()); }
    private void refresh() {
        AudioManager manager = (AudioManager) getSystemService(AUDIO_SERVICE);
        boolean wired = false;
        StringBuilder outputs = new StringBuilder("Audio outputs:\n");
        for (AudioDeviceInfo device : manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (device.getType() == AudioDeviceInfo.TYPE_WIRED_HEADSET || device.getType() == AudioDeviceInfo.TYPE_WIRED_HEADPHONES) wired = true;
            outputs.append("• ").append(device.getProductName()).append(" (type ").append(device.getType()).append(")\n");
        }
        status.setText(wired ? "Wired headset connected" : "No wired headset connected");
        detail.setText(outputs.toString() + "\nConnect or disconnect a headset to check detection.");
    }
    @Override protected void onResume() { super.onResume(); registerReceiver(receiver, new IntentFilter(Intent.ACTION_HEADSET_PLUG)); registered = true; refresh(); }
    @Override protected void onPause() { if (registered) { unregisterReceiver(receiver); registered = false; } super.onPause(); }
}
