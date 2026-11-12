package com.staybook.auth.oauth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 소셜 로그인 키. 코드나 저장소에 넣지 않고 환경 변수로 받는다. */
@ConfigurationProperties(prefix = "staybook.oauth")
public record OAuthProperties(Provider kakao, Provider naver) {

    public record Provider(String clientId, String clientSecret, String redirectUri) {

        public boolean isConfigured() {
            return clientId != null && !clientId.isBlank();
        }
    }
}
