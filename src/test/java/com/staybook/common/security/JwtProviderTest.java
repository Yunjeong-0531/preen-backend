package com.staybook.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-0123456789";
    private static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");

    private JwtProvider providerAt(Instant instant) {
        return providerAt(instant, SECRET);
    }

    private JwtProvider providerAt(Instant instant, String secret) {
        JwtProperties properties = new JwtProperties(secret, Duration.ofMinutes(30), Duration.ofDays(14));
        return new JwtProvider(properties, Clock.fixed(instant, ZoneOffset.UTC));
    }

    @Test
    void 액세스_토큰에서_회원과_역할을_꺼낸다() {
        JwtProvider provider = providerAt(NOW);

        String token = provider.createAccessToken(42L, UserRole.HOST);

        assertThat(provider.parseAccessToken(token)).contains(new AuthUser(42L, UserRole.HOST));
    }

    @Test
    void 만료된_액세스_토큰은_거부한다() {
        String token = providerAt(NOW).createAccessToken(42L, UserRole.GUEST);

        assertThat(providerAt(NOW.plus(Duration.ofMinutes(29))).parseAccessToken(token)).isPresent();
        assertThat(providerAt(NOW.plus(Duration.ofMinutes(31))).parseAccessToken(token)).isEmpty();
    }

    @Test
    void 다른_키로_서명했거나_변조된_토큰은_거부한다() {
        JwtProvider provider = providerAt(NOW);
        String token = provider.createAccessToken(42L, UserRole.GUEST);
        String otherKeyToken = providerAt(NOW, "another-secret-another-secret-0123456789")
                .createAccessToken(42L, UserRole.ADMIN);

        assertThat(provider.parseAccessToken(otherKeyToken)).isEmpty();
        assertThat(provider.parseAccessToken(token.substring(0, token.length() - 2) + "xx")).isEmpty();
        assertThat(provider.parseAccessToken("not-a-token")).isEmpty();
        assertThat(provider.parseAccessToken(null)).isEmpty();
    }

    @Test
    void 리프레시_토큰은_액세스_토큰으로_쓸_수_없고_반대도_마찬가지다() {
        JwtProvider provider = providerAt(NOW);
        String access = provider.createAccessToken(42L, UserRole.GUEST);
        String refresh = provider.createRefreshToken(42L, "token-id-1");

        assertThat(provider.parseAccessToken(refresh)).isEmpty();
        assertThat(provider.parseRefreshToken(access)).isEmpty();
        assertThat(provider.parseRefreshToken(refresh))
                .contains(new JwtProvider.RefreshTokenClaims(42L, "token-id-1"));
    }

    @Test
    void 리프레시_토큰은_14일_뒤에_만료된다() {
        String refresh = providerAt(NOW).createRefreshToken(42L, "token-id-1");

        assertThat(providerAt(NOW.plus(Duration.ofDays(13))).parseRefreshToken(refresh)).isPresent();
        assertThat(providerAt(NOW.plus(Duration.ofDays(15))).parseRefreshToken(refresh)).isEmpty();
    }

    @Test
    void 비밀키가_32바이트보다_짧으면_시작하지_않는다() {
        assertThatThrownBy(() -> providerAt(NOW, "short"))
                .isInstanceOf(IllegalStateException.class);
    }
}
