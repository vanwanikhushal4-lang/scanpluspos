package com.example.hardware;
import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
public class TouchSensor extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        TextView help = new TextView(this); help.setText("Draw across the screen. Check that each finger leaves a continuous line."); help.setPadding(16, 16, 16, 16); content.addView(help);
        SingleTouchEventView surface = new SingleTouchEventView(this, null); surface.setId(R.id.touch_surface);
        content.addView(surface, new LinearLayout.LayoutParams(-1, 0, 1));
        Button clear = new Button(this); clear.setText("Clear drawing"); clear.setId(R.id.test_action); clear.setOnClickListener(v -> surface.clear()); content.addView(clear);
        setContentView(content);
    }
}
