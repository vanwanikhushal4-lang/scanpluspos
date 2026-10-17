package com.example.hardware;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.provider.Settings;
import java.util.Locale;
public class Gpsloc extends DiagnosticActivity implements Gpslocate.Callback {
    private Gpslocate gps;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        gps = new Gpslocate(this, this);
        status.setText("Ready to check location");
        detail.setText("A GPS fix may take time indoors. Move near a window or outdoors for satellite reception.\n\nNo coordinates are sent to a server.");
        action("Start location test", R.id.test_action, v -> startTest());
        action("Location settings", R.id.test_secondary_action, v -> {
            try { startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)); }
            catch (RuntimeException error) { status.setText("Location settings are restricted"); }
        });
    }
    private boolean allowed() { return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED; }
    private void startTest() {
        if (!allowed()) { requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 2); return; }
        gps.stop(); gps.start();
    }
    @Override protected void onResume() { super.onResume(); if (allowed()) gps.start(); }
    @Override protected void onPause() { gps.stop(); super.onPause(); }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 2 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startTest();
        else status.setText("Location permission is needed for this test");
    }
    @Override public void state(String message) { status.setText(message); }
    @Override public void fix(Location location, boolean cached) {
        status.setText(cached ? "Last known location" : "Location fix received");
        detail.setText(String.format(Locale.US, "Latitude: %.6f\nLongitude: %.6f\nAccuracy: %.1f m\nProvider: %s\n\n%s",
            location.getLatitude(), location.getLongitude(), location.getAccuracy(), location.getProvider(),
            cached ? "Waiting for a fresh reading." : "Live location updates are active."));
    }
}
