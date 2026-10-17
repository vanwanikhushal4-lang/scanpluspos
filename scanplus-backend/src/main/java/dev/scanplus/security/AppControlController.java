package dev.scanplus.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import static dev.scanplus.security.AppModels.*;

@RestController
@RequestMapping("/api")
class AppControlController {
    private final AppControlStore store;
    AppControlController(AppControlStore store){this.store=store;}
    @GetMapping("/admin/devices/{id}/apps") Map<String,Object> apps(@PathVariable UUID id){return store.inventory(id);}
    @GetMapping("/admin/devices/{id}/app-commands") List<Map<String,Object>> history(@PathVariable UUID id){return store.commands(id);}
    @PostMapping("/admin/devices/{id}/app-commands") Map<String,Object> queue(@PathVariable UUID id,@Valid @RequestBody Request input){return store.queue(id,input);}
    @PostMapping(value="/admin/apks",consumes="application/vnd.android.package-archive") Map<String,Object> upload(HttpServletRequest request,@RequestHeader(value="X-APK-Name",required=false) String filename)throws Exception{return store.upload(request.getInputStream(),filename);}
    @PostMapping("/device/apps/inventory") Map<String,Object> report(@RequestAttribute UUID deviceId,@Valid @RequestBody Inventory input){store.report(deviceId,input);return Map.of("ok",true);}
    @PostMapping("/device/app-commands/{id}/start") Map<String,Object> start(@RequestAttribute UUID deviceId,@PathVariable UUID id){return store.start(deviceId,id);}
    @PostMapping("/device/app-commands/{id}/ack") Map<String,Object> ack(@RequestAttribute UUID deviceId,@PathVariable UUID id,@Valid @RequestBody Ack ack){return store.ack(deviceId,id,ack);}
    @GetMapping("/device/apks/{id}") ResponseEntity<FileSystemResource> download(@RequestAttribute UUID deviceId,@PathVariable UUID id)throws Exception{var path=store.download(deviceId,id);return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.android.package-archive")).contentLength(java.nio.file.Files.size(path)).body(new FileSystemResource(path));}
}
