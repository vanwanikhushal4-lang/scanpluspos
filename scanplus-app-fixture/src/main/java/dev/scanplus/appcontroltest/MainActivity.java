package dev.scanplus.appcontroltest;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

/** Disposable, data-free fixture for testing management on the owner's terminal. */
public final class MainActivity extends Activity {
    @Override public void onCreate(Bundle state){super.onCreate(state);TextView text=new TextView(this);text.setText("SCANPLUS\nApp control test\n\nThis temporary app verifies launch, stop, restrictions, permissions and removal.\nIt holds no user or payment data.");text.setPadding(32,60,32,32);text.setTextSize(20);text.setTextColor(0xff164e49);setContentView(text);}
}
