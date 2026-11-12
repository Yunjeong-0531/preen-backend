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

/** 소셜 로그인 연결. 제공자의 액세스 토큰은 저장하지 않는다. */
@Entity
@Table(name = "user_social_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserSocialAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private SocialProvider provider;

    @Column(nullable = false, length = 100)
    private String providerUserId;

    @Column(nullable = false)
    private LocalDateTime connectedAt;

    public static UserSocialAccount create(Long userId, SocialProvider provider, String providerUserId,
                                           LocalDateTime now) {
        UserSocialAccount account = new UserSocialAccount();
        account.userId = userId;
        account.provider = provider;
        account.providerUserId = providerUserId;
        account.connectedAt = now;
        return account;
    }
}
