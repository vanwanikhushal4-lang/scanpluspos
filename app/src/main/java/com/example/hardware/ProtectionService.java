package com.example.hardware;
import android.app.Service;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
public final class ProtectionService extends Service {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Runnable verify=new Runnable() { @Override public void run() {
        if (!new SecurityPolicy(ProtectionService.this).active()) { stopSelf(); return; }
        worker.execute(() -> { synchronized (RootBridge.class) {
            if (!new SecurityPolicy(ProtectionService.this).active()) return;
            try { RootBridge.request("SEAL"); } catch (Exception ignored) { }
        }});
        handler.postDelayed(this,15000);
    }};
    @Override public void onCreate() {
        super.onCreate(); NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(new NotificationChannel("protection","Terminal protection",NotificationManager.IMPORTANCE_LOW));
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,"protection"):new Notification.Builder(this);
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,Splash.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        startForeground(12,builder.setSmallIcon(R.drawable.ic_protection).setContentTitle(getString(R.string.app_name))
            .setContentText("Protection requested · tap to review live status").setContentIntent(open).setOngoing(true).build());
        handler.post(verify);
    }
    @Override public int onStartCommand(Intent intent,int flags,int id) { return START_STICKY; }
    @Override public void onDestroy() { handler.removeCallbacks(verify); worker.shutdownNow(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
