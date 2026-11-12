package com.staybook.review.spotify;

import com.fasterxml.jackson.databind.JsonNode;

/** Spotify Web API 호출 지점. HTTP 통신만 맡고, 토큰 캐시와 응답 가공은 SpotifyService가 한다. */
public interface SpotifyApi {

    /** 클라이언트 ID·시크릿이 환경 변수로 설정돼 있는지. */
    boolean isConfigured();

    /** Client Credentials 방식으로 앱 액세스 토큰을 받는다. */
    AppToken requestAppToken();

    /**
     * 트랙 검색. Spotify의 응답 본문을 그대로 돌려준다.
     *
     * @throws TokenRejectedException 토큰이 만료됐거나 거부된 경우(401)
     */
    JsonNode searchTracks(String accessToken, String query, int limit);

    record AppToken(String accessToken, long expiresInSeconds) {
    }

    class TokenRejectedException extends RuntimeException {
    }
}
