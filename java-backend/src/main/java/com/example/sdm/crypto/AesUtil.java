package com.example.sdm.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256 GCM 양방향 암호화/복호화 유틸리티 클래스.
 * NFC 보안키 보관 및 기타 민감 데이터 보호용.
 */
@Component
public class AesUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int GCM_IV_LENGTH = 12; // bytes

    private final SecretKeySpec secretKeySpec;

    public AesUtil(@Value("${app.security.aes-key:}") String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            // 주입된 키가 없을 경우 임시로 테스트용 256비트 키 생성 (경고 출력)
            System.err.println("Warning: app.security.aes-key is not configured. Creating a temporary key for fallback.");
            byte[] fallbackKey = new byte[32];
            new SecureRandom().nextBytes(fallbackKey);
            this.secretKeySpec = new SecretKeySpec(fallbackKey, ALGORITHM);
        } else {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key.trim());
            if (keyBytes.length != 32) {
                throw new IllegalArgumentException("AES-256 key must be 32 bytes (256 bits) after Base64 decoding.");
            }
            this.secretKeySpec = new SecretKeySpec(keyBytes, ALGORITHM);
        }
    }

    /**
     * 주입된 기본 비밀키를 이용하여 평문을 암호화합니다.
     * 암호문 앞에 12바이트 랜덤 IV가 결합되어 Base64로 인코딩됩니다.
     */
    public String encrypt(String plainText) {
        return encrypt(plainText, this.secretKeySpec);
    }

    /**
     * 주입된 기본 비밀키를 이용하여 Base64 암호문을 복호화합니다.
     */
    public String decrypt(String encryptedText) {
        return decrypt(encryptedText, this.secretKeySpec);
    }

    /**
     * 특정 256비트 비밀키(Base64 인코딩 형태)를 직접 지정하여 평문을 암호화합니다.
     */
    public String encryptWithKey(String plainText, String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key.trim());
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("AES-256 key must be 32 bytes (256 bits) after Base64 decoding.");
        }
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
        return encrypt(plainText, keySpec);
    }

    /**
     * 특정 256비트 비밀키(Base64 인코딩 형태)를 직접 지정하여 Base64 암호문을 복호화합니다.
     */
    public String decryptWithKey(String encryptedText, String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key.trim());
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("AES-256 key must be 32 bytes (256 bits) after Base64 decoding.");
        }
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
        return decrypt(encryptedText, keySpec);
    }

    private String encrypt(String plainText, SecretKeySpec keySpec) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom.getInstanceStrong().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, parameterSpec);

            byte[] encryptedBytes = cipher.doFinal(plainText.getBytes());

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + encryptedBytes.length);
            byteBuffer.put(iv);
            byteBuffer.put(encryptedBytes);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            throw new RuntimeException("AES encryption failed", e);
        }
    }

    private String decrypt(String encryptedText, SecretKeySpec keySpec) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedText);

            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);

            byte[] encryptedBytes = new byte[byteBuffer.remaining()];
            byteBuffer.get(encryptedBytes);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, parameterSpec);

            byte[] decryptedBytes = cipher.doFinal(encryptedBytes);
            return new String(decryptedBytes);
        } catch (Exception e) {
            throw new RuntimeException("AES decryption failed", e);
        }
    }
}
