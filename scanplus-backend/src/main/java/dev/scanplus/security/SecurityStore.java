package dev.scanplus.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import static dev.scanplus.security.ApiModels.*;

@Service
public class SecurityStore {
    private final JdbcTemplate db;
    private final Clock clock;
    private final AppControlStore apps;
    private final WifiStore wifi;
    public SecurityStore(JdbcTemplate db,Clock clock,AppControlStore apps,WifiStore wifi){this.db=db;this.clock=clock;this.apps=apps;this.wifi=wifi;}
    private Timestamp now(){return Timestamp.from(clock.instant());}
    private void audit(String actor,String action,UUID device,UUID command,String detail){
        db.update("INSERT INTO audit(event_time,actor,action,device_id,command_id,detail) VALUES(?,?,?,?,?,?)",now(),actor,action,device==null?null:device.toString(),command==null?null:command.toString(),detail);
    }
    private void requireDevice(UUID id){
        if(db.queryForObject("SELECT COUNT(*) FROM devices WHERE id=? AND revoked=FALSE",Integer.class,id.toString())!=1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    boolean authenticate(UUID id,String token){
        return db.queryForObject("SELECT COUNT(*) FROM devices WHERE id=? AND token_hash=? AND revoked=FALSE",Integer.class,id.toString(),Tokens.hash(token))==1;
    }
    @Transactional public Enrollment createEnrollment(EnrollmentRequest input){
        String code=Tokens.create();Instant expires=clock.instant().plusSeconds(600);
        db.update("INSERT INTO enrollments(code_hash,label,expected_serial,expires_at) VALUES(?,?,?,?)",Tokens.hash(code),input.label(),input.expectedSerial(),Timestamp.from(expires));
        audit("administrator","ENROLLMENT_CREATED",null,null,input.label());
        return new Enrollment(code,expires);
    }
    @Transactional public Identity enroll(EnrollRequest input){
        var rows=db.queryForList("SELECT * FROM enrollments WHERE code_hash=? FOR UPDATE",Tokens.hash(input.code()));
        if(rows.size()!=1) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        var row=rows.get(0);
        if(Boolean.TRUE.equals(row.get("CONSUMED"))||!((Timestamp)row.get("EXPIRES_AT")).toInstant().isAfter(clock.instant())||!input.serial().equals(row.get("EXPECTED_SERIAL")))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        String token=Tokens.create();UUID id=UUID.randomUUID();
        db.update("INSERT INTO devices(id,token_hash,label,serial,model,version,enrolled_at) VALUES(?,?,?,?,?,?,?)",id.toString(),Tokens.hash(token),row.get("LABEL"),input.serial(),input.model(),input.version(),now());
        db.update("UPDATE enrollments SET consumed=TRUE WHERE code_hash=?",Tokens.hash(input.code()));
        audit("device","ENROLLED",id,null,input.model());
        return new Identity(id,token);
    }
    public List<Map<String,Object>> devices(){
        return db.queryForList("SELECT d.id,d.label,d.serial,d.model,d.version,d.enrolled_at,d.last_seen,d.enabled,d.owner_active,d.usb_sealed,d.bridge_available,d.detail,d.revoked,(SELECT COUNT(*) FROM sim_alerts s WHERE s.device_id=d.id AND s.acknowledged_at IS NULL) AS sim_alert_count FROM devices d ORDER BY d.enrolled_at");
    }
    private Map<String,Object> viewCommand(UUID id){
        return db.queryForMap("SELECT id,device_id,request_id,enabled,created_at,expires_at,state,detail FROM commands WHERE id=?",id.toString());
    }
    @Transactional public Map<String,Object> queue(UUID device,ProtectionRequest request){
        requireDevice(device);
        db.queryForMap("SELECT id FROM devices WHERE id=? FOR UPDATE",device.toString());
        if(request.enabled()&&apps.installInProgress(device))throw new ResponseStatusException(HttpStatus.CONFLICT,"An app installation or removal is in progress");
        var existing=db.queryForList("SELECT id,enabled FROM commands WHERE device_id=? AND request_id=?",device.toString(),request.requestId().toString());
        if(!existing.isEmpty()){
            if(!request.enabled().equals(existing.get(0).get("ENABLED"))) throw new ResponseStatusException(HttpStatus.CONFLICT);
            return viewCommand(UUID.fromString((String)existing.get(0).get("ID")));
        }
        db.update("UPDATE commands SET state='CANCELLED',detail='Superseded by a newer administrator request' WHERE device_id=? AND state='PENDING'",device.toString());
        UUID id=UUID.randomUUID();Instant expires=clock.instant().plusSeconds(request.ttlSeconds()==null?120:request.ttlSeconds());
        db.update("INSERT INTO commands(id,device_id,request_id,enabled,created_at,expires_at,state) VALUES(?,?,?,?,?,?,'PENDING')",id.toString(),device.toString(),request.requestId().toString(),request.enabled(),now(),Timestamp.from(expires));
        audit("administrator",request.enabled()?"PROTECTION_ENABLE_REQUESTED":"PROTECTION_DISABLE_REQUESTED",device,id,"Queued with expiry");
        return viewCommand(id);
    }
    private void updateStatus(UUID device,Status status){
        db.update("UPDATE devices SET last_seen=?,enabled=?,owner_active=?,usb_sealed=?,bridge_available=?,detail=? WHERE id=?",now(),status.enabled(),status.ownerActive(),status.usbSealed(),status.bridgeAvailable(),status.detail(),device.toString());
    }
    @Transactional public CheckInReply checkIn(UUID device,CheckIn request){
        requireDevice(device);
        db.queryForMap("SELECT id FROM devices WHERE id=? FOR UPDATE",device.toString());
        updateStatus(device,request.status());
        db.update("UPDATE devices SET version=? WHERE id=?",request.version(),device.toString());
        db.update("UPDATE commands SET state='EXPIRED',detail='Command expired before acknowledgement' WHERE device_id=? AND state='PENDING' AND expires_at<=?",device.toString(),now());
        var pending=db.queryForList("SELECT id,enabled,expires_at FROM commands WHERE device_id=? AND state='PENDING' ORDER BY created_at LIMIT 1",device.toString());
        Command command=null;
        if(!pending.isEmpty()) {var row=pending.get(0);command=new Command(UUID.fromString((String)row.get("ID")),(Boolean)row.get("ENABLED"),((Timestamp)row.get("EXPIRES_AT")).toInstant());}
        return new CheckInReply(clock.instant(),command,10,command==null?apps.next(device):null,command==null?wifi.next(device):null,true,true);
    }
    @Transactional public Map<String,Object> acknowledge(UUID device,UUID command,Ack ack){
        requireDevice(device);
        db.queryForMap("SELECT id FROM devices WHERE id=? FOR UPDATE",device.toString());
        var rows=db.queryForList("SELECT * FROM commands WHERE id=? AND device_id=? FOR UPDATE",command.toString(),device.toString());
        if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var row=rows.get(0);
        String state=(String)row.get("STATE");
        if(state.equals("SUCCEEDED")||state.equals("FAILED")) return viewCommand(command);
        if(!state.equals("PENDING"))throw new ResponseStatusException(HttpStatus.CONFLICT);
        boolean consistent=ack.status().enabled().equals(row.get("ENABLED")) && ack.status().ownerActive() &&
                (!(Boolean)row.get("ENABLED") || (ack.status().usbSealed() && ack.status().bridgeAvailable()));
        if(ack.success()&&!consistent)throw new ResponseStatusException(HttpStatus.CONFLICT);
        String result=ack.success()?"SUCCEEDED":"FAILED";
        db.update("UPDATE commands SET state=?,detail=? WHERE id=?",result,ack.status().detail(),command.toString());
        updateStatus(device,ack.status());
        audit("device",result,device,command,ack.status().detail());
        return viewCommand(command);
    }
    @Transactional public List<Map<String,Object>> commands(UUID device){requireDevice(device);db.update("UPDATE commands SET state='EXPIRED',detail='Command expired before acknowledgement' WHERE device_id=? AND state='PENDING' AND expires_at<=?",device.toString(),now());return db.queryForList("SELECT id,request_id,enabled,created_at,expires_at,state,detail FROM commands WHERE device_id=? ORDER BY created_at DESC LIMIT 100",device.toString());}
    @Transactional public void revoke(UUID device){requireDevice(device);db.update("UPDATE devices SET revoked=TRUE WHERE id=?",device.toString());db.update("UPDATE commands SET state='CANCELLED',detail='Device revoked' WHERE device_id=? AND state='PENDING'",device.toString());apps.revoke(device);wifi.revoke(device);audit("administrator","DEVICE_REVOKED",device,null,"Remote authentication revoked; local protection state unchanged");}
    public List<Map<String,Object>> audit(){return db.queryForList("SELECT * FROM audit ORDER BY id DESC LIMIT 200");}
}
