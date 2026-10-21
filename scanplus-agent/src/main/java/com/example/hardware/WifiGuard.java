package com.example.hardware;

import android.content.*;
import android.net.wifi.*;
import android.os.Build;
import android.os.Parcel;
import android.os.UserManager;
import android.util.Base64;
import org.json.*;
import java.util.*;

/** Independent strict lockdown and monitored open-connection guard; preserves private recovery state. */
final class WifiGuard {
    private static final Object LOCK=new Object();
    private static volatile boolean monitoring;
    private static volatile long lastMonitorSuccess;
    private final SecurityPolicy policy;
    private final WifiManager wifi;
    private final WifiPolicyEngine engine;
    WifiGuard(Context context) {
        Context app=context.getApplicationContext();policy=new SecurityPolicy(app);
        wifi=(WifiManager)app.getSystemService(Context.WIFI_SERVICE);
        SharedPreferences prefs=app.getSharedPreferences("scanplus_wifi_policy",Context.MODE_PRIVATE);
        engine=new WifiPolicyEngine(new Driver(),new WifiPolicyEngine.Storage() {
            @Override public WifiPolicyEngine.State load() throws Exception {
                WifiPolicyEngine.State state=new WifiPolicyEngine.State();
                state.blocked=prefs.getBoolean("blocked",false);state.guard=prefs.getBoolean("openGuard",false);state.snapshot=prefs.getBoolean("snapshot",false);
                state.nativePolicy=prefs.getBoolean("native",false);state.previousLock=prefs.getBoolean("previousLock",false);
                state.previousMinimum=prefs.getInt("previousMinimum",0);
                JSONArray rows=new JSONArray(prefs.getString("recovery","[]"));
                for(int i=0;i<rows.length();i++) {
                    JSONObject row=rows.getJSONObject(i);
                    state.recovery.add(new WifiPolicyEngine.Profile(row.getInt("id"),row.getString("key"),row.getString("data"),true));
                }
                return state;
            }
            @Override public void save(WifiPolicyEngine.State state) throws Exception {
                JSONArray rows=new JSONArray();
                for(WifiPolicyEngine.Profile item:state.recovery) rows.put(new JSONObject().put("id",item.id).put("key",item.key).put("data",item.recovery));
                if(!prefs.edit().putBoolean("blocked",state.blocked).putBoolean("openGuard",state.guard).putBoolean("snapshot",state.snapshot)
                    .putBoolean("native",state.nativePolicy).putBoolean("previousLock",state.previousLock)
                    .putInt("previousMinimum",state.previousMinimum).putString("recovery",rows.toString()).commit())
                    throw new IllegalStateException("Wi-Fi policy recovery state could not be saved");
            }
        });
    }
    void setBlocked(boolean blocked) throws Exception { synchronized(LOCK) { engine.setBlocked(blocked); } }
    void setOpenGuard(boolean enabled) throws Exception {
        synchronized(LOCK) {
            if(enabled&&Build.VERSION.SDK_INT<33&&!monitoring)throw new IllegalStateException("The local Wi-Fi connection monitor is not running");
            engine.setGuard(enabled);if(monitoring)lastMonitorSuccess=android.os.SystemClock.elapsedRealtime();
        }
    }
    static void monitoring(boolean active) { monitoring=active;lastMonitorSuccess=0; }
    void maintain() throws Exception {
        synchronized(LOCK) {
            try { engine.maintain();if(monitoring)lastMonitorSuccess=android.os.SystemClock.elapsedRealtime(); }
            catch(Exception error) { lastMonitorSuccess=0;throw error; }
        }
    }
    JSONObject snapshot() throws Exception {
        synchronized(LOCK) { return new JSONObject().put("policy",status()).put("openGuard",guardStatus()); }
    }
    private JSONObject guardStatus() throws Exception {
        WifiPolicyEngine.State state=engine.state();boolean applied;
        try { applied=!state.guard||engine.applied()&&(state.nativePolicy||monitoring&&lastMonitorSuccess>0&&android.os.SystemClock.elapsedRealtime()-lastMonitorSuccess<5000); }
        catch(Exception error) { applied=false; }
        String mode=!applied?"DEGRADED":!state.guard?"OFF":state.nativePolicy?"NATIVE":"MONITORED";
        String detail=!applied?"Open-network guard needs attention":!state.guard?"Open-network connection guard is off":state.nativePolicy?"Android blocks open Wi-Fi; secured networks remain selectable":"Open-network attempts are disconnected on detection; brief association is possible. Secured networks remain selectable unless another policy locks Wi-Fi settings.";
        return new JSONObject().put("enabled",state.guard).put("applied",applied).put("enforcement",mode).put("detail",detail);
    }
    JSONObject status() throws Exception {
        synchronized(LOCK) {
            WifiPolicyEngine.State state=engine.state();boolean applied=false;boolean locked=false;
            try { applied=engine.applied();locked=policy.restricted(UserManager.DISALLOW_CONFIG_WIFI); } catch(Exception ignored) {}
            String mode=!applied?"DEGRADED":!state.blocked?"OFF":state.nativePolicy?"NATIVE":"LEGACY_LOCKDOWN";
            String detail=!applied?"Wi-Fi policy needs attention; enforcement could not be verified":!state.blocked?"Full Wi-Fi protection is off":state.nativePolicy?"Android is enforcing the minimum Wi-Fi security policy":"Open profiles are removed from the usable list and local Wi-Fi changes are locked";
            return new JSONObject().put("blocked",state.blocked).put("applied",applied).put("enforcement",mode).put("localSettingsLocked",locked).put("detail",detail);
        }
    }
    private List<WifiConfiguration> configurations() {
        if(wifi==null) throw new IllegalStateException("Wi-Fi is unavailable");
        List<WifiConfiguration> configs;
        try { configs=wifi.getConfiguredNetworks(); }
        catch(SecurityException denied) { throw new SecurityException("Terminal policy denied access to saved Wi-Fi profiles",denied); }
        if(configs==null) throw new IllegalStateException("Saved Wi-Fi networks are unavailable");
        return configs;
    }
    private static String key(WifiConfiguration config) { return String.valueOf(config.SSID)+"\u0000"+String.valueOf(config.BSSID)+"\u0000"+config.hiddenSSID; }
    private final class Driver implements WifiPolicyEngine.Host {
        @Override public boolean owner() { return policy.owner(); }
        @Override public boolean securedConnection() {
            int id=connectedId();if(id<0) return false;
            for(WifiConfiguration config:configurations()) if(config.networkId==id)
                return Arrays.asList("WEP","WPA","WPA2","WPA3","ENTERPRISE").contains(WifiScanner.configuredSecurity(config));
            return false;
        }
        @Override public boolean nativePolicy() { return Build.VERSION.SDK_INT>=33; }
        @Override public int minimum() { return Build.VERSION.SDK_INT>=33?policy.manager.getMinimumRequiredWifiSecurityLevel():0; }
        @Override public void minimum(int value) {
            if(Build.VERSION.SDK_INT<33) throw new IllegalStateException("Native Wi-Fi security policy is unavailable");
            policy.manager.setMinimumRequiredWifiSecurityLevel(value);
        }
        @Override public boolean locked() { return policy.restricted(UserManager.DISALLOW_CONFIG_WIFI); }
        @Override public boolean ownLock() { android.os.Bundle restrictions=policy.manager.getUserRestrictions(policy.admin);return restrictions!=null&&restrictions.getBoolean(UserManager.DISALLOW_CONFIG_WIFI); }
        @Override public void lock(boolean value) {
            if(value) policy.manager.addUserRestriction(policy.admin,UserManager.DISALLOW_CONFIG_WIFI);
            else policy.manager.clearUserRestriction(policy.admin,UserManager.DISALLOW_CONFIG_WIFI);
        }
        @Override public List<WifiPolicyEngine.Profile> profiles() {
            List<WifiPolicyEngine.Profile> result=new ArrayList<>();
            for(WifiConfiguration config:configurations()) {
                String security=WifiScanner.configuredSecurity(config);
                if(security.equals("UNKNOWN")) throw new IllegalStateException("A saved Wi-Fi profile has an unrecognized security type; strict blocking cannot be verified");
                boolean open=security.equals("OPEN");String encoded="";
                if(open) {
                    Parcel parcel=Parcel.obtain();
                    try { parcel.writeParcelable(config,0);encoded=Base64.encodeToString(parcel.marshall(),Base64.NO_WRAP); }
                    finally { parcel.recycle(); }
                }
                result.add(new WifiPolicyEngine.Profile(config.networkId,key(config),encoded,open));
            }
            return result;
        }
        private int connectedId() { WifiInfo info=wifi==null?null:wifi.getConnectionInfo();return info==null?-1:info.getNetworkId(); }
        @Override public void remove(WifiPolicyEngine.Profile profile) {
            // Android 7 saveConfiguration can renumber IDs. Resolve a matching open
            // profile again before every mutation so an old ID cannot target a secured one.
            int id=-1;
            for(WifiConfiguration current:configurations()) if(key(current).equals(profile.key)&&WifiScanner.configuredSecurity(current).equals("OPEN")) { id=current.networkId;break; }
            if(id<0) return;
            if(connectedId()==id&&!wifi.disconnect()) throw new IllegalStateException("Open Wi-Fi connection could not be disconnected");
            if(!wifi.removeNetwork(id)) throw new IllegalStateException("An open Wi-Fi profile could not be removed; previous policy will be restored");
            if(Build.VERSION.SDK_INT<26&&!wifi.saveConfiguration()) throw new IllegalStateException("Wi-Fi profile removal could not be persisted");
        }
        @Override public void restore(WifiPolicyEngine.Profile profile) throws Exception {
            Parcel parcel=Parcel.obtain();WifiConfiguration config;
            try {
                byte[] data=Base64.decode(profile.recovery,Base64.NO_WRAP);parcel.unmarshall(data,0,data.length);parcel.setDataPosition(0);
                config=parcel.readParcelable(WifiConfiguration.class.getClassLoader());
            } finally { parcel.recycle(); }
            if(config==null||!key(config).equals(profile.key)||!WifiScanner.configuredSecurity(config).equals("OPEN")) throw new IllegalStateException("Invalid saved Wi-Fi recovery profile");
            boolean enabled=config.status!=WifiConfiguration.Status.DISABLED;
            int id=-1;
            for(WifiConfiguration existing:configurations()) if(key(existing).equals(profile.key)&&WifiScanner.configuredSecurity(existing).equals("OPEN")) { id=existing.networkId;break; }
            if(id<0) { config.networkId=-1;id=wifi.addNetwork(config); }
            if(id<0) throw new IllegalStateException("A saved open Wi-Fi profile could not be restored; retry disabling the policy");
            if(enabled&&!wifi.enableNetwork(id,false)) throw new IllegalStateException("A restored Wi-Fi profile could not be enabled");
            if(!enabled&&!wifi.disableNetwork(id)) throw new IllegalStateException("A restored Wi-Fi profile could not retain its disabled state");
            if(Build.VERSION.SDK_INT<26&&!wifi.saveConfiguration()) throw new IllegalStateException("Restored Wi-Fi profiles could not be persisted");
        }
    }
}
