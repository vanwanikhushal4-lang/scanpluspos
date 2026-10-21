package com.example.hardware;

import java.util.*;

/** Stable per-slot observations and a durable, bounded outbox; no raw SIM identifiers. */
final class SimChangeTracker {
    static final long STABLE_MS=15000,BOOT_GRACE_MS=30000;
    static final int MAX_EVENTS=128;
    static final class Reading {
        final int slot;final String state,fingerprint,suffix,carrier;
        Reading(int slot,String state,String fingerprint,String suffix,String carrier) {
            this.slot=slot;this.state=state;this.fingerprint=fingerprint;this.suffix=suffix;this.carrier=carrier;
        }
        boolean present() { return state.equals("PRESENT"); }
        boolean comparable() { return state.equals("ABSENT")||present()&&fingerprint!=null; }
        boolean sameIdentity(Reading other) { return state.equals(other.state)&&Objects.equals(fingerprint,other.fingerprint); }
        boolean sameMetadata(Reading other) { return sameIdentity(other)&&Objects.equals(suffix,other.suffix)&&Objects.equals(carrier,other.carrier); }
    }
    static final class Known {
        final Reading reading;final String lastFingerprint,lastSuffix,lastCarrier;
        Known(Reading reading,Known previous) {
            this(reading,reading.present()?reading.fingerprint:previous==null?null:previous.lastFingerprint,
                reading.present()?reading.suffix:previous==null?null:previous.lastSuffix,
                reading.present()?reading.carrier:previous==null?"":previous.lastCarrier);
        }
        Known(Reading reading,String fingerprint,String suffix,String carrier) {
            this.reading=reading;lastFingerprint=fingerprint;lastSuffix=suffix;lastCarrier=carrier;
        }
    }
    static final class Event {
        final String id,type,observedAt,previousFingerprint,currentFingerprint,previousSuffix,currentSuffix,previousCarrier,currentCarrier;
        final int slot;
        Event(String id,String type,int slot,String at,String before,String after,String beforeSuffix,String afterSuffix,String beforeCarrier,String afterCarrier) {
            this.id=id;this.type=type;this.slot=slot;observedAt=at;previousFingerprint=before;currentFingerprint=after;
            previousSuffix=beforeSuffix;currentSuffix=afterSuffix;previousCarrier=beforeCarrier;currentCarrier=afterCarrier;
        }
    }
    static final class State {
        String baselineAt;boolean gapRecorded;
        final Map<Integer,Known> baseline=new HashMap<>();
        final List<Event> pending=new ArrayList<>();
    }
    interface Store { State load() throws Exception;void save(State state) throws Exception; }
    private static final class Candidate {
        final Reading reading;final long since;int samples=1;
        Candidate(Reading reading,long now) { this.reading=reading;since=now; }
    }
    private final Store storage;
    private final Map<Integer,Candidate> candidates=new HashMap<>();
    SimChangeTracker(Store storage) { this.storage=storage; }
    synchronized void observe(List<Reading> readings,boolean reliable,long elapsed,String at) throws Exception {
        if(!reliable||elapsed<BOOT_GRACE_MS) { candidates.clear();return; }
        State state=storage.load();boolean changed=false;Set<Integer> seen=new HashSet<>();
        for(Reading reading:readings) {
            seen.add(reading.slot);
            if(!reading.comparable()) { candidates.remove(reading.slot);continue; }
            Candidate candidate=candidates.get(reading.slot);
            if(candidate==null||elapsed<candidate.since||!candidate.reading.sameIdentity(reading)) {
                candidates.put(reading.slot,new Candidate(reading,elapsed));continue;
            }
            candidate.samples++;
            if(candidate.samples<2||elapsed-candidate.since<STABLE_MS)continue;
            Known previous=state.baseline.get(reading.slot);
            if(previous==null) {
                state.baseline.put(reading.slot,new Known(reading,null));
                if(state.baselineAt==null)state.baselineAt=at;
                changed=true;continue;
            }
            if(previous.reading.sameIdentity(reading)) {
                if(!previous.reading.sameMetadata(reading)) { state.baseline.put(reading.slot,new Known(reading,previous));changed=true; }
                continue;
            }
            if(state.pending.size()>=MAX_EVENTS-1) {
                if(!state.gapRecorded&&state.pending.size()<MAX_EVENTS) {
                    state.pending.add(new Event(UUID.randomUUID().toString(),"MONITORING_GAP",-1,at,null,null,null,null,"",""));
                    state.gapRecorded=true;changed=true;
                }
                continue; // Never advance a SIM baseline without recording its change durably.
            }
            String type=!reading.present()?"SIM_REMOVED":previous.lastFingerprint!=null&&!previous.lastFingerprint.equals(reading.fingerprint)?"SIM_REPLACED":"SIM_INSERTED";
            state.pending.add(new Event(UUID.randomUUID().toString(),type,reading.slot,at,previous.lastFingerprint,
                reading.present()?reading.fingerprint:null,previous.lastSuffix,reading.present()?reading.suffix:null,
                previous.lastCarrier,reading.present()?reading.carrier:""));
            state.baseline.put(reading.slot,new Known(reading,previous));changed=true;
        }
        candidates.keySet().retainAll(seen);
        if(changed)storage.save(state);
    }
    synchronized State state() throws Exception { return storage.load(); }
    synchronized void acknowledge(Set<String> ids) throws Exception {
        State state=storage.load();
        if(state.pending.removeIf(event->ids.contains(event.id))) {
            if(state.pending.size()<MAX_EVENTS-1)state.gapRecorded=false;
            storage.save(state);
        }
    }
}
