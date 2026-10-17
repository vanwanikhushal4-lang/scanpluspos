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
    private final java.util.concurrent.atomic.AtomicBoolean appBusy=new java.util.concurrent.atomic.AtomicBoolean();
    private long lastInventory;
    private static final String VERSION="3.1.2-scanplus";
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
    @Override public void onCreate(){super.onCreate();protection=new AgentProtection(this);notification();worker.execute(this::cycle);}
    private void cycle(){
        int delay=10;
        try{
            AgentConfiguration config=new AgentConfiguration(this);BackendClient backend=new BackendClient(config);
            if(!config.enrolled()){
                JSONObject request=new JSONObject().put("code",config.data.getString("enrollmentCode")).put("serial",config.data.getString("serial")).put("model",Build.MODEL).put("version",VERSION);
                config.saveIdentity(backend.post("/api/enroll",request,false));
            }
            protection.maintain();
            JSONObject reply=backend.post("/api/device/check-in",new JSONObject().put("version",VERSION).put("status",protection.status()),true);
            JSONObject command=reply.optJSONObject("command");
            if(command!=null)execute(config,backend,reply,command);
            JSONObject appCommand=reply.optJSONObject("appCommand");
            if(appCommand!=null)executeApp(appCommand);
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
    @Override public void onDestroy(){worker.shutdownNow();appWorker.shutdownNow();super.onDestroy();}
    @Override public android.os.IBinder onBind(Intent intent){return null;}
}
