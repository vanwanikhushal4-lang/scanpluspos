package com.example.hardware;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
/** Retains the upstream speaker test without requiring a downloaded speech engine. */
public class Mictesting extends DiagnosticActivity {
    private ToneGenerator tone;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        status.setText("Ready to test the speaker");
        detail.setText("Tap Play test tone and listen for a short sound.\n\nThe test uses the current media volume and output route.");
        action("Play test tone", R.id.test_action, v -> play());
    }
    private void play() {
        try {
            if (tone == null) tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 60);
            boolean started = tone.startTone(ToneGenerator.TONE_DTMF_1, 800);
            status.setText(started ? "Test tone requested" : "Audio output could not start");
            if (started) detail.setText("Listen for the tone and confirm that you heard it.\n\nIf silent, check media volume and whether audio is routed to a headset.");
        } catch (RuntimeException error) {
            status.setText("Audio output is unavailable");
            detail.setText("Close another audio app and try again.");
        }
    }
    @Override protected void onPause() {
        if (tone != null) { tone.stopTone(); tone.release(); tone = null; }
        super.onPause();
    }
}
