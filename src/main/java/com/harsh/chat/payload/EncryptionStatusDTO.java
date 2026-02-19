package com.harsh.chat.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EncryptionStatusDTO {
    private String roomId;
    private boolean isEncrypted;
    private String encryptionType = "AES-256-GCM";
    private String message;
}