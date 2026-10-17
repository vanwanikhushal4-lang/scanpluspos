package com.example.hardware;
import android.hardware.Sensor;
public class Gravitysensor extends SensorTestActivity {
    @Override protected int sensorType() { return Sensor.TYPE_GRAVITY; }
    @Override protected String units() { return "m/s²"; }
}
