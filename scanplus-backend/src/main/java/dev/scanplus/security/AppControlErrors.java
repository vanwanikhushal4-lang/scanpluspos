package dev.scanplus.security;

import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import java.util.Map;

@RestControllerAdvice(assignableTypes=AppControlController.class)
class AppControlErrors {
    @ExceptionHandler(ResponseStatusException.class) ResponseEntity<Map<String,String>> status(ResponseStatusException error){return ResponseEntity.status(error.getStatusCode()).body(Map.of("error",error.getReason()==null?"App request was not accepted":error.getReason()));}
    @ExceptionHandler(MethodArgumentNotValidException.class) ResponseEntity<Map<String,String>> invalid(){return ResponseEntity.badRequest().body(Map.of("error","Invalid app control request"));}
}
