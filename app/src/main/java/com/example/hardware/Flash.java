package com.example.hardware;
import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.widget.Button;
public class Flash extends DiagnosticActivity {
    private CameraManager manager;
    private String cameraId;
    private boolean on;
    private Button toggle;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        toggle = action("Turn flashlight on", R.id.test_action, v -> toggle());
        detail.setText("Check the camera light when toggling this test. It turns off when you leave this screen.");
        try {
            if (manager != null) for (String id : manager.getCameraIdList()) {
                if (Boolean.TRUE.equals(manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE))) { cameraId = id; break; }
            }
            status.setText(cameraId == null ? "Not available on this device" : "Flashlight ready");
            toggle.setEnabled(cameraId != null);
        } catch (CameraAccessException | SecurityException error) {
            status.setText("Camera service is unavailable"); toggle.setEnabled(false);
        }
    }
    private void toggle() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 1); return;
        }
        try {
            manager.setTorchMode(cameraId, !on); on = !on;
            status.setText(on ? "Flashlight on" : "Flashlight off");
            toggle.setText(on ? "Turn flashlight off" : "Turn flashlight on");
        } catch (CameraAccessException | SecurityException | IllegalArgumentException error) {
            status.setText("Flashlight could not be started");
            detail.setText("Another app may be using the camera. Close it and try again.");
        }
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 1 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) toggle();
        else status.setText("Camera permission is needed for the flashlight");
    }
    @Override protected void onPause() {
        if (on) try { manager.setTorchMode(cameraId, false); } catch (CameraAccessException | SecurityException | IllegalArgumentException ignored) { }
        on = false;
        if (cameraId != null) { status.setText("Flashlight off"); toggle.setText("Turn flashlight on"); }
        super.onPause();
    }
}
