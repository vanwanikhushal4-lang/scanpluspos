package com.example.hardware;
import android.os.Bundle;
import android.os.Vibrator;
import android.media.AudioAttributes;
import android.widget.Button;
public class Vibration extends DiagnosticActivity {
    private Vibrator vibrator;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        boolean available = vibrator != null && vibrator.hasVibrator();
        status.setText(available ? "Ready to test" : "Not available on this device");
        detail.setText("Hold the terminal in your hand. Run the test, then choose whether you felt the three pulses.");
        Button button = action("Run vibration test", R.id.test_action, v -> {
            AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
            vibrator.vibrate(new long[]{0, 600, 250, 600, 250, 600}, -1, attributes);
            status.setText("Feel for three pulses");
            detail.setText("The test lasts about two seconds. Choose the result below after holding the terminal.");
            findViewById(R.id.test_confirm).setEnabled(true);
            findViewById(R.id.test_not_felt).setEnabled(true);
        });
        button.setEnabled(available);
        Button felt = action("I felt vibration", R.id.test_confirm, v -> {
            status.setText("Vibration felt");
            detail.setText("You confirmed that the terminal vibrated during this test.");
        });
        Button absent = action("No vibration felt", R.id.test_not_felt, v -> {
            status.setText("Vibration not felt");
            detail.setText("Android reports a vibrator, but you did not feel a pulse. Try again with the terminal in your hand. A persistent failure may indicate a device or driver problem.");
        });
        felt.setEnabled(false);
        absent.setEnabled(false);
    }
    @Override protected void onPause() { if (vibrator != null) vibrator.cancel(); super.onPause(); }
}
