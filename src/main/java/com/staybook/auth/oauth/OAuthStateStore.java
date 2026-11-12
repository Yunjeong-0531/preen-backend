package com.staybook.auth.oauth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staybook.auth.domain.SocialProvider;
import com.staybook.common.redis.RedisKeys;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 소셜 로그인 CSRF 방지용 state. 키: oauth:state:{state}, 값: 리다이렉트 정보(JSON), TTL 5분.
 */
@Component
@RequiredArgsConstructor
public class OAuthStateStore {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public void save(String state, StateInfo info) {
        try {
            redis.opsForValue().set(RedisKeys.oauthState(state), objectMapper.writeValueAsString(info),
                    RedisKeys.OAUTH_STATE_TTL);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 한 번만 쓸 수 있도록 읽으면서 지운다. */
    public Optional<StateInfo> consume(String state) {
        String value = redis.opsForValue().getAndDelete(RedisKeys.oauthState(state));
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, StateInfo.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    public record StateInfo(SocialProvider provider, String redirectUri) {
    }
}
