package com.example.hardware;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static com.example.hardware.SimChangeTracker.*;

public class SimChangeTrackerTest {
    static final String AT="2026-10-19T08:00:00.000Z";
    static final class Memory implements Store {
        State saved=new State();boolean fail;
        private State copy() { State result=new State();result.baselineAt=saved.baselineAt;result.gapRecorded=saved.gapRecorded;result.baseline.putAll(saved.baseline);result.pending.addAll(saved.pending);return result; }
        public State load() { return copy(); }
        public void save(State state)throws Exception { if(fail)throw new Exception("Storage unavailable");saved=state; }
    }
    Reading present(int slot,String id,String carrier) { return new Reading(slot,"PRESENT",id,"1234",carrier); }
    Reading absent(int slot) { return new Reading(slot,"ABSENT",null,null,""); }
    void stable(SimChangeTracker tracker,long at,Reading... readings)throws Exception {
        tracker.observe(Arrays.asList(readings),true,at,AT);tracker.observe(Arrays.asList(readings),true,at+STABLE_MS,AT);
    }
    @Test public void firstStableReadingCreatesBaselineWithoutAnAlertAndBootNoiseIsIgnored()throws Exception {
        Memory memory=new Memory();SimChangeTracker tracker=new SimChangeTracker(memory);
        tracker.observe(List.of(absent(0)),true,1000,AT);tracker.observe(List.of(absent(0)),true,20000,AT);
        assertTrue(memory.saved.baseline.isEmpty());
        stable(tracker,40000,present(0,"sim-a","Office carrier"));
        assertEquals("sim-a",memory.saved.baseline.get(0).lastFingerprint);assertEquals(AT,memory.saved.baselineAt);assertTrue(memory.saved.pending.isEmpty());
    }
    @Test public void carrierChangesAndTransientAbsenceNeverBecomeSimReplacement()throws Exception {
        Memory memory=new Memory();SimChangeTracker tracker=new SimChangeTracker(memory);stable(tracker,40000,present(0,"sim-a","Carrier A"));
        tracker.observe(List.of(absent(0)),true,80000,AT);
        stable(tracker,85000,present(0,"sim-a","Carrier renamed"));
        assertTrue(memory.saved.pending.isEmpty());assertEquals("Carrier renamed",memory.saved.baseline.get(0).lastCarrier);
        stable(tracker,120000,present(0,"sim-b","Carrier B"));
        assertEquals(1,memory.saved.pending.size());assertEquals("SIM_REPLACED",memory.saved.pending.get(0).type);
        assertEquals("sim-a",memory.saved.pending.get(0).previousFingerprint);assertEquals("sim-b",memory.saved.pending.get(0).currentFingerprint);
    }
    @Test public void removalAndReplacementSurviveRestartAndOfflineReceiptsRemoveOnlyTheirEvents()throws Exception {
        Memory memory=new Memory();SimChangeTracker tracker=new SimChangeTracker(memory);stable(tracker,40000,present(0,"sim-a","A"));stable(tracker,80000,absent(0));
        String removed=memory.saved.pending.get(0).id;assertEquals("SIM_REMOVED",memory.saved.pending.get(0).type);
        tracker=new SimChangeTracker(memory);stable(tracker,120000,present(0,"sim-b","B"));
        assertEquals(2,memory.saved.pending.size());assertEquals("SIM_REPLACED",memory.saved.pending.get(1).type);
        String replaced=memory.saved.pending.get(1).id;tracker.acknowledge(Set.of(removed,"not-a-sent-event"));
        assertEquals(1,memory.saved.pending.size());assertEquals(replaced,memory.saved.pending.get(0).id);
        stable(tracker,160000,present(0,"sim-b","B"));assertEquals(1,memory.saved.pending.size());
        tracker.acknowledge(Set.of(replaced));assertTrue(memory.saved.pending.isEmpty());assertEquals("sim-b",memory.saved.baseline.get(0).lastFingerprint);
    }
    @Test public void theSameSimCanBeRemovedAndReinsertedAndSlotsAreIndependent()throws Exception {
        Memory memory=new Memory();SimChangeTracker tracker=new SimChangeTracker(memory);stable(tracker,40000,present(0,"sim-a","A"),absent(1));
        stable(tracker,80000,absent(0),present(1,"sim-b","B"));stable(tracker,120000,present(0,"sim-a","A"),present(1,"sim-b","B"));
        assertEquals(3,memory.saved.pending.size());
        assertEquals("SIM_REMOVED",memory.saved.pending.get(0).type);assertEquals(0,memory.saved.pending.get(0).slot);
        assertEquals("SIM_INSERTED",memory.saved.pending.get(1).type);assertEquals(1,memory.saved.pending.get(1).slot);
        assertEquals("SIM_INSERTED",memory.saved.pending.get(2).type);assertEquals(0,memory.saved.pending.get(2).slot);
    }
    @Test public void permissionLossAirplaneModeLockedAndUnidentifiedSimsDoNotEraseBaseline()throws Exception {
        Memory memory=new Memory();SimChangeTracker tracker=new SimChangeTracker(memory);stable(tracker,40000,present(0,"sim-a","A"));
        tracker.observe(List.of(absent(0)),true,80000,AT);tracker.observe(Collections.emptyList(),false,90000,AT);
        tracker.observe(List.of(absent(0)),true,100000,AT);assertTrue(memory.saved.pending.isEmpty());
        stable(tracker,105000,new Reading(0,"LOCKED",null,null,""));
        stable(tracker,140000,new Reading(0,"PRESENT",null,null,"Carrier A"));
        assertTrue(memory.saved.pending.isEmpty());assertEquals("sim-a",memory.saved.baseline.get(0).lastFingerprint);
        stable(tracker,180000,present(0,"sim-a","A"));assertTrue(memory.saved.pending.isEmpty());
    }
    @Test public void failedDurableSaveCannotAdvanceTheKnownSimOrLoseItsAlert()throws Exception {
        Memory memory=new Memory();SimChangeTracker tracker=new SimChangeTracker(memory);stable(tracker,40000,present(0,"sim-a","A"));
        memory.fail=true;
        try{stable(tracker,80000,present(0,"sim-b","B"));fail();}catch(Exception expected){}
        assertEquals("sim-a",memory.saved.baseline.get(0).lastFingerprint);assertTrue(memory.saved.pending.isEmpty());
        memory.fail=false;tracker.observe(List.of(present(0,"sim-b","B")),true,110000,AT);
        assertEquals("sim-b",memory.saved.baseline.get(0).lastFingerprint);assertEquals(1,memory.saved.pending.size());
    }
    @Test public void fullOutboxRecordsAGapAndNeverSilentlyAdvancesPastUnrecordedChanges()throws Exception {
        Memory memory=new Memory();SimChangeTracker tracker=new SimChangeTracker(memory);stable(tracker,40000,present(0,"sim-a","A"));
        for(int i=0;i<MAX_EVENTS-1;i++)memory.saved.pending.add(new Event(UUID.randomUUID().toString(),"SIM_INSERTED",0,AT,null,"older",null,"1234","","A"));
        stable(tracker,80000,present(0,"sim-b","B"));
        assertEquals(MAX_EVENTS,memory.saved.pending.size());assertEquals("MONITORING_GAP",memory.saved.pending.get(MAX_EVENTS-1).type);assertEquals("sim-a",memory.saved.baseline.get(0).lastFingerprint);
        Set<String> delivered=new HashSet<>();for(int i=0;i<8;i++)delivered.add(memory.saved.pending.get(i).id);tracker.acknowledge(delivered);
        tracker.observe(List.of(present(0,"sim-b","B")),true,120000,AT);
        assertEquals("sim-b",memory.saved.baseline.get(0).lastFingerprint);assertEquals("SIM_REPLACED",memory.saved.pending.get(memory.saved.pending.size()-1).type);
    }
}
