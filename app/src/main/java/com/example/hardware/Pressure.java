package com.example.hardware;
import android.hardware.Sensor;
public class Pressure extends SensorTestActivity {
    @Override protected int sensorType() { return Sensor.TYPE_PRESSURE; }
    @Override protected String units() { return "hPa"; }
}
