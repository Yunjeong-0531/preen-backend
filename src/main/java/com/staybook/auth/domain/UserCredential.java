package com.staybook.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 이메일 로그인용 비밀번호 정보. 소셜 전용 회원은 행이 없다. */
@Entity
@Table(name = "user_credentials")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCredential {

    @Id
    private Long userId;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private LocalDateTime passwordChangedAt;

    @Column(nullable = false, columnDefinition = "smallint")
    private int failedLoginCount;

    private LocalDateTime lockedUntil;

    public static UserCredential create(Long userId, String passwordHash, LocalDateTime now) {
        UserCredential credential = new UserCredential();
        credential.userId = userId;
        credential.passwordHash = passwordHash;
        credential.passwordChangedAt = now;
        credential.failedLoginCount = 0;
        return credential;
    }

    public boolean isLocked(LocalDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** 잠금 시간이 지났으면 실패 횟수를 처음부터 다시 센다. */
    public void clearExpiredLock(LocalDateTime now) {
        if (lockedUntil != null && !lockedUntil.isAfter(now)) {
            lockedUntil = null;
            failedLoginCount = 0;
        }
    }

    /** 실패 횟수를 올리고, 기준 횟수에 이르면 잠근다. */
    public void recordFailure(LocalDateTime now, int maxFailedLogins, Duration lockDuration) {
        failedLoginCount++;
        if (failedLoginCount >= maxFailedLogins) {
            lockedUntil = now.plus(lockDuration);
        }
    }

    public void recordSuccess() {
        failedLoginCount = 0;
        lockedUntil = null;
    }

    public void changePassword(String passwordHash, LocalDateTime now) {
        this.passwordHash = passwordHash;
        this.passwordChangedAt = now;
        this.failedLoginCount = 0;
        this.lockedUntil = null;
    }
}
