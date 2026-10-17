package dev.scanplus.security;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"scanplus.admin-token=app-test-admin-token-01234567890123456789","scanplus.require-tls=true","spring.datasource.url=jdbc:h2:mem:appcontrol;DB_CLOSE_DELAY=-1","scanplus.apks-directory=./build/test-apks"})
@AutoConfigureMockMvc
class AppControlIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate db;
    final String admin="Bearer app-test-admin-token-01234567890123456789";
    static final java.util.concurrent.atomic.AtomicInteger addresses=new java.util.concurrent.atomic.AtomicInteger(1);
    final String address="127.0.2."+addresses.getAndIncrement();
    String encode(Object value)throws Exception{return json.writeValueAsString(value);}
    JsonNode call(MockHttpServletRequestBuilder req)throws Exception{return json.readTree(mvc.perform(req.secure(true).with(r->{r.setRemoteAddr(address);return r;})).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder req,Object value)throws Exception{return req.contentType("application/json").content(encode(value));}
    JsonNode enroll()throws Exception{String serial=UUID.randomUUID().toString();String code=call(body(post("/api/admin/enrollments").header("Authorization",admin),new ApiModels.EnrollmentRequest("App test",serial))).get("code").asText();return call(body(post("/api/enroll"),new ApiModels.EnrollRequest(code,serial,"A920","3.1.0-scanplus")));}
    MockHttpServletRequestBuilder device(MockHttpServletRequestBuilder req,JsonNode d){return req.header("Authorization","Bearer "+d.get("deviceToken").asText()).header("X-Device-Id",d.get("deviceId").asText());}
    String path(JsonNode d){return "/api/admin/devices/"+d.get("deviceId").asText();}
    AppModels.AppInfo app(String name,boolean system,boolean critical){return new AppModels.AppInfo(name,name,"1.0",1,system,critical,true,false,false,false,true,false,1,2,List.of(new AppModels.Permission("android.permission.CAMERA",false,0)));}
    void inventory(JsonNode d)throws Exception{call(body(device(post("/api/device/apps/inventory"),d),new AppModels.Inventory(List.of(app("dev.scanplus.test",false,false),app("dev.velox.hardwaresecurity",false,true),app("com.android.example",true,false),app("android",true,true)),true)));}
    AppModels.Request request(AppModels.Action action,String pkg){return new AppModels.Request(UUID.randomUUID(),action,pkg,null,null,180);}
    JsonNode queue(JsonNode d,AppModels.Request request)throws Exception{return call(body(post(path(d)+"/app-commands").header("Authorization",admin),request));}
    @Test void inventoryIsAuthenticatedAndPersistsStructuredFields()throws Exception{
        JsonNode d=enroll();mvc.perform(post("/api/device/apps/inventory").secure(true).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());inventory(d);JsonNode data=call(get(path(d)+"/apps").header("Authorization",admin));assertEquals(4,data.get("apps").size());assertTrue(data.get("canForceStop").asBoolean());assertEquals("android.permission.CAMERA",data.get("apps").get(0).get("permissions").get(0).get("name").asText());assertNotNull(data.get("reportedAt"));
    }
    @Test void typedCommandsAreDeliveredClaimedAndAcknowledged()throws Exception{
        JsonNode d=enroll();inventory(d);JsonNode queued=queue(d,request(AppModels.Action.SUSPEND,"dev.scanplus.test"));String id=queued.get("ID").asText();
        JsonNode poll=call(body(device(post("/api/device/check-in"),d),new ApiModels.CheckIn("3.1.0",new ApiModels.Status(false,true,false,true,"Maintenance"))));assertEquals(id,poll.get("appCommand").get("id").asText());
        mvc.perform(body(device(post("/api/device/app-commands/"+id+"/ack"),d),new AppModels.Ack(true,"Done",null)).secure(true)).andExpect(status().isConflict());
        call(body(device(post("/api/device/app-commands/"+id+"/start"),d),Map.of()));JsonNode ack=call(body(device(post("/api/device/app-commands/"+id+"/ack"),d),new AppModels.Ack(true,"Suspended",app("dev.scanplus.test",false,false))));assertEquals("SUCCEEDED",ack.get("STATE").asText());assertTrue(ack.get("RESULT_JSON").asText().contains("observedApp"));
        assertEquals("SUCCEEDED",call(body(device(post("/api/device/app-commands/"+id+"/ack"),d),new AppModels.Ack(true,"Duplicate",null))).get("STATE").asText());
    }
    @Test void invalidAndProtectedTargetsCannotBeChanged()throws Exception{
        JsonNode d=enroll();inventory(d);
        for(String pkg:List.of("dev.velox.hardwaresecurity","com.android.example","android"))mvc.perform(body(post(path(d)+"/app-commands").header("Authorization",admin),request(AppModels.Action.HIDE,pkg)).secure(true)).andExpect(status().isConflict());
        mvc.perform(body(post(path(d)+"/app-commands").header("Authorization",admin),request(AppModels.Action.FORCE_STOP,"dev.test;reboot")).secure(true)).andExpect(status().isBadRequest());
        var invalid=new AppModels.Request(UUID.randomUUID(),AppModels.Action.GRANT_PERMISSION,"dev.scanplus.test","android.permission.RECORD_AUDIO",null,180);mvc.perform(body(post(path(d)+"/app-commands").header("Authorization",admin),invalid).secure(true)).andExpect(status().isBadRequest());
        mvc.perform(post(path(d)+"/app-commands").secure(true).header("Authorization",admin).contentType("application/json").content("{\"action\":\"SHELL\",\"command\":\"id\"}")).andExpect(status().isBadRequest());
    }
    @Test void requestKeysAreIdempotentAndConflictingRetriesFail()throws Exception{JsonNode d=enroll();inventory(d);var input=request(AppModels.Action.HIDE,"dev.scanplus.test");JsonNode first=queue(d,input);assertEquals(first.get("ID"),queue(d,input).get("ID"));var conflict=new AppModels.Request(input.requestId(),AppModels.Action.SHOW,input.packageName(),null,null,180);mvc.perform(body(post(path(d)+"/app-commands").header("Authorization",admin),conflict).secure(true)).andExpect(status().isConflict());}
    @Test void aDeviceCannotClaimOrAcknowledgeAnotherDevicesJobs()throws Exception{JsonNode a=enroll(),b=enroll();inventory(a);String id=queue(a,request(AppModels.Action.HIDE,"dev.scanplus.test")).get("ID").asText();mvc.perform(body(device(post("/api/device/app-commands/"+id+"/start"),b),Map.of()).secure(true)).andExpect(status().isNotFound());mvc.perform(body(device(post("/api/device/app-commands/"+id+"/ack"),b),new AppModels.Ack(true,"Forged",null)).secure(true)).andExpect(status().isNotFound());}
    @Test void expiredJobsAreNotClaimedAndLostExecutionLeasesFail()throws Exception{JsonNode d=enroll();String id=queue(d,request(AppModels.Action.REFRESH,null)).get("ID").asText();db.update("UPDATE app_commands SET expires_at=? WHERE id=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(1)),id);mvc.perform(body(device(post("/api/device/app-commands/"+id+"/start"),d),Map.of()).secure(true)).andExpect(status().isConflict());String running=queue(d,request(AppModels.Action.REFRESH,null)).get("ID").asText();call(body(device(post("/api/device/app-commands/"+running+"/start"),d),Map.of()));db.update("UPDATE app_commands SET started_at=? WHERE id=?",java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(700)),running);assertEquals("FAILED",call(get(path(d)+"/app-commands").header("Authorization",admin)).get(0).get("STATE").asText());}
    byte[] apk()throws Exception{ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream zip=new ZipOutputStream(bytes)){zip.putNextEntry(new ZipEntry("AndroidManifest.xml"));zip.write("test fixture manifest".getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();}return bytes.toByteArray();}
    @Test void uploadedApksAreChecksummedAndDownloadsAreBoundToClaimedJobs()throws Exception{
        JsonNode a=enroll(),b=enroll();byte[] bytes=apk();JsonNode artifact=call(post("/api/admin/apks").header("Authorization",admin).header("X-APK-Name","test.apk").contentType("application/vnd.android.package-archive").content(bytes));String artifactId=artifact.get("ID").asText();assertEquals(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)),artifact.get("SHA256").asText());
        var input=new AppModels.Request(UUID.randomUUID(),AppModels.Action.INSTALL,null,null,UUID.fromString(artifactId),600);
        db.update("UPDATE devices SET enabled=TRUE WHERE id=?",a.get("deviceId").asText());mvc.perform(body(post(path(a)+"/app-commands").header("Authorization",admin),input).secure(true)).andExpect(status().isConflict());db.update("UPDATE devices SET enabled=FALSE WHERE id=?",a.get("deviceId").asText());String job=queue(a,input).get("ID").asText();mvc.perform(device(get("/api/device/apks/"+artifactId),a).secure(true)).andExpect(status().isNotFound());call(body(device(post("/api/device/app-commands/"+job+"/start"),a),Map.of()));mvc.perform(device(get("/api/device/apks/"+artifactId),b).secure(true)).andExpect(status().isNotFound());mvc.perform(device(get("/api/device/apks/"+artifactId),a).secure(true)).andExpect(status().isOk()).andExpect(content().bytes(bytes));
        mvc.perform(body(post(path(a)+"/protection").header("Authorization",admin),new ApiModels.ProtectionRequest(UUID.randomUUID(),true,120)).secure(true)).andExpect(status().isConflict());
    }
    @Test void invalidArchivesAndOversizedUploadsAreRejected()throws Exception{mvc.perform(post("/api/admin/apks").secure(true).header("Authorization",admin).contentType("application/vnd.android.package-archive").content("not an apk")).andExpect(status().isBadRequest());mvc.perform(post("/api/admin/apks").secure(true).header("Authorization",admin).header("Content-Length",AppControlStore.MAX_APK+1).contentType("application/vnd.android.package-archive")).andExpect(status().isPayloadTooLarge());}
    @Test void operationBlocksDoNotPretendTheAndroidPermissionWasRevoked()throws Exception{
        JsonNode d=enroll();var sample=app("dev.scanplus.test",false,false);
        var reported=new AppModels.AppInfo(sample.packageName(),sample.label(),sample.versionName(),sample.versionCode(),false,false,true,false,false,false,true,false,1,2,List.of(new AppModels.Permission("android.permission.CAMERA",true,2,true,"APP_OPERATION")));
        call(body(device(post("/api/device/apps/inventory"),d),new AppModels.Inventory(List.of(reported),true)));
        JsonNode permission=call(get(path(d)+"/apps").header("Authorization",admin)).get("apps").get(0).get("permissions").get(0);
        assertTrue(permission.get("granted").asBoolean());assertTrue(permission.get("operationBlocked").asBoolean());assertEquals(2,permission.get("policy").asInt());
    }
}
