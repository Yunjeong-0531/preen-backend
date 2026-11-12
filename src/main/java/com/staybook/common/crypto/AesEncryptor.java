package com.staybook.common.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * 개인정보 암호화 (AES-256-GCM).
 * 저장 형식: "v1:" + Base64(IV 12바이트 + 암호문 + 인증 태그).
 * IV가 매번 달라 같은 평문도 암호문이 다르므로, 조회는 LookupHasher의 해시 컬럼으로 한다.
 */
@Component
public class AesEncryptor {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final String VERSION_PREFIX = "v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public AesEncryptor(CryptoProperties properties) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(properties.encryptionKey());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalStateException(
                    "staybook.crypto.encryption-key(CRYPTO_ENCRYPTION_KEY)는 Base64 문자열이어야 합니다.");
        }
        if (decoded.length != 32) {
            throw new IllegalStateException(
                    "staybook.crypto.encryption-key(CRYPTO_ENCRYPTION_KEY)는 Base64로 인코딩한 32바이트여야 합니다.");
        }
        this.key = new SecretKeySpec(decoded, "AES");
    }

    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] combined = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return VERSION_PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("암호화에 실패했습니다.", e);
        }
    }

    public String decrypt(String cipherText) {
        if (cipherText == null) {
            return null;
        }
        if (!cipherText.startsWith(VERSION_PREFIX)) {
            throw new IllegalStateException("알 수 없는 암호문 형식입니다.");
        }
        try {
            byte[] combined = Base64.getDecoder().decode(cipherText.substring(VERSION_PREFIX.length()));
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, combined, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(combined, IV_LENGTH, combined.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("복호화에 실패했습니다.", e);
        }
    }
}
