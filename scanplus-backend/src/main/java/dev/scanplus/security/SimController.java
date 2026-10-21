package dev.scanplus.security;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static dev.scanplus.security.SimModels.*;

@RestController
@RequestMapping("/api")
class SimController {
    private final SimStore store;
    SimController(SimStore store) { this.store=store; }
    @GetMapping("/admin/devices/{id}/sim") Map<String,Object> view(@PathVariable UUID id) { return store.view(id); }
    @PostMapping("/admin/devices/{id}/sim/alerts/{event}/ack") Map<String,Object> acknowledge(@PathVariable UUID id,@PathVariable UUID event,@RequestBody Map<String,Object> input) {
        if(input==null||!input.isEmpty())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unexpected SIM acknowledgment fields");
        return store.acknowledge(id,event);
    }
    @PostMapping("/device/sim/report") Receipt report(@RequestAttribute UUID deviceId,@Valid @RequestBody Report report) { return store.report(deviceId,report); }
}
