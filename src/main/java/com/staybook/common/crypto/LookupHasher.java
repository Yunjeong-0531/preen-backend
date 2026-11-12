package com.staybook.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * 암호화 저장한 값을 찾기 위한 조회용 해시 (email_hash, phone_hash).
 * 키가 있는 HMAC-SHA256이라 DB만 유출돼서는 값을 대입해 맞춰 볼 수 없다. 결과는 64자 hex.
 */
@Component
public class LookupHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public LookupHasher(CryptoProperties properties) {
        if (properties.hashKey() == null || properties.hashKey().length() < 32) {
            throw new IllegalStateException("staybook.crypto.hash-key(CRYPTO_HASH_KEY)는 32자 이상이어야 합니다.");
        }
        this.key = new SecretKeySpec(properties.hashKey().getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    /** 앞뒤 공백을 없애고 소문자로 바꾼 뒤 해시한다. */
    public String hashEmail(String email) {
        return hmac(email.trim().toLowerCase(Locale.ROOT));
    }

    /** 숫자만 남긴 뒤 해시한다. "010-1234-5678"과 "01012345678"은 같은 해시가 된다. */
    public String hashPhone(String phone) {
        return hmac(phone.replaceAll("\\D", ""));
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("해시 계산에 실패했습니다.", e);
        }
    }
}
