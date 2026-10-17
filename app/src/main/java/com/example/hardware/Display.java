package com.example.hardware;
import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;
public class Display extends Activity {
    private final int[] colors = {Color.RED, Color.GREEN, Color.BLUE, Color.WHITE, Color.BLACK, Color.GRAY, Color.CYAN, Color.MAGENTA, Color.YELLOW};
    private final String[] names = {"Red", "Green", "Blue", "White", "Black", "Gray", "Cyan", "Magenta", "Yellow"};
    private TextView surface;
    private int index;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (getActionBar() != null) getActionBar().hide();
        surface = new TextView(this); surface.setId(R.id.display_surface); surface.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL); surface.setTextSize(20); surface.setPadding(12,12,12,30);
        surface.setOnClickListener(v -> { index = (index + 1) % colors.length; update(); });
        setContentView(surface); update();
    }
    private void update() {
        surface.setBackgroundColor(colors[index]); surface.setTextColor(index == 4 || index == 2 ? Color.WHITE : Color.BLACK);
        surface.setText(names[index] + "\nTap for next color · Back to finish");
        surface.setContentDescription(names[index] + " display test");
    }
}
