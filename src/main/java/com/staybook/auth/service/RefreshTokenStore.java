package com.staybook.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.staybook.common.redis.RedisKeys;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 리프레시 토큰 저장소. Redis에만 저장하며 DB 테이블은 없다.
 * 키: refresh:{userId}:{tokenId}, 값: 토큰 해시·user agent·IP (JSON), TTL 14일.
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public void save(Long userId, String tokenId, Entry entry, Duration ttl) {
        try {
            redis.opsForValue().set(RedisKeys.refresh(userId, tokenId), objectMapper.writeValueAsString(entry), ttl);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public Optional<Entry> find(Long userId, String tokenId) {
        String value = redis.opsForValue().get(RedisKeys.refresh(userId, tokenId));
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, Entry.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    public void delete(Long userId, String tokenId) {
        redis.delete(RedisKeys.refresh(userId, tokenId));
    }

    /** 그 회원의 리프레시 키 전부 삭제 (비밀번호 변경, 탈퇴). */
    public void deleteAll(Long userId) {
        List<String> keys = new ArrayList<>();
        ScanOptions options = ScanOptions.scanOptions().match(RedisKeys.refreshPattern(userId)).count(200).build();
        try (Cursor<String> cursor = redis.scan(options)) {
            cursor.forEachRemaining(keys::add);
        }
        if (!keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    public record Entry(String tokenHash, String userAgent, String ip) {
    }
}
