package com.staybook.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * 액세스·리프레시 토큰 발급과 검증.
 * 리프레시 토큰의 저장·폐기(Redis)는 auth feature가 맡고, 여기서는 서명과 만료만 다룬다.
 */
@Component
public class JwtProvider {

    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;
    private final Clock clock;

    public JwtProvider(JwtProperties properties, Clock clock) {
        byte[] secret = properties.secret() == null
                ? new byte[0]
                : properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("staybook.jwt.secret(JWT_SECRET)은 32바이트 이상이어야 합니다.");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.accessTokenTtl = properties.accessTokenTtl();
        this.refreshTokenTtl = properties.refreshTokenTtl();
        this.clock = clock;
    }

    public String createAccessToken(Long userId, UserRole role) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_ROLE, role.name())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenTtl)))
                .signWith(key)
                .compact();
    }

    /** tokenId는 Redis 키 {@code refresh:{userId}:{tokenId}}의 tokenId와 같다. */
    public String createRefreshToken(Long userId, String tokenId) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .id(tokenId)
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(refreshTokenTtl)))
                .signWith(key)
                .compact();
    }

    /** 서명·만료·종류가 유효한 액세스 토큰이면 로그인 사용자를 돌려준다. */
    public Optional<AuthUser> parseAccessToken(String token) {
        return parse(token, TYPE_ACCESS).map(claims -> new AuthUser(
                Long.valueOf(claims.getSubject()),
                UserRole.valueOf(claims.get(CLAIM_ROLE, String.class))
        ));
    }

    public Optional<RefreshTokenClaims> parseRefreshToken(String token) {
        return parse(token, TYPE_REFRESH)
                .filter(claims -> claims.getId() != null)
                .map(claims -> new RefreshTokenClaims(Long.valueOf(claims.getSubject()), claims.getId()));
    }

    public Duration accessTokenTtl() {
        return accessTokenTtl;
    }

    public Duration refreshTokenTtl() {
        return refreshTokenTtl;
    }

    private Optional<Claims> parse(String token, String expectedType) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!expectedType.equals(claims.get(CLAIM_TYPE, String.class))) {
                return Optional.empty();
            }
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public record RefreshTokenClaims(Long userId, String tokenId) {
    }
}
