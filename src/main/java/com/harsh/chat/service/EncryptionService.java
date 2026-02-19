package com.harsh.chat.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EncryptionService {

    private static final Logger log = LoggerFactory.getLogger(EncryptionService.class);

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    // Store room keys in memory
    private final ConcurrentHashMap<String, SecretKey> roomKeys = new ConcurrentHashMap<>();

    @Value("${encryption.master-key:MySuperSecretMasterKeyForEncryption2026!}")
    private String masterKeySecret;

    private SecretKey masterKey;

    // 🔥 FIX 2: Default constructor (don't log here, masterKeySecret not injected yet)
    public EncryptionService() {
        // Don't log here - dependencies not injected yet
    }

    @PostConstruct
    public void init() {
        try {
            log.info("Initializing encryption with master key");

            // 🔥 FIX 3: Ensure masterKeySecret is not null
            if (masterKeySecret == null || masterKeySecret.isEmpty()) {
                masterKeySecret = "MySuperSecretMasterKeyForEncryption2026!";
                log.warn("Master key not configured, using default key");
            }

            byte[] keyBytes = masterKeySecret.getBytes();
            // Use first 32 bytes for AES-256
            byte[] aesKey = new byte[32];
            System.arraycopy(keyBytes, 0, aesKey, 0, Math.min(keyBytes.length, 32));
            this.masterKey = new SecretKeySpec(aesKey, "AES");

            log.info("Encryption initialized successfully");

        } catch (Exception e) {
            log.error("Failed to initialize encryption: {}", e.getMessage());
            throw new RuntimeException("Failed to initialize encryption", e);
        }
    }

    /**
     * Generate a new room key
     */
    public String generateRoomKey(String roomId) {
        try {
            log.debug("Generating room key for: {}", roomId);
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            SecretKey roomKey = keyGen.generateKey();
            roomKeys.put(roomId, roomKey);

            // Encrypt room key with master key for storage
            String encryptedKey = encryptKey(roomKey);
            log.debug("Room key generated for: {}", roomId);
            return encryptedKey;

        } catch (Exception e) {
            log.error("Failed to generate room key: {}", e.getMessage());
            throw new RuntimeException("Failed to generate room key", e);
        }
    }

    /**
     * Get or create room key
     */
    private SecretKey getRoomKey(String roomId) {
        SecretKey key = roomKeys.get(roomId);
        if (key == null) {
            log.debug("Room key not found for {}, generating new one", roomId);
            key = generateAndStoreRoomKey(roomId);
            roomKeys.put(roomId, key);
        }
        return key;
    }

    /**
     * Generate and store room key
     */
    private SecretKey generateAndStoreRoomKey(String roomId) {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256);
            SecretKey roomKey = keyGen.generateKey();
            return roomKey;
        } catch (Exception e) {
            log.error("Failed to generate room key: {}", e.getMessage());
            throw new RuntimeException("Failed to generate room key", e);
        }
    }

    /**
     * Encrypt a message for a specific room
     * Returns: base64(iv) + ":" + base64(encryptedData)
     */
    public String encryptMessage(String message, String roomId) {
        try {
            SecretKey roomKey = getRoomKey(roomId);

            // Generate random IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, roomKey, spec);

            // Encrypt
            byte[] encryptedData = cipher.doFinal(message.getBytes("UTF-8"));

            // Combine IV and encrypted data
            String ivBase64 = Base64.getEncoder().encodeToString(iv);
            String dataBase64 = Base64.getEncoder().encodeToString(encryptedData);

            return ivBase64 + ":" + dataBase64;

        } catch (Exception e) {
            log.error("Encryption failed: {}", e.getMessage());
            throw new RuntimeException("Encryption failed", e);
        }
    }


    /**
     * Decrypt a message for a specific room
     */
    public String decryptMessage(String encryptedMessage, String roomId) {
        try {
            log.debug("Decrypting message for room: {}, data length: {}", roomId, encryptedMessage.length());

            SecretKey roomKey = getRoomKey(roomId);
            if (roomKey == null) {
                log.error("No room key found for room: {}", roomId);
                throw new RuntimeException("No room key found for room: " + roomId);
            }

            // Split IV and encrypted data
            String[] parts = encryptedMessage.split(":");
            if (parts.length != 2) {
                log.error("Invalid encrypted message format: expected 2 parts, got {}", parts.length);
                throw new IllegalArgumentException("Invalid encrypted message format");
            }

            log.debug("IV length: {}, encrypted data length: {}", parts[0].length(), parts[1].length());

            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] encryptedData = Base64.getDecoder().decode(parts[1]);

            log.debug("IV bytes: {}, encrypted bytes: {}", iv.length, encryptedData.length);

            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, roomKey, spec);

            // Decrypt
            byte[] decryptedData = cipher.doFinal(encryptedData);
            String result = new String(decryptedData, "UTF-8");

            log.debug("Successfully decrypted message, length: {}", result.length());

            return result;

        } catch (Exception e) {
            log.error("Decryption failed for room {}: {}", roomId, e.getMessage(), e);
            throw new RuntimeException("Decryption failed", e);
        }
    }


    /**
     * Encrypt a room key with master key (for storage)
     */
    private String encryptKey(SecretKey key) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, masterKey, spec);

            byte[] encryptedKey = cipher.doFinal(key.getEncoded());

            String ivBase64 = Base64.getEncoder().encodeToString(iv);
            String keyBase64 = Base64.getEncoder().encodeToString(encryptedKey);

            return ivBase64 + ":" + keyBase64;

        } catch (Exception e) {
            log.error("Failed to encrypt room key: {}", e.getMessage());
            throw new RuntimeException("Failed to encrypt room key", e);
        }
    }

    /**
     * Decrypt a room key (for loading from storage)
     */
    private SecretKey decryptKey(String encryptedKey) {
        try {
            String[] parts = encryptedKey.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid encrypted key format");
            }

            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] encryptedKeyData = Base64.getDecoder().decode(parts[1]);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, masterKey, spec);

            byte[] keyBytes = cipher.doFinal(encryptedKeyData);
            return new SecretKeySpec(keyBytes, "AES");

        } catch (Exception e) {
            log.error("Failed to decrypt room key: {}", e.getMessage());
            throw new RuntimeException("Failed to decrypt room key", e);
        }
    }

    /**
     * Check if room has encryption key
     */
    public boolean hasRoomKey(String roomId) {
        return roomKeys.containsKey(roomId);
    }

    /**
     * Remove room key (when room is deleted)
     */
    public void removeRoomKey(String roomId) {
        roomKeys.remove(roomId);
        log.debug("Removed room key for: {}", roomId);
    }
}