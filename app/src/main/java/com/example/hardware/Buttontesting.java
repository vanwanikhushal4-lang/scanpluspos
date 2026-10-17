package com.example.hardware;
import android.os.Bundle;
import android.view.KeyEvent;
public class Buttontesting extends DiagnosticActivity {
    private int presses;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        status.setText("Press a volume button");
        detail.setText("Volume up and down are captured here without changing volume.\n\nBack returns to the list. Home and Power remain controlled by Android.");
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                presses++;
                status.setText((code == KeyEvent.KEYCODE_VOLUME_UP ? "Volume up" : "Volume down") + " detected");
                detail.setText("Button presses detected: " + presses);
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
}
