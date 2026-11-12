package com.staybook.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.staybook.review.spotify.SpotifyApi;
import com.staybook.review.spotify.SpotifyApi.AppToken;
import com.staybook.review.spotify.SpotifyApi.TokenRejectedException;
import com.staybook.support.IntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;

/** Spotify 트랙 검색. Spotify 서버 호출만 가짜로 바꾸고 토큰 캐시는 실제 Redis를 쓴다. */
class SpotifySearchTest extends IntegrationTestSupport {

    private static final String TOKEN_KEY = "spotify:app_token";
    private static final String SEARCH_RESPONSE = """
            {"tracks": {"items": [{
                "id": "4uLU6hMCjMI75M1A2tKUQC",
                "name": "테스트 곡",
                "duration_ms": 213000,
                "preview_url": "https://p.scdn.co/mp3-preview/should-not-be-forwarded",
                "artists": [{"name": "가수 A"}, {"name": "가수 B"}],
                "album": {"name": "앨범", "images": [{"url": "https://i.scdn.co/image/large"},
                                                      {"url": "https://i.scdn.co/image/small"}]},
                "external_urls": {"spotify": "https://open.spotify.com/track/4uLU6hMCjMI75M1A2tKUQC"}
            }]}}
            """;

    @MockBean
    SpotifyApi spotifyApi;

    @BeforeEach
    void setUp() throws Exception {
        redis.delete(TOKEN_KEY);
        when(spotifyApi.isConfigured()).thenReturn(true);
        when(spotifyApi.requestAppToken()).thenReturn(new AppToken("app-token-1", 3600));
        when(spotifyApi.searchTracks(anyString(), anyString(), anyInt()))
                .thenReturn(objectMapper.readTree(SEARCH_RESPONSE));
    }

    @Test
    void 검색_결과를_가공해_전달하고_preview_url은_넣지_않는다() throws Exception {
        Member member = createMember();

        String response = getWith("/api/spotify/tracks/search?q=테스트", member.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value("4uLU6hMCjMI75M1A2tKUQC"))
                .andExpect(jsonPath("$.data[0].name").value("테스트 곡"))
                .andExpect(jsonPath("$.data[0].artists[0]").value("가수 A"))
                .andExpect(jsonPath("$.data[0].artists[1]").value("가수 B"))
                .andExpect(jsonPath("$.data[0].albumName").value("앨범"))
                .andExpect(jsonPath("$.data[0].albumImageUrl").value("https://i.scdn.co/image/large"))
                .andExpect(jsonPath("$.data[0].durationMs").value(213000))
                .andReturn().getResponse().getContentAsString();

        assertThat(response).doesNotContain("preview").doesNotContain("mp3");
        verify(spotifyApi).searchTracks(eq("app-token-1"), eq("테스트"), eq(10));
    }

    @Test
    void 앱_토큰은_Redis에_만료_시각까지_캐시하고_다음_검색에서는_다시_받지_않는다() throws Exception {
        Member member = createMember();

        getWith("/api/spotify/tracks/search?q=first", member.token()).andExpect(status().isOk());
        getWith("/api/spotify/tracks/search?q=second", member.token()).andExpect(status().isOk());

        verify(spotifyApi, times(1)).requestAppToken();
        assertThat(redis.opsForValue().get(TOKEN_KEY)).isEqualTo("app-token-1");
        // 3600초에서 여유분 60초를 뺀 값
        assertThat(redis.getExpire(TOKEN_KEY)).isBetween(3500L, 3540L);
    }

    @Test
    void 캐시한_토큰이_거부되면_새로_받아_한_번만_다시_시도한다() throws Exception {
        Member member = createMember();
        redis.opsForValue().set(TOKEN_KEY, "stale-token");
        when(spotifyApi.searchTracks(eq("stale-token"), anyString(), anyInt()))
                .thenThrow(new TokenRejectedException());

        getWith("/api/spotify/tracks/search?q=retry", member.token())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        verify(spotifyApi, times(1)).requestAppToken();
        assertThat(redis.opsForValue().get(TOKEN_KEY)).isEqualTo("app-token-1");
    }

    @Test
    void 로그인이_필요하고_검색어가_비면_거부하며_키가_없으면_503이다() throws Exception {
        Member member = createMember();

        mockMvc.perform(get("/api/spotify/tracks/search").param("q", "anything"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(withToken(get("/api/spotify/tracks/search"), member.token()).param("q", " "))
                .andExpect(status().isBadRequest());
        getWith("/api/spotify/tracks/search", member.token()).andExpect(status().isBadRequest());

        when(spotifyApi.isConfigured()).thenReturn(false);
        getWith("/api/spotify/tracks/search?q=anything", member.token())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("REVIEW-007"));
    }
}
