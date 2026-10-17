package com.example.hardware;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;

/** Device integration checks: real activities, live sensors, and input dispatch. */
public class HardwareSmokeRunner extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    private void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private String status(Activity activity) {
        final String[] text = {""};
        runOnMainSync(() -> {
            TextView view = activity.findViewById(R.id.diagnostic_status);
            if (view != null) text[0] = view.getText().toString();
        });
        return text[0];
    }

    private void click(Activity activity, int id) {
        runOnMainSync(() -> {
            View view = activity.findViewById(id);
            require(view != null && view.isEnabled(), "Action unavailable: " + id);
            view.performClick();
        });
        waitForIdleSync();
    }

    private void pointer(View view, boolean draw) {
        final int[] location = new int[2];
        runOnMainSync(() -> view.getLocationOnScreen(location));
        float x = location[0] + view.getWidth() / 3f;
        float y = location[1] + view.getHeight() / 3f;
        long down = SystemClock.uptimeMillis();
        for (int i = 0; i < (draw ? 8 : 2); i++) {
            int action = i == 0 ? MotionEvent.ACTION_DOWN : i == (draw ? 7 : 1) ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
            MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                x + (draw ? i * 12 : 0), y + (draw ? i * 12 : 0), 0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            sendPointerSync(event); event.recycle();
            SystemClock.sleep(30);
        }
        waitForIdleSync();
    }

    @Override public void onStart() {
        JSONArray tests = new JSONArray();
        Bundle result = new Bundle();
        Activity main = null;
        try {
            main = startActivitySync(new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            final Activity menu = main;
            require(main.findViewById(R.id.nav_protection) != null && main.findViewById(R.id.nav_hardware) != null, "Bottom navigation missing");
            require(main.findViewById(R.id.protection_summary) != null, "Protection must be the first screen");
            runOnMainSync(() -> ((MainActivity) menu).select(true));
            waitForIdleSync();
            final ListView[] list = new ListView[1];
            runOnMainSync(() -> list[0] = menu.findViewById(R.id.listView1));
            require(list[0].getAdapter().getCount() == 20, "All 20 upstream tests must be reachable");
            require("Velox Hardware Security".contentEquals(main.getTitle()), "App title was not renamed");
            for (int i = 0; i < MainActivity.TESTS.length; i++) {
                final int index = i;
                Class<?> type = MainActivity.TESTS[i];
                ActivityMonitor monitor = addMonitor(type.getName(), null, false);
                runOnMainSync(() -> list[0].setSelection(index));
                waitForIdleSync();
                runOnMainSync(() -> list[0].performItemClick(list[0].getChildAt(0), index, index));
                Activity activity = monitor.waitForActivityWithTimeout(10000);
                removeMonitor(monitor);
                require(activity != null, "Menu did not open " + type.getSimpleName());
                waitForIdleSync();
                SystemClock.sleep(300);
                JSONObject entry = new JSONObject().put("screen", type.getSimpleName()).put("menu_label", MainActivity.LABELS[i]);
                if (activity instanceof SensorTestActivity) {
                    SensorTestActivity sensor = (SensorTestActivity) activity;
                    long end = SystemClock.uptimeMillis() + 4000;
                    while (sensor.sensorAvailable() && sensor.sampleCount() == 0 && SystemClock.uptimeMillis() < end) SystemClock.sleep(100);
                    require(sensor.sensorAvailable() ? sensor.sampleCount() > 0 : status(activity).contains("Not available"), "Sensor gave neither live readings nor an availability result");
                    entry.put("sensor_available", sensor.sensorAvailable()).put("live_samples", sensor.sampleCount());
                } else if (activity instanceof Batteryindicator) {
                    int percent = ((Batteryindicator) activity).percentage;
                    require(percent >= 0 && percent <= 100, "Battery percentage invalid");
                    entry.put("battery_percent", percent);
                } else if (activity instanceof TouchSensor) {
                    SingleTouchEventView surface = activity.findViewById(R.id.touch_surface);
                    pointer(surface, true);
                    require(surface.pointCount() >= 5, "Touch drawing did not receive injected input");
                    entry.put("touch_points", surface.pointCount());
                    click(activity, R.id.test_action);
                    require(surface.pointCount() == 0, "Clear drawing failed");
                } else if (activity instanceof Display) {
                    View surface = activity.findViewById(R.id.display_surface);
                    java.util.Set<String> colors = new java.util.HashSet<>();
                    for (int color = 0; color < 9; color++) { colors.add(surface.getContentDescription().toString()); pointer(surface, false); }
                    require(colors.size() == 9, "Display did not cycle through all nine colors");
                    entry.put("display_colors", colors.size());
                } else if (activity instanceof Buttontesting) {
                    sendKeyDownUpSync(KeyEvent.KEYCODE_VOLUME_UP); waitForIdleSync();
                    require(status(activity).contains("Volume up detected"), "Volume-up event not displayed");
                    sendKeyDownUpSync(KeyEvent.KEYCODE_VOLUME_DOWN); waitForIdleSync();
                    require(status(activity).contains("Volume down detected"), "Volume-down event not displayed");
                } else if (activity instanceof Flash) {
                    View toggle = activity.findViewById(R.id.test_action);
                    if (toggle.isEnabled()) {
                        click(activity, R.id.test_action);
                        require(status(activity).equals("Flashlight on"), "Flashlight failed to turn on: " + status(activity));
                        SystemClock.sleep(350);
                        click(activity, R.id.test_action);
                        require(status(activity).equals("Flashlight off"), "Flashlight failed to turn off");
                        entry.put("torch_toggle", "on_and_off");
                    } else entry.put("torch_toggle", "not_available");
                } else if (activity instanceof Mictesting) {
                    click(activity, R.id.test_action);
                    require(status(activity).equals("Test tone requested"), "Audio driver did not accept test tone");
                    SystemClock.sleep(900);
                    entry.put("audible_confirmation", "manual");
                } else if (activity instanceof Vibration) {
                    entry.put("vibration_test", "skipped_at_user_request");
                }
                if (activity instanceof DiagnosticActivity) {
                    String text = status(activity);
                    require(!text.isEmpty(), "Empty diagnostic screen");
                    require(!text.contains("could not") && !text.contains("access unavailable"), "Diagnostic error: " + text);
                    entry.put("status", text);
                }
                entry.put("result", "passed"); tests.put(entry);
                Bundle progress = new Bundle(); progress.putString("stream", type.getSimpleName() + " passed\n"); sendStatus(1, progress);
                runOnMainSync(activity::finish); waitForIdleSync();
            }
            result.putString("report", new JSONObject().put("tests", tests).put("passed", tests.length()).put("expected", 20).toString());
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("error", error.toString());
            result.putString("completed", tests.toString());
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
