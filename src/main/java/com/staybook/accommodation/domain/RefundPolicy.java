package com.staybook.accommodation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 숙소별 환불정책. 내용을 고치지 않고 새 버전을 만들어 active를 옮긴다.
 * 기존 예약이 과거 버전을 참조하기 때문이다.
 */
@Entity
@Table(name = "refund_policies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long accommodationId;

    @Column(nullable = false, length = 20)
    private String version;

    @Column(columnDefinition = "text")
    private String policyText;

    @Column(nullable = false)
    private boolean active;

    public static RefundPolicy createActive(Long accommodationId, String version, String policyText) {
        RefundPolicy policy = new RefundPolicy();
        policy.accommodationId = accommodationId;
        policy.version = version;
        policy.policyText = policyText;
        policy.active = true;
        return policy;
    }

    public void deactivate() {
        this.active = false;
    }
}
