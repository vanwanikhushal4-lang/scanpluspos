package com.example.hardware;
import android.hardware.Sensor;
public class Accelarometer extends SensorTestActivity {
    @Override protected int sensorType() { return Sensor.TYPE_ACCELEROMETER; }
    @Override protected String units() { return "m/s²"; }
}
