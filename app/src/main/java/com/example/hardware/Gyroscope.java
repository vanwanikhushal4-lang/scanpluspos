package com.example.hardware;
import android.hardware.Sensor;
public class Gyroscope extends SensorTestActivity {
    @Override protected int sensorType() { return Sensor.TYPE_GYROSCOPE; }
    @Override protected String units() { return "rad/s"; }
}
