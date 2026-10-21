package dev.scanplus.security;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;
import static dev.scanplus.security.WifiModels.*;

@RestController
@RequestMapping("/api")
class WifiController {
    private final WifiStore store;
    WifiController(WifiStore store) { this.store=store; }
    @GetMapping("/admin/devices/{id}/wifi") Map<String,Object> view(@PathVariable UUID id) { return store.view(id); }
    @PostMapping("/admin/devices/{id}/wifi/scan") Map<String,Object> scan(@PathVariable UUID id,@Valid @RequestBody ScanRequest request) { return store.scan(id,request); }
    @PostMapping("/admin/devices/{id}/wifi/policy") Map<String,Object> policy(@PathVariable UUID id,@Valid @RequestBody PolicyRequest request) { return store.policy(id,request); }
    @PostMapping("/admin/devices/{id}/wifi/guard") Map<String,Object> guard(@PathVariable UUID id,@Valid @RequestBody PolicyRequest request) { return store.guard(id,request); }
    @PostMapping("/device/wifi/report") Map<String,Boolean> report(@RequestAttribute UUID deviceId,@Valid @RequestBody Snapshot snapshot) { store.report(deviceId,snapshot); return Map.of("accepted",true); }
    @PostMapping("/device/wifi/commands/{id}/ack") Map<String,Object> ack(@RequestAttribute UUID deviceId,@PathVariable UUID id,@Valid @RequestBody Ack ack) { return store.acknowledge(deviceId,id,ack); }
}
