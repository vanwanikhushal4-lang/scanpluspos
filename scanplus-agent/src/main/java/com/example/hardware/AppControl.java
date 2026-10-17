package com.example.hardware;

import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import org.json.*;
import java.io.*;
import java.util.*;

/** Typed administrator app management. Never evaluates a backend command or URL. */
final class AppControl {
    private final Context context; private final PackageManager packages; private final SecurityPolicy policy;
    AppControl(Context context){this.context=context;packages=context.getPackageManager();policy=new SecurityPolicy(context);}
    private boolean critical(String name){
        if(name.equals(context.getPackageName())||name.equals("android")||name.equals("com.android.systemui")||name.equals("com.android.settings")||name.endsWith(".packageinstaller"))return true;
        try{if(packages.getApplicationInfo(name,PackageManager.MATCH_UNINSTALLED_PACKAGES).uid<10000)return true;}catch(PackageManager.NameNotFoundException ignored){}
        List<ComponentName> admins=policy.manager.getActiveAdmins();if(admins!=null)for(ComponentName admin:admins)if(admin.getPackageName().equals(name))return true;
        Intent home=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        for(ResolveInfo item:packages.queryIntentActivities(home,0))if(item.activityInfo.packageName.equals(name))return true;
        return false;
    }
    private PackageInfo info(String name)throws Exception{return packages.getPackageInfo(name,PackageManager.GET_PERMISSIONS|PackageManager.MATCH_UNINSTALLED_PACKAGES|PackageManager.MATCH_DISABLED_COMPONENTS);}
    private static boolean system(PackageInfo info){return (info.applicationInfo.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0;}
    private boolean installed(PackageInfo info){return (info.applicationInfo.flags&ApplicationInfo.FLAG_INSTALLED)!=0;}
    private void mutable(String name)throws Exception{if(!name.matches("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")||critical(name))throw new SecurityException("Management, launcher and core apps are protected.");PackageInfo app=info(name);if(!installed(app)||system(app))throw new SecurityException("Only installed user apps may be changed.");}
    private JSONObject describe(PackageInfo app)throws Exception{
        String name=app.packageName;JSONArray permissions=new JSONArray();
        if(app.requestedPermissions!=null)for(String permission:app.requestedPermissions){
            try{PermissionInfo entry=packages.getPermissionInfo(permission,0);if((entry.protectionLevel&PermissionInfo.PROTECTION_MASK_BASE)!=PermissionInfo.PROTECTION_DANGEROUS||!permission.startsWith("android.permission."))continue;
                int grant=policy.owner()?policy.manager.getPermissionGrantState(policy.admin,name,permission):0;boolean operationBlocked=false;String enforcement="ANDROID";
                if(context.getSharedPreferences("scanplus_app_permissions",Context.MODE_PRIVATE).getBoolean(name+"|"+permission,false)){
                    try{operationBlocked=RootBridge.request("APP_PERMISSION:STATUS:"+name+":"+permission).getBoolean("blocked");grant=operationBlocked?DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED:DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT;enforcement=operationBlocked?"APP_OPERATION":"NOT_ENFORCED";}catch(Exception error){grant=DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT;enforcement="UNVERIFIED";}
                }
                permissions.put(new JSONObject().put("name",permission).put("granted",packages.checkPermission(permission,name)==PackageManager.PERMISSION_GRANTED).put("policy",grant).put("operationBlocked",operationBlocked).put("enforcement",enforcement));
            }catch(PackageManager.NameNotFoundException ignored){}
            if(permissions.length()>=128)break;
        }
        boolean hidden=false,suspended=false,blocked=false;if(policy.owner()){hidden=policy.manager.isApplicationHidden(policy.admin,name);suspended=policy.manager.isPackageSuspended(policy.admin,name);blocked=policy.manager.isUninstallBlocked(policy.admin,name);}
        String label=String.valueOf(app.applicationInfo.loadLabel(packages));if(label.length()>160)label=label.substring(0,160);if(label.isEmpty())label=name;
        return new JSONObject().put("packageName",name).put("label",label).put("versionName",app.versionName==null?"":app.versionName.length()>100?app.versionName.substring(0,100):app.versionName)
            .put("versionCode",Build.VERSION.SDK_INT>=28?app.getLongVersionCode():app.versionCode).put("systemApp",system(app)).put("critical",critical(name))
            .put("enabled",app.applicationInfo.enabled).put("hidden",hidden).put("suspended",suspended).put("uninstallBlocked",blocked)
            .put("launchable",packages.getLaunchIntentForPackage(name)!=null).put("stopped",(app.applicationInfo.flags&ApplicationInfo.FLAG_STOPPED)!=0)
            .put("firstInstallTime",Math.max(0,app.firstInstallTime)).put("lastUpdateTime",Math.max(0,app.lastUpdateTime)).put("permissions",permissions);
    }
    JSONObject inventory()throws Exception{
        JSONArray apps=new JSONArray();List<PackageInfo> list=packages.getInstalledPackages(PackageManager.GET_PERMISSIONS|PackageManager.MATCH_UNINSTALLED_PACKAGES|PackageManager.MATCH_DISABLED_COMPONENTS);
        Collections.sort(list,(a,b)->a.packageName.compareTo(b.packageName));
        for(PackageInfo app:list){if(!installed(app)||app.packageName.indexOf('.')<0&& !app.packageName.equals("android"))continue;if(apps.length()>=1000)throw new IllegalStateException("App inventory exceeds supported size.");apps.put(describe(app));}
        boolean forceStop=false;try{forceStop=RootBridge.request("STATUS").optInt("appControlVersion")>=1;}catch(Exception ignored){}
        return new JSONObject().put("apps",apps).put("canForceStop",forceStop);
    }
    void report(BackendClient backend)throws Exception{backend.post("/api/device/apps/inventory",inventory(),true);}
    private void permission(String name,String permission,int state)throws Exception{
        PackageInfo app=info(name);if(app.requestedPermissions==null||!Arrays.asList(app.requestedPermissions).contains(permission)||!permission.matches("android\\.permission\\.[A-Z0-9_]+"))throw new SecurityException("Permission is not requested by this app.");
        PermissionInfo entry=packages.getPermissionInfo(permission,0);if((entry.protectionLevel&PermissionInfo.PROTECTION_MASK_BASE)!=PermissionInfo.PROTECTION_DANGEROUS)throw new SecurityException("Only runtime permissions can be managed.");
        android.content.SharedPreferences overrides=context.getSharedPreferences("scanplus_app_permissions",Context.MODE_PRIVATE);String key=name+"|"+permission;boolean previous=overrides.getBoolean(key,false);
        if(previous&&state==DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED){RootBridge.request("APP_PERMISSION:DENY:"+name+":"+permission);return;}
        if(previous)RootBridge.request("APP_PERMISSION:DEFAULT:"+name+":"+permission);
        boolean applied=false;
        try{
            policy.manager.setPermissionGrantState(policy.admin,name,permission,state);
            for(int i=0;i<5;i++){
                boolean grant=packages.checkPermission(permission,name)==PackageManager.PERMISSION_GRANTED;
                if(policy.manager.getPermissionGrantState(policy.admin,name,permission)==state&&(state==DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT||grant==(state==DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED))){applied=true;break;}
                SystemClock.sleep(150);
            }
        }catch(SecurityException failure){if(state!=DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED){if(previous)RootBridge.request("APP_PERMISSION:DENY:"+name+":"+permission);throw failure;}}
        if(applied){if(!overrides.edit().remove(key).commit())throw new IllegalStateException("Permission status could not be saved.");return;}
        if(state==DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED){
            // Some PAX builds retain install-time grants even for SDK 23+ apps.
            // Deny the mapped operation; never claim the Android grant was revoked.
            policy.manager.setPermissionGrantState(policy.admin,name,permission,DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT);
            if(!overrides.edit().putBoolean(key,true).commit())throw new IllegalStateException("Permission recovery state could not be saved.");
            RootBridge.request("APP_PERMISSION:DENY:"+name+":"+permission);return;
        }
        if(previous)RootBridge.request("APP_PERMISSION:DENY:"+name+":"+permission);
        throw new IllegalStateException("Permission policy was not applied.");
    }
    private void maintenance(){if(policy.active()||policy.restricted(UserManager.DISALLOW_INSTALL_APPS))throw new SecurityException("Enter maintenance mode before installing or removing apps.");}
    private static final class LocalApprovalRequired extends IllegalStateException {
        LocalApprovalRequired(){super("Firmware requires local approval; the operation was not completed.");}
    }
    private String installer(PackageInstaller installer,String name,File apk)throws Exception{
        String token=UUID.randomUUID().toString();android.content.SharedPreferences results=context.getSharedPreferences("scanplus_installer",Context.MODE_PRIVATE);
        if(!results.edit().putBoolean(token+".pending",true).commit())throw new IOException("Cannot save installer state.");
        Intent callback=new Intent(context,AgentInstallReceiver.class).setData(Uri.parse("scanplus-install:"+token)).putExtra("scanplusToken",token);
        PendingIntent pending=PendingIntent.getBroadcast(context,token.hashCode(),callback,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_ONE_SHOT|(Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0));
        int sessionId=-1;
        try{
            if(apk==null)installer.uninstall(name,pending.getIntentSender());
            else{PackageInstaller.SessionParams params=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);params.setAppPackageName(name);params.setSize(apk.length());sessionId=installer.createSession(params);try(PackageInstaller.Session session=installer.openSession(sessionId)){try(InputStream input=new FileInputStream(apk);OutputStream out=session.openWrite("base.apk",0,apk.length())){byte[] buffer=new byte[65536];int n;while((n=input.read(buffer))!=-1)out.write(buffer,0,n);session.fsync(out);}session.commit(pending.getIntentSender());}}
            long deadline=SystemClock.elapsedRealtime()+120000;
            while(SystemClock.elapsedRealtime()<deadline){String result=results.getString(token,null);if(result!=null){JSONObject response=new JSONObject(result);int status=response.getInt("status");if(status==PackageInstaller.STATUS_PENDING_USER_ACTION)throw new LocalApprovalRequired();if(status!=PackageInstaller.STATUS_SUCCESS)throw new IllegalStateException("Installer rejected request ("+status+"): "+response.optString("message"));return name;}SystemClock.sleep(300);}
            throw new IllegalStateException("Installer did not return a result; refresh inventory before retrying.");
        }catch(Exception error){if(sessionId>=0)try{installer.abandonSession(sessionId);}catch(Exception ignored){}throw error;}
        finally{pending.cancel();results.edit().remove(token).remove(token+".pending").commit();}
    }
    JSONObject execute(JSONObject command,BackendClient backend)throws Exception{
        if(!policy.owner())throw new SecurityException("Device-owner enrollment required.");
        String action=command.getString("action"),name=command.optString("packageName","");String detail;
        if(action.equals("REFRESH")){report(backend);return new JSONObject().put("success",true).put("detail","App inventory refreshed");}
        if(action.equals("INSTALL")){
            maintenance();String artifact=command.getString("artifactId");UUID.fromString(artifact);File apk=new File(context.getCacheDir(),artifact+".apk");
            try{backend.downloadApk(artifact,apk,command.getString("sha256"),command.getLong("sizeBytes"));PackageInfo archive=packages.getPackageArchiveInfo(apk.getAbsolutePath(),0);if(archive==null||archive.applicationInfo==null)throw new IllegalArgumentException("Invalid base APK.");name=archive.packageName;if(critical(name))throw new SecurityException("Management and core apps cannot be replaced through app control.");try{PackageInfo current=info(name);if(installed(current))mutable(name);}catch(PackageManager.NameNotFoundException ignored){}if(archive.applicationInfo.minSdkVersion>Build.VERSION.SDK_INT)throw new IllegalStateException("APK requires a newer Android version.");installer(packages.getPackageInstaller(),name,apk);}finally{if(apk.exists()&&!apk.delete())apk.deleteOnExit();}
            detail="Installed "+name+"; version "+info(name).versionName;
        }else{
            if(action.equals("LAUNCH")){if(critical(name))throw new SecurityException("This core app is protected.");Intent launch=packages.getLaunchIntentForPackage(name);if(launch==null)throw new IllegalStateException("App has no available launcher activity.");if(policy.manager.isApplicationHidden(policy.admin,name)||policy.manager.isPackageSuspended(policy.admin,name))throw new IllegalStateException("Show and resume this app before opening it.");context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));detail="Opened "+name;}
            else{mutable(name);
                switch(action){
                    case "FORCE_STOP":RootBridge.request("APP_FORCE_STOP:"+name);if((info(name).applicationInfo.flags&ApplicationInfo.FLAG_STOPPED)==0)throw new IllegalStateException("App stop was not observed.");break;
                    case "SUSPEND":case "RESUME":boolean suspend=action.equals("SUSPEND");if(policy.manager.setPackagesSuspended(policy.admin,new String[]{name},suspend).length!=0||policy.manager.isPackageSuspended(policy.admin,name)!=suspend)throw new IllegalStateException("Suspension state was not applied.");break;
                    case "HIDE":case "SHOW":boolean hidden=action.equals("HIDE");if(!policy.manager.setApplicationHidden(policy.admin,name,hidden)||policy.manager.isApplicationHidden(policy.admin,name)!=hidden)throw new IllegalStateException("Visibility state was not applied.");break;
                    case "BLOCK_UNINSTALL":case "ALLOW_UNINSTALL":boolean blocked=action.equals("BLOCK_UNINSTALL");policy.manager.setUninstallBlocked(policy.admin,name,blocked);if(policy.manager.isUninstallBlocked(policy.admin,name)!=blocked)throw new IllegalStateException("Uninstall policy was not applied.");break;
                    case "GRANT_PERMISSION":permission(name,command.getString("permission"),DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);break;
                    case "DENY_PERMISSION":permission(name,command.getString("permission"),DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED);break;
                    case "RESET_PERMISSION":permission(name,command.getString("permission"),DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT);break;
                    case "UNINSTALL":
                        maintenance();if(policy.manager.isUninstallBlocked(policy.admin,name))throw new IllegalStateException("Allow uninstall before removing this app.");
                        try{installer(packages.getPackageInstaller(),name,null);}
                        catch(LocalApprovalRequired required){
                            // Explicitly provisioned administrator helper, fixed user-app removal only.
                            // Installation continues to use Android/PAX verification without a fallback.
                            mutable(name);maintenance();
                            if(policy.manager.isUninstallBlocked(policy.admin,name))throw new IllegalStateException("Allow uninstall before removing this app.");
                            if(RootBridge.request("STATUS").optInt("appControlVersion")<3)throw required;
                            RootBridge.request("APP_UNINSTALL:"+name);
                        }
                        try{if(installed(info(name)))throw new IllegalStateException("Package remains installed.");}catch(PackageManager.NameNotFoundException ignored){}break;
                    default:throw new SecurityException("Unsupported app operation.");
                }
                detail=action.replace('_',' ')+" completed for "+name;
            }
        }
        JSONObject ack=new JSONObject().put("success",true).put("detail",detail);if(!action.equals("UNINSTALL"))ack.put("observedApp",describe(info(name)));return ack;
    }
}
