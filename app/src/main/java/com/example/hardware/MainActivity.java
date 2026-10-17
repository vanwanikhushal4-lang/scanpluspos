package com.example.hardware;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Switch;
import android.widget.Toast;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    static final String[] LABELS = {"Vibration test", "System information", "SIM card", "Proximity sensor",
        "Flashlight", "Touchscreen", "Display", "Light sensor", "Pressure sensor", "Buttons",
        "Speaker test", "Wi-Fi", "Bluetooth", "Gravity sensor", "Magnetic sensor", "Headphones",
        "Gyroscope", "GPS location", "Battery", "Accelerometer"};
    static final Class<?>[] TESTS = {Vibration.class, Systeminfo.class, Tele.class, ProximitySensor.class,
        Flash.class, TouchSensor.class, Display.class, Lightsensor.class, Pressure.class, Buttontesting.class,
        Mictesting.class, Wifiaddress.class, Blueadd.class, Gravitysensor.class, Magneticsensor.class,
        Headphone.class, Gyroscope.class, Gpsloc.class, Batteryindicator.class, Accelarometer.class};
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    // PIN hashing must never wait for USB status or system-service queries.
    private final ExecutorService credentials=Executors.newSingleThreadExecutor(r->new Thread(r,"velox-pin"));
    private final Handler ui=new Handler(Looper.getMainLooper());
    private FrameLayout screen;
    private LinearLayout protectionTab,hardwareTab;
    private boolean hardware;
    private boolean busy;
    private ProtectionStatus latest;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setTitle(R.string.app_name);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(245,248,249));
        screen=new FrameLayout(this); root.addView(screen,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout navigation=new LinearLayout(this); navigation.setElevation(dp(8)); navigation.setBackgroundColor(Color.WHITE);
        protectionTab=tab("Protection",R.id.nav_protection,R.drawable.ic_protection,()->select(false));
        hardwareTab=tab("Hardware",R.id.nav_hardware,R.drawable.ic_hardware,()->select(true));
        navigation.addView(protectionTab,new LinearLayout.LayoutParams(0,dp(68),1));
        navigation.addView(hardwareTab,new LinearLayout.LayoutParams(0,dp(68),1));
        root.addView(navigation); setContentView(root);
        select(state!=null&&state.getBoolean("hardware"));
    }
    private int dp(int value) { return Math.round(value*getResources().getDisplayMetrics().density); }
    private TextView text(String value,int size,int color) {
        TextView view=new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); return view;
    }
    private LinearLayout tab(String label,int id,int icon,Runnable action) {
        LinearLayout tab=new LinearLayout(this); tab.setId(id); tab.setOrientation(LinearLayout.VERTICAL); tab.setGravity(Gravity.CENTER);
        tab.setContentDescription(label); tab.setClickable(true); tab.setFocusable(true); tab.setFilterTouchesWhenObscured(true);
        ImageView image=new ImageView(this); image.setImageResource(icon); image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        tab.addView(image,new LinearLayout.LayoutParams(dp(24),dp(24)));
        TextView caption=text(label,13,Color.rgb(0,96,100)); caption.setGravity(Gravity.CENTER); caption.setPadding(0,dp(4),0,0); tab.addView(caption);
        tab.setOnClickListener(v->action.run()); return tab;
    }
    void select(boolean showHardware) {
        hardware=showHardware; screen.removeAllViews();
        protectionTab.setSelected(!hardware); hardwareTab.setSelected(hardware);
        protectionTab.setBackgroundColor(!hardware?Color.rgb(220,240,239):Color.WHITE);
        hardwareTab.setBackgroundColor(hardware?Color.rgb(220,240,239):Color.WHITE);
        if (hardware) showHardware(); else { renderProtection(); refresh(); }
    }
    private void showHardware() {
        ListView list = new ListView(this);
        list.setId(R.id.listView1);
        list.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, LABELS));
        list.setOnItemClickListener((parent, view, position, id) -> startActivity(new Intent(this, TESTS[position])));
        list.setBackgroundColor(Color.WHITE); screen.addView(list);
    }
    private Button button(LinearLayout parent,String label,int id,Runnable action) {
        Button button=new Button(this); button.setText(label); button.setId(id); button.setAllCaps(false); button.setFilterTouchesWhenObscured(true);
        button.setEnabled(!busy); button.setOnClickListener(v->action.run()); parent.addView(button,new LinearLayout.LayoutParams(-1,-2)); return button;
    }
    private void renderProtection() {
        if (hardware) return; screen.removeAllViews();
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(18),dp(16),dp(18),dp(20));
        TextView title=text("Protection",28,Color.rgb(0,77,80)); title.setTypeface(null,Typeface.BOLD); content.addView(title);
        TextView summary=text(latest==null?"Checking this terminal…":latest.summary,21,Color.rgb(38,50,56));
        summary.setId(R.id.protection_summary); summary.setPadding(0,dp(12),0,dp(10)); content.addView(summary);
        SecurityPolicy policy=new SecurityPolicy(this); AdminPin pin=new AdminPin(this);
        String setup=!policy.owner()?"Administrator enrollment required":!pin.configured()?"Set an administrator PIN":"Administrator PIN configured";
        TextView enrollment=text(setup,15,Color.rgb(85,70,30)); enrollment.setPadding(0,0,0,dp(8)); content.addView(enrollment);
        button(content,pin.configured()?"Change administrator PIN":"Set administrator PIN",R.id.protection_pin,()->{
            if (pin.configured()) authorize(()->pinDialog(true,this::refresh)); else pinDialog(true,this::refresh);
        });
        Switch protection=new Switch(this); protection.setId(R.id.protection_toggle);
        protection.setText("Protection"); protection.setTextSize(20); protection.setTextColor(Color.rgb(0,77,80));
        protection.setPadding(dp(4),dp(16),dp(4),dp(16)); protection.setFilterTouchesWhenObscured(true);
        protection.setChecked(policy.active()); protection.setEnabled(!busy);
        protection.setOnCheckedChangeListener((view,checked)->{
            boolean current=new SecurityPolicy(this).active();
            if (checked==current) return;
            view.setChecked(current);
            if (!busy) authorize(checked?this::confirmEnable:this::confirmMaintenance);
        });
        content.addView(protection,new LinearLayout.LayoutParams(-1,-2));
        if (policy.prefs.getBoolean("usb_restore_pending",false))
            button(content,"Retry USB restoration",R.id.protection_maintenance,()->authorize(this::confirmMaintenance));
        button(content,"Refresh security status",R.id.protection_refresh,this::refresh);
        scroll.addView(content); screen.addView(scroll);
    }
    void refresh() {
        worker.execute(()->{
            ProtectionStatus result=ProtectionStatus.read(getApplicationContext());
            ui.post(()->{ if (!isDestroyed()) { latest=result; renderProtection(); } });
        });
    }
    private void notice(String title,String detail) { new AlertDialog.Builder(this).setTitle(title).setMessage(detail).setPositiveButton("OK",null).show(); }
    private void authorize(Runnable action) {
        if (busy) return;
        AdminPin pin=new AdminPin(this);
        if (!pin.configured()) { pinDialog(true,action); return; }
        if (pin.waitMillis()>0) { notice("Wait before trying again","PIN attempts are temporarily locked. Try again in "+((pin.waitMillis()+999)/1000)+" seconds."); return; }
        pinDialog(false,action);
    }
    private char[] value(EditText input) {
        char[] result=new char[input.length()]; TextUtils.getChars(input.getText(),0,input.length(),result,0); return result;
    }
    private void pinDialog(boolean create,Runnable action) {
        LinearLayout fields=new LinearLayout(this); fields.setOrientation(LinearLayout.VERTICAL); fields.setPadding(dp(24),dp(8),dp(24),dp(8));
        TextView hint=text(create?"Choose 8–32 digits and enter the same PIN twice.":"Enter the administrator PIN you previously saved.",16,Color.DKGRAY); hint.setId(R.id.pin_message); fields.addView(hint);
        EditText first=new EditText(this); first.setHint(create?"New PIN (8–32 digits)":"Saved administrator PIN"); first.setId(R.id.pin_entry); first.setSaveEnabled(false);
        first.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD); first.setFilterTouchesWhenObscured(true); fields.addView(first);
        first.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});
        EditText confirmation=new EditText(this); confirmation.setHint("Enter the same new PIN again"); confirmation.setId(R.id.pin_confirmation); confirmation.setSaveEnabled(false); confirmation.setInputType(first.getInputType()); confirmation.setFilterTouchesWhenObscured(true);
        confirmation.setFilters(new InputFilter[]{new InputFilter.LengthFilter(32)});
        if (create) fields.addView(confirmation);
        CheckBox show=new CheckBox(this); show.setId(R.id.pin_show); show.setText(create?"Show both PIN entries":"Show PIN"); show.setFilterTouchesWhenObscured(true);
        show.setOnCheckedChangeListener((button,checked)->{
            first.setTransformationMethod(checked?null:PasswordTransformationMethod.getInstance()); first.setSelection(first.length());
            confirmation.setTransformationMethod(checked?null:PasswordTransformationMethod.getInstance()); confirmation.setSelection(confirmation.length());
        }); fields.addView(show);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(create?"Save a new administrator PIN":"Verify your current PIN")
            .setView(fields).setNegativeButton("Cancel",null).setPositiveButton(create?"Save PIN":"Verify PIN",null).create();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.setOnDismissListener(d->{ first.setText(""); confirmation.setText(""); getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE); });
        dialog.setOnShowListener(d->{
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setFilterTouchesWhenObscured(true);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                first.setError(null); confirmation.setError(null);
                char[] entered=value(first),second=create?value(confirmation):null;
                String invalid=create?PinRules.error(entered):entered.length==0?"Enter your saved administrator PIN.":null;
                if (invalid!=null) {
                    Arrays.fill(entered,'\0'); if (second!=null) Arrays.fill(second,'\0');
                    first.setError(invalid); first.requestFocus(); hint.setText(invalid); return;
                }
                if (create&&!PinRules.matches(entered,second)) {
                    Arrays.fill(entered,'\0'); Arrays.fill(second,'\0');
                    String mismatch="The PINs differ. Check or retype only the confirmation.";
                    confirmation.setError(mismatch); confirmation.requestFocus(); confirmation.selectAll(); hint.setText(mismatch); return;
                }
                if (second!=null) Arrays.fill(second,'\0');
                first.setText(""); confirmation.setText(""); first.setEnabled(false); confirmation.setEnabled(false); show.setEnabled(false);
                hint.setText(create?"Saving your PIN…":"Checking your PIN…");
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(create?"Saving…":"Checking…");
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
                dialog.setCancelable(false);
                credentials.execute(()->{
                    String failure=null;
                    try {
                        AdminPin pin=new AdminPin(this);
                        if (create) pin.create(entered);
                        else if (!pin.verify(entered)) {
                            long remaining=pin.waitMillis();
                            failure=remaining>0?"Too many attempts. Wait "+((remaining+999)/1000)+" seconds before trying again.":"PIN not accepted. Enter the PIN you saved previously.";
                        }
                    }
                    catch (Exception error) { failure=error.getMessage(); }
                    final String problem=failure;
                    ui.post(()->{
                        if (isDestroyed()) return;
                        if (problem!=null) {
                            hint.setText(problem); first.setEnabled(true); confirmation.setEnabled(true); show.setEnabled(true); first.requestFocus();
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setText(create?"Save PIN":"Verify PIN");
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true); dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true); dialog.setCancelable(true);
                        } else {
                            dialog.dismiss(); renderProtection();
                            if (create) Toast.makeText(this,"Administrator PIN saved",Toast.LENGTH_LONG).show();
                            action.run();
                        }
                    });
                });
            });
        });
        dialog.show();
    }
    private void confirmEnable() {
        if (!new SecurityPolicy(this).owner()) { notice("Enrollment required","A device administrator must enroll Velox as the device owner before these controls can be enforced."); return; }
        new AlertDialog.Builder(this).setTitle("Enable terminal protection?")
            .setMessage("USB data and debugging will disconnect. New app installations, including PAXSTORE updates, will be blocked. Existing apps and Wi-Fi keep working. Use your PIN to enter maintenance.")
            .setNegativeButton("Cancel",null).setPositiveButton("Enable protection",(dialog,which)->activate()).show();
    }
    private void activate() {
        busy=true; renderProtection();
        worker.execute(()->{
            String problem=null;
            try {
                if (new SecurityPolicy(this).prefs.getBoolean("usb_restore_pending",false))
                    throw new IllegalStateException("Finish USB restoration before enabling protection again.");
                // Save the usable USB state BEFORE device policy disables debugging.
                RootBridge.request("PREPARE");
                new SecurityPolicy(this).apply();
                RootBridge.request("SEAL");
                startService(new Intent(this,ProtectionService.class));
            } catch (Exception error) { problem=error.getMessage(); }
            final String message=problem;
            ui.post(()->{ busy=false; refresh(); if (message!=null) notice("Protection needs attention",message); });
        });
    }
    private void confirmMaintenance() {
        new AlertDialog.Builder(this).setTitle("Enter maintenance?")
            .setMessage("This restores the previous installation restrictions and requests USB debugging so your administrator can service this terminal. Enable protection again when finished.")
            .setNegativeButton("Cancel",null).setPositiveButton("Enter maintenance",(dialog,which)->{
                busy=true; renderProtection(); worker.execute(()->{
                    String problem=null;
                    try { synchronized (RootBridge.class) {
                        SecurityPolicy policy=new SecurityPolicy(this);
                        if (!policy.prefs.edit().putBoolean("usb_restore_pending",true).commit())
                            throw new IllegalStateException("USB recovery intent could not be saved.");
                        stopService(new Intent(this,ProtectionService.class)); policy.maintenance(true);
                        try { RootBridge.request("RESTORE"); }
                        catch (RootBridge.Unavailable unavailable) { /* Device-owner ADB setting requests recovery after a reboot. */ }
                        // A connected helper that fails RESTORE is an error, not successful maintenance.
                        if ("none".equals(ProtectionStatus.property("sys.usb.config")))
                            throw new IllegalStateException("Android policies were restored, but USB data is still disabled. USB recovery needs administrator attention.");
                        if (!policy.prefs.edit().putBoolean("usb_restore_pending",false).commit())
                            throw new IllegalStateException("USB recovery completed, but its saved status needs to be updated. Retry restoration.");
                    }
                    } catch (Exception error) { problem=error.getMessage(); }
                    final String message=problem;
                    ui.post(()->{ busy=false; refresh(); if (message!=null) notice("Maintenance needs attention",message); });
                });
            }).show();
    }
    @Override protected void onResume() { super.onResume(); if (screen!=null&&!hardware) refresh(); }
    @Override protected void onSaveInstanceState(Bundle state) { state.putBoolean("hardware",hardware); super.onSaveInstanceState(state); }
    @Override public void onBackPressed() { if (hardware) select(false); else super.onBackPressed(); }
    @Override protected void onDestroy() { worker.shutdownNow(); credentials.shutdownNow(); super.onDestroy(); }
}
