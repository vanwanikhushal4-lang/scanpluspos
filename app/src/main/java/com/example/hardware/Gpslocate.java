package com.example.hardware;
import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
/** Lifecycle-bound GPS helper used by the upstream GPS screen. */
final class Gpslocate implements LocationListener {
    interface Callback { void state(String message); void fix(Location location, boolean cached); }
    private final LocationManager manager;
    private final Callback callback;
    private boolean registered;
    Gpslocate(Context context, Callback callback) {
        manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        this.callback = callback;
    }
    @SuppressLint("MissingPermission") // Gpsloc checks the runtime permission before calling start.
    void start() {
        if (manager == null) { callback.state("Location service not available"); return; }
        boolean active = false;
        try {
            for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (manager.getAllProviders().contains(provider) && manager.isProviderEnabled(provider)) {
                    active = true;
                    manager.requestLocationUpdates(provider, 1500, 0, this);
                    registered = true;
                    Location previous = manager.getLastKnownLocation(provider);
                    if (previous != null) callback.fix(previous, true);
                }
            }
            callback.state(active ? "Waiting for a location fix" : "Location is turned off");
        } catch (SecurityException | IllegalArgumentException error) { callback.state("Location access unavailable"); }
    }
    void stop() {
        if (manager != null && registered) {
            try { manager.removeUpdates(this); } catch (SecurityException ignored) { }
            registered = false;
        }
    }
    @Override public void onLocationChanged(Location location) { callback.fix(location, false); }
    @Override public void onProviderDisabled(String provider) { callback.state("Location provider turned off"); }
    @Override public void onProviderEnabled(String provider) { callback.state("Waiting for a location fix"); }
    @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
}
