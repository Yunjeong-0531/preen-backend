package com.staybook.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 인증코드. 코드는 해시로만 저장한다. 가입 전에도 쓰이므로 회원과 연결하지 않는다. */
@Entity
@Table(name = "verification_codes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VerificationCode {

    public static final String TARGET_EMAIL = "EMAIL";
    public static final String PURPOSE_SIGNUP = "SIGNUP";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String targetType;

    @Column(nullable = false, columnDefinition = "char(64)")
    private String targetHash;

    @Column(nullable = false, length = 10)
    private String purpose;

    @Column(nullable = false, columnDefinition = "char(64)")
    private String codeHash;

    @Column(nullable = false, columnDefinition = "smallint")
    private int attemptCount;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime verifiedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static VerificationCode emailSignup(String targetHash, String codeHash, LocalDateTime now, Duration ttl) {
        VerificationCode code = new VerificationCode();
        code.targetType = TARGET_EMAIL;
        code.targetHash = targetHash;
        code.purpose = PURPOSE_SIGNUP;
        code.codeHash = codeHash;
        code.attemptCount = 0;
        code.expiresAt = now.plus(ttl);
        code.createdAt = now;
        return code;
    }

    public boolean isExpired(LocalDateTime now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }

    public void recordFailedAttempt() {
        attemptCount++;
    }

    public void markVerified(LocalDateTime now) {
        this.verifiedAt = now;
    }
}
