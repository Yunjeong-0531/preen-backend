package com.staybook.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 인증코드, 재설정 토큰, 리프레시 토큰처럼 해시로만 저장하는 값에 쓴다.
 * (비밀번호는 PasswordEncoder를 쓴다.)
 */
public final class HashUtils {

    private static final SecureRandom RANDOM = new SecureRandom();

    private HashUtils() {
    }

    /** SHA-256, 64자 hex. */
    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 원문을 해시해 저장된 해시와 비교한다. 비교 시간으로 값이 새지 않게 한다. */
    public static boolean matches(String rawValue, String storedHash) {
        if (rawValue == null || storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                sha256Hex(rawValue).getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }

    /** 추측하기 어려운 URL-safe 무작위 문자열. byteLength 32면 43자. */
    public static String randomToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 숫자로만 된 인증코드. */
    public static String randomNumericCode(int digits) {
        StringBuilder sb = new StringBuilder(digits);
        for (int i = 0; i < digits; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}
