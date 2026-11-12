package com.staybook.review.spotify;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Spotify 키. 코드나 저장소에 넣지 않고 환경 변수(SPOTIFY_CLIENT_ID, SPOTIFY_CLIENT_SECRET)로 받는다. */
@ConfigurationProperties(prefix = "staybook.spotify")
public record SpotifyProperties(@DefaultValue("") String clientId, @DefaultValue("") String clientSecret) {
}
