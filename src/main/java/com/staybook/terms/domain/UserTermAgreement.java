package com.staybook.terms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회원별·약관 버전별 동의 기록. (user_id, term_id) 유니크. */
@Entity
@Table(name = "user_term_agreements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserTermAgreement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long termId;

    @Column(name = "is_agreed", nullable = false)
    private boolean agreed;

    @Column(nullable = false)
    private LocalDateTime agreedAt;

    public static UserTermAgreement create(Long userId, Long termId, boolean agreed, LocalDateTime now) {
        UserTermAgreement agreement = new UserTermAgreement();
        agreement.userId = userId;
        agreement.termId = termId;
        agreement.agreed = agreed;
        agreement.agreedAt = now;
        return agreement;
    }

    public void change(boolean agreed, LocalDateTime now) {
        this.agreed = agreed;
        this.agreedAt = now;
    }
}
