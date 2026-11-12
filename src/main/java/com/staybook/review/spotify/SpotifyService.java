package com.staybook.review.spotify;

import com.fasterxml.jackson.databind.JsonNode;
import com.staybook.common.exception.BaseException;
import com.staybook.common.redis.RedisKeys;
import com.staybook.review.exception.ReviewErrorCode;
import com.staybook.review.spotify.SpotifyApi.AppToken;
import com.staybook.review.spotify.SpotifyApi.TokenRejectedException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * 트랙 검색. Spotify 응답을 가공해 전달만 하고 DB에는 아무것도 저장하지 않는다.
 * 앱 액세스 토큰은 Redis(spotify:app_token)에 토큰 만료 시각까지 캐시한다.
 */
@Service
@RequiredArgsConstructor
public class SpotifyService {

    private static final int SEARCH_LIMIT = 10;
    /** 만료 직전의 토큰을 쓰다가 거부되지 않도록 조금 일찍 버린다. */
    private static final long EXPIRY_MARGIN_SECONDS = 60;

    private final SpotifyApi spotifyApi;
    private final StringRedisTemplate redis;

    public List<Track> searchTracks(String query) {
        if (!spotifyApi.isConfigured()) {
            throw new BaseException(ReviewErrorCode.SPOTIFY_NOT_CONFIGURED);
        }
        JsonNode response;
        try {
            response = spotifyApi.searchTracks(appToken(), query, SEARCH_LIMIT);
        } catch (TokenRejectedException e) {
            // 캐시한 토큰이 거부되면 버리고 새로 받아 한 번만 다시 시도한다
            redis.delete(RedisKeys.SPOTIFY_APP_TOKEN);
            try {
                response = spotifyApi.searchTracks(appToken(), query, SEARCH_LIMIT);
            } catch (TokenRejectedException again) {
                throw new BaseException(ReviewErrorCode.SPOTIFY_ERROR);
            }
        }
        List<Track> tracks = new ArrayList<>();
        if (response != null) {
            response.path("tracks").path("items").forEach(item -> tracks.add(toTrack(item)));
        }
        return tracks;
    }

    private String appToken() {
        String cached = redis.opsForValue().get(RedisKeys.SPOTIFY_APP_TOKEN);
        if (cached != null) {
            return cached;
        }
        AppToken token = spotifyApi.requestAppToken();
        long ttl = Math.max(token.expiresInSeconds() - EXPIRY_MARGIN_SECONDS, 1);
        redis.opsForValue().set(RedisKeys.SPOTIFY_APP_TOKEN, token.accessToken(), Duration.ofSeconds(ttl));
        return token.accessToken();
    }

    /** preview_url은 쓰지 않는다. 재생은 프론트의 공식 임베드 플레이어가 맡는다. */
    private static Track toTrack(JsonNode item) {
        List<String> artists = new ArrayList<>();
        item.path("artists").forEach(artist -> artists.add(artist.path("name").asText()));
        JsonNode images = item.path("album").path("images");
        return new Track(
                item.path("id").asText(),
                item.path("name").asText(),
                artists,
                item.path("album").path("name").asText(null),
                images.isArray() && !images.isEmpty() ? images.get(0).path("url").asText(null) : null,
                item.path("duration_ms").asLong(),
                item.path("external_urls").path("spotify").asText(null));
    }

    /**
     * 검색 결과의 한 곡. 사용자가 고르면 프론트는 id만 리뷰 작성 요청에 담는다.
     */
    public record Track(String id, String name, List<String> artists, String albumName, String albumImageUrl,
                        long durationMs, String spotifyUrl) {
    }
}
