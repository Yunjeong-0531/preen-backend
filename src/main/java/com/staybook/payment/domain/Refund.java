package com.staybook.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 환불 내역. amount는 실제 환불액, penalty_amount는 위약금이다. */
@Entity
@Table(name = "refunds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Refund {

    public enum Status {
        /** PG 취소 요청 전 */
        REQUESTED,
        COMPLETED,
        /** PG 취소 실패. 예약 취소는 되돌리지 않으며 수동 처리가 필요하다 */
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long paymentId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal penaltyAmount;

    private String reason;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private Status status;

    private LocalDateTime refundedAt;

    public static Refund requested(Long paymentId, BigDecimal amount, BigDecimal penaltyAmount, String reason) {
        Refund refund = new Refund();
        refund.paymentId = paymentId;
        refund.amount = amount;
        refund.penaltyAmount = penaltyAmount;
        refund.reason = reason;
        refund.status = Status.REQUESTED;
        return refund;
    }

    public void complete(LocalDateTime now) {
        this.status = Status.COMPLETED;
        this.refundedAt = now;
    }

    public void fail() {
        this.status = Status.FAILED;
    }
}
