package com.staybook.auth.domain;

import java.util.Arrays;
import java.util.Optional;

public enum SocialProvider {
    KAKAO,
    NAVER;

    /** URL 경로의 {provider} 값(대소문자 무시)을 찾는다. */
    public static Optional<SocialProvider> from(String value) {
        return Arrays.stream(values()).filter(p -> p.name().equalsIgnoreCase(value)).findFirst();
    }
}
