package com.staybook.common.redis;

import java.time.Duration;

/**
 * Redis 키 prefix와 TTL. 키 형식은 요구사항 6장을 따른다.
 */
public final class RedisKeys {

    public static final Duration REFRESH_TTL = Duration.ofDays(14);
    public static final Duration OAUTH_STATE_TTL = Duration.ofMinutes(5);

    /** Spotify 앱 액세스 토큰. TTL은 토큰 만료 시각까지. */
    public static final String SPOTIFY_APP_TOKEN = "spotify:app_token";

    private static final String REFRESH_PREFIX = "refresh:";
    private static final String OAUTH_STATE_PREFIX = "oauth:state:";

    private RedisKeys() {
    }

    /** refresh:{userId}:{tokenId} */
    public static String refresh(Long userId, String tokenId) {
        return REFRESH_PREFIX + userId + ":" + tokenId;
    }

    /** 한 회원의 리프레시 키 전체를 찾는 패턴. 비밀번호 변경·탈퇴 시 전부 지울 때 쓴다. */
    public static String refreshPattern(Long userId) {
        return REFRESH_PREFIX + userId + ":*";
    }

    /** oauth:state:{state} */
    public static String oauthState(String state) {
        return OAUTH_STATE_PREFIX + state;
    }
}
