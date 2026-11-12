package com.staybook.auth.oauth;

import com.staybook.auth.domain.SocialProvider;

/**
 * 소셜 제공자 클라이언트. 제공자의 액세스 토큰은 회원 식별 값을 얻는 데만 쓰고 저장하지 않는다.
 */
public interface OAuthClient {

    SocialProvider provider();

    /** 클라이언트 ID가 환경 변수로 설정돼 있는지. */
    boolean isConfigured();

    String redirectUri();

    /** 사용자를 보낼 제공자 로그인 주소. */
    String authorizationUrl(String state);

    /** 인가 코드를 제공자 토큰으로 바꾸고 회원 정보를 읽는다. */
    OAuthUserInfo fetchUser(String code, String state);

    /**
     * @param email 제공자가 주지 않으면 null
     * @param name  제공자가 주지 않으면 null
     */
    record OAuthUserInfo(String providerUserId, String email, String name) {
    }
}
