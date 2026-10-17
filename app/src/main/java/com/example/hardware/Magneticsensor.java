package com.example.hardware;
import android.hardware.Sensor;
public class Magneticsensor extends SensorTestActivity {
    @Override protected int sensorType() { return Sensor.TYPE_MAGNETIC_FIELD; }
    @Override protected String units() { return "µT"; }
}
