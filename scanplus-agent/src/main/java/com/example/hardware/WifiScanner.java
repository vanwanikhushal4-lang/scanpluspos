package com.example.hardware;

import android.Manifest;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.wifi.*;
import android.os.SystemClock;
import android.provider.Settings;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Reports access points around this terminal, never passwords or GPS coordinates. */
final class WifiScanner {
    private final Context context;
    private final WifiManager wifi;
    WifiScanner(Context context) {
        this.context=context.getApplicationContext();
        wifi=(WifiManager)this.context.getSystemService(Context.WIFI_SERVICE);
    }
    private boolean locationPermission() {
        if(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED) return true;
        SecurityPolicy policy=new SecurityPolicy(context);
        return policy.owner()&&policy.manager.setPermissionGrantState(policy.admin,context.getPackageName(),Manifest.permission.ACCESS_FINE_LOCATION,DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
            &&context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
    }
    private boolean locationOn() {
        return Settings.Secure.getInt(context.getContentResolver(),Settings.Secure.LOCATION_MODE,Settings.Secure.LOCATION_MODE_OFF)!=Settings.Secure.LOCATION_MODE_OFF;
    }
    static String security(String capabilities) {
        return WifiSecurity.classify(capabilities);
    }
    static String configuredSecurity(WifiConfiguration network) {
        BitSet keys=network.allowedKeyManagement;
        if(android.os.Build.VERSION.SDK_INT>=29&&keys.get(WifiConfiguration.KeyMgmt.SAE)) return "WPA3";
        if(android.os.Build.VERSION.SDK_INT>=29&&keys.get(WifiConfiguration.KeyMgmt.OWE)) return "ENHANCED_OPEN";
        if(keys.get(WifiConfiguration.KeyMgmt.WPA_EAP)||keys.get(WifiConfiguration.KeyMgmt.IEEE8021X)) return "ENTERPRISE";
        if(keys.get(WifiConfiguration.KeyMgmt.WPA_PSK)) return "WPA2";
        if(network.wepKeys!=null) for(String key:network.wepKeys) if(key!=null&&!key.isEmpty()) return "WEP";
        return keys.cardinality()==1&&keys.get(WifiConfiguration.KeyMgmt.NONE)?"OPEN":"UNKNOWN";
    }
    private static String ssid(String text) {
        if(text==null||text.equals("<unknown ssid>")) return null;
        if(text.startsWith("\"")&&text.endsWith("\"")&&text.length()>=2) return text.substring(1,text.length()-1);
        return text;
    }
    private static String bssid(String text) {
        return text!=null&&text.matches("(?i)[0-9a-f]{2}(:[0-9a-f]{2}){5}")?text:null;
    }
    private static int rssi(int signal) { return Math.max(-127,Math.min(0,signal)); }
    private JSONObject connection(List<ScanResult> scans) throws Exception {
        WifiInfo current=wifi.getConnectionInfo();
        if(current==null||current.getNetworkId()<0) return null;
        String security="UNKNOWN";
        List<WifiConfiguration> configs;
        try { configs=wifi.getConfiguredNetworks(); }
        catch(SecurityException denied) { configs=Collections.emptyList(); }
        if(configs!=null) for(WifiConfiguration config:configs) if(config.networkId==current.getNetworkId()) { security=configuredSecurity(config); break; }
        // Scan capabilities identify the actual negotiated network family more precisely.
        for(ScanResult scan:scans) if(scan.BSSID!=null&&scan.BSSID.equalsIgnoreCase(current.getBSSID())) { security=security(scan.capabilities); break; }
        int ip=current.getIpAddress();
        String address=ip==0?null:(ip&255)+"."+((ip>>8)&255)+"."+((ip>>16)&255)+"."+((ip>>24)&255);
        return new JSONObject().put("ssid",ssid(current.getSSID())).put("bssid",bssid(current.getBSSID()))
            .put("security",security).put("rssi",rssi(current.getRssi())).put("frequencyMhz",Math.max(0,Math.min(7500,current.getFrequency()))).put("ipAddress",address);
    }
    private List<ScanResult> results() {
        if(context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) throw new SecurityException("Wi-Fi scan permission is not granted");
        List<ScanResult> scans=wifi.getScanResults();
        return scans==null?new ArrayList<>():new ArrayList<>(scans);
    }
    JSONObject scan() throws Exception {
        if(wifi==null) return snapshot("UNAVAILABLE","This terminal has no available Wi-Fi manager",Collections.emptyList());
        if(!wifi.isWifiEnabled()) return snapshot("WIFI_OFF","Wi-Fi is turned off on the terminal",Collections.emptyList());
        try {
            if(!locationPermission()) return snapshot("PERMISSION_REQUIRED","Wi-Fi scanning needs the agent's location permission",Collections.emptyList());
            if(!locationOn()) return snapshot("LOCATION_OFF","Enable Location on the terminal to scan nearby Wi-Fi networks",Collections.emptyList());
            CountDownLatch done=new CountDownLatch(1); AtomicBoolean updated=new AtomicBoolean();
            BroadcastReceiver receiver=new BroadcastReceiver() {
                @Override public void onReceive(Context ignored,Intent intent) {
                    if(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
                        updated.set(intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED,false)); done.countDown();
                    }
                }
            };
            context.registerReceiver(receiver,new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
            try {
                if(!wifi.startScan()) return snapshot("FAILED","Android declined the scan; displayed networks are cached",results());
                if(!done.await(15,TimeUnit.SECONDS)) return snapshot("FAILED","The scan timed out; displayed networks are cached",results());
                return updated.get()?snapshot("FRESH","Nearby Wi-Fi scan completed on the terminal",results()):snapshot("FAILED","Android did not return a successful scan; displayed networks are cached",results());
            } finally { context.unregisterReceiver(receiver); }
        } catch(SecurityException error) {
            return snapshot("PERMISSION_REQUIRED","Terminal policy denied Wi-Fi scan access",Collections.emptyList());
        }
    }
    JSONObject cached() throws Exception {
        if(wifi==null) return snapshot("UNAVAILABLE","This terminal has no available Wi-Fi manager",Collections.emptyList());
        if(!wifi.isWifiEnabled()) return snapshot("WIFI_OFF","Wi-Fi is turned off on the terminal",Collections.emptyList());
        try { return snapshot("CACHED","Latest available Wi-Fi results; use Scan now to refresh",results()); }
        catch(SecurityException error) { return snapshot("PERMISSION_REQUIRED","Terminal policy denied Wi-Fi scan access",Collections.emptyList()); }
    }
    private JSONObject snapshot(String state,String detail,List<ScanResult> scans) throws Exception {
        long elapsed=SystemClock.elapsedRealtime();
        scans.sort((a,b)->Integer.compare(b.level,a.level));
        JSONArray networks=new JSONArray();Set<String> seen=new HashSet<>();long newestAge=86400;
        WifiInfo current=null;
        if(wifi!=null) try { current=wifi.getConnectionInfo(); } catch(SecurityException ignored) {}
        for(ScanResult scan:scans) {
            String mac=bssid(scan.BSSID);
            if(mac==null||!seen.add(mac)||scan.timestamp<=0) continue;
            long age=Math.max(0,(elapsed-scan.timestamp/1000)/1000);
            if(age>86400) continue;
            newestAge=Math.min(newestAge,age);
            networks.put(new JSONObject().put("ssid",scan.SSID==null?"":scan.SSID).put("bssid",mac)
                .put("security",security(scan.capabilities)).put("rssi",rssi(scan.level))
                .put("frequencyMhz",Math.max(0,Math.min(7500,scan.frequency))).put("ageSeconds",age)
                .put("connected",current!=null&&scan.BSSID.equalsIgnoreCase(current.getBSSID())));
            if(networks.length()>=40) break;
        }
        JSONObject connected=null;
        if(wifi!=null) try { connected=connection(scans); } catch(SecurityException ignored) {}
        JSONObject policies=new WifiGuard(context).snapshot();
        return new JSONObject().put("scanState",state).put("detail",detail)
            .put("scanAgeSeconds",state.equals("FRESH")&&scans.isEmpty()?0:networks.length()==0?JSONObject.NULL:newestAge)
            .put("connection",connected==null?JSONObject.NULL:connected).put("policy",policies.getJSONObject("policy")).put("openGuard",policies.getJSONObject("openGuard")).put("networks",networks);
    }
}
