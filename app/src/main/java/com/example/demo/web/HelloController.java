package com.example.demo.web;

import com.example.demo.opa.OpaClient;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HelloController {

    private final OpaClient opa;

    public HelloController(OpaClient opa) {
        this.opa = opa;
    }

    @GetMapping("/hello")
    public ResponseEntity<String> hello(HttpServletRequest request) {
        // This endpoint is allowed by OPA policy (public for demo)
        if (!opa.isAllowed(request)) {
            return ResponseEntity.status(403).body("forbidden by OPA");
        }
        return ResponseEntity.ok("hello from spring-app");
    }

    @GetMapping("/secure")
    public ResponseEntity<String> secure(HttpServletRequest request, @RequestHeader(value="X-User", required=false) String user) {
        // Require X-User header as per policy
        if (!opa.isAllowed(request)) {
            return ResponseEntity.status(403).body("forbidden by OPA");
        }
        return ResponseEntity.ok("secure resource for user=" + user);
    }
}
