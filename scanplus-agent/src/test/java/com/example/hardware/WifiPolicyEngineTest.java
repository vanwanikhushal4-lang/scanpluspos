package com.example.hardware;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static com.example.hardware.WifiPolicyEngine.*;

public class WifiPolicyEngineTest {
    private static State copy(State state) {
        State result=new State();result.blocked=state.blocked;result.guard=state.guard;result.snapshot=state.snapshot;
        result.previousLock=state.previousLock;result.previousMinimum=state.previousMinimum;result.nativePolicy=state.nativePolicy;
        result.recovery.addAll(state.recovery);return result;
    }
    static final class Memory implements Storage {
        State saved=new State();boolean fail;
        public State load() { return copy(saved); }
        public void save(State state) throws Exception { if(fail)throw new Exception("Disk failure");saved=copy(state); }
    }
    static final class Device implements Host {
        boolean owner=true,secure=true,locked,otherLock,nativePolicy,failRemove,failRestore,failLock;
        int minimum,removed,restored;
        final List<Profile> profiles=new ArrayList<>();final Memory memory;
        Device(Memory memory) { this.memory=memory;profiles.add(new Profile(1,"office","",false));profiles.add(new Profile(2,"cafe","original-open-config",true)); }
        public boolean owner(){return owner;}
        public boolean securedConnection(){return secure;}
        public boolean nativePolicy(){return nativePolicy;}
        public int minimum(){return minimum;}
        public void minimum(int value){minimum=value;}
        public boolean locked(){return locked||otherLock;}
        public boolean ownLock(){return locked;}
        public void lock(boolean value)throws Exception{if(failLock)throw new Exception("Lock failed");locked=value;}
        public List<Profile> profiles(){return new ArrayList<>(profiles);}
        public void remove(Profile profile)throws Exception{
            assertTrue(memory.saved.snapshot);
            assertTrue(memory.saved.recovery.stream().anyMatch(item->item.key.equals(profile.key)));
            if(failRemove)throw new Exception("Remove failed");
            profiles.removeIf(item->item.key.equals(profile.key)&&item.open);removed++;
        }
        public void restore(Profile profile)throws Exception{
            if(failRestore)throw new Exception("Restore failed");
            if(profiles.stream().noneMatch(item->item.key.equals(profile.key)&&item.open))profiles.add(profile);
            restored++;
        }
    }
    @Test public void refusesOpenOrUnknownCurrentConnectionWithoutChangingAnything()throws Exception{
        Memory memory=new Memory();Device host=new Device(memory);host.secure=false;
        WifiPolicyEngine engine=new WifiPolicyEngine(host,memory);
        try{engine.setBlocked(true);fail();}catch(IllegalStateException expected){}
        assertFalse(host.locked);assertEquals(0,host.removed);assertFalse(memory.saved.snapshot);assertFalse(memory.saved.blocked);
    }
    @Test public void strictModeKeepsSecuredProfilesAndRestoresOpenProfilesAfterRestart()throws Exception{
        Memory memory=new Memory();Device host=new Device(memory);WifiPolicyEngine engine=new WifiPolicyEngine(host,memory);
        engine.setBlocked(true);assertTrue(engine.applied());assertTrue(host.locked);
        assertEquals(1,host.profiles.size());assertEquals("office",host.profiles.get(0).key);
        assertEquals("original-open-config",memory.saved.recovery.get(0).recovery);
        WifiPolicyEngine restarted=new WifiPolicyEngine(host,memory);restarted.maintain();assertEquals(1,host.removed);
        restarted.setBlocked(false);assertFalse(host.locked);assertEquals(2,host.profiles.size());
        assertFalse(memory.saved.snapshot);assertFalse(memory.saved.blocked);assertTrue(restarted.applied());
    }
    @Test public void failureBeforeDurableCheckpointCannotRemoveProfiles()throws Exception{
        Memory memory=new Memory();Device host=new Device(memory);WifiPolicyEngine engine=new WifiPolicyEngine(host,memory);memory.fail=true;
        try{engine.setBlocked(true);fail();}catch(Exception expected){}
        assertEquals(0,host.removed);assertFalse(host.locked);assertEquals(2,host.profiles.size());
    }
    @Test public void removalFailureRollsBackAndPreservesEarlierLocalRestriction()throws Exception{
        Memory memory=new Memory();Device host=new Device(memory);host.locked=true;host.failRemove=true;
        WifiPolicyEngine engine=new WifiPolicyEngine(host,memory);
        try{engine.setBlocked(true);fail();}catch(Exception expected){}
        assertTrue(host.locked);assertFalse(memory.saved.blocked);assertEquals(2,host.profiles.size());
    }
    @Test public void disablingDoesNotRemoveAnotherAdministratorsRestriction()throws Exception{
        Memory memory=new Memory();Device host=new Device(memory);host.otherLock=true;
        WifiPolicyEngine engine=new WifiPolicyEngine(host,memory);engine.setBlocked(true);engine.setBlocked(false);
        assertFalse(host.locked);assertTrue(host.otherLock);assertTrue(host.locked());
    }
    @Test public void failedRestoreKeepsRecoveryAndCanBeRetried()throws Exception{
        Memory memory=new Memory();Device host=new Device(memory);WifiPolicyEngine engine=new WifiPolicyEngine(host,memory);
        engine.setBlocked(true);host.failRestore=true;
        try{engine.setBlocked(false);fail();}catch(Exception expected){}
        assertTrue(memory.saved.blocked);assertTrue(memory.saved.snapshot);assertEquals(1,memory.saved.recovery.size());assertTrue(host.locked);
        host.failRestore=false;engine.setBlocked(false);assertFalse(memory.saved.blocked);assertEquals(2,host.profiles.size());
    }
    @Test public void policyIsReappliedAfterRestartAndNewOpenProfileIsCheckpointed()throws Exception{
        Memory memory=new Memory();Device host=new Device(memory);WifiPolicyEngine engine=new WifiPolicyEngine(host,memory);
        engine.setBlocked(true);host.locked=false;host.profiles.add(new Profile(20,"guest","guest-config",true));
        new WifiPolicyEngine(host,memory).maintain();assertTrue(host.locked);assertEquals(1,host.profiles.size());assertEquals(2,memory.saved.recovery.size());
        engine.setBlocked(false);assertEquals(3,host.profiles.size());
    }
    @Test public void nativePolicyPreservesStrongerPriorPolicyAndDoesNotEditProfiles()throws Exception{
        Memory memory=new Memory();Device host=new Device(memory);host.nativePolicy=true;host.minimum=3;
        WifiPolicyEngine engine=new WifiPolicyEngine(host,memory);engine.setBlocked(true);
        assertEquals(3,host.minimum);assertEquals(0,host.removed);assertFalse(host.locked);assertTrue(engine.applied());
        engine.setBlocked(false);assertEquals(3,host.minimum);assertEquals(0,host.restored);
    }
    @Test public void ownerLossNeverReportsThePolicyAsApplied()throws Exception{
        Memory memory=new Memory();Device host=new Device(memory);WifiPolicyEngine engine=new WifiPolicyEngine(host,memory);
        engine.setBlocked(true);host.owner=false;assertFalse(engine.applied());
        try{engine.setBlocked(false);fail();}catch(SecurityException expected){}
        assertTrue(memory.saved.blocked);
    }
    @Test public void connectionGuardLeavesSettingsAndNewSecuredNetworksAvailable()throws Exception{
        Memory memory=new Memory();Device host=new Device(memory);WifiPolicyEngine engine=new WifiPolicyEngine(host,memory);
        engine.setGuard(true);assertTrue(engine.applied());assertFalse(host.locked);assertFalse(memory.saved.blocked);assertTrue(memory.saved.guard);
        host.profiles.add(new Profile(30,"new-secured-office","",false));
        host.profiles.add(new Profile(31,"new-open-guest","guest-recovery",true));
        new WifiPolicyEngine(host,memory).maintain();
        assertFalse(host.locked);assertEquals(2,host.profiles.size());
        assertTrue(host.profiles.stream().anyMatch(profile->profile.key.equals("new-secured-office")));
        assertEquals(2,memory.saved.recovery.size());
        engine.setGuard(false);assertEquals(4,host.profiles.size());assertFalse(memory.saved.snapshot);assertFalse(memory.saved.guard);
    }
    @Test public void fullProtectionCanBeTurnedOffWithoutTurningOffConnectionGuard()throws Exception{
        Memory memory=new Memory();Device host=new Device(memory);WifiPolicyEngine engine=new WifiPolicyEngine(host,memory);
        engine.setBlocked(true);engine.setGuard(true);assertTrue(host.locked);
        new WifiPolicyEngine(host,memory).setBlocked(false);
        assertFalse(host.locked);assertFalse(memory.saved.blocked);assertTrue(memory.saved.guard);assertTrue(engine.applied());assertEquals(1,host.profiles.size());
        engine.setGuard(false);assertEquals(2,host.profiles.size());assertFalse(host.locked);
    }
    @Test public void turningOffConnectionGuardNeverWeakensFullProtection()throws Exception{
        Memory memory=new Memory();Device host=new Device(memory);WifiPolicyEngine engine=new WifiPolicyEngine(host,memory);
        engine.setGuard(true);engine.setBlocked(true);engine.setGuard(false);
        assertTrue(memory.saved.blocked);assertFalse(memory.saved.guard);assertTrue(host.locked);assertEquals(1,host.profiles.size());assertTrue(engine.applied());
        engine.setBlocked(false);assertFalse(host.locked);assertEquals(2,host.profiles.size());
    }
    @Test public void guardPreservesPreexistingRestrictionsAndRestorationCanBeRetried()throws Exception{
        Memory memory=new Memory();Device host=new Device(memory);host.locked=true;host.otherLock=true;
        WifiPolicyEngine engine=new WifiPolicyEngine(host,memory);engine.setGuard(true);host.failRestore=true;
        try{engine.setGuard(false);fail();}catch(Exception expected){}
        assertTrue(memory.saved.guard);assertTrue(memory.saved.snapshot);assertTrue(host.locked);
        host.failRestore=false;engine.setGuard(false);assertTrue(host.locked);assertTrue(host.otherLock);assertFalse(memory.saved.guard);
    }
    @Test public void unsafeConnectionCannotEnableGuardAndFailedUpgradeKeepsExistingGuard()throws Exception{
        Memory memory=new Memory();Device host=new Device(memory);WifiPolicyEngine engine=new WifiPolicyEngine(host,memory);host.secure=false;
        try{engine.setGuard(true);fail();}catch(IllegalStateException expected){}
        assertFalse(memory.saved.guard);assertEquals(0,host.removed);
        host.secure=true;engine.setGuard(true);host.failLock=true;
        try{engine.setBlocked(true);fail();}catch(Exception expected){}
        assertTrue(memory.saved.guard);assertFalse(memory.saved.blocked);assertFalse(host.locked);assertTrue(engine.applied());
    }
}
