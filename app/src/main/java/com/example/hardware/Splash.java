package com.example.hardware;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.TextView;
public class Splash extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable open = () -> { startActivity(new Intent(this, MainActivity.class)); finish(); };
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView title = new TextView(this);
        title.setGravity(Gravity.CENTER);
        title.setTextSize(26);
        title.setText(getString(R.string.app_name) + "\n\nProtection and hardware diagnostics");
        setContentView(title);
    }
    @Override protected void onResume() { super.onResume(); handler.postDelayed(open, 650); }
    @Override protected void onPause() { handler.removeCallbacks(open); super.onPause(); }
}
