package dev.scanplus.security;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static dev.scanplus.security.ApiModels.*;

@RestController
@RequestMapping("/api")
class SecurityController {
    private final SecurityStore store;
    SecurityController(SecurityStore store){this.store=store;}
    @GetMapping("/health") Map<String,String> health(){return Map.of("service","SCANPLUS UNIFIED POS SECURITY","status","up");}
    @PostMapping("/admin/enrollments") Enrollment enrollment(@Valid @RequestBody EnrollmentRequest request){return store.createEnrollment(request);}
    @PostMapping("/enroll") Identity enroll(@Valid @RequestBody EnrollRequest request){return store.enroll(request);}
    @GetMapping("/admin/devices") List<Map<String,Object>> devices(){return store.devices();}
    @PostMapping("/admin/devices/{id}/protection") Map<String,Object> protection(@PathVariable UUID id,@Valid @RequestBody ProtectionRequest request){return store.queue(id,request);}
    @GetMapping("/admin/devices/{id}/commands") List<Map<String,Object>> commands(@PathVariable UUID id){return store.commands(id);}
    @DeleteMapping("/admin/devices/{id}") void revoke(@PathVariable UUID id){store.revoke(id);}
    @GetMapping("/admin/audit") List<Map<String,Object>> audit(){return store.audit();}
    @PostMapping("/device/check-in") CheckInReply checkIn(@RequestAttribute UUID deviceId,@Valid @RequestBody CheckIn request){return store.checkIn(deviceId,request);}
    @PostMapping("/device/commands/{id}/ack") Map<String,Object> ack(@RequestAttribute UUID deviceId,@PathVariable UUID id,@Valid @RequestBody Ack request){return store.acknowledge(deviceId,id,request);}
}
