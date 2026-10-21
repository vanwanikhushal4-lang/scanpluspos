package dev.scanplus.security;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.time.Instant;
import java.util.*;
import static dev.scanplus.security.SimModels.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"scanplus.admin-token=integration-admin-token-01234567890123456789","scanplus.require-tls=true","spring.datasource.url=jdbc:h2:mem:sim;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class SimIntegrationTest {
    @Autowired MockMvc mvc;@Autowired ObjectMapper mapper;@Autowired JdbcTemplate db;
    final String admin="Bearer integration-admin-token-01234567890123456789",fingerprint="a".repeat(64);
    String json(Object value)throws Exception{return mapper.writeValueAsString(value);}
    JsonNode call(MockHttpServletRequestBuilder request)throws Exception{return mapper.readTree(mvc.perform(request.secure(true).contentType("application/json")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    JsonNode enroll()throws Exception {
        String serial=UUID.randomUUID().toString();JsonNode code=call(post("/api/admin/enrollments").header("Authorization",admin).content(json(new ApiModels.EnrollmentRequest("SIM Test",serial))));
        return call(post("/api/enroll").content(json(new ApiModels.EnrollRequest(code.path("code").asText(),serial,"A920","3.5.0-scanplus"))));
    }
    MockHttpServletRequestBuilder device(MockHttpServletRequestBuilder request,JsonNode identity){return request.header("Authorization","Bearer "+identity.path("deviceToken").asText()).header("X-Device-Id",identity.path("deviceId").asText());}
    String base(JsonNode identity){return "/api/admin/devices/"+identity.path("deviceId").asText()+"/sim";}
    Snapshot snapshot(){return new Snapshot(ReadState.READY,"SIM slots monitored",true,Instant.parse("2026-10-19T00:00:00Z"),1,List.of(new Slot(0,SlotState.PRESENT,fingerprint,"1234","Test carrier",true)));}
    Event inserted(UUID id){return new Event(id,EventType.SIM_INSERTED,0,Instant.parse("2020-01-01T00:00:00Z"),null,fingerprint,null,"1234","","Test carrier");}
    void report(JsonNode identity,Report report)throws Exception{call(device(post("/api/device/sim/report"),identity).content(json(report)));}
    @Test void reportsAndAlertReadsAreAuthenticatedAndBoundToTheEnrolledDevice()throws Exception{
        JsonNode a=enroll(),b=enroll();UUID event=UUID.randomUUID();report(a,new Report(snapshot(),List.of(inserted(event))));
        JsonNode view=call(get(base(a)).header("Authorization",admin));assertEquals(1,view.path("unacknowledgedCount").asInt());assertTrue(view.path("reportedAt").isTextual());
        assertNotNull(Instant.parse(view.path("reportedAt").asText()));assertEquals(0,call(get(base(b)).header("Authorization",admin)).path("unacknowledgedCount").asInt());
        mvc.perform(get(base(a)).secure(true)).andExpect(status().isUnauthorized());
        mvc.perform(get(base(a)).secure(true).header("Authorization","Bearer "+a.path("deviceToken").asText())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/device/sim/report").secure(true).header("Authorization",admin).header("X-Device-Id",a.path("deviceId").asText()).contentType("application/json").content(json(new Report(snapshot(),List.of())))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/device/sim/report").secure(true).header("Authorization","Bearer "+a.path("deviceToken").asText()).header("X-Device-Id",b.path("deviceId").asText()).contentType("application/json").content(json(new Report(snapshot(),List.of())))).andExpect(status().isUnauthorized());
    }
    @Test void retriesCannotDuplicateAlertsOrRewriteAnEarlierEvent()throws Exception{
        JsonNode a=enroll();UUID id=UUID.randomUUID();Report report=new Report(snapshot(),List.of(inserted(id)));
        JsonNode receipt=call(device(post("/api/device/sim/report"),a).content(json(report)));assertEquals(id.toString(),receipt.path("acceptedEventIds").get(0).asText());report(a,report);
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM sim_alerts WHERE device_id=?",Integer.class,a.path("deviceId").asText()));
        Event changed=new Event(id,EventType.SIM_INSERTED,0,inserted(id).observedAt(),null,fingerprint,null,"1234","","Different carrier");
        mvc.perform(device(post("/api/device/sim/report"),a).secure(true).contentType("application/json").content(json(new Report(snapshot(),List.of(changed))))).andExpect(status().isConflict());
        JsonNode view=call(get(base(a)).header("Authorization",admin));assertEquals("Test carrier",view.path("alerts").get(0).path("CURRENT_CARRIER").asText());
        assertTrue(Instant.parse(view.path("alerts").get(0).path("RECEIVED_AT").asText()).isAfter(Instant.parse("2020-01-01T00:00:00Z")));assertTrue(view.path("alerts").get(0).path("OBSERVED_AT").asText().startsWith("2020-"));
        assertFalse(view.path("alerts").get(0).has("PAYLOAD_HASH"));
    }
    @Test void acknowledgmentIsDeviceBoundIdempotentAndRetainsHistory()throws Exception{
        JsonNode a=enroll(),b=enroll();UUID id=UUID.randomUUID();report(a,new Report(snapshot(),List.of(inserted(id))));
        mvc.perform(post(base(b)+"/alerts/"+id+"/ack").secure(true).header("Authorization",admin).contentType("application/json").content("{}")).andExpect(status().isNotFound());
        String path=base(a)+"/alerts/"+id+"/ack";JsonNode first=call(post(path).header("Authorization",admin).content("{}"));
        assertEquals(first.path("ACKNOWLEDGED_AT"),call(post(path).header("Authorization",admin).content("{}")).path("ACKNOWLEDGED_AT"));
        JsonNode view=call(get(base(a)).header("Authorization",admin));assertEquals(0,view.path("unacknowledgedCount").asInt());assertEquals(1,view.path("alerts").size());
        assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM audit WHERE device_id=? AND action='SIM_ALERT_ACKNOWLEDGED'",Integer.class,a.path("deviceId").asText()));
        JsonNode devices=call(get("/api/admin/devices").header("Authorization",admin));
        for(JsonNode row:devices)if(row.path("ID").asText().equals(a.path("deviceId").asText()))assertEquals(0,row.path("SIM_ALERT_COUNT").asInt());
        mvc.perform(post(path).secure(true).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post(path).secure(true).header("Authorization",admin).contentType("application/json").content("{\"enabled\":false}")).andExpect(status().isBadRequest());
    }
    @Test void malformedRawIdentifiersAndInconsistentEventsNeverBecomeAlerts()throws Exception{
        JsonNode a=enroll();ObjectNode raw=mapper.valueToTree(new Report(snapshot(),List.of()));((ObjectNode)raw.path("snapshot").path("slots").get(0)).put("iccid","89000000000000001234");
        mvc.perform(device(post("/api/device/sim/report"),a).secure(true).contentType("application/json").content(raw.toString())).andExpect(status().isBadRequest());
        Event bad=new Event(UUID.randomUUID(),EventType.SIM_REMOVED,0,Instant.now(),fingerprint,fingerprint,"1234","1234","A","A");
        mvc.perform(device(post("/api/device/sim/report"),a).secure(true).contentType("application/json").content(json(new Report(snapshot(),List.of(bad))))).andExpect(status().isBadRequest());
        Snapshot duplicate=new Snapshot(ReadState.READY,"Slots",true,Instant.now(),0,List.of(snapshot().slots().get(0),snapshot().slots().get(0)));
        mvc.perform(device(post("/api/device/sim/report"),a).secure(true).contentType("application/json").content(json(new Report(duplicate,List.of())))).andExpect(status().isBadRequest());
        report(a,new Report(new Snapshot(ReadState.PERMISSION_REQUIRED,"Permission unavailable",true,Instant.now(),0,List.of()),List.of()));
        assertEquals(0,call(get(base(a)).header("Authorization",admin)).path("alerts").size());
        call(delete("/api/admin/devices/"+a.path("deviceId").asText()).header("Authorization",admin));
        mvc.perform(device(post("/api/device/sim/report"),a).secure(true).contentType("application/json").content(json(new Report(snapshot(),List.of())))).andExpect(status().isUnauthorized());
    }
}
