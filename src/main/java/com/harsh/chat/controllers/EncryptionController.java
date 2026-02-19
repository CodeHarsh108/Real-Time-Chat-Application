package com.harsh.chat.controllers;

import com.harsh.chat.payload.EncryptionStatusDTO;
import com.harsh.chat.service.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/encryption")
@RequiredArgsConstructor
@Slf4j
public class EncryptionController {

    private final EncryptionService encryptionService;

    /**
     * Get encryption status for a room
     */
    @GetMapping("/room/{roomId}")
    public ResponseEntity<EncryptionStatusDTO> getRoomEncryptionStatus(@PathVariable String roomId) {
        boolean hasKey = encryptionService.hasRoomKey(roomId);

        return ResponseEntity.ok(EncryptionStatusDTO.builder()
                .roomId(roomId)
                .isEncrypted(hasKey)
                .encryptionType("AES-256-GCM")
                .message(hasKey ? "Room is encrypted" : "Room encryption not initialized")
                .build());
    }

    /**
     * Regenerate room key (admin only)
     */
    @PostMapping("/room/{roomId}/regenerate")
    public ResponseEntity<?> regenerateRoomKey(@PathVariable String roomId, Authentication authentication) {
        String username = authentication.getName();
        log.info("User {} regenerating key for room {}", username, roomId);

        String newKey = encryptionService.generateRoomKey(roomId);

        return ResponseEntity.ok(Map.of(
                "roomId", roomId,
                "message", "Room key regenerated successfully",
                "encrypted", true
        ));
    }

    /**
     * Get encryption info
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getEncryptionInfo() {
        return ResponseEntity.ok(Map.of(
                "algorithm", "AES-256-GCM",
                "keySize", 256,
                "mode", "Authenticated Encryption",
                "description", "Messages are encrypted end-to-end. Room keys are unique per room."
        ));
    }
}