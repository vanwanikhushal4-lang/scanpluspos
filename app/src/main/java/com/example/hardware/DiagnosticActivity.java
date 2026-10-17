package com.example.hardware;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Shared, scrollable presentation for the original hardware-test activities. */
public abstract class DiagnosticActivity extends Activity {
    protected LinearLayout content;
    protected TextView status;
    protected TextView detail;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        content.setPadding(padding, padding, padding, padding);
        content.setBackgroundColor(Color.WHITE);
        status = new TextView(this);
        status.setId(R.id.diagnostic_status);
        status.setTextSize(24);
        status.setTextColor(Color.rgb(0, 96, 100));
        content.addView(status, new LinearLayout.LayoutParams(-1, -2));
        detail = new TextView(this);
        detail.setId(R.id.diagnostic_detail);
        detail.setTextSize(18);
        detail.setTextColor(Color.rgb(38, 50, 56));
        detail.setPadding(0, dp(20), 0, dp(20));
        detail.setTextIsSelectable(true);
        content.addView(detail, new LinearLayout.LayoutParams(-1, -2));
        scroll.addView(content);
        setContentView(scroll);
    }

    protected Button action(String label, int id, android.view.View.OnClickListener listener) {
        Button button = new Button(this);
        button.setId(id);
        button.setText(label);
        button.setOnClickListener(listener);
        content.addView(button, new LinearLayout.LayoutParams(-1, -2));
        return button;
    }

    protected int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
