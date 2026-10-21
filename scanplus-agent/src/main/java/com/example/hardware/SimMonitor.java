package com.example.hardware;

import android.content.*;
import android.os.Build;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;
import org.json.*;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Headless, offline-capable SIM monitoring. Only pseudonymous identities leave this process. */
final class SimMonitor implements AutoCloseable {
    private final Context context;
    private final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
    private final AtomicBoolean queued=new AtomicBoolean();
    private SimChangeTracker tracker;private SimReader reader;private boolean registered;
    private volatile SimReader.Observation latest=new SimReader.Observation("INITIALIZING","Waiting for a stable SIM observation",Collections.emptyList());
    private final BroadcastReceiver receiver=new BroadcastReceiver() {
        @Override public void onReceive(Context ignored,Intent intent) { kick(); }
    };
    SimMonitor(Context context) {
        this.context=context.getApplicationContext();
        SharedPreferences prefs=this.context.getSharedPreferences("scanplus_sim_monitor",Context.MODE_PRIVATE);
        try {
            if(!prefs.contains("secret")) {
                if(prefs.contains("state"))throw new IllegalStateException("Existing SIM recovery state has no identity key");
                byte[] secret=new byte[32];new SecureRandom().nextBytes(secret);
                if(!prefs.edit().putString("secret",Base64.encodeToString(secret,Base64.NO_WRAP)).putString("state","{}").commit())throw new IllegalStateException("SIM state could not be initialized");
            }
            byte[] secret=Base64.decode(prefs.getString("secret",""),Base64.NO_WRAP);if(secret.length!=32)throw new IllegalStateException("Invalid SIM identity key");
            tracker=new SimChangeTracker(new SimChangeTracker.Store() {
                @Override public SimChangeTracker.State load()throws Exception {
                    String encoded=prefs.getString("state","{}");if(encoded.length()>2*1024*1024)throw new IllegalStateException("SIM recovery state exceeds its bound");
                    JSONObject data=new JSONObject(encoded);SimChangeTracker.State state=new SimChangeTracker.State();
                    state.baselineAt=data.isNull("baselineAt")?null:data.optString("baselineAt",null);state.gapRecorded=data.optBoolean("gapRecorded",false);
                    JSONArray slots=data.optJSONArray("baseline");
                    if(slots!=null)for(int i=0;i<slots.length();i++) {
                        JSONObject row=slots.getJSONObject(i);SimChangeTracker.Reading reading=reading(row.getJSONObject("reading"));
                        state.baseline.put(reading.slot,new SimChangeTracker.Known(reading,nullable(row,"lastFingerprint"),nullable(row,"lastSuffix"),row.optString("lastCarrier","")));
                    }
                    JSONArray pending=data.optJSONArray("pending");
                    if(pending!=null)for(int i=0;i<pending.length();i++)state.pending.add(event(pending.getJSONObject(i)));
                    if(state.baseline.size()>4||state.pending.size()>SimChangeTracker.MAX_EVENTS)throw new IllegalStateException("Invalid bounded SIM state");
                    return state;
                }
                @Override public void save(SimChangeTracker.State state)throws Exception {
                    JSONArray baseline=new JSONArray(),pending=new JSONArray();
                    for(SimChangeTracker.Known known:state.baseline.values())baseline.put(new JSONObject().put("reading",json(known.reading))
                        .put("lastFingerprint",nullValue(known.lastFingerprint)).put("lastSuffix",nullValue(known.lastSuffix)).put("lastCarrier",known.lastCarrier));
                    for(SimChangeTracker.Event item:state.pending)pending.put(json(item));
                    JSONObject data=new JSONObject().put("baselineAt",nullValue(state.baselineAt)).put("gapRecorded",state.gapRecorded).put("baseline",baseline).put("pending",pending);
                    if(!prefs.edit().putString("state",data.toString()).commit())throw new IllegalStateException("SIM changes could not be saved durably");
                }
            });
            tracker.state(); // Refuse corrupt state instead of silently replacing the baseline/outbox.
            reader=new SimReader(this.context,secret);
        } catch(Exception error) { tracker=null;latest=new SimReader.Observation("UNAVAILABLE","SIM monitoring recovery state needs administrator attention",Collections.emptyList()); }
    }
    void start() {
        IntentFilter filter=new IntentFilter("android.intent.action.SIM_STATE_CHANGED");
        filter.addAction("android.telephony.action.SIM_CARD_STATE_CHANGED");
        filter.addAction("android.telephony.action.SIM_APPLICATION_STATE_CHANGED");
        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        try {
            // Telephony can broadcast from the radio UID. We ignore all payloads and re-read actual state.
            if(Build.VERSION.SDK_INT>=33)context.registerReceiver(receiver,filter,Context.RECEIVER_EXPORTED);
            else context.registerReceiver(receiver,filter);
            registered=true;
        } catch(RuntimeException error) { Log.w("SCANPLUS","SIM broadcasts unavailable; local polling remains active"); }
        worker.scheduleWithFixedDelay(this::sample,0,5,TimeUnit.SECONDS);
    }
    private void kick() {
        if(worker.isShutdown()||!queued.compareAndSet(false,true))return;
        try { worker.execute(()->{queued.set(false);sample();}); }
        catch(RejectedExecutionException ignored) { queued.set(false); }
    }
    private void sample() {
        if(tracker==null||reader==null)return;
        try {
            SimReader.Observation reading=reader.read();
            tracker.observe(reading.slots,reading.state.equals("READY"),SystemClock.elapsedRealtime(),utc());
            latest=reading;
        } catch(Exception error) { latest=new SimReader.Observation("UNAVAILABLE","SIM alert storage needs attention; the previous baseline is retained",Collections.emptyList()); }
    }
    private static String utc() {
        SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.US);format.setTimeZone(TimeZone.getTimeZone("UTC"));return format.format(new Date());
    }
    private static Object nullValue(String text) { return text==null?JSONObject.NULL:text; }
    private static String nullable(JSONObject row,String name) { return row.isNull(name)?null:row.optString(name,null); }
    private static JSONObject json(SimChangeTracker.Reading row)throws Exception {
        return new JSONObject().put("slot",row.slot).put("state",row.state).put("fingerprint",nullValue(row.fingerprint)).put("suffix",nullValue(row.suffix)).put("carrier",row.carrier);
    }
    private static SimChangeTracker.Reading reading(JSONObject row)throws Exception {
        return new SimChangeTracker.Reading(row.getInt("slot"),row.getString("state"),nullable(row,"fingerprint"),nullable(row,"suffix"),row.optString("carrier",""));
    }
    private static JSONObject json(SimChangeTracker.Event row)throws Exception {
        return new JSONObject().put("id",row.id).put("type",row.type).put("slot",row.slot).put("observedAt",row.observedAt)
            .put("previousFingerprint",nullValue(row.previousFingerprint)).put("currentFingerprint",nullValue(row.currentFingerprint))
            .put("previousSuffix",nullValue(row.previousSuffix)).put("currentSuffix",nullValue(row.currentSuffix))
            .put("previousCarrier",row.previousCarrier).put("currentCarrier",row.currentCarrier);
    }
    private static SimChangeTracker.Event event(JSONObject row)throws Exception {
        return new SimChangeTracker.Event(row.getString("id"),row.getString("type"),row.getInt("slot"),row.getString("observedAt"),nullable(row,"previousFingerprint"),nullable(row,"currentFingerprint"),nullable(row,"previousSuffix"),nullable(row,"currentSuffix"),row.optString("previousCarrier",""),row.optString("currentCarrier",""));
    }
    int queued() {
        try { return tracker==null?0:tracker.state().pending.size(); }
        catch(Exception error) { return 0; }
    }
    JSONObject report()throws Exception {
        SimChangeTracker.State state;SimReader.Observation current=latest;
        try { state=tracker==null?new SimChangeTracker.State():tracker.state(); }
        catch(Exception error) { state=new SimChangeTracker.State();current=new SimReader.Observation("UNAVAILABLE","SIM alert recovery state needs attention; existing data was retained",Collections.emptyList()); }
        JSONArray slots=new JSONArray(),events=new JSONArray();
        for(SimChangeTracker.Reading row:current.slots)slots.put(json(row).put("baselineKnown",state.baseline.containsKey(row.slot)));
        for(int i=0;i<Math.min(8,state.pending.size());i++)events.put(json(state.pending.get(i)));
        JSONObject snapshot=new JSONObject().put("readState",current.state).put("detail",current.detail)
            .put("baselineEstablished",!state.baseline.isEmpty()).put("baselineAt",nullValue(state.baselineAt))
            .put("outboxDepth",state.pending.size()).put("slots",slots);
        return new JSONObject().put("snapshot",snapshot).put("events",events);
    }
    void acknowledge(JSONObject sent,JSONObject response)throws Exception {
        if(tracker==null)return;
        Set<String> sentIds=new HashSet<>(),accepted=new HashSet<>();JSONArray items=sent.getJSONArray("events"),ids=response.getJSONArray("acceptedEventIds");
        for(int i=0;i<items.length();i++)sentIds.add(items.getJSONObject(i).getString("id"));
        if(ids.length()>8)throw new IllegalStateException("Invalid SIM receipt size");
        for(int i=0;i<ids.length();i++) { String id=ids.getString(i);if(!sentIds.contains(id))throw new IllegalStateException("SIM receipt contains an unsent event");accepted.add(id); }
        if(!accepted.isEmpty())tracker.acknowledge(accepted);
    }
    @Override public void close() { worker.shutdownNow();if(registered) { context.unregisterReceiver(receiver);registered=false; } }
}
