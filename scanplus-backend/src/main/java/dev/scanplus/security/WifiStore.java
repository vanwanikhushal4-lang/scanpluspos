package dev.scanplus.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import static dev.scanplus.security.WifiModels.*;

@Service
public class WifiStore {
    private final JdbcTemplate db;
    private final Clock clock;
    private final ObjectMapper mapper;
    public WifiStore(JdbcTemplate db, Clock clock, ObjectMapper mapper) { this.db=db; this.clock=clock; this.mapper=mapper; }
    private Timestamp now() { return Timestamp.from(clock.instant()); }
    private void requireDevice(UUID device) {
        if(db.queryForObject("SELECT COUNT(*) FROM devices WHERE id=? AND revoked=FALSE",Integer.class,device.toString())!=1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    private void expire(UUID device) {
        db.update("UPDATE wifi_commands SET state='EXPIRED',detail='Wi-Fi request expired' WHERE device_id=? AND state='PENDING' AND expires_at<=?",device.toString(),now());
    }
    private Map<String,Object> command(UUID id) {
        return db.queryForMap("SELECT id,request_id,action,blocked,created_at,expires_at,completed_at,state,detail FROM wifi_commands WHERE id=?",id.toString());
    }
    private com.fasterxml.jackson.databind.JsonNode snapshot(UUID device) {
        var rows=db.queryForList("SELECT CAST(snapshot_json AS VARCHAR) AS snapshot_json FROM wifi_snapshots WHERE device_id=?",device.toString());
        if(rows.isEmpty()) return null;
        try { return mapper.readTree((String)rows.get(0).get("SNAPSHOT_JSON")); }
        catch(Exception error) { throw new IllegalStateException("Stored Wi-Fi report could not be read",error); }
    }
    @Transactional public Map<String,Object> view(UUID device) {
        requireDevice(device); expire(device);
        var rows=db.queryForList("SELECT reported_at FROM wifi_snapshots WHERE device_id=?",device.toString());
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("reportedAt",rows.isEmpty()?null:((Timestamp)rows.get(0).get("REPORTED_AT")).toInstant());
        result.put("snapshot",snapshot(device));
        result.put("commands",db.queryForList("SELECT id,request_id,action,blocked,created_at,expires_at,completed_at,state,detail FROM wifi_commands WHERE device_id=? ORDER BY created_at DESC LIMIT 30",device.toString()));
        return result;
    }
    @Transactional public void report(UUID device, Snapshot snapshot) {
        requireDevice(device);
        try {
            db.update("MERGE INTO wifi_snapshots(device_id,reported_at,snapshot_json) KEY(device_id) VALUES(?,?,?)",device.toString(),now(),mapper.writeValueAsString(snapshot));
        } catch(com.fasterxml.jackson.core.JsonProcessingException error) { throw new IllegalArgumentException("Invalid Wi-Fi report",error); }
    }
    @Transactional public Map<String,Object> scan(UUID device, ScanRequest request) {
        return queue(device,request.requestId(),Action.SCAN,null,request.ttlSeconds());
    }
    @Transactional public Map<String,Object> policy(UUID device, PolicyRequest request) {
        return queue(device,request.requestId(),Action.SET_OPEN_POLICY,request.blocked(),request.ttlSeconds());
    }
    @Transactional public Map<String,Object> guard(UUID device, PolicyRequest request) {
        return queue(device,request.requestId(),Action.SET_OPEN_GUARD,request.blocked(),request.ttlSeconds());
    }
    private Map<String,Object> queue(UUID device,UUID requestId,Action action,Boolean blocked,Integer ttl) {
        requireDevice(device); db.queryForMap("SELECT id FROM devices WHERE id=? FOR UPDATE",device.toString()); expire(device);
        var existing=db.queryForList("SELECT id,action,blocked FROM wifi_commands WHERE device_id=? AND request_id=?",device.toString(),requestId.toString());
        if(!existing.isEmpty()) {
            if(!action.name().equals(existing.get(0).get("ACTION"))||!Objects.equals(blocked,existing.get(0).get("BLOCKED"))) throw new ResponseStatusException(HttpStatus.CONFLICT,"Request ID was already used for another Wi-Fi operation");
            return command(UUID.fromString((String)existing.get(0).get("ID")));
        }
        var latest=snapshot(device);
        if(latest==null) throw new ResponseStatusException(HttpStatus.CONFLICT,"Update the agent and wait for its first Wi-Fi report");
        if(action==Action.SET_OPEN_GUARD&&!latest.path("openGuard").isObject())
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Update the agent to 3.4.0 or newer for the separate open-network connection guard");
        if(Boolean.TRUE.equals(blocked)) {
            var row=db.queryForMap("SELECT d.owner_active,w.reported_at,CAST(w.snapshot_json AS VARCHAR) AS snapshot_json FROM devices d JOIN wifi_snapshots w ON d.id=w.device_id WHERE d.id=?",device.toString());
            try {
                var connection=mapper.readTree((String)row.get("SNAPSHOT_JSON")).path("connection");
                if(!Boolean.TRUE.equals(row.get("OWNER_ACTIVE"))||!((Timestamp)row.get("REPORTED_AT")).toInstant().isAfter(clock.instant().minusSeconds(90))||!Set.of("WEP","WPA","WPA2","WPA3","ENTERPRISE").contains(connection.path("security").asText()))
                    throw new ResponseStatusException(HttpStatus.CONFLICT,"A recent secured Wi-Fi connection and device-owner enrollment are required before enabling this policy");
            } catch(com.fasterxml.jackson.core.JsonProcessingException error) { throw new IllegalStateException(error); }
        }
        var pending=db.queryForList("SELECT id FROM wifi_commands WHERE device_id=? AND state='PENDING'",device.toString());
        if(!pending.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,"A Wi-Fi operation is already pending");
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO wifi_commands(id,device_id,request_id,action,blocked,created_at,expires_at,state,detail) VALUES(?,?,?,?,?,?,?,'PENDING','Waiting for terminal')",id.toString(),device.toString(),requestId.toString(),action.name(),blocked,now(),Timestamp.from(clock.instant().plusSeconds(ttl==null?120:ttl)));
        audit("administrator",action==Action.SCAN?"WIFI_SCAN_REQUESTED":action==Action.SET_OPEN_GUARD?Boolean.TRUE.equals(blocked)?"WIFI_GUARD_ENABLED_REQUESTED":"WIFI_GUARD_DISABLED_REQUESTED":Boolean.TRUE.equals(blocked)?"WIFI_BLOCK_OPEN_REQUESTED":"WIFI_ALLOW_OPEN_REQUESTED",device,id,"Wi-Fi operation queued");
        return command(id);
    }
    @Transactional public Command next(UUID device) {
        expire(device);
        var rows=db.queryForList("SELECT id,action,blocked,expires_at FROM wifi_commands WHERE device_id=? AND state='PENDING' ORDER BY created_at LIMIT 1",device.toString());
        return rows.isEmpty()?null:new Command(UUID.fromString((String)rows.get(0).get("ID")),Action.valueOf((String)rows.get(0).get("ACTION")),(Boolean)rows.get(0).get("BLOCKED"),((Timestamp)rows.get(0).get("EXPIRES_AT")).toInstant());
    }
    @Transactional public Map<String,Object> acknowledge(UUID device, UUID id, Ack ack) {
        requireDevice(device); db.queryForMap("SELECT id FROM devices WHERE id=? FOR UPDATE",device.toString()); expire(device);
        var rows=db.queryForList("SELECT state,action,blocked FROM wifi_commands WHERE id=? AND device_id=? FOR UPDATE",id.toString(),device.toString());
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        String state=(String)rows.get(0).get("STATE");
        if(Set.of("SUCCEEDED","FAILED").contains(state)) return command(id);
        if(!state.equals("PENDING")) throw new ResponseStatusException(HttpStatus.CONFLICT);
        if(ack.success()) {
            if(rows.get(0).get("ACTION").equals("SCAN")&&ack.snapshot().scanState()!=ScanState.FRESH) throw new ResponseStatusException(HttpStatus.CONFLICT,"A successful scan needs fresh results");
            if(rows.get(0).get("ACTION").equals("SET_OPEN_POLICY")&&(!ack.snapshot().policy().applied()||!Objects.equals(ack.snapshot().policy().blocked(),rows.get(0).get("BLOCKED")))) throw new ResponseStatusException(HttpStatus.CONFLICT,"Wi-Fi policy was not observed on the terminal");
            if(rows.get(0).get("ACTION").equals("SET_OPEN_GUARD")&&(ack.snapshot().openGuard()==null||!ack.snapshot().openGuard().applied()||!Objects.equals(ack.snapshot().openGuard().enabled(),rows.get(0).get("BLOCKED")))) throw new ResponseStatusException(HttpStatus.CONFLICT,"The open-network connection guard was not observed on the terminal");
        }
        report(device,ack.snapshot());
        String result=ack.success()?"SUCCEEDED":"FAILED";
        db.update("UPDATE wifi_commands SET state=?,detail=?,completed_at=? WHERE id=?",result,ack.snapshot().detail(),now(),id.toString());
        audit("device","WIFI_"+rows.get(0).get("ACTION")+"_"+result,device,id,ack.snapshot().detail());
        return command(id);
    }
    @Transactional public void revoke(UUID device) {
        db.update("UPDATE wifi_commands SET state='CANCELLED',detail='Device access revoked' WHERE device_id=? AND state='PENDING'",device.toString());
    }
    private void audit(String actor,String action,UUID device,UUID id,String detail) {
        db.update("INSERT INTO audit(event_time,actor,action,device_id,command_id,detail) VALUES(?,?,?,?,?,?)",now(),actor,action,device.toString(),id.toString(),detail);
    }
}
