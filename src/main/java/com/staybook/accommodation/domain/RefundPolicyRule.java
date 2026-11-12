package com.staybook.accommodation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** "체크인 daysBefore일 전까지 취소하면 refundRate만큼 환불". refundRate는 0.00~1.00. */
@Entity
@Table(name = "refund_policy_rules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundPolicyRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long refundPolicyId;

    @Column(nullable = false)
    private int daysBefore;

    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal refundRate;

    public static RefundPolicyRule of(Long refundPolicyId, int daysBefore, BigDecimal refundRate) {
        RefundPolicyRule rule = new RefundPolicyRule();
        rule.refundPolicyId = refundPolicyId;
        rule.daysBefore = daysBefore;
        rule.refundRate = refundRate;
        return rule;
    }
}
