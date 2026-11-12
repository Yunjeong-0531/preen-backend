package com.staybook.common.crypto;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param encryptionKey Base64로 인코딩한 32바이트 AES 키
 * @param hashKey       email_hash, phone_hash용 HMAC 키
 */
@ConfigurationProperties(prefix = "staybook.crypto")
public record CryptoProperties(String encryptionKey, String hashKey) {
}
