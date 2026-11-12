package com.staybook.auth.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.staybook.auth.domain.SocialProvider;
import com.staybook.auth.exception.AuthErrorCode;
import com.staybook.common.exception.BaseException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class KakaoOAuthClient implements OAuthClient {

    private static final String AUTHORIZE_URL = "https://kauth.kakao.com/oauth/authorize";
    private static final String TOKEN_URL = "https://kauth.kakao.com/oauth/token";
    private static final String USER_URL = "https://kapi.kakao.com/v2/user/me";

    private final OAuthProperties.Provider config;
    private final RestClient restClient;

    public KakaoOAuthClient(OAuthProperties properties, RestClient.Builder restClientBuilder) {
        this.config = properties.kakao();
        this.restClient = restClientBuilder.build();
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.KAKAO;
    }

    @Override
    public boolean isConfigured() {
        return config != null && config.isConfigured();
    }

    @Override
    public String redirectUri() {
        return config.redirectUri();
    }

    @Override
    public String authorizationUrl(String state) {
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("response_type", "code")
                .queryParam("client_id", config.clientId())
                .queryParam("redirect_uri", config.redirectUri())
                .queryParam("state", state)
                .encode().build().toUriString();
    }

    @Override
    public OAuthUserInfo fetchUser(String code, String state) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", config.clientId());
        form.add("redirect_uri", config.redirectUri());
        form.add("code", code);
        if (config.clientSecret() != null && !config.clientSecret().isBlank()) {
            form.add("client_secret", config.clientSecret());
        }
        try {
            JsonNode token = restClient.post().uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve().body(JsonNode.class);
            String accessToken = text(token, "access_token");
            if (accessToken == null) {
                throw new BaseException(AuthErrorCode.OAUTH_PROVIDER_ERROR);
            }
            JsonNode me = restClient.get().uri(USER_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve().body(JsonNode.class);
            if (me == null || !me.hasNonNull("id")) {
                throw new BaseException(AuthErrorCode.OAUTH_PROVIDER_ERROR);
            }
            JsonNode account = me.path("kakao_account");
            return new OAuthUserInfo(me.get("id").asText(), text(account, "email"),
                    text(account.path("profile"), "nickname"));
        } catch (RestClientException e) {
            throw new BaseException(AuthErrorCode.OAUTH_PROVIDER_ERROR, e);
        }
    }

    private static String text(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
