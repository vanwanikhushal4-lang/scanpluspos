package com.example.hardware;

import java.util.*;

/** Device-independent policy transaction with a durable recovery checkpoint before removal. */
final class WifiPolicyEngine {
    static final class Profile {
        final int id; final String key, recovery; final boolean open;
        Profile(int id,String key,String recovery,boolean open) { this.id=id;this.key=key;this.recovery=recovery;this.open=open; }
    }
    static final class State {
        boolean blocked, guard, snapshot, previousLock, nativePolicy;
        int previousMinimum;
        final List<Profile> recovery=new ArrayList<>();
        boolean active() { return blocked||guard; }
    }
    interface Host {
        boolean owner(); boolean securedConnection() throws Exception;
        boolean nativePolicy(); int minimum() throws Exception; void minimum(int value) throws Exception;
        boolean locked() throws Exception; boolean ownLock() throws Exception; void lock(boolean value) throws Exception;
        List<Profile> profiles() throws Exception;
        void remove(Profile profile) throws Exception;
        void restore(Profile profile) throws Exception;
    }
    interface Storage { State load() throws Exception; void save(State state) throws Exception; }
    private final Host host; private final Storage storage;
    WifiPolicyEngine(Host host,Storage storage) { this.host=host;this.storage=storage; }
    synchronized State state() throws Exception { return storage.load(); }
    synchronized boolean applied() throws Exception {
        State state=storage.load();
        if(!state.active()) return !state.snapshot;
        if(!host.owner()) return false;
        if(state.nativePolicy) return host.minimum()>=Math.max(1,state.previousMinimum);
        if(state.blocked?(!host.locked()||!host.ownLock()):host.ownLock()!=state.previousLock) return false;
        for(Profile profile:host.profiles()) if(profile.open) return false;
        return true;
    }
    synchronized void setBlocked(boolean blocked) throws Exception {
        change(false,blocked);
    }
    synchronized void setGuard(boolean enabled) throws Exception {
        change(true,enabled);
    }
    private void change(boolean guardControl,boolean enabled) throws Exception {
        if(!host.owner()) throw new SecurityException("Device-owner enrollment is required for Wi-Fi policy");
        State state=storage.load();
        boolean wasActive=state.active(),previousBlocked=state.blocked,previousGuard=state.guard;
        if((guardControl?state.guard:state.blocked)==enabled) {
            if(state.active()) maintain(); else if(state.snapshot) restore(state);
            return;
        }
        if(!wasActive&&enabled) {
            // Repeat the connectivity check on-device immediately before changing any policy.
            if(!host.securedConnection()) throw new IllegalStateException("Connect the terminal to a secured Wi-Fi network before blocking open Wi-Fi");
            state=new State();state.snapshot=true;state.nativePolicy=host.nativePolicy();
            if(state.nativePolicy) state.previousMinimum=host.minimum();
            else state.previousLock=host.ownLock();
        }
        if(guardControl) state.guard=enabled; else state.blocked=enabled;
        // Keep the old intent durable until all restoration succeeds.
        if(!state.active()) { restore(storage.load()); return; }
        storage.save(state);
        try {
            maintain();
            if(!applied()) throw new IllegalStateException("Open Wi-Fi policy could not be verified");
        } catch(Exception failure) {
            try {
                State recovery=storage.load();
                if(wasActive) {
                    recovery.blocked=previousBlocked;recovery.guard=previousGuard;
                    storage.save(recovery);maintain();
                } else restore(recovery);
            } catch(Exception rollback) { failure.addSuppressed(rollback); }
            throw failure;
        }
    }
    synchronized void maintain() throws Exception {
        State state=storage.load();if(!state.active()) return;
        if(!state.snapshot) throw new IllegalStateException("Wi-Fi policy recovery checkpoint is missing");
        if(!host.owner()) throw new SecurityException("Device-owner enrollment is required for Wi-Fi policy");
        if(state.nativePolicy) { int required=Math.max(1,state.previousMinimum);if(host.minimum()<required)host.minimum(required);return; }
        boolean desiredLock=state.blocked||state.previousLock;
        if(host.ownLock()!=desiredLock)host.lock(desiredLock);
        if(state.blocked&&!host.locked()||host.ownLock()!=desiredLock) throw new IllegalStateException("Local Wi-Fi settings policy could not be verified");
        for(Profile profile:host.profiles()) if(profile.open) {
            boolean saved=false;long bytes=0;
            for(Profile prior:state.recovery) { saved|=prior.key.equals(profile.key);bytes+=prior.recovery.length(); }
            if(!saved) {
                if(state.recovery.size()>=128||bytes+profile.recovery.length()>2*1024*1024) throw new IllegalStateException("Open network recovery storage is full");
                state.recovery.add(profile);
                storage.save(state); // No profile is removed before its recovery copy is durable.
            }
            host.remove(profile);
        }
        for(Profile profile:host.profiles()) if(profile.open) throw new IllegalStateException("An open Wi-Fi profile remains usable");
    }
    private void restore(State state) throws Exception {
        if(!state.snapshot) return; // Leave unrelated administrator restrictions untouched.
        if(state.nativePolicy) {
            host.minimum(state.previousMinimum);
            if(host.minimum()!=state.previousMinimum) throw new IllegalStateException("Previous native Wi-Fi policy could not be restored");
        }
        else {
            for(Profile profile:state.recovery) host.restore(profile);
            host.lock(state.previousLock);
            if(host.ownLock()!=state.previousLock) throw new IllegalStateException("Previous local Wi-Fi settings could not be restored");
        }
        storage.save(new State());
    }
}
