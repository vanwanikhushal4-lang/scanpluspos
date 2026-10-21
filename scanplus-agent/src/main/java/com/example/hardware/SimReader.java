package com.example.hardware;

import android.Manifest;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.telephony.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Reads SIM state only: never phone numbers, IMSI, contacts, messages or payment data. */
final class SimReader {
    static final class Observation {
        final String state,detail;final List<SimChangeTracker.Reading> slots;
        Observation(String state,String detail,List<SimChangeTracker.Reading> slots) { this.state=state;this.detail=detail;this.slots=slots; }
    }
    private final Context context;private final byte[] secret;
    SimReader(Context context,byte[] secret) { this.context=context.getApplicationContext();this.secret=secret.clone(); }
    private boolean permission() {
        if(context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)==PackageManager.PERMISSION_GRANTED)return true;
        SecurityPolicy policy=new SecurityPolicy(context);
        if(!policy.owner()||policy.manager.getPermissionGrantState(policy.admin,context.getPackageName(),Manifest.permission.READ_PHONE_STATE)==DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED)return false;
        return policy.manager.setPermissionGrantState(policy.admin,context.getPackageName(),Manifest.permission.READ_PHONE_STATE,DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
            &&context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)==PackageManager.PERMISSION_GRANTED;
    }
    static String normalized(String identifier) {
        if(identifier==null)return null;
        String value=identifier.trim().toUpperCase(Locale.US);
        if(!value.matches("[0-9]{10,32}F?"))return null;
        return value.endsWith("F")?value.substring(0,value.length()-1):value;
    }
    private String fingerprint(String identifier)throws Exception {
        Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret,"HmacSHA256"));
        byte[] hash=mac.doFinal(identifier.getBytes(StandardCharsets.US_ASCII));StringBuilder result=new StringBuilder();
        for(byte value:hash)result.append(String.format(Locale.US,"%02x",value&255));
        return result.toString();
    }
    private static String carrier(CharSequence value) {
        String text=value==null?"":value.toString().replaceAll("\\p{Cntrl}","").trim();
        return text.substring(0,Math.min(text.length(),64));
    }
    @android.annotation.SuppressLint("MissingPermission")
    private String serial(TelephonyManager scoped) {
        // On API 25 READ_PHONE_STATE is sufficient. Newer Android also permits
        // the enrolled device owner; a privileged-only permission cannot be granted to this app.
        if(context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException("Phone-state permission is unavailable");
        if(Build.VERSION.SDK_INT>=29&&!new SecurityPolicy(context).owner())return null;
        return scoped.getSimSerialNumber();
    }
    private static String state(int value) {
        switch(value) {
            case TelephonyManager.SIM_STATE_ABSENT:return "ABSENT";
            case TelephonyManager.SIM_STATE_READY:case 10:return "PRESENT";
            case TelephonyManager.SIM_STATE_PIN_REQUIRED:case TelephonyManager.SIM_STATE_PUK_REQUIRED:case TelephonyManager.SIM_STATE_NETWORK_LOCKED:return "LOCKED";
            case TelephonyManager.SIM_STATE_NOT_READY:return "NOT_READY";
            default:return "UNKNOWN";
        }
    }
    private static String legacyState(String value) {
        switch(value) {
            case "ABSENT":return "ABSENT";
            case "READY":case "LOADED":return "PRESENT";
            case "PIN_REQUIRED":case "PUK_REQUIRED":case "NETWORK_LOCKED":return "LOCKED";
            case "NOT_READY":return "NOT_READY";
            default:return "UNKNOWN";
        }
    }
    Observation read() {
        List<SimChangeTracker.Reading> slots=new ArrayList<>();
        try {
            if(!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY))return new Observation("UNSUPPORTED","This terminal has no telephony hardware",slots);
            if(!permission())return new Observation("PERMISSION_REQUIRED","SIM monitoring needs its managed phone-state permission",slots);
            if(Settings.Global.getInt(context.getContentResolver(),Settings.Global.AIRPLANE_MODE_ON,0)!=0)return new Observation("AIRPLANE_MODE","Airplane mode is on; SIM-change comparisons are paused",slots);
            TelephonyManager phone=(TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
            SubscriptionManager subscriptions=(SubscriptionManager)context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            if(phone==null||subscriptions==null)return new Observation("UNAVAILABLE","Android telephony services are unavailable",slots);
            int count=Math.max(0,Math.min(4,phone.getPhoneCount()));
            if(count==0)return new Observation("UNAVAILABLE","The modem has not reported its SIM slots",slots);
            List<SubscriptionInfo> active=subscriptions.getActiveSubscriptionInfoList();if(active==null)active=Collections.emptyList();
            // API 25 lacks public getSimState(slot). This A920's modem exposes
            // per-slot state through gsm.sim.state; never infer removal from an empty subscription list.
            String[] legacy=Build.VERSION.SDK_INT<26&&count>1?AgentProtection.property("gsm.sim.state").split(",",-1):new String[0];
            for(int slot=0;slot<count;slot++) {
                String status=Build.VERSION.SDK_INT>=26?state(phone.getSimState(slot)):count==1?state(phone.getSimState()):slot<legacy.length?legacyState(legacy[slot].trim()):"UNKNOWN";
                SubscriptionInfo info=null;for(SubscriptionInfo item:active)if(item.getSimSlotIndex()==slot) { info=item;break; }
                String identifier=null,operator="";
                if(status.equals("PRESENT")) {
                    if(info!=null) { identifier=normalized(info.getIccId());operator=carrier(info.getCarrierName()); }
                    if(identifier==null&&(info!=null||count==1)) {
                        TelephonyManager scoped=info==null?phone:phone.createForSubscriptionId(info.getSubscriptionId());
                        identifier=normalized(serial(scoped));
                        if(operator.isEmpty())operator=carrier(scoped.getSimOperatorName());
                    }
                }
                slots.add(new SimChangeTracker.Reading(slot,status,identifier==null?null:fingerprint(identifier),identifier==null?null:identifier.substring(identifier.length()-4),operator));
            }
            return new Observation("READY","SIM slots are monitored; confirmed changes are saved for delivery",slots);
        } catch(SecurityException error) { return new Observation("PERMISSION_REQUIRED","Firmware denied SIM metadata access; no removal or replacement was inferred",Collections.emptyList()); }
        catch(Exception error) { return new Observation("UNAVAILABLE","SIM metadata is temporarily unavailable; the previous baseline is retained",Collections.emptyList()); }
    }
}
