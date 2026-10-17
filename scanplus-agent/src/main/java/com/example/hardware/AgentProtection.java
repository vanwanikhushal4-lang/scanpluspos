package com.example.hardware;

import android.content.Context;
import android.os.SystemClock;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Allowlisted management operations. Never interprets a server-supplied shell command. */
final class AgentProtection {
    private final Context context;
    AgentProtection(Context context){this.context=context;}
    static String property(String name){
        try{
            java.lang.Process process=new ProcessBuilder("/system/bin/getprop",name).start();
            try(ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] b=new byte[128];int n;
                while((n=process.getInputStream().read(b))!=-1){if(out.size()+n>4096)break;out.write(b,0,n);}
                return new String(out.toByteArray(),StandardCharsets.UTF_8).trim();
            }
        }catch(Exception error){return "unknown";}
    }
    JSONObject status()throws Exception {
        SecurityPolicy policy=new SecurityPolicy(context);boolean bridge=false,sealed=false;
        try{JSONObject root=RootBridge.request("STATUS");bridge=true;sealed="none".equals(root.optString("usb_config"))&&"none".equals(root.optString("persistent_usb_config"))&&"0".equals(root.optString("usb_enabled"))&&"stopped".equals(root.optString("adb_service"))&&"-1".equals(root.optString("tcp_port"));}catch(Exception ignored){}
        boolean restrictions=policy.owner();
        if(restrictions)for(String key:SecurityPolicy.RESTRICTIONS)restrictions &=policy.restricted(key);
        boolean uninstallBlocked=policy.owner()&&policy.manager.isUninstallBlocked(policy.admin,context.getPackageName());
        boolean verifier=android.provider.Settings.Global.getInt(context.getContentResolver(),"package_verifier_enable",0)==1&&"yes".equals(property("ro.build.paxdroid"));
        boolean adbOff=android.provider.Settings.Global.getInt(context.getContentResolver(),android.provider.Settings.Global.ADB_ENABLED,-1)==0;
        boolean enabled=policy.active()&&restrictions&&sealed&&uninstallBlocked&&verifier&&adbOff;
        String detail=!policy.owner()?"Device-owner enrollment required":policy.prefs.getBoolean("usb_restore_pending",false)?"USB restoration needs attention":policy.active()?(enabled?"Protection active; managed policies and USB seal observed":"Protection requested; inspect helper and policy status"):"Maintenance; protection policies are not requested";
        return new JSONObject().put("enabled",enabled).put("ownerActive",policy.owner()).put("usbSealed",sealed).put("bridgeAvailable",bridge).put("detail",detail);
    }
    void apply(boolean enabled)throws Exception {
        synchronized(RootBridge.class){
            SecurityPolicy policy=new SecurityPolicy(context);
            if(!policy.owner())throw new SecurityException("Device-owner enrollment required.");
            if(enabled){
                if(policy.prefs.getBoolean("usb_restore_pending",false))throw new IllegalStateException("USB restoration is pending.");
                if(!policy.active()){RootBridge.request("PREPARE");policy.apply();}
                RootBridge.request("SEAL");
                for(int i=0;i<20;i++){if(status().getBoolean("enabled"))return;SystemClock.sleep(200);}
                throw new IllegalStateException("Protection requested but the complete state was not observed.");
            }
            if(!policy.prefs.edit().putBoolean("usb_restore_pending",true).commit())throw new IllegalStateException("Recovery state could not be saved.");
            policy.maintenance(true);
            try{RootBridge.request("RESTORE");}catch(RootBridge.Unavailable unavailable){/* Android's device-owner API requests recovery; verify below. */}
            for(int i=0;i<20&&"none".equals(property("sys.usb.config"));i++)SystemClock.sleep(200);
            if("none".equals(property("sys.usb.config"))||"unknown".equals(property("sys.usb.config")))throw new IllegalStateException("USB data restoration needs administrator attention.");
            if(!policy.prefs.edit().putBoolean("usb_restore_pending",false).commit())throw new IllegalStateException("Recovery status could not be saved.");
        }
    }
    void maintain(){
        synchronized(RootBridge.class){
            SecurityPolicy policy=new SecurityPolicy(context);
            if(policy.active()&&!policy.prefs.getBoolean("usb_restore_pending",false))try{RootBridge.request("SEAL");}catch(Exception ignored){}
        }
    }
}
