package com.example.hardware;
import android.hardware.Sensor;
public class ProximitySensor extends SensorTestActivity {
    @Override protected int sensorType() { return Sensor.TYPE_PROXIMITY; }
    @Override protected String units() { return "cm"; }
}
