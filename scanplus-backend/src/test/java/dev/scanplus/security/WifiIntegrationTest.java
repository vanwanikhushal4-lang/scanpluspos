package dev.scanplus.security;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.*;
import static dev.scanplus.security.WifiModels.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"scanplus.admin-token=integration-admin-token-01234567890123456789","scanplus.require-tls=true","spring.datasource.url=jdbc:h2:mem:wifi;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class WifiIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper; @Autowired JdbcTemplate db;
    final String admin="Bearer integration-admin-token-01234567890123456789";
    String json(Object value)throws Exception{return mapper.writeValueAsString(value);}
    JsonNode call(MockHttpServletRequestBuilder request)throws Exception {
        return mapper.readTree(mvc.perform(request.secure(true).contentType("application/json")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    JsonNode enroll()throws Exception {
        String serial=UUID.randomUUID().toString();
        JsonNode enrollment=call(post("/api/admin/enrollments").header("Authorization",admin).content(json(new ApiModels.EnrollmentRequest("Wi-Fi Test",serial))));
        JsonNode identity=call(post("/api/enroll").content(json(new ApiModels.EnrollRequest(enrollment.get("code").asText(),serial,"A920","3.3.0-scanplus"))));
        checkIn(identity);
        return identity;
    }
    MockHttpServletRequestBuilder device(MockHttpServletRequestBuilder request,JsonNode identity) {
        return request.header("Authorization","Bearer "+identity.get("deviceToken").asText()).header("X-Device-Id",identity.get("deviceId").asText());
    }
    JsonNode checkIn(JsonNode device)throws Exception {
        return call(device(post("/api/device/check-in"),device).content(json(new ApiModels.CheckIn("3.3.0-scanplus",new ApiModels.Status(false,true,false,true,"Managed")))));
    }
    Snapshot snapshot(Security security,boolean blocked,ScanState state) {
        return new Snapshot(state,"Terminal Wi-Fi report",0L,new Connection("Office","10:20:30:40:50:60",security,-55,2412,"192.168.0.100"),
            new Policy(blocked,true,blocked?Enforcement.LEGACY_LOCKDOWN:Enforcement.OFF,blocked,"Observed on terminal"),List.of(new Network("Office","10:20:30:40:50:60",security,-55,2412,0,true)));
    }
    void report(JsonNode device,Snapshot report)throws Exception {
        call(device(post("/api/device/wifi/report"),device).content(json(report)));
    }
    Snapshot guarded(boolean full,boolean guarded) {
        Snapshot original=snapshot(Security.WPA2,full,ScanState.CACHED);
        return new Snapshot(original.scanState(),original.detail(),original.scanAgeSeconds(),original.connection(),original.policy(),original.networks(),
            new OpenGuard(guarded,true,guarded?GuardEnforcement.MONITORED:GuardEnforcement.OFF,"Local connection guard report"));
    }
    String base(JsonNode device){return "/api/admin/devices/"+device.get("deviceId").asText()+"/wifi";}
    @Test void reportsAreDeviceBoundAndNeverPublic()throws Exception {
        JsonNode a=enroll(),b=enroll();report(a,snapshot(Security.WPA2,false,ScanState.FRESH));
        JsonNode observed=call(get(base(a)).header("Authorization",admin));
        assertEquals("Office",observed.path("snapshot").path("connection").path("ssid").asText());
        assertTrue(observed.path("reportedAt").isTextual());
        assertNotNull(Instant.parse(observed.path("reportedAt").asText()));
        assertTrue(call(get(base(b)).header("Authorization",admin)).path("snapshot").isNull());
        mvc.perform(get(base(a)).secure(true)).andExpect(status().isUnauthorized());
        mvc.perform(get(base(a)).secure(true).header("Authorization","Bearer "+a.get("deviceToken").asText())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/device/wifi/report").secure(true).header("Authorization",admin).header("X-Device-Id",a.get("deviceId").asText()).contentType("application/json").content(json(snapshot(Security.WPA2,false,ScanState.FRESH)))).andExpect(status().isUnauthorized());
    }
    @Test void scansNeedFreshObservedResultsAndCannotBeAcknowledgedByAnotherDevice()throws Exception {
        JsonNode a=enroll(),b=enroll();report(a,snapshot(Security.WPA2,false,ScanState.FRESH));
        JsonNode queued=call(post(base(a)+"/scan").header("Authorization",admin).content(json(new ScanRequest(UUID.randomUUID(),120))));
        assertEquals("SCAN",checkIn(a).path("wifiCommand").path("action").asText());
        String ack="/api/device/wifi/commands/"+queued.get("ID").asText()+"/ack";
        mvc.perform(device(post(ack),b).secure(true).contentType("application/json").content(json(new Ack(true,snapshot(Security.WPA2,false,ScanState.FRESH))))).andExpect(status().isNotFound());
        mvc.perform(device(post(ack),a).secure(true).contentType("application/json").content(json(new Ack(true,snapshot(Security.WPA2,false,ScanState.CACHED))))).andExpect(status().isConflict());
        assertEquals("SUCCEEDED",call(device(post(ack),a).content(json(new Ack(true,snapshot(Security.WPA2,false,ScanState.FRESH))))).get("STATE").asText());
        assertTrue(checkIn(a).path("wifiCommand").isNull());
    }
    @Test void unsafeOrStaleConnectionsCannotEnableBlocking()throws Exception {
        JsonNode a=enroll();
        for(Security security:List.of(Security.OPEN,Security.UNKNOWN,Security.ENHANCED_OPEN)) {
            report(a,snapshot(security,false,ScanState.FRESH));
            mvc.perform(post(base(a)+"/policy").secure(true).header("Authorization",admin).contentType("application/json").content(json(new PolicyRequest(UUID.randomUUID(),true,120)))).andExpect(status().isConflict());
        }
        report(a,snapshot(Security.WPA2,false,ScanState.FRESH));
        db.update("UPDATE wifi_snapshots SET reported_at=? WHERE device_id=?",Timestamp.from(Instant.now().minusSeconds(100)),a.get("deviceId").asText());
        mvc.perform(post(base(a)+"/policy").secure(true).header("Authorization",admin).contentType("application/json").content(json(new PolicyRequest(UUID.randomUUID(),true,120)))).andExpect(status().isConflict());
        assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM wifi_commands WHERE device_id=?",Integer.class,a.get("deviceId").asText()));
    }
    @Test void retriesAreIdempotentAndOnlyConfirmedPolicyIsSuccessful()throws Exception {
        JsonNode a=enroll();report(a,snapshot(Security.WPA2,false,ScanState.FRESH));UUID request=UUID.randomUUID();
        String body=json(new PolicyRequest(request,true,120));
        JsonNode queued=call(post(base(a)+"/policy").header("Authorization",admin).content(body));
        assertEquals(queued.get("ID"),call(post(base(a)+"/policy").header("Authorization",admin).content(body)).get("ID"));
        mvc.perform(post(base(a)+"/policy").secure(true).header("Authorization",admin).contentType("application/json").content(json(new PolicyRequest(request,false,120)))).andExpect(status().isConflict());
        String ack="/api/device/wifi/commands/"+queued.get("ID").asText()+"/ack";
        mvc.perform(device(post(ack),a).secure(true).contentType("application/json").content(json(new Ack(true,snapshot(Security.WPA2,false,ScanState.CACHED))))).andExpect(status().isConflict());
        assertEquals("SUCCEEDED",call(device(post(ack),a).content(json(new Ack(true,snapshot(Security.WPA2,true,ScanState.CACHED))))).get("STATE").asText());
        assertTrue(call(get(base(a)).header("Authorization",admin)).path("snapshot").path("policy").path("blocked").asBoolean());
    }
    @Test void expiredRequestsAndRevokedDevicesCannotChangePolicy()throws Exception {
        JsonNode a=enroll();report(a,snapshot(Security.WPA2,false,ScanState.FRESH));
        JsonNode queued=call(post(base(a)+"/policy").header("Authorization",admin).content(json(new PolicyRequest(UUID.randomUUID(),true,15))));
        db.update("UPDATE wifi_commands SET expires_at=? WHERE id=?",Timestamp.from(Instant.now().minusSeconds(1)),queued.get("ID").asText());
        assertTrue(checkIn(a).path("wifiCommand").isNull());
        assertEquals("EXPIRED",call(get(base(a)).header("Authorization",admin)).path("commands").get(0).get("STATE").asText());
        call(post(base(a)+"/policy").header("Authorization",admin).content(json(new PolicyRequest(UUID.randomUUID(),true,120))));
        mvc.perform(delete("/api/admin/devices/"+a.get("deviceId").asText()).secure(true).header("Authorization",admin)).andExpect(status().isOk());
        assertEquals("CANCELLED",db.queryForObject("SELECT state FROM wifi_commands WHERE device_id=? AND state='CANCELLED'",String.class,a.get("deviceId").asText()));
        mvc.perform(device(post("/api/device/wifi/report"),a).secure(true).contentType("application/json").content(json(snapshot(Security.WPA2,true,ScanState.FRESH)))).andExpect(status().isUnauthorized());
    }
    @Test void rejectsInconsistentEnforcementAndArbitraryCommands()throws Exception {
        JsonNode a=enroll();Snapshot snapshot=snapshot(Security.WPA2,false,ScanState.FRESH);
        ObjectNode report=mapper.valueToTree(snapshot);((ObjectNode)report.get("policy")).put("blocked",true);
        mvc.perform(device(post("/api/device/wifi/report"),a).secure(true).contentType("application/json").content(report.toString())).andExpect(status().isBadRequest());
        mvc.perform(post(base(a)+"/scan").secure(true).header("Authorization",admin).contentType("application/json").content("{\"requestId\":\""+UUID.randomUUID()+"\",\"command\":\"shell\"}")).andExpect(status().isBadRequest());
    }
    @Test void selectiveGuardRequiresUpdatedAgentAndItsOwnObservedResult()throws Exception {
        JsonNode a=enroll();report(a,snapshot(Security.WPA2,false,ScanState.FRESH));
        mvc.perform(post(base(a)+"/guard").secure(true).header("Authorization",admin).contentType("application/json").content(json(new PolicyRequest(UUID.randomUUID(),true,120)))).andExpect(status().isConflict());
        report(a,guarded(false,false));UUID requestId=UUID.randomUUID();String body=json(new PolicyRequest(requestId,true,120));
        JsonNode job=call(post(base(a)+"/guard").header("Authorization",admin).content(body));
        assertEquals(job.get("ID"),call(post(base(a)+"/guard").header("Authorization",admin).content(body)).get("ID"));
        mvc.perform(post(base(a)+"/policy").secure(true).header("Authorization",admin).contentType("application/json").content(body)).andExpect(status().isConflict());
        JsonNode check=checkIn(a);assertTrue(check.path("openWifiGuardSupported").asBoolean());assertEquals("SET_OPEN_GUARD",check.path("wifiCommand").path("action").asText());
        String ack="/api/device/wifi/commands/"+job.get("ID").asText()+"/ack";
        mvc.perform(device(post(ack),a).secure(true).contentType("application/json").content(json(new Ack(true,guarded(true,false))))).andExpect(status().isConflict());
        assertEquals("SUCCEEDED",call(device(post(ack),a).content(json(new Ack(true,guarded(false,true))))).get("STATE").asText());
        JsonNode state=call(get(base(a)).header("Authorization",admin)).path("snapshot");
        assertFalse(state.path("policy").path("blocked").asBoolean());assertFalse(state.path("policy").path("localSettingsLocked").asBoolean());assertTrue(state.path("openGuard").path("enabled").asBoolean());
        assertEquals("MONITORED",state.path("openGuard").path("enforcement").asText());
    }
    @Test void fullProtectionAndGuardHaveIndependentAcknowledgmentsAndCannotBeForged()throws Exception {
        JsonNode a=enroll();report(a,guarded(true,true));
        JsonNode job=call(post(base(a)+"/policy").header("Authorization",admin).content(json(new PolicyRequest(UUID.randomUUID(),false,120))));
        String ack="/api/device/wifi/commands/"+job.get("ID").asText()+"/ack";
        call(device(post(ack),a).content(json(new Ack(true,guarded(false,true)))));
        assertTrue(call(get(base(a)).header("Authorization",admin)).path("snapshot").path("openGuard").path("enabled").asBoolean());
        job=call(post(base(a)+"/guard").header("Authorization",admin).content(json(new PolicyRequest(UUID.randomUUID(),false,120))));
        ack="/api/device/wifi/commands/"+job.get("ID").asText()+"/ack";
        mvc.perform(device(post(ack),a).secure(true).contentType("application/json").content(json(new Ack(true,guarded(false,true))))).andExpect(status().isConflict());
        call(device(post(ack),a).content(json(new Ack(true,guarded(false,false)))));
        ObjectNode bad=mapper.valueToTree(guarded(false,true));((ObjectNode)bad.path("openGuard")).put("applied",false);
        mvc.perform(device(post("/api/device/wifi/report"),a).secure(true).contentType("application/json").content(bad.toString())).andExpect(status().isBadRequest());
        mvc.perform(post(base(a)+"/guard").secure(true).contentType("application/json").content(json(new PolicyRequest(UUID.randomUUID(),true,120)))).andExpect(status().isUnauthorized());
    }
}
