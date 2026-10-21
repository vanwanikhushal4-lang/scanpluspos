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
import static dev.scanplus.security.SimModels.*;

@Service
public class SimStore {
    private final JdbcTemplate db;private final Clock clock;private final ObjectMapper mapper;
    public SimStore(JdbcTemplate db,Clock clock,ObjectMapper mapper) { this.db=db;this.clock=clock;this.mapper=mapper; }
    private Timestamp now() { return Timestamp.from(clock.instant()); }
    private void lockDevice(UUID device) {
        var rows=db.queryForList("SELECT id FROM devices WHERE id=? AND revoked=FALSE FOR UPDATE",device.toString());
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch(com.fasterxml.jackson.core.JsonProcessingException error) { throw new IllegalArgumentException("Invalid SIM metadata"); }
    }
    @Transactional public Receipt report(UUID device,Report report) {
        lockDevice(device);List<UUID> accepted=new ArrayList<>();
        for(Event event:report.events()) {
            String hash=Tokens.hash(json(event));
            var prior=db.queryForList("SELECT payload_hash FROM sim_alerts WHERE device_id=? AND event_id=?",device.toString(),event.id().toString());
            if(!prior.isEmpty()) {
                if(!hash.equals(prior.get(0).get("PAYLOAD_HASH")))throw new ResponseStatusException(HttpStatus.CONFLICT,"A SIM event ID was reused with different content");
            } else {
                db.update("INSERT INTO sim_alerts(device_id,event_id,event_type,slot_index,observed_at,received_at,previous_suffix,current_suffix,previous_carrier,current_carrier,payload_hash) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                    device.toString(),event.id().toString(),event.type().name(),event.slot(),Timestamp.from(event.observedAt()),now(),event.previousSuffix(),event.currentSuffix(),event.previousCarrier(),event.currentCarrier(),hash);
                String detail=event.type()==EventType.MONITORING_GAP?"SIM monitoring backlog reached its limit; some changes may need review":"SIM "+(event.slot()+1)+": "+event.type().name().toLowerCase(Locale.ROOT).replace('_',' ');
                db.update("INSERT INTO audit(event_time,actor,action,device_id,detail) VALUES(?,'device',?,?,?)",now(),event.type().name(),device.toString(),detail);
            }
            accepted.add(event.id());
        }
        db.update("MERGE INTO sim_snapshots(device_id,reported_at,snapshot_json) KEY(device_id) VALUES(?,?,?)",device.toString(),now(),json(report.snapshot()));
        return new Receipt(accepted);
    }
    private static void instant(Map<String,Object> row,String key) { if(row.get(key) instanceof Timestamp value)row.put(key,value.toInstant()); }
    private Map<String,Object> alert(UUID device,UUID id) {
        var rows=db.queryForList("SELECT event_id AS id,event_type,slot_index AS slot,observed_at,received_at,previous_suffix,current_suffix,previous_carrier,current_carrier,acknowledged_at FROM sim_alerts WHERE device_id=? AND event_id=?",device.toString(),id.toString());
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var result=rows.get(0);for(String key:List.of("OBSERVED_AT","RECEIVED_AT","ACKNOWLEDGED_AT"))instant(result,key);return result;
    }
    @Transactional public Map<String,Object> view(UUID device) {
        lockDevice(device);Map<String,Object> result=new LinkedHashMap<>();
        var rows=db.queryForList("SELECT reported_at,CAST(snapshot_json AS VARCHAR) AS snapshot_json FROM sim_snapshots WHERE device_id=?",device.toString());
        result.put("reportedAt",rows.isEmpty()?null:((Timestamp)rows.get(0).get("REPORTED_AT")).toInstant());
        try { result.put("snapshot",rows.isEmpty()?null:mapper.readTree((String)rows.get(0).get("SNAPSHOT_JSON"))); }
        catch(com.fasterxml.jackson.core.JsonProcessingException error) { throw new IllegalStateException("Stored SIM report needs attention"); }
        var alerts=db.queryForList("SELECT event_id AS id,event_type,slot_index AS slot,observed_at,received_at,previous_suffix,current_suffix,previous_carrier,current_carrier,acknowledged_at FROM sim_alerts WHERE device_id=? ORDER BY CASE WHEN acknowledged_at IS NULL THEN 0 ELSE 1 END,received_at DESC,event_id LIMIT 200",device.toString());
        for(var row:alerts)for(String key:List.of("OBSERVED_AT","RECEIVED_AT","ACKNOWLEDGED_AT"))instant(row,key);
        result.put("alerts",alerts);result.put("unacknowledgedCount",db.queryForObject("SELECT COUNT(*) FROM sim_alerts WHERE device_id=? AND acknowledged_at IS NULL",Long.class,device.toString()));
        return result;
    }
    @Transactional public Map<String,Object> acknowledge(UUID device,UUID id) {
        lockDevice(device);alert(device,id);
        int changed=db.update("UPDATE sim_alerts SET acknowledged_at=? WHERE device_id=? AND event_id=? AND acknowledged_at IS NULL",now(),device.toString(),id.toString());
        if(changed>0)db.update("INSERT INTO audit(event_time,actor,action,device_id,detail) VALUES(?,'administrator','SIM_ALERT_ACKNOWLEDGED',?,?)",now(),device.toString(),"SIM alert acknowledged: "+id);
        return alert(device,id);
    }
}
