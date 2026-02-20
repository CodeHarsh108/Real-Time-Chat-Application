package com.harsh.chat.controllers;

import com.harsh.chat.service.EncryptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final MongoTemplate mongoTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final EncryptionService encryptionService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("version", "1.0.0");

        // Check MongoDB
        try {
            mongoTemplate.executeCommand("{ ping: 1 }");
            response.put("mongodb", "connected");
        } catch (Exception e) {
            response.put("mongodb", "disconnected: " + e.getMessage());
        }

        // Check Redis
        try {
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            response.put("redis", pong.equals("PONG") ? "connected" : "error");
        } catch (Exception e) {
            response.put("redis", "disconnected: " + e.getMessage());
        }

        // Check Encryption
        try {
            response.put("encryption", Map.of(
                    "enabled", encryptionService.isEnabled(),
                    "algorithm", "AES-256-GCM"
            ));
        } catch (Exception e) {
            response.put("encryption", "error: " + e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/ping")
    public ResponseEntity<Map<String, String>> ping() {
        return ResponseEntity.ok(Map.of(
                "ping", "pong",
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}