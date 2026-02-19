package com.harsh.chat.payload;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EncryptedMessageDTO {
    private String roomId;
    private String sender;
    private String encryptedContent;
    private String iv;
    private long timestamp;
}