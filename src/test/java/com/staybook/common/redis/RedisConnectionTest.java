package com.staybook.common.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 로컬에 떠 있는 Redis(REDIS_HOST, REDIS_PORT, REDIS_PASSWORD)에 붙어 키 형식과 TTL을 확인한다.
 * 테스트가 만든 키는 테스트 전용 회원 번호를 쓰고 끝나면 지운다.
 */
@DataRedisTest
@Import(RedisConfig.class)
class RedisConnectionTest {

    /** 실제 데이터와 겹치지 않도록 음수 회원 번호를 쓴다. */
    private static final Long TEST_USER_ID = -990001L;

    @Autowired
    StringRedisTemplate redis;

    @Test
    void 키_형식은_요구사항_6장과_같다() {
        assertThat(RedisKeys.refresh(12L, "abc")).isEqualTo("refresh:12:abc");
        assertThat(RedisKeys.refreshPattern(12L)).isEqualTo("refresh:12:*");
        assertThat(RedisKeys.oauthState("xyz")).isEqualTo("oauth:state:xyz");
        assertThat(RedisKeys.SPOTIFY_APP_TOKEN).isEqualTo("spotify:app_token");
        assertThat(RedisKeys.REFRESH_TTL).isEqualTo(Duration.ofDays(14));
        assertThat(RedisKeys.OAUTH_STATE_TTL).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void 리프레시_키를_TTL과_함께_저장하고_회원_단위로_전부_지운다() {
        String first = RedisKeys.refresh(TEST_USER_ID, UUID.randomUUID().toString());
        String second = RedisKeys.refresh(TEST_USER_ID, UUID.randomUUID().toString());
        try {
            redis.opsForValue().set(first, "hash-1", RedisKeys.REFRESH_TTL);
            redis.opsForValue().set(second, "hash-2", RedisKeys.REFRESH_TTL);

            assertThat(redis.opsForValue().get(first)).isEqualTo("hash-1");
            assertThat(redis.getExpire(first)).isBetween(
                    RedisKeys.REFRESH_TTL.minusMinutes(1).toSeconds(), RedisKeys.REFRESH_TTL.toSeconds());

            Set<String> keys = redis.keys(RedisKeys.refreshPattern(TEST_USER_ID));
            assertThat(keys).containsExactlyInAnyOrder(first, second);

            redis.delete(keys);
            assertThat(redis.hasKey(first)).isFalse();
            assertThat(redis.hasKey(second)).isFalse();
        } finally {
            redis.delete(first);
            redis.delete(second);
        }
    }
}
