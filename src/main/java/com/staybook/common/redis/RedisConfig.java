package com.staybook.common.redis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 접속 정보는 spring.data.redis.* (환경 변수 REDIS_HOST, REDIS_PORT, REDIS_PASSWORD)에서 받는다.
 * 값은 문자열(JSON 포함)로만 저장하므로 StringRedisTemplate 하나만 쓴다.
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
