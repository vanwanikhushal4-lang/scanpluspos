package com.example.hardware;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import java.util.Locale;

/** A missing sensor is an explicit device capability, never a blank test. */
public abstract class SensorTestActivity extends DiagnosticActivity implements SensorEventListener {
    protected SensorManager manager;
    protected Sensor sensor;
    private volatile int samples;
    protected abstract int sensorType();
    protected abstract String units();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        manager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensor = manager == null ? null : manager.getDefaultSensor(sensorType());
        if (sensor == null) {
            status.setText("Not available on this device");
            detail.setText("Android does not report this sensor. You can continue with the other hardware tests.");
        } else {
            status.setText("Waiting for sensor readings");
            detail.setText(sensor.getName() + "\n\nMove or rotate the terminal gently to see live values.");
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (sensor != null && !manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)) {
            status.setText("Sensor could not be started");
        }
    }

    @Override protected void onPause() {
        if (manager != null) manager.unregisterListener(this);
        super.onPause();
    }

    @Override public void onSensorChanged(SensorEvent event) {
        samples++;
        StringBuilder text = new StringBuilder(sensor.getName()).append("\n\n");
        String[] axes = event.values.length >= 3 ? new String[]{"X", "Y", "Z"} : new String[]{"Value"};
        for (int i = 0; i < Math.min(event.values.length, axes.length); i++) {
            text.append(String.format(Locale.US, "%s: %.3f %s\n", axes[i], event.values[i], units()));
        }
        status.setText("Live readings");
        detail.setText(text.toString());
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    public int sampleCount() { return samples; }
    public boolean sensorAvailable() { return sensor != null; }
}
