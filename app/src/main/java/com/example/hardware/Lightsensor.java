package com.example.hardware;
import android.hardware.Sensor;
public class Lightsensor extends SensorTestActivity {
    @Override protected int sensorType() { return Sensor.TYPE_LIGHT; }
    @Override protected String units() { return "lux"; }
}
