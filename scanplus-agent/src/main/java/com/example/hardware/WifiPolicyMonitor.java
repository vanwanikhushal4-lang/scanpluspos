package com.example.hardware;

import android.content.*;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Local, headless enforcement continues even when the management backend is offline. */
final class WifiPolicyMonitor implements AutoCloseable {
    private final Context context;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean queued=new AtomicBoolean();
    private boolean registered;
    private final BroadcastReceiver receiver=new BroadcastReceiver() {
        @Override public void onReceive(Context ignored,Intent intent) { kick(); }
    };
    WifiPolicyMonitor(Context context) { this.context=context.getApplicationContext(); }
    void start() {
        IntentFilter filter=new IntentFilter();
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        try {
            if(Build.VERSION.SDK_INT>=33)context.registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED);
            else context.registerReceiver(receiver,filter);
            registered=true;WifiGuard.monitoring(true);
            worker.scheduleWithFixedDelay(this::maintain,0,1,TimeUnit.SECONDS);
        } catch(RuntimeException error) { close();throw error; }
    }
    private void kick() {
        if(worker.isShutdown()||!queued.compareAndSet(false,true))return;
        try { worker.execute(()->{queued.set(false);maintain();}); }
        catch(RejectedExecutionException ignored) { queued.set(false); }
    }
    private void maintain() {
        try { new WifiGuard(context).maintain(); }
        catch(Exception error) { Log.w("SCANPLUS","Local Wi-Fi guard needs attention: "+error.getClass().getSimpleName()); }
    }
    @Override public void close() {
        WifiGuard.monitoring(false);worker.shutdownNow();
        if(registered) { context.unregisterReceiver(receiver);registered=false; }
    }
}
