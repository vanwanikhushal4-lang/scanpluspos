package dev.scanplus.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"scanplus.admin-token=integration-admin-token-01234567890123456789","scanplus.require-tls=true","spring.datasource.url=jdbc:h2:mem:integration;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
class ApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired org.springframework.jdbc.core.JdbcTemplate db;
    final String admin="Bearer integration-admin-token-01234567890123456789";
    static final java.util.concurrent.atomic.AtomicInteger addresses=new java.util.concurrent.atomic.AtomicInteger(1);
    final String testAddress="127.0.1."+addresses.getAndIncrement();
    String json(Object value)throws Exception{return mapper.writeValueAsString(value);}
    JsonNode call(MockHttpServletRequestBuilder req)throws Exception{
        return mapper.readTree(mvc.perform(req.secure(true).with(request -> { request.setRemoteAddr(testAddress); return request; }).contentType("application/json")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    String enrollment(String serial)throws Exception{
        return call(post("/api/admin/enrollments").header("Authorization",admin).content(json(new ApiModels.EnrollmentRequest("Test",serial)))).get("code").asText();
    }
    JsonNode enroll(String serial)throws Exception{
        return call(post("/api/enroll").content(json(new ApiModels.EnrollRequest(enrollment(serial),serial,"A920","3.0.0"))));
    }
    MockHttpServletRequestBuilder device(MockHttpServletRequestBuilder req,JsonNode identity){return req.header("Authorization","Bearer "+identity.get("deviceToken").asText()).header("X-Device-Id",identity.get("deviceId").asText());}
    ApiModels.Status state(boolean enabled){return new ApiModels.Status(enabled,true,enabled,true,"Observed by agent");}
    @Test void requiresTlsAndSeparateAuthentication()throws Exception{
        mvc.perform(get("/api/admin/devices").header("Authorization",admin)).andExpect(status().isUpgradeRequired());
        mvc.perform(get("/api/admin/devices").secure(true)).andExpect(status().isUnauthorized());
        JsonNode d=enroll("auth-"+UUID.randomUUID());
        mvc.perform(get("/api/admin/devices").secure(true).header("Authorization","Bearer "+d.get("deviceToken").asText())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/device/check-in").secure(true).header("Authorization",admin).header("X-Device-Id",d.get("deviceId").asText()).contentType("application/json").content(json(new ApiModels.CheckIn("3.0.0",state(false))))).andExpect(status().isUnauthorized());
    }
    @Test void enrollmentIsSerialBoundAndSingleUse()throws Exception{
        String serial="serial-"+UUID.randomUUID(),code=enrollment(serial);
        mvc.perform(post("/api/enroll").secure(true).contentType("application/json").content(json(new ApiModels.EnrollRequest(code,"wrong","A920","3.0.0")))).andExpect(status().isUnauthorized());
        call(post("/api/enroll").content(json(new ApiModels.EnrollRequest(code,serial,"A920","3.0.0"))));
        mvc.perform(post("/api/enroll").secure(true).contentType("application/json").content(json(new ApiModels.EnrollRequest(code,serial,"A920","3.0.0")))).andExpect(status().isUnauthorized());
    }
    @Test void remoteEnableAndDisableNeedObservedAcknowledgement()throws Exception{
        JsonNode d=enroll("cycle-"+UUID.randomUUID());String id=d.get("deviceId").asText();
        for(boolean enabled:new boolean[]{true,false}){
            String requestId=UUID.randomUUID().toString();
            String body="{\"requestId\":\""+requestId+"\",\"enabled\":"+enabled+",\"ttlSeconds\":120}";
            JsonNode queued=call(post("/api/admin/devices/"+id+"/protection").header("Authorization",admin).content(body));
            assertEquals("PENDING",queued.get("STATE").asText());
            JsonNode duplicate=call(post("/api/admin/devices/"+id+"/protection").header("Authorization",admin).content(body));
            assertEquals(queued.get("ID"),duplicate.get("ID"));
            JsonNode poll=call(device(post("/api/device/check-in"),d).content(json(new ApiModels.CheckIn("3.0.0",state(!enabled)))));
            assertEquals(enabled,poll.get("command").get("enabled").asBoolean());
            String cmd=poll.get("command").get("id").asText();
            JsonNode ack=call(device(post("/api/device/commands/"+cmd+"/ack"),d).content(json(new ApiModels.Ack(true,state(enabled)))));
            assertEquals("SUCCEEDED",ack.get("STATE").asText());
            assertTrue(call(device(post("/api/device/check-in"),d).content(json(new ApiModels.CheckIn("3.0.0",state(enabled))))).get("command").isNull());
        }
        assertTrue(call(get("/api/admin/audit").header("Authorization",admin)).toString().contains("SUCCEEDED"));
    }
    @Test void aDeviceCannotAcknowledgeAnotherDevicesCommand()throws Exception{
        JsonNode a=enroll("a-"+UUID.randomUUID()),b=enroll("b-"+UUID.randomUUID());
        JsonNode q=call(post("/api/admin/devices/"+a.get("deviceId").asText()+"/protection").header("Authorization",admin).content(json(new ApiModels.ProtectionRequest(UUID.randomUUID(),true,120))));
        mvc.perform(device(post("/api/device/commands/"+q.get("ID").asText()+"/ack"),b).secure(true).contentType("application/json").content(json(new ApiModels.Ack(true,state(true))))).andExpect(status().isNotFound());
        mvc.perform(device(post("/api/device/commands/"+q.get("ID").asText()+"/ack"),a).secure(true).contentType("application/json").content(json(new ApiModels.Ack(true,state(false))))).andExpect(status().isConflict());
    }
    @Test void rejectsArbitraryCommandsAndConflictingRetries()throws Exception{
        JsonNode d=enroll("validate-"+UUID.randomUUID());String endpoint="/api/admin/devices/"+d.get("deviceId").asText()+"/protection";
        mvc.perform(post(endpoint).secure(true).header("Authorization",admin).contentType("application/json").content("{\"enabled\":true,\"command\":\"id\"}")).andExpect(status().isBadRequest());
        UUID request=UUID.randomUUID();call(post(endpoint).header("Authorization",admin).content(json(new ApiModels.ProtectionRequest(request,true,120))));
        mvc.perform(post(endpoint).secure(true).header("Authorization",admin).contentType("application/json").content(json(new ApiModels.ProtectionRequest(request,false,120)))).andExpect(status().isConflict());
    }
    @Test void expiredAndSupersededRequestsAreNotDelivered()throws Exception{
        JsonNode d=enroll("expiry-"+UUID.randomUUID());String id=d.get("deviceId").asText();
        String endpoint="/api/admin/devices/"+id+"/protection";
        JsonNode old=call(post(endpoint).header("Authorization",admin).content(json(new ApiModels.ProtectionRequest(UUID.randomUUID(),true,15))));
        JsonNode newer=call(post(endpoint).header("Authorization",admin).content(json(new ApiModels.ProtectionRequest(UUID.randomUUID(),false,15))));
        assertEquals("CANCELLED",db.queryForObject("SELECT state FROM commands WHERE id=?",String.class,old.get("ID").asText()));
        db.update("UPDATE commands SET expires_at=? WHERE id=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)),newer.get("ID").asText());
        assertTrue(call(device(post("/api/device/check-in"),d).content(json(new ApiModels.CheckIn("3.0.0",state(false))))).get("command").isNull());
        assertEquals("EXPIRED",db.queryForObject("SELECT state FROM commands WHERE id=?",String.class,newer.get("ID").asText()));
    }
    @Test void commandHistoryExpiresOfflineRequestsWithoutADeviceCheckIn()throws Exception{
        JsonNode d=enroll("offline-"+UUID.randomUUID());String id=d.get("deviceId").asText();
        JsonNode queued=call(post("/api/admin/devices/"+id+"/protection").header("Authorization",admin).content(json(new ApiModels.ProtectionRequest(UUID.randomUUID(),true,15))));
        db.update("UPDATE commands SET expires_at=? WHERE id=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)),queued.get("ID").asText());
        JsonNode rows=call(get("/api/admin/devices/"+id+"/commands").header("Authorization",admin));
        assertEquals("EXPIRED",rows.get(0).get("STATE").asText());
    }
    @Test void revokedDeviceStopsAuthenticating()throws Exception{
        JsonNode d=enroll("revoke-"+UUID.randomUUID());
        mvc.perform(delete("/api/admin/devices/"+d.get("deviceId").asText()).secure(true).header("Authorization",admin)).andExpect(status().isOk());
        mvc.perform(device(post("/api/device/check-in"),d).secure(true).contentType("application/json").content(json(new ApiModels.CheckIn("3.0.0",state(false))))).andExpect(status().isUnauthorized());
    }
}
