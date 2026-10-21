package com.example.hardware;

import android.app.*;
import android.content.*;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import org.json.JSONObject;
import java.util.concurrent.*;

public final class AgentService extends Service {
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService appWorker=Executors.newSingleThreadExecutor();
    private final ExecutorService wifiWorker=Executors.newSingleThreadExecutor();
    private final ExecutorService simWorker=Executors.newSingleThreadExecutor();
    private final java.util.concurrent.atomic.AtomicBoolean appBusy=new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicBoolean wifiBusy=new java.util.concurrent.atomic.AtomicBoolean();
    private final java.util.concurrent.atomic.AtomicBoolean simBusy=new java.util.concurrent.atomic.AtomicBoolean();
    private long lastInventory;
    private volatile long lastWifiAttempt=-120000;
    private static final String VERSION="3.5.0-scanplus";
    private WifiPolicyMonitor wifiMonitor;
    private SimMonitor simMonitor;
    private volatile long lastSimAttempt=-120000;
    private int failures;
    private AgentProtection protection;
    private String message="Connecting to administrator backend";
    private void notification(){
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel("scanplus-managed","SCANPLUS device management",NotificationManager.IMPORTANCE_LOW));
        Intent settings=new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+getPackageName()));
        PendingIntent details=PendingIntent.getActivity(this,0,settings,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"scanplus-managed"):new Notification.Builder(this);
        startForeground(41,builder.setSmallIcon(android.R.drawable.ic_lock_lock).setContentTitle("SCANPLUS UNIFIED POS SECURITY").setContentText(message).setContentIntent(details).setOngoing(true).build());
    }
    @Override public void onCreate(){super.onCreate();protection=new AgentProtection(this);notification();wifiMonitor=new WifiPolicyMonitor(this);try{wifiMonitor.start();}catch(RuntimeException error){Log.w("SCANPLUS","Wi-Fi monitor could not start");}try{simMonitor=new SimMonitor(this);simMonitor.start();}catch(RuntimeException error){Log.w("SCANPLUS","SIM monitor could not start");}worker.execute(this::cycle);}
    private void cycle(){
        int delay=10;
        try{new WifiGuard(this).maintain();}catch(Exception error){Log.w("SCANPLUS","Wi-Fi policy needs attention: "+error.getClass().getSimpleName());}
        try{
            AgentConfiguration config=new AgentConfiguration(this);BackendClient backend=new BackendClient(config);
            if(!config.enrolled()){
                JSONObject request=new JSONObject().put("code",config.data.getString("enrollmentCode")).put("serial",config.data.getString("serial")).put("model",Build.MODEL).put("version",VERSION);
                config.saveIdentity(backend.post("/api/enroll",request,false));
            }
            protection.maintain();
            JSONObject reply=backend.post("/api/device/check-in",new JSONObject().put("version",VERSION).put("status",protection.status()),true);
            delay=Math.max(10,Math.min(60,reply.optInt("pollSeconds",10)));
            JSONObject command=reply.optJSONObject("command");
            if(command!=null)execute(config,backend,reply,command);
            JSONObject appCommand=reply.optJSONObject("appCommand");
            if(appCommand!=null)executeApp(appCommand);
            JSONObject wifiCommand=reply.optJSONObject("wifiCommand");
            if(wifiCommand!=null||android.os.SystemClock.elapsedRealtime()-lastWifiAttempt>60000)executeWifi(wifiCommand,reply.getString("serverTime"),reply.optBoolean("openWifiGuardSupported",false));
            if(reply.optBoolean("simChangeAlertsSupported",false)&&simMonitor!=null&&(simMonitor.queued()>0||android.os.SystemClock.elapsedRealtime()-lastSimAttempt>60000))executeSim();
            if(!appBusy.get()&&android.os.SystemClock.elapsedRealtime()-lastInventory>60000){new AppControl(this).report(backend);lastInventory=android.os.SystemClock.elapsedRealtime();}
            failures=0;message=protection.status().getString("detail");
        }catch(Exception error){
            failures=Math.min(6,failures+1);delay=Math.min(60,10*failures);
            // Never log provisioning data, credentials, request bodies or backend URLs.
            message="Management needs attention; retrying connection";Log.w("SCANPLUS","Management cycle failed: "+error.getClass().getSimpleName());
        }
        notification();
        if(!worker.isShutdown())worker.schedule(this::cycle,delay,TimeUnit.SECONDS);
    }
    private void executeSim(){
        if(!simBusy.compareAndSet(false,true))return;
        simWorker.execute(()->{
            lastSimAttempt=android.os.SystemClock.elapsedRealtime();
            try{
                BackendClient backend=new BackendClient(new AgentConfiguration(this));JSONObject report=simMonitor.report();
                simMonitor.acknowledge(report,backend.post("/api/device/sim/report",report,true));
            }catch(Exception error){Log.w("SCANPLUS","SIM alert delivery failed: "+error.getClass().getSimpleName());}
            finally{simBusy.set(false);}
        });
    }
    private void executeWifi(JSONObject command,String serverTime,boolean guardProtocol){
        if(!wifiBusy.compareAndSet(false,true))return;
        wifiWorker.execute(()->{
            lastWifiAttempt=android.os.SystemClock.elapsedRealtime();
            try{
                BackendClient backend=new BackendClient(new AgentConfiguration(this));
                if(command==null){
                    JSONObject report=new WifiScanner(this).scan();if(!guardProtocol)report.remove("openGuard");
                    backend.post("/api/device/wifi/report",report,true);
                }else{
                    String id=command.getString("id");java.util.UUID.fromString(id);
                    java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",java.util.Locale.US);
                    format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                    if(format.parse(command.getString("expiresAt").substring(0,19)).getTime()<=format.parse(serverTime.substring(0,19)).getTime())return;
                    SharedPreferences saved=getSharedPreferences("scanplus_wifi_results",MODE_PRIVATE);
                    String prior=saved.getString(id,null);JSONObject ack;
                    if(prior!=null)ack=new JSONObject(prior);
                    else{
                        WifiScanner scanner=new WifiScanner(this);JSONObject snapshot;boolean success;
                        if(command.getString("action").equals("SET_OPEN_POLICY")||command.getString("action").equals("SET_OPEN_GUARD")){
                            String error=null;
                            boolean guard=command.getString("action").equals("SET_OPEN_GUARD");
                            try{if(guard){if(!guardProtocol)throw new SecurityException("Update the backend before using the selective Wi-Fi guard");new WifiGuard(this).setOpenGuard(command.getBoolean("blocked"));}else new WifiGuard(this).setBlocked(command.getBoolean("blocked"));}catch(Exception failure){error=failure.getMessage()==null?failure.getClass().getSimpleName():failure.getMessage();}
                            snapshot=scanner.cached();success=error==null;
                            JSONObject observed=snapshot.getJSONObject(guard?"openGuard":"policy");
                            if(success&&!observed.optBoolean("applied",false))success=false;
                            snapshot.put("detail",error==null?observed.getString("detail"):error.substring(0,Math.min(error.length(),500)));
                        }else if(command.getString("action").equals("SCAN")){
                            snapshot=scanner.scan();success=snapshot.getString("scanState").equals("FRESH");
                        }else throw new SecurityException("Unsupported Wi-Fi operation");
                        ack=new JSONObject().put("success",success).put("snapshot",snapshot);
                        if(!guardProtocol)snapshot.remove("openGuard");
                        SharedPreferences.Editor edit=saved.edit();if(saved.getAll().size()>30)edit.clear();
                        if(!edit.putString(id,ack.toString()).commit())throw new IllegalStateException("Wi-Fi result could not be saved");
                    }
                    if(!guardProtocol)ack.getJSONObject("snapshot").remove("openGuard");
                    backend.post("/api/device/wifi/commands/"+id+"/ack",ack,true);
                }
            }catch(Exception error){Log.w("SCANPLUS","Wi-Fi report delivery failed: "+error.getClass().getSimpleName());}
            finally{wifiBusy.set(false);}
        });
    }
    private void executeApp(JSONObject command){
        if(!appBusy.compareAndSet(false,true))return;
        appWorker.execute(()->{
            try{
                String id=command.getString("id");java.util.UUID.fromString(id);
                BackendClient backend=new BackendClient(new AgentConfiguration(this));
                backend.post("/api/device/app-commands/"+id+"/start",new JSONObject(),true);
                SharedPreferences results=getSharedPreferences("scanplus_app_results",MODE_PRIVATE);
                String saved=results.getString(id,null);JSONObject ack;
                AppControl apps=new AppControl(this);
                if(saved!=null)ack=new JSONObject(saved);
                else{
                    if(id.equals(results.getString("running",null)))ack=new JSONObject().put("success",false).put("detail","Agent restarted during this operation; inventory has been refreshed. Inspect its state before retrying.");
                    else{
                        if(!results.edit().putString("running",id).commit())throw new IllegalStateException("App job state could not be saved.");
                        try{ack=apps.execute(command,backend);}catch(Exception error){String detail=error.getMessage();if(detail==null)detail=error.getClass().getSimpleName();ack=new JSONObject().put("success",false).put("detail",detail.length()>1900?detail.substring(0,1900):detail);}
                    }
                    SharedPreferences.Editor editor=results.edit();if(results.getAll().size()>100)editor.clear();
                    if(!editor.remove("running").putString(id,ack.toString()).commit())throw new IllegalStateException("App result could not be saved.");
                }
                apps.report(backend);
                backend.post("/api/device/app-commands/"+id+"/ack",ack,true);
            }catch(Exception error){Log.w("SCANPLUS","App management delivery failed: "+error.getClass().getSimpleName());}
            finally{appBusy.set(false);}
        });
    }
    private void execute(AgentConfiguration config,BackendClient backend,JSONObject reply,JSONObject command)throws Exception{
        String id=command.getString("id");java.util.UUID.fromString(id);
        SharedPreferences completed=getSharedPreferences("scanplus_completed",MODE_PRIVATE);
        String prior=completed.getString(id,null);JSONObject ack;
        if(prior!=null)ack=new JSONObject(prior);
        else{
            long serverTime;
            // The reply's two ISO timestamps share the server clock; local wall-clock accuracy is irrelevant.
            java.text.SimpleDateFormat format=new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss",java.util.Locale.US);format.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            serverTime=format.parse(reply.getString("serverTime").substring(0,19)).getTime();
            long expiry=format.parse(command.getString("expiresAt").substring(0,19)).getTime();
            boolean success=false;String failure=null;
            if(expiry<=serverTime)failure="Command expired before execution";
            else try{protection.apply(command.getBoolean("enabled"));success=true;}catch(Exception error){failure=error.getMessage();}
            JSONObject state=protection.status();
            if(failure!=null)state.put("detail",failure.length()>1900?failure.substring(0,1900):failure);
            ack=new JSONObject().put("success",success).put("status",state);
            SharedPreferences.Editor saved=completed.edit();
            if(completed.getAll().size()>=32)saved.clear();
            if(!saved.putString(id,ack.toString()).commit())throw new IllegalStateException("Command result could not be saved.");
        }
        backend.post("/api/device/commands/"+id+"/ack",ack,true);
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){return START_STICKY;}
    @Override public void onDestroy(){if(wifiMonitor!=null)wifiMonitor.close();if(simMonitor!=null)simMonitor.close();worker.shutdownNow();appWorker.shutdownNow();wifiWorker.shutdownNow();simWorker.shutdownNow();super.onDestroy();}
    @Override public android.os.IBinder onBind(Intent intent){return null;}
}
