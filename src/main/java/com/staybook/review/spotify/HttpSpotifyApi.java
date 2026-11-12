package com.staybook.review.spotify;

import com.fasterxml.jackson.databind.JsonNode;
import com.staybook.common.exception.BaseException;
import com.staybook.review.exception.ReviewErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpSpotifyApi implements SpotifyApi {

    private static final String TOKEN_URL = "https://accounts.spotify.com/api/token";
    private static final String SEARCH_URL = "https://api.spotify.com/v1/search";

    private final SpotifyProperties properties;
    private final RestClient restClient;

    public HttpSpotifyApi(SpotifyProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public boolean isConfigured() {
        return !properties.clientId().isBlank() && !properties.clientSecret().isBlank();
    }

    @Override
    public AppToken requestAppToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        String basic = Base64.getEncoder().encodeToString(
                (properties.clientId() + ":" + properties.clientSecret()).getBytes(StandardCharsets.UTF_8));
        try {
            JsonNode response = restClient.post().uri(TOKEN_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve().body(JsonNode.class);
            if (response == null || !response.hasNonNull("access_token")) {
                throw new BaseException(ReviewErrorCode.SPOTIFY_ERROR);
            }
            return new AppToken(response.get("access_token").asText(), response.path("expires_in").asLong(3600));
        } catch (RestClientException e) {
            throw new BaseException(ReviewErrorCode.SPOTIFY_ERROR, e);
        }
    }

    @Override
    public JsonNode searchTracks(String accessToken, String query, int limit) {
        try {
            return restClient.get()
                    .uri(SEARCH_URL + "?q={q}&type=track&limit={limit}", query, limit)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve().body(JsonNode.class);
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new TokenRejectedException();
        } catch (RestClientException e) {
            throw new BaseException(ReviewErrorCode.SPOTIFY_ERROR, e);
        }
    }
}
