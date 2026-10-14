package com.apollo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Server liveness and keep-alive health check operations")
public class HealthController {

    @Operation(summary = "Check service health", description = "Lightweight liveness probe for monitoring and preventing server sleep on cloud platforms.")
    @GetMapping
    public ResponseEntity<Map<String, Object>> checkHealth() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "apollo-backend",
                "timestamp", Instant.now().toString()
        ));
    }
}
