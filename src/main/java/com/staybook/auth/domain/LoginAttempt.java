package com.staybook.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 로그인 시도 기록. 없는 계정으로 시도하면 userId가 null이다. */
@Entity
@Table(name = "login_attempts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Column(nullable = false, columnDefinition = "char(64)")
    private String loginIdHash;

    @Column(length = 45)
    private String ipAddress;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30)
    private LoginResult result;

    @Column(nullable = false)
    private LocalDateTime attemptedAt;

    public static LoginAttempt of(Long userId, String loginIdHash, String ipAddress, LoginResult result,
                                  LocalDateTime now) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.userId = userId;
        attempt.loginIdHash = loginIdHash;
        attempt.ipAddress = ipAddress;
        attempt.result = result;
        attempt.attemptedAt = now;
        return attempt;
    }
}
