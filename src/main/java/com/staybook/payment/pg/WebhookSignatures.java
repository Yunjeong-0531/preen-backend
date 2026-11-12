package com.staybook.payment.pg;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** 웹훅 서명 계산 공통. */
final class WebhookSignatures {

    private static final String ALGORITHM = "HmacSHA256";

    private WebhookSignatures() {
    }

    static byte[] hmacSha256(String secret, String message) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 비교 시간으로 서명이 새지 않게 한다. */
    static boolean constantTimeEquals(byte[] expected, byte[] actual) {
        return actual != null && MessageDigest.isEqual(expected, actual);
    }
}
