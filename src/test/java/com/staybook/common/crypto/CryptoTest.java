package com.staybook.common.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class CryptoTest {

    private static final String ENCRYPTION_KEY = Base64.getEncoder()
            .encodeToString("unit-test-encryption-key-32byte!".getBytes(StandardCharsets.UTF_8));
    private static final CryptoProperties PROPERTIES =
            new CryptoProperties(ENCRYPTION_KEY, "unit-test-hash-key-unit-test-hash-key");

    private final AesEncryptor encryptor = new AesEncryptor(PROPERTIES);
    private final LookupHasher hasher = new LookupHasher(PROPERTIES);

    @Test
    void 암호화한_값은_원문과_다르고_복호화하면_원문이_된다() {
        String encrypted = encryptor.encrypt("guest@example.com");

        assertThat(encrypted).doesNotContain("guest@example.com");
        assertThat(encryptor.decrypt(encrypted)).isEqualTo("guest@example.com");
    }

    @Test
    void 같은_값을_암호화해도_암호문은_매번_다르다() {
        assertThat(encryptor.encrypt("010-1234-5678")).isNotEqualTo(encryptor.encrypt("010-1234-5678"));
    }

    @Test
    void null은_그대로_null이다() {
        assertThat(encryptor.encrypt(null)).isNull();
        assertThat(encryptor.decrypt(null)).isNull();
    }

    @Test
    void 변조된_암호문과_다른_키의_암호문은_복호화하지_않는다() {
        String encrypted = encryptor.encrypt("guest@example.com");
        String tampered = encrypted.substring(0, encrypted.length() - 4) + "AAAA";
        AesEncryptor other = new AesEncryptor(new CryptoProperties(
                Base64.getEncoder().encodeToString("another-encryption-key-32-bytes!".getBytes(StandardCharsets.UTF_8)),
                "unit-test-hash-key-unit-test-hash-key"));

        assertThatThrownBy(() -> encryptor.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> other.decrypt(encrypted)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> encryptor.decrypt("plain-text")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 키_길이가_32바이트가_아니면_시작하지_않는다() {
        String shortKey = Base64.getEncoder().encodeToString("short".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new AesEncryptor(new CryptoProperties(shortKey, "x")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 컨버터는_DB에는_암호문을_엔티티에는_원문을_준다() {
        EncryptedStringConverter converter = new EncryptedStringConverter(encryptor);

        String column = converter.convertToDatabaseColumn("01012345678");

        assertThat(column).startsWith("v1:").doesNotContain("01012345678");
        assertThat(converter.convertToEntityAttribute(column)).isEqualTo("01012345678");
    }

    @Test
    void 이메일_해시는_대소문자와_공백을_무시한다() {
        assertThat(hasher.hashEmail("  Guest@Example.com "))
                .isEqualTo(hasher.hashEmail("guest@example.com"))
                .hasSize(64);
        assertThat(hasher.hashEmail("guest@example.com")).isNotEqualTo(hasher.hashEmail("host@example.com"));
    }

    @Test
    void 전화번호_해시는_구분_기호를_무시한다() {
        assertThat(hasher.hashPhone("010-1234-5678")).isEqualTo(hasher.hashPhone("01012345678"));
    }

    @Test
    void 조회용_해시는_키가_다르면_달라진다() {
        LookupHasher other = new LookupHasher(
                new CryptoProperties(ENCRYPTION_KEY, "another-hash-key-another-hash-key-123"));

        assertThat(other.hashEmail("guest@example.com")).isNotEqualTo(hasher.hashEmail("guest@example.com"));
    }

    @Test
    void 토큰_해시는_원문과_일치할_때만_통과한다() {
        String token = HashUtils.randomToken(32);
        String stored = HashUtils.sha256Hex(token);

        assertThat(stored).hasSize(64).isNotEqualTo(token);
        assertThat(HashUtils.matches(token, stored)).isTrue();
        assertThat(HashUtils.matches(token + "x", stored)).isFalse();
        assertThat(HashUtils.matches(null, stored)).isFalse();
    }

    @Test
    void 무작위_토큰과_인증코드의_형식() {
        assertThat(HashUtils.randomToken(32)).hasSize(43).isNotEqualTo(HashUtils.randomToken(32));
        assertThat(HashUtils.randomNumericCode(6)).matches("\\d{6}");
    }
}
